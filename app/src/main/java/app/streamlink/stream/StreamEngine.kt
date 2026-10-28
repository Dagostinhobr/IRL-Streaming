package app.streamlink.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import app.streamlink.data.AppSettings
import app.streamlink.data.CodecSetting
import app.streamlink.data.DualCameraSettings
import app.streamlink.data.PipPosition
import app.streamlink.data.StreamProtocol
import app.streamlink.data.StreamTarget
import app.streamlink.data.VideoOrientation
import app.streamlink.util.AudioDevices
import app.streamlink.util.CameraCapabilities
import com.pedro.common.ConnectChecker
import com.pedro.common.StreamingStatsReport
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.base.StreamBase
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericStream
import com.pedro.library.whip.WhipStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10

/**
 * Encapsula o RootEncoder (RTMP/RTMPS/SRT/RTSP) e a câmera.
 * Toda a API é pensada para ser chamada da main thread (como no exemplo oficial da lib).
 */
class StreamEngine(private val context: Context) : ConnectChecker {

    private val _state = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = _state.asStateFlow()

    /** Nível do microfone (0..1) para o VU meter, atualizado em tempo real. */
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private var stream: StreamBase? = null
    private var encoderProtocol: StreamProtocol? = null
    private var activeCameraId: String? = null
    private var preparedConfig: StreamConfig? = null
    private var previewView: SurfaceView? = null
    private var autoReconnect = true
    private var recordFile: File? = null

    // Composição PiP (segunda câmera sobre a principal)
    private var pipFilter: PipFilterRender? = null
    private var secondCamera: SecondCamera? = null
    private var pipCameraId: String? = null
    private var pendingPipId: String = ""
    private var dualSettings: DualCameraSettings = DualCameraSettings()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Garante que o encoder correto esteja criado: WHIP (WebRTC) para links http(s)
     * e GenericStream (RTMP/RTMPS/SRT/RTSP/UDP) para os demais protocolos.
     */
    private fun encoderFor(protocol: StreamProtocol, requestedCameraId: String): StreamBase {
        // Se a câmera USB foi removida, cai para a câmera nativa em vez de ficar em ecrã negro.
        val cameraId = if (CameraCapabilities.isUsbCameraId(requestedCameraId) &&
            CameraCapabilities.usbCameras(context).none { it.id == requestedCameraId }
        ) {
            Log.w(TAG, "Câmera USB ausente; a usar câmera nativa")
            ""
        } else {
            requestedCameraId
        }
        val current = stream
        val sameCamera = activeCameraId == cameraId
        val compatible = when (protocol) {
            StreamProtocol.WHIP -> current is WhipStream
            else -> current != null && current !is WhipStream
        }
        if (compatible && current != null && sameCamera) return current

        releaseDualCamera()
        runCatching { current?.release() }
        preparedConfig = null
        _micLevel.value = 0f

        // Fontes explícitas: a de áudio mede o nível para o VU meter sem
        // alterar o PCM entregue ao encoder.
        val usb = CameraCapabilities.isUsbCameraId(cameraId)
        val video: VideoSource = if (usb) {
            UvcVideoSource(context, CameraCapabilities.usbDeviceName(cameraId))
        } else {
            Camera2Source(context).also { camera ->
                if (cameraId.isNotBlank()) {
                    runCatching { camera.openCameraId(cameraId) }
                        .onFailure { Log.w(TAG, "Câmera principal indisponível: $cameraId", it) }
                }
            }
        }
        val mic = MeteredMicrophoneSource()
        val created: StreamBase = if (protocol == StreamProtocol.WHIP) {
            WhipStream(context, this, video, mic)
        } else {
            GenericStream(context, this, video, mic)
        }
        created.setFpsListener { fps ->
            _state.update { it.copy(fps = fps) }
        }
        encoderProtocol = protocol
        activeCameraId = cameraId
        stream = created
        _state.update { it.copy(usbCamera = usb) }
        return created
    }

