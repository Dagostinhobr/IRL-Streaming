package app.streamlink.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.streamlink.chat.ChatManager
import app.streamlink.chat.ChatStatus
import app.streamlink.data.AppSettings
import app.streamlink.data.ChatProvider
import app.streamlink.data.SettingsRepository
import app.streamlink.data.StreamTarget
import app.streamlink.data.TargetKind
import app.streamlink.stream.StreamEngine
import app.streamlink.stream.StreamService
import app.streamlink.stream.StreamStatus
import app.streamlink.util.AudioDevices
import app.streamlink.util.AudioInputOption
import app.streamlink.util.AudioRouting
import app.streamlink.util.CameraCapabilities
import app.streamlink.util.DeepLinkParser
import app.streamlink.util.OrientationSupport
import app.streamlink.util.TorchController
import app.streamlink.util.VdoNinja
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.log10

class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val engine = StreamEngine(application)
    private val chatManager = ChatManager(viewModelScope)
    private val torchController = TorchController(application)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val settings = repository.settings
    val stream = engine.state

    /**
     * Nível do microfone (0..1) para o VU meter da tela principal.
     *
     * Na transmissão nativa vem do encoder; no estúdio P2P vem do
     * `AnalyserNode` do WebView, para o medidor continuar a funcionar enquanto
     * o VDO.Ninja é dono do microfone.
     */
    private val _micLevel = MutableStateFlow(0f)
    val micLevel = _micLevel.asStateFlow()
    val messages = chatManager.messages
    val chatStatus = chatManager.status

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted = _permissionsGranted.asStateFlow()

    private val _audioDevices = MutableStateFlow<List<AudioInputOption>>(emptyList())
    val audioDevices = _audioDevices.asStateFlow()

    private val _cameras = MutableStateFlow<List<CameraCapabilities.CameraInfo>>(emptyList())
    val cameras = _cameras.asStateFlow()

    /** Câmeras nativas + USB para escolher a câmera principal. */
    private val _cameraOptions = MutableStateFlow<List<CameraCapabilities.CameraInfo>>(emptyList())
    val cameraOptions = _cameraOptions.asStateFlow()

    private val _dualCameraSupported = MutableStateFlow(false)
    val dualCameraSupported = _dualCameraSupported.asStateFlow()

    /** URL do estúdio P2P que a interface deve abrir (consumida uma vez). */
    private val _studioUrl = MutableStateFlow<String?>(null)
    val studioUrl = _studioUrl.asStateFlow()

    // Estúdio P2P incorporado: a Home continua visível e controla o WebView.
    private val _vdoLive = MutableStateFlow(false)
    val vdoLive = _vdoLive.asStateFlow()

    private val _vdoUrl = MutableStateFlow<String?>(null)
    val vdoUrl = _vdoUrl.asStateFlow()

    private val _vdoStartedAt = MutableStateFlow<Long?>(null)
    val vdoStartedAt = _vdoStartedAt.asStateFlow()

    private val _vdoRecording = MutableStateFlow(false)
    val vdoRecording = _vdoRecording.asStateFlow()

    private val _vdoRecordPath = MutableStateFlow<String?>(null)
    val vdoRecordPath = _vdoRecordPath.asStateFlow()

    private val _vdoMuted = MutableStateFlow(false)
    val vdoMuted = _vdoMuted.asStateFlow()

    private val _vdoTorchOn = MutableStateFlow(false)
    val vdoTorchOn = _vdoTorchOn.asStateFlow()

    private val _studioCommands = MutableSharedFlow<StudioCommand>(extraBufferCapacity = 8)
    val studioCommands = _studioCommands.asSharedFlow()

    private var previewSurface: SurfaceView? = null
    private val deviceLandscape =
        MutableStateFlow(OrientationSupport.isDeviceLandscape(application))

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var chatReconnectJob: Job? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) {
            refreshAudioDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) {
            refreshAudioDevices()
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val attached = intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED
            refreshCameras()
            refreshAudioDevices()
            if (attached) {
                val usb = CameraCapabilities.usbCameras(getApplication()).firstOrNull()
                if (usb != null) _events.tryEmit("${usb.label} detetada: ${usb.detail}")
            }
        }
    }

    init {
        val app = getApplication<Application>()
        runCatching {
            val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.registerAudioDeviceCallback(audioDeviceCallback, null)
        }
        runCatching {
            ContextCompat.registerReceiver(
                app,
                usbReceiver,
                IntentFilter().apply {
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                },
                // As ações USB são broadcasts protegidos do sistema; o receiver
                // apenas atualiza as listas locais de câmera/áudio.
                ContextCompat.RECEIVER_EXPORTED
            )
        }
        initialiseTextToSpeech()
        startStatsLoop()
        startMicLevelLoop()
        observeStreamStatusForService()
        observeChatForTextToSpeech()
        observeEngineMicLevel()
        refreshAudioDevices()
        refreshCameras()
    }

    // ---------------------------------------------------------------------
    // Permissões / preview
    // ---------------------------------------------------------------------

    fun updatePermissions(granted: Boolean) {
        _permissionsGranted.value = granted
    }

    fun onPreviewReady(view: SurfaceView) {
        previewSurface = view
        if (!_permissionsGranted.value) return
        // Durante o estúdio P2P a câmera pertence ao WebView. O preview nativo
        // é reanexado automaticamente quando a transmissão termina.
        if (_vdoLive.value) return
        engine.attachPreview(view, resolved(settings.value))
    }

    private fun resolved(settings: AppSettings): AppSettings = settings.copy(
        orientation = OrientationSupport.resolve(settings.orientation, deviceLandscape.value)
    )

    /**
     * A tela principal avisa quando o sensor muda de orientação. No modo
     * automático, o encoder acompanha o dispositivo (vertical/horizontal).
     */
    fun onDeviceOrientationChanged(landscape: Boolean) {
        deviceLandscape.value = landscape
        val current = settings.value
        if (current.orientation != app.streamlink.data.VideoOrientation.AUTO) return
        if (_vdoLive.value) return // O WebView/VDO.Ninja já acompanha o sensor.
        val orientation = OrientationSupport.resolve(current.orientation, landscape)
        if (engine.state.value.isLive || engine.state.value.isBusy) {
            engine.applyLiveOrientation(orientation)
        } else {
            engine.prepare(resolved(current))
        }
    }

    /** Alterna Auto (sensor) → Vertical → Horizontal no botão da Home. */
    fun cycleOrientation() {
        val next = OrientationSupport.next(settings.value.orientation)
        // A mudança de orientação não mostra aviso: o vídeo/UI já reflete o novo modo.
        updateSettings { it.copy(orientation = next) }
    }

    fun tapToFocus(view: SurfaceView, x: Float, y: Float) {
        engine.tapToFocus(view, x, y)
    }

    fun zoomBy(factor: Float) = engine.zoomBy(factor)
    fun setZoom(level: Float) = engine.setZoom(level)
    fun switchCamera() {
        if (_vdoLive.value) {
            _vdoTorchOn.value = false
            _studioCommands.tryEmit(StudioCommand.FlipCamera)
            return
        }
        if (settings.value.dualCamera.enabled) {
            swapDualCameras()
        } else {
            engine.switchCamera()
        }
    }

    /** Troca a câmera principal com a da janela PiP. */
    fun swapDualCameras() {
        val dual = settings.value.dualCamera
        val pair = CameraCapabilities.resolvePair(
            getApplication(),
            dual.mainCameraId,
            dual.pipCameraId
        ) ?: return
        updateSettings {
            it.copy(
                dualCamera = it.dualCamera.copy(
                    mainCameraId = pair.second.id,
                    pipCameraId = pair.first.id
                )
            )
        }
    }
    fun toggleTorch() {
        if (_vdoLive.value) {
            val on = !_vdoTorchOn.value
            _vdoTorchOn.value = on
            _studioCommands.tryEmit(StudioCommand.SetTorch(on))
            return
        }
        engine.toggleTorch()
    }

    fun toggleMute() {
        if (_vdoLive.value) {
            val muted = !_vdoMuted.value
            _vdoMuted.value = muted
            _studioCommands.tryEmit(StudioCommand.SetMuted(muted))
            return
        }
        engine.toggleMute()
    }

    // ---------------------------------------------------------------------
    // Callbacks do estúdio incorporado (WebView)
    // ---------------------------------------------------------------------

    fun onWebMicLevel(linear: Float) {
        if (!_vdoLive.value) return
        val target = if (_vdoMuted.value) 0f else linearToMeter(linear)
        val previous = _micLevel.value
        val next = if (target >= previous) {
            previous + (target - previous) * 0.65f
        } else {
            previous * 0.80f
        }
        _micLevel.value = if (next < 0.004f) 0f else next
    }

    fun onWebRecordingStarted(path: String) {
        _vdoRecording.value = true
        _vdoRecordPath.value = path.ifBlank { null }
        val format = if (path.endsWith(".webm", ignoreCase = true)) "WebM" else "MP4"
        _events.tryEmit("Gravando transmissão em $format")
    }

    fun onWebRecordingStopped(path: String?) {
        _vdoRecording.value = false
        _vdoRecordPath.value = path
        if (!path.isNullOrBlank()) {
            _events.tryEmit("Gravação salva em $path")
        } else {
            _events.tryEmit("A gravação terminou, mas o ficheiro ficou vazio.")
        }
    }

    fun onWebRecordingError(message: String) {
        _vdoRecording.value = false
        _events.tryEmit("Não foi possível gravar: $message")
    }

    fun onWebTorchState(on: Boolean) {
        _vdoTorchOn.value = on
    }

    fun onWebTorchUnavailable() {
        val on = _vdoTorchOn.value
        val ok = torchController.setTorch(on)
        _vdoTorchOn.value = if (ok) on else false
        if (!ok) _events.tryEmit("Lanterna indisponível nesta câmera.")
    }

    fun clearError() = engine.clearError()

    // ---------------------------------------------------------------------
    // Transmissão
    // ---------------------------------------------------------------------

    fun startStream() {
        if (!_permissionsGranted.value) {
            _events.tryEmit("Conceda acesso à câmera e ao microfone para iniciar.")
            return
        }
        val current = settings.value
        val target = current.selectedTarget()
        if (target == null) {
            _events.tryEmit("Adicione um destino antes de transmitir.")
            return
        }
        if (target.kind == TargetKind.VDO_P2P) {
            val vdo = current.vdoNinja
            if (VdoNinja.pushId(vdo).isBlank()) {
                _events.tryEmit("Informe o ID de ligação ou a sala do VDO.Ninja.")
                return
            }
            // O estúdio WebRTC corre embutido por baixo da Home, sem navegar
            // para outra tela. Assim os controles do app continuam disponíveis.
            startVdo(vdo)
            return
        }
        if (target.endpoint().isBlank()) {
            _events.tryEmit("Configure a URL e a chave do destino \"${target.name}\".")
            return
        }

        val started = engine.start(resolved(current), target)
        if (started) {
            if (current.chat.provider == ChatProvider.TWITCH && current.chat.channel.isNotBlank()) {
                chatManager.connect(current.chat)
            }
        } else {
            engine.state.value.error?.let { _events.tryEmit(it) }
        }
    }

    fun stopStream() {
        if (_vdoLive.value) {
            stopVdo()
            return
        }
        engine.stop()
        chatManager.disconnect()
    }

    private fun startVdo(vdo: app.streamlink.data.VdoNinjaSettings) {
        if (_vdoLive.value) return
        // Liberta câmera e microfone nativos antes de o WebView pedir getUserMedia.
        engine.pausePreview()
        AudioRouting.clearPreferredInput(getApplication())
        _micLevel.value = 0f
        _vdoMuted.value = false
        _vdoTorchOn.value = false
        _vdoRecording.value = false
        _vdoRecordPath.value = null
        _vdoStartedAt.value = System.currentTimeMillis()
        _vdoUrl.value = VdoNinja.studioUrl(vdo)
        _vdoLive.value = true
        startStreamService()

        val current = settings.value
        if (current.chat.provider == ChatProvider.TWITCH && current.chat.channel.isNotBlank()) {
            chatManager.connect(current.chat)
        }
        _events.tryEmit("Transmissão P2P iniciada. O estúdio corre em segundo plano.")
    }

    /**
     * Encerra o estúdio P2P e devolve a Home ao preview nativo. Se houver
     * gravação em curso, dá tempo ao MediaRecorder de escrever o último bloco
     * antes de destruir o WebView.
     */
    fun stopVdo() {
        if (!_vdoLive.value) return
        val wasRecording = _vdoRecording.value
        if (wasRecording) _studioCommands.tryEmit(StudioCommand.StopRecording)

        viewModelScope.launch {
            if (wasRecording) delay(1_400)

            _vdoLive.value = false
            _vdoUrl.value = null
            _vdoStartedAt.value = null
            _vdoRecording.value = false
            _vdoRecordPath.value = null
            _vdoMuted.value = false
            if (_vdoTorchOn.value) {
                torchController.setTorch(false)
                _vdoTorchOn.value = false
            }
            _micLevel.value = 0f
            chatManager.disconnect()
            stopStreamService()

            // O WebView é removido nesta composição; espera que ele liberte a
            // câmera antes de reanexar o preview nativo.
            delay(900)
            previewSurface?.let { surface ->
                if (!_vdoLive.value) {
                    engine.attachPreview(surface, resolved(settings.value))
                }
            }
        }
    }

    fun toggleRecording() {
        if (_vdoLive.value) {
            if (_vdoRecording.value) {
                _studioCommands.tryEmit(StudioCommand.StopRecording)
            } else {
                _studioCommands.tryEmit(StudioCommand.StartRecording)
            }
            return
        }
        if (engine.state.value.recording) {
            engine.stopRecording()
            _events.tryEmit("Gravação salva em ${engine.state.value.recordPath ?: "armazenamento do app"}")
        } else if (engine.startRecording()) {
            _events.tryEmit("Gravando transmissão em MP4")
        }
    }

    // ---------------------------------------------------------------------
    // Configurações / destinos
    // ---------------------------------------------------------------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = repository.settings.value
        repository.update(transform)
        val after = repository.settings.value
        applySettingsChange(before, after)
    }

    private fun applySettingsChange(before: AppSettings, after: AppSettings) {
        val live = engine.state.value.status == StreamStatus.LIVE

        if (before.audioInputId != after.audioInputId) {
            engine.applyAudioInput(after)
        }

        if (before.dualCamera != after.dualCamera) {
            engine.applyDualCamera(after)
        }

        if (before.chat != after.chat) {
            chatReconnectJob?.cancel()
            chatReconnectJob = viewModelScope.launch {
                // Debounce: evita reconectar a cada caractere digitado no canal/token.
                delay(1_200)
                val chat = repository.settings.value.chat
                val shouldConnect = chat.provider == ChatProvider.TWITCH &&
                    chat.channel.isNotBlank() &&
                    (engine.state.value.isLive || chatManager.status.value != ChatStatus.DISCONNECTED)
                if (shouldConnect) {
                    chatManager.connect(chat)
                } else if (chat.provider == ChatProvider.DISABLED || chat.channel.isBlank()) {
                    chatManager.disconnect()
                }
            }
        }

        if (!live) {
            engine.prepare(resolved(after))
            return
        }

        if (before.orientation != after.orientation) {
            engine.applyLiveOrientation(
                OrientationSupport.resolve(after.orientation, deviceLandscape.value)
            )
        }

        val encoderChanged = before.resolution != after.resolution ||
            before.fps != after.fps ||
            before.videoBitrateKbps != after.videoBitrateKbps ||
            before.codec != after.codec ||
            before.audioBitrateKbps != after.audioBitrateKbps ||
            before.sampleRate != after.sampleRate ||
            before.stereo != after.stereo
        if (encoderChanged) {
            _events.tryEmit("Resolução/FPS/codec só mudam na próxima transmissão.")
        }
        if (before.cameraId != after.cameraId) {
            _events.tryEmit("Câmera principal só muda na próxima transmissão.")
        }
    }

    fun selectTarget(id: String) = repository.update { it.copy(selectedTargetId = id) }

    /**
     * Atualiza a lista de microfones disponíveis. Deve ser chamada ao abrir os ajustes
     * (e quando um dispositivo USB é conectado/removido).
     */
    fun refreshAudioDevices() {
        val devices = AudioDevices.list(getApplication())
        _audioDevices.value = devices
        val current = settings.value
        val usb = devices.firstOrNull { it.isUsb }
        val currentExists = devices.any { it.id == current.audioInputId }
        if (current.audioInputId >= 0 && !currentExists) {
            // O microfone escolhido foi removido: volta ao USB disponível ou ao interno.
            updateSettings {
                it.copy(
                    audioInputId = usb?.id ?: -1,
                    audioInputLabel = usb?.detail.orEmpty()
                )
            }
        } else if (current.audioInputId < 0 && usb != null) {
            // Entrada automática: se a câmera USB tem microfone, usa-o já.
            updateSettings {
                it.copy(audioInputId = usb.id, audioInputLabel = usb.detail)
            }
        }
    }

    /** Seleciona um microfone específico; null volta para o microfone interno. */
    fun selectAudioInput(option: AudioInputOption?) {
        updateSettings {
            it.copy(
                audioInputId = option?.id ?: -1,
                audioInputLabel = option?.detail.orEmpty()
            )
        }
        engine.applyAudioInput(settings.value)
    }

    // ---------------------------------------------------------------------
    // PiP com duas câmeras
    // ---------------------------------------------------------------------

    /** Recarrega a lista de câmeras e a capacidade de duas câmeras simultâneas. */
    fun refreshCameras() {
        val app = getApplication<Application>()
        _cameras.value = CameraCapabilities.cameras(app)
        _cameraOptions.value = CameraCapabilities.selectableCameras(app)
        _dualCameraSupported.value = CameraCapabilities.supportsDualCamera(app)

        // Se a câmera USB escolhida acabou de ser ligada, prepara-a já (fora de live).
        val current = settings.value
        if (CameraCapabilities.isUsbCameraId(current.cameraId) &&
            !engine.state.value.isLive &&
            !engine.state.value.isBusy
        ) {
            engine.prepare(resolved(current))
        }
    }

    /** Define a câmera principal; câmeras USB aparecem como `usb:<deviceName>`. */
    fun selectCamera(camera: CameraCapabilities.CameraInfo) {
        updateSettings {
            it.copy(cameraId = camera.id, cameraLabel = camera.detail)
        }
    }

    fun updateDualCamera(
        transform: (app.streamlink.data.DualCameraSettings) -> app.streamlink.data.DualCameraSettings
    ) {
        updateSettings { it.copy(dualCamera = transform(it.dualCamera)) }
    }

    fun clearDualCameraError() {
        engine.clearDualCameraError()
    }

    // ---------------------------------------------------------------------
    // VDO.Ninja
    // ---------------------------------------------------------------------

    fun updateVdoNinja(transform: (app.streamlink.data.VdoNinjaSettings) -> app.streamlink.data.VdoNinjaSettings) {
        repository.update { it.copy(vdoNinja = transform(it.vdoNinja)) }
    }

    fun regenerateVdoNinjaRoom() {
        updateVdoNinja { it.copy(room = VdoNinja.newRoom(), whipToken = "") }
    }

    /** Gera um ID de ligação aleatório (Stream ID do VDO.Ninja). */
    fun regenerateVdoNinjaId() {
        updateVdoNinja { it.copy(streamId = VdoNinja.newRoom(), whipToken = "") }
    }

    /** Consome a solicitação de abrir o estúdio P2P (evita reabrir ao recompor a tela). */
    fun consumeStudioUrl() {
        _studioUrl.value = null
    }

    /**
     * O estúdio P2P precisa do microfone para o WebView. Enquanto ele está
     * aberto, pausamos a captura nativa do VU meter para não bloquear o
     * `getUserMedia` do VDO.Ninja.
     */
    fun pauseMicMonitoring() = engine.setMicMonitoring(false)

    /**
     * Volta a medir o microfone ao sair do estúdio. A pequena espera dá tempo
     * ao WebView de libertar o `AudioRecord`.
     */
    fun resumeMicMonitoring() {
        viewModelScope.launch {
            delay(300)
            engine.setMicMonitoring(true)
        }
    }

    /**
     * Cria (ou atualiza) um destino **P2P**: ao transmitir, o app abre o estúdio
     * WebRTC do VDO.Ninja com o link `?push=ID&password=...` em vez de usar WHIP.
     */
    fun applyVdoP2PTarget(): Boolean {
        val vdo = settings.value.vdoNinja
        if (VdoNinja.pushId(vdo).isBlank()) {
            _events.tryEmit("Informe um ID de ligação ou uma sala do VDO.Ninja.")
            return false
        }
        val target = StreamTarget(
            name = "VDO.Ninja (P2P)",
            url = VdoNinja.pushUrl(vdo),
            streamKey = "",
            kind = TargetKind.VDO_P2P
        )
        repository.update { current ->
            val remaining = current.targets.filterNot {
                it.kind == TargetKind.VDO_P2P ||
                    // Remove o destino WHIP criado pela versão antiga do botão.
                    (it.name == "VDO.Ninja" && it.url.contains("whip.vdo.ninja"))
            }
            current.copy(targets = remaining + target, selectedTargetId = target.id)
        }
        _events.tryEmit("Destino P2P pronto. Toque em transmitir para abrir o estúdio.")
        return true
    }

    /**
     * Cria (ou atualiza) um destino WHIP apontando para a sala/ID do VDO.Ninja
     * e o seleciona para transmissão nativa com a câmera do app.
     */
    fun applyVdoNinjaTarget(): Boolean {
        val vdo = settings.value.vdoNinja
        if (VdoNinja.pushId(vdo).isBlank()) {
            _events.tryEmit("Informe um ID de ligação ou gere uma sala do VDO.Ninja.")
            return false
        }
        val target = StreamTarget(
            name = "VDO.Ninja (WHIP)",
            url = VdoNinja.whipEndpoint(vdo),
            streamKey = ""
        )
        repository.update { current ->
            val remaining = current.targets.filterNot {
                it.name == "VDO.Ninja (WHIP)" || it.name == "VDO.Ninja"
            }
            current.copy(targets = remaining + target, selectedTargetId = target.id)
        }
        _events.tryEmit("Destino WHIP (experimental) pronto. Toque em transmitir.")
        return true
    }

    fun addTarget(target: StreamTarget) = repository.update {
        it.copy(targets = it.targets + target, selectedTargetId = target.id)
    }

    fun updateTarget(target: StreamTarget) = repository.update { current ->
        current.copy(targets = current.targets.map { if (it.id == target.id) target else it })
    }

    fun deleteTarget(id: String) = repository.update { current ->
        current.copy(targets = current.targets.filterNot { it.id == id })
    }

    fun importDeepLink(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = DeepLinkParser.parse(uri)
            if (imported == null) {
                _events.tryEmit("Link inválido ou sem destinos.")
                return@launch
            }
            repository.update { current ->
                current.copy(
                    targets = current.targets + imported.targets,
                    selectedTargetId = imported.targets.first().id,
                    codec = imported.codec ?: current.codec
                )
            }
            _events.tryEmit("${imported.targets.size} destino(s) importado(s).")
        }
    }

    // ---------------------------------------------------------------------
    // Chat
    // ---------------------------------------------------------------------

    fun connectChat() {
        val chat = settings.value.chat
        if (chat.channel.isBlank()) {
            _events.tryEmit("Informe o canal da Twitch nas configurações.")
            return
        }
        chatManager.connect(chat)
    }

    fun disconnectChat() = chatManager.disconnect()

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        if (!chatManager.send(text)) {
            _events.tryEmit("Para enviar mensagens, informe usuário e token OAuth nas configurações.")
        }
    }

    fun canSendChat(): Boolean = chatManager.canSend()

    fun clearChat() = chatManager.clearHistory()

    // ---------------------------------------------------------------------
    // Internos
    // ---------------------------------------------------------------------

    private fun startStatsLoop() {
        viewModelScope.launch {
            while (isActive) {
                engine.refreshStats()
                delay(1_000)
            }
        }
    }

    /**
     * O medidor de microfone precisa de ~16 atualizações por segundo para
     * parecer instantâneo. É um StateFlow próprio, então só o componente do
     * VU meter recompõe (a tela inteira não é reconstruída).
     */
    private fun startMicLevelLoop() {
        viewModelScope.launch {
            while (isActive) {
                engine.refreshMicLevel()
                delay(60)
            }
        }
    }

    /** Copia o VU meter nativo, exceto enquanto o estúdio P2P fornece o nível. */
    private fun observeEngineMicLevel() {
        viewModelScope.launch {
            engine.micLevel.collect { level ->
                if (!_vdoLive.value) _micLevel.value = level
            }
        }
    }

    /** Converte amplitude linear (0..1) para a escala do medidor (dB -60..0). */
    private fun linearToMeter(linear: Float): Float {
        if (linear <= 0.0006f) return 0f
        val db = (20.0 * log10(linear.toDouble())).coerceIn(-60.0, 0.0)
        return ((db + 60.0) / 60.0).toFloat()
    }

    private fun observeStreamStatusForService() {
        viewModelScope.launch {
            engine.state.map { it.status }.distinctUntilChanged().collect { status ->
                when (status) {
                    StreamStatus.CONNECTING,
                    StreamStatus.LIVE,
                    StreamStatus.RECONNECTING -> startStreamService()
                    // O serviço também mantém o estúdio P2P vivo enquanto o
                    // encoder nativo está em IDLE.
                    else -> if (!_vdoLive.value) stopStreamService()
                }
            }
        }
    }

    private fun startStreamService() {
        val context = getApplication<Application>()
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StreamService::class.java)
            )
        }
    }

    private fun stopStreamService() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, StreamService::class.java))
    }

    private fun initialiseTextToSpeech() {
        val context = getApplication<Application>()
        textToSpeech = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                runCatching { textToSpeech?.language = Locale.getDefault() }
            }
        }
    }

    private fun observeChatForTextToSpeech() {
        viewModelScope.launch {
            var lastId: String? = null
            chatManager.messages.collect { list ->
                val last = list.lastOrNull() ?: return@collect
                if (last.id == lastId) return@collect
                lastId = last.id
                if (last.isSystem || last.isDeleted) return@collect
                val chat = settings.value.chat
                if (!chat.textToSpeech || !ttsReady || !engine.state.value.isLive) return@collect
                textToSpeech?.speak(
                    "${last.displayName} disse: ${last.text}",
                    TextToSpeech.QUEUE_ADD,
                    null,
                    last.id
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            val audio = getApplication<Application>()
                .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        runCatching { getApplication<Application>().unregisterReceiver(usbReceiver) }
        engine.release()
        chatManager.disconnect()
        stopStreamService()
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        textToSpeech = null
    }
}
