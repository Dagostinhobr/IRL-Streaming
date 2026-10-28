package app.streamlink.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.streamlink.BuildConfig
import app.streamlink.R
import app.streamlink.data.AppLanguage
import app.streamlink.data.ChatProvider
import app.streamlink.data.CodecSetting
import app.streamlink.data.PipPosition
import app.streamlink.data.ResolutionPreset
import app.streamlink.data.VideoOrientation
import app.streamlink.i18n.localizedLabel
import app.streamlink.i18n.tr
import app.streamlink.stream.StreamStatus
import app.streamlink.ui.StreamViewModel
import app.streamlink.ui.components.ChoiceChips
import app.streamlink.ui.components.LabelValueRow
import app.streamlink.ui.components.SectionDivider
import app.streamlink.ui.components.SettingsSection
import app.streamlink.ui.components.SwitchSettingRow
import app.streamlink.ui.theme.Ink
import app.streamlink.ui.theme.Mint
import app.streamlink.ui.theme.TextMuted
import app.streamlink.ui.theme.TextPrimary
import app.streamlink.ui.theme.Warn
import app.streamlink.util.AudioInputOption
import app.streamlink.util.CameraCapabilities

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: StreamViewModel,
    onBack: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenVdoNinja: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.stream.collectAsStateWithLifecycle()
    val audioDevices by viewModel.audioDevices.collectAsStateWithLifecycle()
    val cameras by viewModel.cameras.collectAsStateWithLifecycle()
    val cameraOptions by viewModel.cameraOptions.collectAsStateWithLifecycle()
    val dualCameraSupported by viewModel.dualCameraSupported.collectAsStateWithLifecycle()
    var tokenVisible by remember { mutableStateOf(false) }
    val target = settings.selectedTarget()

    LaunchedEffect(Unit) {
        viewModel.refreshAudioDevices()
        viewModel.refreshCameras()
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text(tr("Ajustes", "Settings"), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Voltar", "Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                SettingsSection(tr("Transmissão", "Streaming")) {
                    LabelValueRow(tr("Destino", "Target"), target?.name ?: tr("Nenhum", "None"))
                    TextButton(
                        onClick = onOpenTargets,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(tr("Gerenciar destinos", "Manage targets"), color = Mint, fontSize = 12.sp)
                    }

                    SectionDivider()
                    SettingLabel(tr("Resolução", "Resolution"))
                    ChoiceChips(
                        options = ResolutionPreset.entries,
                        selected = settings.resolution,
                        label = { it.label },
                        onSelect = { value ->
                            viewModel.updateSettings { it.copy(resolution = value) }
                        }
                    )

                    SectionDivider()
                    SettingLabel("FPS")
                    ChoiceChips(
                        options = listOf(24, 30, 60),
                        selected = settings.fps,
                        label = { "$it" },
                        onSelect = { value ->
                            viewModel.updateSettings { it.copy(fps = value) }
                        }
                    )

                    SectionDivider()
                    SettingLabel(tr("Bitrate de vídeo", "Video bitrate"))
                    ChoiceChips(
                        options = BITRATE_OPTIONS,
                        selected = settings.videoBitrateKbps,
                        label = { kbpsLabel(it) },
                        onSelect = { value ->
                            viewModel.updateSettings { it.copy(videoBitrateKbps = value) }
                        }
                    )

                    SectionDivider()
                    SettingLabel("Codec")
                    ChoiceChips(
                        options = CodecSetting.entries,
                        selected = settings.codec,
                        label = { it.label },
                        onSelect = { value ->
                            viewModel.updateSettings { it.copy(codec = value) }
                        }
                    )

                    SectionDivider()
                    SettingLabel(tr("Orientação do vídeo", "Video orientation"))
                    ChoiceChips(
                        options = VideoOrientation.entries,
                        selected = settings.orientation,
                        label = { it.localizedLabel() },
                        onSelect = { value ->
                            viewModel.updateSettings { it.copy(orientation = value) }
                        }
                    )
                    if (state.status == StreamStatus.LIVE) {
                        Text(
                            tr("Pare a transmissão para aplicar mudanças de resolução, FPS ou orientação.", "Stop the stream to apply resolution, FPS or orientation changes."),
                            color = Warn,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                SettingsSection(tr("Áudio", "Audio")) {
                    SettingLabel(tr("Microfone", "Microphone"))
                    AudioInputSelector(
                        options = audioDevices,
                        selectedId = settings.audioInputId,
                        onSelect = viewModel::selectAudioInput,
                        onRefresh = viewModel::refreshAudioDevices
                    )
                    if (audioDevices.any { it.isUsb }) {
                        Text(
                            tr(
                                "Microfone USB detetado. Se pertence à câmera ligada, é escolhido " +
                                    "automaticamente; pode trocar a entrada a qualquer momento, " +
                                    "inclusive durante a live.",
                                "USB microphone detected. If it belongs to the connected camera " +
                                    "it is selected automatically; you can switch the input at any " +
                                    "time, including during the live stream."
                            ),
                            color = Mint,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    SectionDivider()
                    SettingLabel("Bitrate")
                    ChoiceChips(
                        options = listOf(64, 96, 128, 192),
                        selected = settings.audioBitrateKbps,
                        label = { "$it kbps" },
                        onSelect = { value ->
                            viewModel.updateSettings { it.copy(audioBitrateKbps = value) }
                        }
                    )
                    SectionDivider()
                    SettingLabel(tr("Taxa de amostragem", "Sample rate"))
                    ChoiceChips(
                        options = listOf(44_100, 48_000),
                        selected = settings.sampleRate,
                        label = { "${it / 1000} kHz" },
                        onSelect = { value ->
                            viewModel.updateSettings { it.copy(sampleRate = value) }
                        }
                    )
                    SectionDivider()
                    SwitchSettingRow(
                        title = tr("Estéreo", "Stereo"),
                        checked = settings.stereo,
                        onCheckedChange = { value ->
                            viewModel.updateSettings { it.copy(stereo = value) }
                        }
                    )
                    SectionDivider()
                    SwitchSettingRow(
                        title = tr("Cancelamento de eco", "Echo cancellation"),
                        checked = settings.echoCancellation,
                        onCheckedChange = { value ->
                            viewModel.updateSettings { it.copy(echoCancellation = value) }
                        }
                    )
                    SectionDivider()
                    SwitchSettingRow(
                        title = tr("Supressão de ruído", "Noise suppression"),
                        checked = settings.noiseSuppression,
                        onCheckedChange = { value ->
                            viewModel.updateSettings { it.copy(noiseSuppression = value) }
                        }
                    )
                }
            }

            item {
                SettingsSection(tr("Câmera", "Camera")) {
                    Text(
                        tr(
                            "Escolha a câmera principal. Câmeras USB (UVC) ligadas por OTG " +
                                "aparecem automaticamente na lista, mesmo que não sejam " +
                                "reconhecidas pelo sistema como câmera nativa.",
                            "Choose the main camera. USB (UVC) cameras connected via OTG " +
                                "appear automatically in the list, even if the system does not " +
                                "recognise them as a native camera."
                        ),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingLabel(tr("Câmera principal", "Main camera"))
                    val selectedCameraId = settings.cameraId
                    CameraChoiceRow(
                        camera = CameraCapabilities.CameraInfo(
                            id = "",
                            facing = -1,
                            label = tr("Automática (traseira)", "Automatic (rear)"),
                            detail = tr("Câmera padrão do aparelho", "Device default camera")
                        ),
                        selected = selectedCameraId.isBlank(),
                        onClick = {
                            updateCamera(viewModel, "")
                        }
                    )
                    cameraOptions.forEach { camera ->
                        CameraChoiceRow(
                            camera = camera,
                            selected = camera.id == selectedCameraId,
                            onClick = { viewModel.selectCamera(camera) }
                        )
                    }
                    if (cameraOptions.isEmpty()) {
                        Text(
                            tr("Nenhuma câmera detectada.", "No camera detected."),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = viewModel::refreshCameras) {
                        Text(tr("Atualizar lista", "Refresh list"), color = Mint, fontSize = 12.sp)
                    }
                    if (settings.cameraId.startsWith("usb:")) {
                        Text(
                            tr(
                                "Câmera USB ativa: zoom, lanterna e PiP ficam indisponíveis. " +
                                    "O áudio USB da câmera é usado automaticamente quando existe.",
                                "USB camera active: zoom, torch and PiP are unavailable. " +
                                    "The camera's USB audio is used automatically when present."
                            ),
                            color = Mint,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            item {
                SettingsSection(tr("Câmera e conexão", "Camera and connection")) {
                    SwitchSettingRow(
                        title = tr("Manter tela ligada", "Keep screen on"),
                        subtitle = tr("Evita que o Android apague a tela durante a live", "Prevents Android from turning the screen off during the live stream"),
                        checked = settings.keepScreenOn,
                        onCheckedChange = { value ->
                            viewModel.updateSettings { it.copy(keepScreenOn = value) }
                        }
                    )
                    SectionDivider()
                    SwitchSettingRow(
                        title = tr("Reconectar automaticamente", "Reconnect automatically"),
                        subtitle = tr("Até 5 tentativas com 3s de intervalo", "Up to 5 attempts, 3s apart"),
                        checked = settings.autoReconnect,
                        onCheckedChange = { value ->
                            viewModel.updateSettings { it.copy(autoReconnect = value) }
                        }
                    )
                    SectionDivider()
                    Text(
                        tr("Toque no preview para focar, use pinça para zoom e os botões laterais para lanterna, troca de câmera e mudo.", "Tap the preview to focus, pinch to zoom and use the side buttons for torch, camera switch and mute."),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            item {
                SettingsSection(tr("PiP com duas câmeras", "PiP with two cameras")) {
                    Text(
                        tr(
                            "Desenha uma segunda câmera como janela sobre o vídeo transmitido e gravado. " +
                                "A câmera principal continua com foco, zoom e lanterna.",
                            "Draws a second camera as a window over the streamed and recorded video. " +
                                "The main camera keeps focus, zoom and torch."
                        ),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    val usbMainCamera = settings.cameraId.startsWith("usb:")
                    SwitchSettingRow(
                        title = tr("Ativar janela PiP", "Enable PiP window"),
                        subtitle = when {
                            usbMainCamera -> tr("Indisponível com câmera USB", "Unavailable with USB camera")
                            dualCameraSupported -> tr("Abre duas câmeras ao mesmo tempo", "Opens two cameras at the same time")
                            else -> tr("Este aparelho não suporta duas câmeras simultâneas", "This device does not support two simultaneous cameras")
                        },
                        checked = settings.dualCamera.enabled && dualCameraSupported && !usbMainCamera,
                        enabled = dualCameraSupported && !usbMainCamera,
                        onCheckedChange = { value ->
                            viewModel.updateDualCamera { it.copy(enabled = value) }
                        }
                    )

                    if (!dualCameraSupported) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr(
                                "O Android só libera duas câmeras simultâneas em aparelhos que anunciam " +
                                    "essa combinação. Neste modelo o modo fica desabilitado.",
                                "Android only allows two simultaneous cameras on devices that advertise " +
                                    "that combination. On this model the mode is disabled."
                            ),
                            color = Warn,
                            fontSize = 11.sp
                        )
                    } else if (settings.dualCamera.enabled) {
                        val mainId = settings.dualCamera.mainCameraId.ifBlank {
                            cameras.firstOrNull { !it.isFront }?.id
                                ?: cameras.firstOrNull()?.id.orEmpty()
                        }
                        val pipId = settings.dualCamera.pipCameraId.ifBlank {
                            cameras.firstOrNull { it.id != mainId }?.id.orEmpty()
                        }

                        SectionDivider()
                        SettingLabel(tr("Câmera principal", "Main camera"))
                        cameras.forEach { camera ->
                            CameraChoiceRow(
                                camera = camera,
                                selected = camera.id == mainId,
                                onClick = {
                                    viewModel.updateDualCamera { dual ->
                                        dual.copy(
                                            mainCameraId = camera.id,
                                            pipCameraId = if (dual.pipCameraId == camera.id) "" else dual.pipCameraId
                                        )
                                    }
                                }
                            )
                        }

                        SectionDivider()
                        SettingLabel(tr("Câmera da janela (PiP)", "PiP window camera"))
                        cameras.filter { it.id != mainId }.forEach { camera ->
                            CameraChoiceRow(
                                camera = camera,
                                selected = camera.id == pipId,
                                onClick = {
                                    viewModel.updateDualCamera { it.copy(pipCameraId = camera.id) }
                                }
                            )
                        }

                        SectionDivider()
                        SettingLabel(tr("Posição da janela", "Window position"))
                        ChoiceChips(
                            options = PipPosition.entries,
                            selected = settings.dualCamera.position,
                            label = { it.localizedLabel() },
                            onSelect = { value ->
                                viewModel.updateDualCamera { it.copy(position = value) }
                            }
                        )

                        SectionDivider()
                        SettingLabel(tr("Tamanho da janela", "Window size"))
                        ChoiceChips(
                            options = listOf(20, 30, 40),
                            selected = settings.dualCamera.sizePercent,
                            label = { "$it%" },
                            onSelect = { value ->
                                viewModel.updateDualCamera { it.copy(sizePercent = value) }
                            }
                        )

                        SectionDivider()
                        SettingLabel(tr("Rotação da janela", "Window rotation"))
                        ChoiceChips(
                            options = listOf(0, 90, 180, 270),
                            selected = settings.dualCamera.rotation,
                            label = { "$it°" },
                            onSelect = { value ->
                                viewModel.updateDualCamera { it.copy(rotation = value) }
                            }
                        )

                        SectionDivider()
                        TextButton(onClick = viewModel::swapDualCameras) {
                            Text(tr("Trocar principal com a janela", "Swap main and window"), color = Mint, fontSize = 12.sp)
                        }

                        val error = state.dualCameraError
                        if (error != null) {
                            Text(error, color = Warn, fontSize = 11.sp)
                            TextButton(onClick = viewModel::clearDualCameraError) {
                                Text(tr("Dispensar aviso", "Dismiss warning"), color = Mint, fontSize = 11.sp)
                            }
                        } else if (state.dualCameraActive) {
                            Text(tr("Janela PiP ativa no vídeo.", "PiP window active in the video."), color = Mint, fontSize = 11.sp)
                        }
                    }
                }
            }

            item {
                SettingsSection("VDO.Ninja") {
                    Text(
                        tr(
                            "Use um ID de ligação ou uma sala, gere links P2P e abra o estúdio no app. " +
                                "A publicação nativa via WHIP é experimental.",
                            "Use a stream ID or a room, generate P2P links and open the studio in the app. " +
                                "Native WHIP publishing is experimental."
                        ),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    if (settings.vdoNinja.streamId.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        LabelValueRow(tr("ID de ligação", "Stream ID"), settings.vdoNinja.streamId)
                    }
                    if (settings.vdoNinja.room.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        LabelValueRow(tr("Sala", "Room"), settings.vdoNinja.room)
                    }
                    TextButton(
                        onClick = onOpenVdoNinja,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(tr("Abrir integração VDO.Ninja", "Open VDO.Ninja integration"), color = Mint, fontSize = 12.sp)
                    }
                }
            }

            item {
                SettingsSection("Chat") {
                    ChoiceChips(
                        options = ChatProvider.entries,
                        selected = settings.chat.provider,
                        label = { it.localizedLabel() },
                        onSelect = { value ->
                            viewModel.updateSettings { it.copy(chat = it.chat.copy(provider = value)) }
                        }
                    )

                    if (settings.chat.provider == ChatProvider.TWITCH) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = settings.chat.channel,
                            onValueChange = { value ->
                                viewModel.updateSettings { it.copy(chat = it.chat.copy(channel = value)) }
                            },
                            label = { Text(tr("Canal da Twitch", "Twitch channel")) },
                            placeholder = { Text(tr("ex.: casimito", "e.g. casimito")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.chat.username,
                            onValueChange = { value ->
                                viewModel.updateSettings { it.copy(chat = it.chat.copy(username = value)) }
                            },
                            label = { Text(tr("Usuário (opcional para enviar)", "Username (optional for sending)")) },
                            placeholder = { Text(tr("seu_login", "your_login")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.chat.oauthToken,
                            onValueChange = { value ->
                                viewModel.updateSettings { it.copy(chat = it.chat.copy(oauthToken = value)) }
                            },
                            label = { Text(tr("Token OAuth (chat:read chat:edit)", "OAuth token (chat:read chat:edit)")) },
                            singleLine = true,
                            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        imageVector = if (tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = tr("Mostrar token", "Show token")
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            tr("A leitura do chat funciona sem token. Para enviar mensagens, gere um token em twitchapps.com/tmi e informe também o usuário.", "Reading chat works without a token. To send messages, generate a token at twitchapps.com/tmi and also provide the username."),
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        SectionDivider()
                        SwitchSettingRow(
                            title = tr("Mostrar chat sobre o vídeo", "Show chat over the video"),
                            checked = settings.showChatOverlay,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(showChatOverlay = value) }
                            }
                        )
                        SectionDivider()
                        SwitchSettingRow(
                            title = tr("Horário nas mensagens", "Timestamps in messages"),
                            checked = settings.chat.showTimestamps,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(chat = it.chat.copy(showTimestamps = value)) }
                            }
                        )
                        SectionDivider()
                        SwitchSettingRow(
                            title = tr("Ler chat em voz alta (TTS)", "Read chat aloud (TTS)"),
                            subtitle = tr("Usa a voz do sistema; só durante a live", "Uses the system voice; only while live"),
                            checked = settings.chat.textToSpeech,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(chat = it.chat.copy(textToSpeech = value)) }
                            }
                        )
                    }
                }
            }

            item {
                SettingsSection(tr("Idioma", "Language")) {
                    Text(
                        tr(
                            "Escolha o idioma da interface. A mudança é aplicada imediatamente.",
                            "Choose the interface language. The change is applied immediately."
                        ),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    ChoiceChips(
                        options = AppLanguage.entries,
                        selected = settings.language,
                        label = { it.nativeName },
                        onSelect = { value ->
                            viewModel.updateSettings { it.copy(language = value) }
                        }
                    )
                }
            }

            item {
                SettingsSection(tr("Sobre", "About")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo_irl),
                            contentDescription = tr("Logo IRL Streaming", "IRL Streaming logo"),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "IRL Streaming",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("${BuildConfig.VERSION_NAME} (beta)", color = Mint, fontSize = 12.sp)
                            }
                            Text(
                                tr("Transmissão IRL para Android", "IRL streaming for Android"),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        tr(
                            "App Android independente de IRL streaming inspirado no Moblin. " +
                                "RTMP/RTMPS/SRT/RTSP/UDP, WHIP (WebRTC), VDO.Ninja com ID de ligação, " +
                                "gravação local, chat da Twitch, microfone USB, PiP com duas câmeras e " +
                                "importação de links moblin://.",
                            "Independent Android IRL streaming app inspired by Moblin. " +
                                "RTMP/RTMPS/SRT/RTSP/UDP, WHIP (WebRTC), VDO.Ninja with stream ID, " +
                                "local recording, Twitch chat, USB microphone, two-camera PiP and " +
                                "moblin:// link import."
                        ),
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        tr("Não afiliado ao Moblin, Twitch, YouTube, Kick ou VDO.Ninja.", "Not affiliated with Moblin, Twitch, YouTube, Kick or VDO.Ninja."),
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun AudioInputSelector(
    options: List<AudioInputOption>,
    selectedId: Int,
    onSelect: (AudioInputOption?) -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        AudioInputRow(
            title = tr("Automático", "Automatic"),
            subtitle = tr("Microfone interno do aparelho", "Device built-in microphone"),
            selected = selectedId < 0,
            badge = null,
            onClick = { onSelect(null) }
        )
        options.forEach { option ->
            AudioInputRow(
                title = option.label,
                subtitle = option.detail,
                selected = selectedId == option.id,
                badge = if (option.isUsb) "USB" else null,
                onClick = { onSelect(option) }
            )
        }
        if (options.isEmpty()) {
            Text(
                tr("Nenhuma entrada de áudio detectada.", "No audio input detected."),
                color = TextMuted,
                fontSize = 11.sp
            )
        }
        TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 2.dp)) {
            Text(tr("Atualizar lista", "Refresh list"), color = Mint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AudioInputRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (selected) TextPrimary else TextMuted,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                )
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = badge,
                        color = Mint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (subtitle.isNotBlank() && subtitle != title) {
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CameraChoiceRow(
    camera: CameraCapabilities.CameraInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(
                text = camera.label,
                color = if (selected) TextPrimary else TextMuted,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
            if (camera.detail.isNotBlank()) {
                Text(camera.detail, color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

private fun updateCamera(viewModel: StreamViewModel, id: String) {
    viewModel.updateSettings { it.copy(cameraId = id, cameraLabel = "") }
}

private val BITRATE_OPTIONS = listOf(1500, 2500, 3500, 5000, 6500, 8000, 12_000)

private fun kbpsLabel(kbps: Int): String =
    if (kbps % 1000 == 0) {
        "${kbps / 1000} Mbps"
    } else {
        String.format(java.util.Locale.US, "%.1f Mbps", kbps / 1000.0)
    }