    fun prepare(
        settings: AppSettings,
        protocol: StreamProtocol = settings.selectedTarget()?.protocol ?: StreamProtocol.RTMP
    ): Boolean {
        if (stream?.isStreaming == true) return true

        val config = settings.toStreamConfig(protocol)
        val hadPreview = stream?.isOnPreview == true
        val encoder = encoderFor(protocol, settings.cameraId)
        if (preparedConfig == config) {
            applyAudioInput(settings)
            // Garante o VU meter ativo mesmo quando a configuração não mudou.
            microphone()?.startMonitoring()
            applyDualCamera(settings)
            return true
        }

        return try {
            val previewTarget = previewView
            val wasPreviewing = hadPreview || encoder.isOnPreview
            if (wasPreviewing) {
                // O GL é reiniciado abaixo e libera todos os filtros: recria o PiP depois.
                releaseDualCamera()
                encoder.stopPreview(true)
            }
            encoder.setVideoCodec(if (config.codec == CodecSetting.H264) VideoCodec.H264 else VideoCodec.H265)

            val videoOk = encoder.prepareVideo(
                width = config.width,
                height = config.height,
                bitrate = config.videoBitrate,
                fps = config.fps,
                iFrameInterval = 2,
                rotation = config.rotation
            )
            val audioOk = encoder.prepareAudio(
                sampleRate = config.sampleRate,
                isStereo = config.stereo,
                bitrate = config.audioBitrate,
                echoCanceler = config.echoCancellation,
                noiseSuppressor = config.noiseSuppression
            )

            if (!videoOk || !audioOk) {
                setError("O dispositivo não suporta esta configuração de vídeo/áudio.")
                return false
            }

            applyAudioInput(settings)
            // O microfone passa a captar já no preview para alimentar o VU meter.
            microphone()?.startMonitoring()

            preparedConfig = config
            _state.update {
                it.copy(
                    resolutionLabel = "${config.outputWidth}x${config.outputHeight}",
                    orientation = settings.orientation,
                    muted = microphone()?.isMuted() == true
                )
            }
            updateZoomRange()

            if (wasPreviewing && previewTarget != null && previewTarget.holder.surface.isValid) {
                runCatching { encoder.startPreview(previewTarget, true) }
                    .onFailure { Log.w(TAG, "Falha ao reanexar preview", it) }
            }
            applyDualCamera(settings)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao preparar encoder", error)
            preparedConfig = null
            setError(error.message ?: "Falha ao preparar a câmera/encoder.")
            false
        }
    }

    fun attachPreview(view: SurfaceView, settings: AppSettings) {
        previewView = view
        if (!prepare(settings)) return
        val encoder = stream ?: return
        if (encoder.isOnPreview) return
        // O preview está sendo (re)iniciado; o GL pode ter liberado o filtro antigo.
        releaseDualCamera()
        runCatching {
            encoder.startPreview(view, true)
            updateZoomRange()
        }.onFailure {
            Log.w(TAG, "Falha ao iniciar preview", it)
        }
        applyDualCamera(settings)
    }

    /**
     * Liberta a câmera e o microfone do preview nativo.
     *
     * Usado ao entrar no estúdio P2P: o WebView precisa de abrir a câmera e o
     * microfone com `getUserMedia`, e o Android não permite dois clientes no
     * mesmo sensor em muitos aparelhos. A configuração preparada é mantida para
     * que o preview nativo possa voltar rapidamente ao sair do estúdio.
     */
    fun pausePreview() {
        releaseDualCamera()
        val encoder = stream
        if (encoder != null) {
            runCatching { if (encoder.isOnPreview) encoder.stopPreview(true) }
        }
        // Encerra o AudioRecord do VU meter para deixar o microfone ao WebRTC.
        microphone()?.stopMonitoring()
        previewView = null
        _micLevel.value = 0f
        _state.update {
            it.copy(
                zoom = 1f,
                dualCameraActive = false,
                dualCameraError = null
            )
        }
    }

    fun start(settings: AppSettings, target: StreamTarget): Boolean {
        if (!prepare(settings, target.protocol)) return false
        val encoder = stream ?: return false
        val endpoint = target.endpoint()
        if (endpoint.isBlank()) {
            setError("Configure a URL de ingest do destino \"${target.name}\".")
            return false
        }
        if (!endpoint.startsWith("rtmp", true) && !endpoint.startsWith("srt", true) &&
            !endpoint.startsWith("rtsp", true) && !endpoint.startsWith("udp", true) &&
            !endpoint.startsWith("http", true)
        ) {
            setError("Protocolo não suportado. Use rtmp://, rtmps://, srt://, rtsp://, udp:// ou https:// (WHIP)")
            return false
        }

        autoReconnect = settings.autoReconnect
        _state.update {
            it.copy(
                status = StreamStatus.CONNECTING,
                startedAt = System.currentTimeMillis(),
                bitrateBps = 0,
                fps = 0,
                droppedVideoFrames = 0,
                error = null,
                reconnecting = false,
                reconnectAttempt = 0
            )
        }

        return try {
            encoder.getStreamClient().setReTries(if (settings.autoReconnect) 5 else 0)
            encoder.startStream(endpoint)
            applyDualCamera(settings)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao iniciar stream", error)
            setError(error.message ?: "Não foi possível iniciar a transmissão.")
            false
        }
    }

    fun stop() {
        val encoder = stream
        runCatching { if (encoder?.isRecording == true) encoder.stopRecord() }
        runCatching { if (encoder?.isStreaming == true) encoder.stopStream() }
        preparedConfig = null
        recordFile = null
        _state.update {
            it.copy(
                status = StreamStatus.IDLE,
                bitrateBps = 0,
                fps = 0,
                droppedVideoFrames = 0,
                congestionPercent = 0f,
                startedAt = null,
                recording = false,
                recordPath = null,
                reconnecting = false,
                reconnectAttempt = 0
            )
        }
    }

    fun release() {
        releaseDualCamera()
        microphone()?.stopMonitoring()
        runCatching { stream?.release() }
        stream = null
        preparedConfig = null
        activeCameraId = null
        previewView = null
        _micLevel.value = 0f
        _state.value = StreamUiState()
    }

    // ---------------------------------------------------------------------
    // Câmera
    // ---------------------------------------------------------------------

    /**
     * Aplica uma nova orientação ao preview/transmissão em curso.
     *
     * A resolução de saída só é reconstruída no próximo `prepare`; aqui
     * rodamos a câmera imediatamente para o vídeo acompanhar o sensor (modo
     * automático) sem interromper a conexão.
     */
    fun applyLiveOrientation(orientation: VideoOrientation) {
        val encoder = stream ?: return
        val rotation = if (orientation == VideoOrientation.PORTRAIT) 90 else 0
        runCatching { encoder.getGlInterface().setRotation(rotation) }
            .onFailure { Log.w(TAG, "Falha ao aplicar orientação", it) }
        _state.update { it.copy(orientation = orientation) }
    }

    fun switchCamera() {
        val camera = camera() ?: return
        runCatching {
            camera.switchCamera()
            _state.update {
                it.copy(
                    frontCamera = camera.getCameraFacing() == CameraHelper.Facing.FRONT,
                    torchOn = false
                )
            }
            updateZoomRange()
        }.onFailure { setError("Não foi possível trocar de câmera.") }
    }

    fun toggleTorch() {
        val camera = camera() ?: return
        if (camera.getCameraFacing() == CameraHelper.Facing.FRONT) return
        runCatching {
            if (_state.value.torchOn) {
                camera.disableLantern()
            } else {
                camera.enableLantern()
            }
            _state.update { it.copy(torchOn = !it.torchOn) }
        }.onFailure { setError("Lanterna indisponível nesta câmera.") }
    }

    fun toggleMute() {
        val mic = microphone() ?: return
        runCatching {
            if (mic.isMuted()) mic.unMute() else mic.mute()
            _state.update { it.copy(muted = mic.isMuted()) }
        }
    }

    fun setMuted(muted: Boolean) {
        val mic = microphone() ?: return
        runCatching {
            if (muted) mic.mute() else mic.unMute()
            _state.update { it.copy(muted = mic.isMuted()) }
        }
    }

    /**
     * Liga/desliga a captura usada pelo VU meter. Não interrompe uma
     * transmissão/gravação nativa em curso, porque o encoder precisa do áudio.
     */
    fun setMicMonitoring(enabled: Boolean) {
        val mic = microphone() ?: return
        if (enabled) {
            mic.startMonitoring()
            return
        }
        _micLevel.value = 0f
        val encoder = stream
        val nativeCapture = encoder?.isStreaming == true || encoder?.isRecording == true
        if (!nativeCapture) mic.stopMonitoring()
    }

    fun setZoom(level: Float) {
        val camera = camera() ?: return
        val range = runCatching { camera.getZoomRange() }.getOrNull() ?: return
        val clamped = level.coerceIn(range.lower, range.upper)
        runCatching { camera.setZoom(clamped) }
        _state.update { it.copy(zoom = clamped, maxZoom = range.upper) }
    }

    fun zoomBy(factor: Float) {
        setZoom(_state.value.zoom * factor)
    }

    fun updateZoomRange() {
        val camera = camera() ?: return
        runCatching {
            val range = camera.getZoomRange()
            _state.update { it.copy(maxZoom = range.upper) }
        }
    }

    fun tapToFocus(view: SurfaceView, x: Float, y: Float) {
        val camera = camera() ?: return
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0)
        try {
            camera.tapToFocus(view, event)
        } catch (error: Exception) {
            Log.w(TAG, "Foco manual indisponível", error)
        } finally {
            event.recycle()
        }
    }

    // ---------------------------------------------------------------------
    // PiP com duas câmeras
    // ---------------------------------------------------------------------

    /** O aparelho suporta abrir duas câmeras ao mesmo tempo? */
    fun dualCameraSupported(): Boolean = CameraCapabilities.supportsDualCamera(context)

    /** Limpa o aviso de erro do modo PiP (chamado ao fechar o diálogo/aviso). */
    fun clearDualCameraError() {
        _state.update { it.copy(dualCameraError = null) }
    }

    /**
     * Sincroniza a composição PiP com as preferências.
     *
     * A câmera principal continua sendo a do RootEncoder (preview, zoom, lanterna
     * e troca de câmera funcionam normalmente). A segunda câmera é aberta aqui e
     * desenhada sobre o vídeo por um [PipFilterRender], com posição e tamanho
     * configuráveis. Nada é alterado quando o modo está desligado.
     */
    fun applyDualCamera(settings: AppSettings) {
        dualSettings = settings.dualCamera
        val encoder = stream ?: return
        if (CameraCapabilities.isUsbCameraId(settings.cameraId)) {
            releaseDualCamera()
            _state.update {
                it.copy(
                    dualCameraActive = false,
                    dualCameraError = if (settings.dualCamera.enabled) {
                        "PiP com duas câmeras não está disponível com câmera USB."
                    } else {
                        null
                    }
                )
            }
            return
        }
        if (!settings.dualCamera.enabled) {
            releaseDualCamera()
            _state.update { it.copy(dualCameraError = null) }
            return
        }
        if (!CameraCapabilities.supportsDualCamera(context)) {
            releaseDualCamera()
            _state.update {
                it.copy(
                    dualCameraActive = false,
                    dualCameraError = "Este aparelho não permite duas câmeras ao mesmo tempo."
                )
            }
            return
        }
        val pair = CameraCapabilities.resolvePair(
            context,
            settings.dualCamera.mainCameraId,
            settings.dualCamera.pipCameraId
        )
        if (pair == null) {
            releaseDualCamera()
            _state.update {
                it.copy(
                    dualCameraActive = false,
                    dualCameraError = "Não há duas câmeras disponíveis para o modo PiP."
                )
            }
            return
        }
        val (main, pip) = pair
        pendingPipId = pip.id

        // Garante que a câmera principal seja a escolhida.
        val mainCamera = camera()
        if (mainCamera != null && mainCamera.getCurrentCameraId() != main.id) {
            secondCamera?.stop()
            pipCameraId = null
            runCatching { mainCamera.openCameraId(main.id) }
                .onFailure { Log.w(TAG, "Falha ao abrir a câmera principal", it) }
            _state.update { it.copy(frontCamera = main.isFront, torchOn = false) }
            updateZoomRange()
            // Dá tempo do Camera2Source liberar a câmera antes de abrir a segunda.
            mainHandler.postDelayed({ startPipWhenReady() }, 500)
        } else {
            startPipWhenReady()
        }

        val filter = ensurePipFilter(encoder) ?: return
        updatePipLayout(filter, settings.dualCamera)
    }

    private fun ensurePipFilter(encoder: StreamBase): PipFilterRender? {
        pipFilter?.let { existing ->
            if (!existing.isReleased) return existing
            // O GL foi reiniciado e liberou o filtro: a Surface antiga não serve mais.
            pipFilter = null
            secondCamera?.stop()
            pipCameraId = null
        }
        val gl = runCatching { encoder.getGlInterface() }.getOrNull() ?: return null
        val filter = PipFilterRender { texture ->
            // Executado na main thread quando o filtro é inicializado no GL.
            val id = pendingPipId
            if (id.isNotBlank()) startPipCamera(id, texture)
        }
        pipFilter = filter
        runCatching { gl.addFilter(filter) }
            .onFailure { Log.w(TAG, "Falha ao adicionar o filtro PiP", it) }
        return filter
    }

    private fun startPipWhenReady() {
        val filter = pipFilter
        val surface = filter?.surface
        val texture = filter?.surfaceTexture
        if (surface == null || texture == null) {
            // O filtro ainda não foi inicializado pelo GL; o callback fará o resto.
            return
        }
        val id = pendingPipId
        if (id.isBlank()) return
        if (secondCamera?.isRunning == true && pipCameraId == id) {
            _state.update { it.copy(dualCameraActive = true, dualCameraError = null) }
            return
        }
        startPipCamera(id, texture)
    }

    private fun startPipCamera(cameraId: String, texture: android.graphics.SurfaceTexture) {
        val filter = pipFilter ?: return
        val surface = filter.surface ?: return
        if (secondCamera?.isRunning == true && pipCameraId == cameraId) return
        val controller = secondCamera ?: SecondCamera(context).also { secondCamera = it }
        pipCameraId = cameraId
        controller.start(
            cameraId = cameraId,
            surface = surface,
            surfaceTexture = texture,
            onStarted = {
                mainHandler.post {
                    updatePipLayout(pipFilter, dualSettings)
                    _state.update { it.copy(dualCameraActive = true, dualCameraError = null) }
                }
            },
            onError = { message ->
                mainHandler.post {
                    Log.w(TAG, "PiP desativado: $message")
                    releaseDualCamera()
                    _state.update {
                        it.copy(dualCameraActive = false, dualCameraError = message)
                    }
                }
            }
        )
    }

    /**
     * Posiciona a janela sobre o vídeo. As medidas são percentuais da imagem
     * final (0–100), com origem no canto superior esquerdo.
     */
    private fun updatePipLayout(
        filter: PipFilterRender?,
        settings: DualCameraSettings
    ) {
        filter ?: return
        val config = preparedConfig ?: return
        val canvasW = config.outputWidth.toFloat().coerceAtLeast(1f)
        val canvasH = config.outputHeight.toFloat().coerceAtLeast(1f)
        val margin = settings.marginPercent.coerceIn(0, 20) / 100f
        val widthFraction = settings.sizePercent.coerceIn(10, 50) / 100f
        val rotation = ((settings.rotation % 360) + 360) % 360

        // Proporção da imagem da segunda câmera (paisagem, como o Camera2 entrega).
        val output = secondCamera?.outputSize
        val imageAspect = if (output != null && output.width > 0) {
            output.height.toFloat() / output.width
        } else {
            9f / 16f
        }
        val displayedAspect = if (rotation == 90 || rotation == 270) {
            1f / imageAspect
        } else {
            imageAspect
        }

        var visualW = widthFraction
        var visualH = visualW * canvasW * displayedAspect / canvasH
        if (visualH > 0.8f) {
            visualH = 0.8f
            visualW = visualH * canvasH / (displayedAspect * canvasW)
        }

        val x = when (settings.position) {
            PipPosition.TOP_LEFT, PipPosition.BOTTOM_LEFT -> margin
            PipPosition.TOP_RIGHT, PipPosition.BOTTOM_RIGHT -> 1f - margin - visualW
            PipPosition.CENTER -> (1f - visualW) / 2f
        }
        val y = when (settings.position) {
            PipPosition.TOP_LEFT, PipPosition.TOP_RIGHT -> margin
            PipPosition.BOTTOM_LEFT, PipPosition.BOTTOM_RIGHT -> 1f - margin - visualH
            PipPosition.CENTER -> (1f - visualH) / 2f
        }

        runCatching {
            filter.setLayout(
                leftPercent = x * 100f,
                topPercent = y * 100f,
                widthPercent = visualW * 100f,
                heightPercent = visualH * 100f,
                rotationDegrees = rotation
            )
        }.onFailure { Log.w(TAG, "Falha ao posicionar o PiP", it) }
    }

    private fun releaseDualCamera(removeFilter: Boolean = true) {
        secondCamera?.release()
        secondCamera = null
        pipCameraId = null
        pendingPipId = ""
        val filter = pipFilter
        pipFilter = null
        if (removeFilter && filter != null) {
            val gl = runCatching { stream?.getGlInterface() }.getOrNull()
            runCatching { gl?.removeFilter(filter) }
        }
        _state.update { it.copy(dualCameraActive = false) }
    }

    // ---------------------------------------------------------------------
    // Gravação local
    // ---------------------------------------------------------------------

    fun startRecording(): Boolean {
        val encoder = stream ?: return false
        if (encoder.isRecording) return true
        return try {
            val dir = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
            val name = "streamlink_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
            val file = File(dir, name)
            recordFile = file
            encoder.startRecord(file.absolutePath) { status ->
                if (status == RecordController.Status.STOPPED) {
                    _state.update { it.copy(recording = false) }
                }
            }
            _state.update { it.copy(recording = true, recordPath = file.absolutePath) }
            true
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao gravar", error)
            setError("Não foi possível iniciar a gravação: ${error.message}")
            false
        }
    }

    fun stopRecording() {
        val encoder = stream ?: return
        if (!encoder.isRecording) return
        runCatching { encoder.stopRecord() }
        _state.update { it.copy(recording = false) }
    }

    // ---------------------------------------------------------------------
    // Estatísticas / ConnectChecker
    // ---------------------------------------------------------------------

    fun refreshStats() {
        val encoder = stream ?: return
        if (!encoder.isStreaming) return
        runCatching {
            val dropped = encoder.getStreamClient().getDroppedVideoFrames()
            _state.update { it.copy(droppedVideoFrames = dropped) }
        }
    }

    override fun onConnectionStarted(url: String) {
        _state.update { it.copy(status = StreamStatus.CONNECTING, error = null) }
    }

    override fun onConnectionSuccess() {
        _state.update {
            it.copy(
                status = StreamStatus.LIVE,
                startedAt = it.startedAt ?: System.currentTimeMillis(),
                error = null,
                reconnecting = false,
                reconnectAttempt = 0
            )
        }
        updateZoomRange()
    }

    override fun onConnectionFailed(reason: String) {
        val encoder = stream
        val retried = autoReconnect && encoder != null &&
            runCatching { encoder.getStreamClient().reTry(3000, reason, null) }.getOrDefault(false)
        if (retried) {
            _state.update {
                it.copy(
                    status = StreamStatus.RECONNECTING,
                    reconnecting = true,
                    reconnectAttempt = it.reconnectAttempt + 1,
                    bitrateBps = 0,
                    fps = 0,
                    error = reason
                )
            }
        } else {
            runCatching { encoder?.stopStream() }
            _state.update {
                it.copy(
                    status = StreamStatus.ERROR,
                    reconnecting = false,
                    bitrateBps = 0,
                    fps = 0,
                    error = reason
                )
            }
        }
    }

    override fun onDisconnect() {
        if (_state.value.status == StreamStatus.RECONNECTING) return
        _state.update {
            it.copy(
                status = if (it.status == StreamStatus.CONNECTING) StreamStatus.ERROR else StreamStatus.IDLE,
                bitrateBps = 0,
                fps = 0,
                congestionPercent = 0f,
                reconnecting = false
            )
        }
    }

    override fun onAuthError() {
        setError("Falha de autenticação no servidor de destino.")
    }

    override fun onAuthSuccess() = Unit

    override fun onNewBitrate(bitrate: Long) {
        _state.update { it.copy(bitrateBps = bitrate) }
    }

    override fun onStreamingStats(report: StreamingStatsReport) {
        _state.update {
            it.copy(
                bitrateBps = report.bitrate,
                congestionPercent = report.queueCongestionPercent
            )
        }
    }

    private fun camera(): Camera2Source? = stream?.videoSource as? Camera2Source

    private fun microphone(): MeteredMicrophoneSource? =
        stream?.audioSource as? MeteredMicrophoneSource

    /**
     * Atualiza o VU meter com o pico captado desde a última chamada.
     * Ataque rápido e decaimento suave, para o medidor acompanhar a fala sem
     * "tremer". Chamado pela interface a cada ~60 ms.
     */
    fun refreshMicLevel() {
        val mic = microphone()
        val raw = if (mic?.isRunning() == true) mic.consumePeak() else 0f
        val target = if (_state.value.muted) 0f else linearToMeter(raw)
        val previous = _micLevel.value
        val next = if (target >= previous) {
            previous + (target - previous) * 0.75f
        } else {
            previous * 0.78f
        }
        _micLevel.value = if (next < 0.004f) 0f else next
    }

    /** Converte amplitude linear (0..1) para a escala do medidor (dB -60..0). */
    private fun linearToMeter(linear: Float): Float {
        if (linear <= 0.0006f) return 0f
        val db = (20.0 * log10(linear.toDouble())).coerceIn(MIN_MIC_DB, 0.0)
        return ((db - MIN_MIC_DB) / -MIN_MIC_DB).toFloat()
    }

    /**
     * Aplica a entrada de áudio escolhida (microfone USB, fone, Bluetooth...) ao encoder.
     * Com [AppSettings.audioInputId] = -1 usa o microfone interno do aparelho.
     */
    fun applyAudioInput(settings: AppSettings) {
        val mic = microphone() ?: return
        val device = if (settings.audioInputId < 0) {
            AudioDevices.builtIn(context)
        } else {
            AudioDevices.find(context, settings.audioInputId)
        } ?: return
        runCatching { mic.setPreferredDevice(device) }
            .onFailure { Log.w(TAG, "Não foi possível selecionar o microfone", it) }
    }

    /** Nome do microfone em uso, útil para mostrar na interface. */
    fun currentAudioInputName(settings: AppSettings): String? {
        val device = if (settings.audioInputId < 0) {
            AudioDevices.builtIn(context)
        } else {
            AudioDevices.find(context, settings.audioInputId)
        } ?: return null
        return runCatching { device.productName?.toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: if (settings.audioInputId < 0) "Microfone do aparelho" else settings.audioInputLabel
    }

    private fun setError(message: String) {
        _state.update { it.copy(status = StreamStatus.ERROR, error = message) }
    }

    fun clearError() {
        _state.update {
            if (it.status == StreamStatus.ERROR) it.copy(status = StreamStatus.IDLE, error = null)
            else it.copy(error = null)
        }
    }

    private fun AppSettings.toStreamConfig(protocol: StreamProtocol): StreamConfig {
        val rotation = if (orientation == VideoOrientation.PORTRAIT) 90 else 0
        val videoBitrate = videoBitrateKbps * 1000
        val outputWidth = if (orientation == VideoOrientation.PORTRAIT) resolution.height else resolution.width
        val outputHeight = if (orientation == VideoOrientation.PORTRAIT) resolution.width else resolution.height
        // O WHIP do VDO.Ninja é mais compatível com H.264; HEVC fica restrito a RTMP/RTSP.
        val effectiveCodec = if (protocol == StreamProtocol.WHIP) CodecSetting.H264 else codec
        return StreamConfig(
            protocol = protocol,
            cameraId = cameraId,
            width = resolution.width,
            height = resolution.height,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            fps = fps,
            videoBitrate = videoBitrate,
            audioBitrate = audioBitrateKbps * 1000,
            sampleRate = sampleRate,
            stereo = stereo,
            echoCancellation = echoCancellation,
            noiseSuppression = noiseSuppression,
            rotation = rotation,
            codec = effectiveCodec
        )
    }

    private data class StreamConfig(
        val protocol: StreamProtocol,
        val cameraId: String,
        val width: Int,
        val height: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val fps: Int,
        val videoBitrate: Int,
        val audioBitrate: Int,
        val sampleRate: Int,
        val stereo: Boolean,
        val echoCancellation: Boolean,
        val noiseSuppression: Boolean,
        val rotation: Int,
        val codec: CodecSetting
    )

    companion object {
        private const val TAG = "StreamEngine"

        /** Fundo de escala do VU meter: abaixo disto o microfone é considerado em silêncio. */
        private const val MIN_MIC_DB = -60.0
    }
}
