package app.streamlink.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.streamlink.data.AppSettings
import app.streamlink.data.StreamTarget
import app.streamlink.data.TargetKind
import app.streamlink.data.VideoOrientation
import app.streamlink.i18n.localizedLabel
import app.streamlink.i18n.tr
import app.streamlink.stream.StreamStatus
import app.streamlink.stream.StreamUiState
import app.streamlink.ui.StreamViewModel
import app.streamlink.ui.components.CameraPreview
import app.streamlink.ui.components.ChatOverlay
import app.streamlink.ui.components.ChatSheetContent
import app.streamlink.ui.components.Pill
import app.streamlink.ui.components.QuickActionButton
import app.streamlink.ui.components.RoundIconButton
import app.streamlink.ui.components.VdoNinjaEmbedded
import app.streamlink.ui.components.cameraGestures
import app.streamlink.ui.theme.Ink
import app.streamlink.ui.theme.LiveRed
import app.streamlink.ui.theme.Mint
import app.streamlink.ui.theme.Panel
import app.streamlink.ui.theme.TextMuted
import app.streamlink.ui.theme.TextPrimary
import app.streamlink.ui.theme.Warn
import app.streamlink.util.formatBitrate
import app.streamlink.util.formatDuration
import app.streamlink.util.AudioInputOption
import app.streamlink.util.OrientationSupport
import app.streamlink.util.hasStreamPermissions
import app.streamlink.util.requiredPermissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: StreamViewModel,
    onOpenSettings: () -> Unit,
    onOpenTargets: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val nativeState by viewModel.stream.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chatStatus by viewModel.chatStatus.collectAsStateWithLifecycle()
    val dualCameraSupported by viewModel.dualCameraSupported.collectAsStateWithLifecycle()
    val audioDevices by viewModel.audioDevices.collectAsStateWithLifecycle()
    val vdoLive by viewModel.vdoLive.collectAsStateWithLifecycle()
    val vdoUrl by viewModel.vdoUrl.collectAsStateWithLifecycle()
    val vdoStartedAt by viewModel.vdoStartedAt.collectAsStateWithLifecycle()
    val vdoRecording by viewModel.vdoRecording.collectAsStateWithLifecycle()
    val vdoRecordPath by viewModel.vdoRecordPath.collectAsStateWithLifecycle()
    val vdoMuted by viewModel.vdoMuted.collectAsStateWithLifecycle()
    val vdoTorchOn by viewModel.vdoTorchOn.collectAsStateWithLifecycle()

    // PT: No modo P2P o encoder nativo fica em IDLE: a interface mostra o estado
    // PT: do estúdio WebRTC que corre por baixo da Home.
    // EN: In P2P mode the native encoder stays IDLE: the interface shows the
    // EN: state of the WebRTC studio running underneath the Home screen.
    val state = if (vdoLive) {
        nativeState.copy(
            status = StreamStatus.LIVE,
            startedAt = vdoStartedAt ?: nativeState.startedAt,
            recording = vdoRecording,
            recordPath = vdoRecordPath,
            muted = vdoMuted,
            torchOn = vdoTorchOn,
            reconnecting = false
        )
    } else {
        nativeState
    }
    val usbCameraActive = state.usbCamera || settings.cameraId.startsWith("usb:")

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context.findActivity()
    val rootView = LocalView.current

    var permissionsGranted by remember { mutableStateOf(hasStreamPermissions(context)) }
    var previewView by remember { mutableStateOf<SurfaceView?>(null) }
    var stealth by rememberSaveable { mutableStateOf(false) }
    var showChatSheet by rememberSaveable { mutableStateOf(false) }
    var showAudioSheet by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = hasStreamPermissions(context)
        permissionsGranted = granted
        viewModel.updatePermissions(granted)
    }

    LaunchedEffect(Unit) {
        val granted = hasStreamPermissions(context)
        permissionsGranted = granted
        viewModel.updatePermissions(granted)
        if (!granted) {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasStreamPermissions(context)
                if (granted != permissionsGranted) {
                    permissionsGranted = granted
                    viewModel.updatePermissions(granted)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val configuration = LocalConfiguration.current
    val deviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(settings.orientation, deviceLandscape) {
        OrientationSupport.lockActivity(activity, settings.orientation)
        if (settings.orientation == VideoOrientation.AUTO) {
            viewModel.onDeviceOrientationChanged(deviceLandscape)
        }
    }

    DisposableEffect(settings.keepScreenOn) {
        rootView.keepScreenOn = settings.keepScreenOn
        onDispose { rootView.keepScreenOn = false }
    }

    // PT: Ao sair do P2P o SurfaceView é recriado; garante que o encoder volta a
    // PT: anexar o preview depois de o WebView libertar a câmera.
    // EN: When leaving P2P the SurfaceView is recreated; ensures the encoder
    // EN: attaches the preview again after the WebView releases the camera.
    LaunchedEffect(vdoLive) {
        if (!vdoLive) {
            delay(1_200)
            previewView?.let { viewModel.onPreviewReady(it) }
        }
    }

    val target = settings.selectedTarget()
    val elapsed = rememberElapsed(state.startedAt)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (permissionsGranted) {
            // PT: Um SurfaceView fica sempre por cima das views do Compose. Por
            // PT: isso o preview nativo é desmontado durante o P2P (o WebView é
            // PT: quem mostra a câmera) e recriado ao terminar.
            // EN: A SurfaceView always sits above Compose views. That is why the
            // EN: native preview is unmounted during P2P (the WebView is the one
            // EN: showing the camera) and recreated when it ends.
            if (!vdoLive) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onReady = { surface ->
                        previewView = surface
                        viewModel.onPreviewReady(surface)
                    }
                )
            } else if (!vdoUrl.isNullOrBlank()) {
                VdoNinjaEmbedded(
                    url = vdoUrl.orEmpty(),
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            )

            if (!vdoLive) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .cameraGestures(
                            onTap = { offset ->
                                previewView?.let { viewModel.tapToFocus(it, offset.x, offset.y) }
                            },
                            onZoom = viewModel::zoomBy
                        )
                )
            }
        } else {
            PermissionPlaceholder(
                onRequest = { permissionLauncher.launch(requiredPermissions()) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isLive || state.isBusy) {
                    LiveStatusPill(state = state, elapsedMillis = elapsed, p2p = vdoLive)
                } else {
                    IdlePill(target)
                }
                Spacer(Modifier.weight(1f))
                RoundIconButton(
                    icon = Icons.Filled.Cast,
                    contentDescription = tr("Destinos", "Targets"),
                    onClick = onOpenTargets
                )
                Spacer(Modifier.width(8.dp))
                RoundIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = tr("Ajustes", "Settings"),
                    onClick = onOpenSettings
                )
            }

            val error = state.error
            if (state.status == StreamStatus.ERROR && error != null) {
                ErrorBanner(message = error, onDismiss = viewModel::clearError)
            }

            Spacer(Modifier.weight(1f))

            if (permissionsGranted && !vdoLive && !usbCameraActive && state.maxZoom > 1.05f) {
                ZoomPresets(
                    zoom = state.zoom,
                    maxZoom = state.maxZoom,
                    onSelect = viewModel::setZoom
                )
            }

            BottomControls(
                state = state,
                target = target,
                settings = settings,
                elapsedMillis = elapsed,
                micLevel = viewModel.micLevel,
                p2p = vdoLive,
                onToggle = {
                    if (state.isLive || state.isBusy) viewModel.stopStream() else viewModel.startStream()
                },
                onOpenTargets = onOpenTargets,
                onOpenChat = { showChatSheet = true },
                onOpenAudio = { showAudioSheet = true }
            )
        }

        if (permissionsGranted) {
            val compactActions = deviceLandscape
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .systemBarsPadding()
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactActions) 4.dp else 12.dp)
            ) {
                QuickActionButton(
                    icon = if (state.torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = tr("Lanterna", "Torch"),
                    active = state.torchOn,
                    compact = compactActions,
                    enabled = !usbCameraActive,
                    onClick = viewModel::toggleTorch
                )
                QuickActionButton(
                    icon = Icons.Filled.Cameraswitch,
                    contentDescription = tr("Trocar câmera", "Switch camera"),
                    compact = compactActions,
                    enabled = !usbCameraActive,
                    onClick = viewModel::switchCamera
                )
                QuickActionButton(
                    icon = Icons.Filled.ScreenRotation,
                    contentDescription = tr(
                        "Orientação: ${settings.orientation.localizedLabel()}. Toque para alternar.",
                        "Orientation: ${settings.orientation.localizedLabel()}. Tap to cycle."
                    ),
                    active = settings.orientation == VideoOrientation.AUTO,
                    compact = compactActions,
                    onClick = viewModel::cycleOrientation
                )
                if (dualCameraSupported && !vdoLive && !usbCameraActive) {
                    QuickActionButton(
                        icon = Icons.Filled.PictureInPictureAlt,
                        contentDescription = if (settings.dualCamera.enabled) {
                            tr("Desativar janela PiP", "Disable PiP window")
                        } else {
                            tr("Ativar janela PiP", "Enable PiP window")
                        },
                        active = settings.dualCamera.enabled && state.dualCameraActive,
                        compact = compactActions,
                        onClick = {
                            viewModel.updateDualCamera { it.copy(enabled = !it.enabled) }
                        }
                    )
                }
                QuickActionButton(
                    icon = if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = tr("Microfone", "Microphone"),
                    active = state.muted,
                    compact = compactActions,
                    onClick = viewModel::toggleMute
                )
                QuickActionButton(
                    icon = if (state.recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    contentDescription = tr("Gravar", "Record"),
                    active = state.recording,
                    compact = compactActions,
                    onClick = viewModel::toggleRecording
                )
                QuickActionButton(
                    icon = Icons.Filled.Chat,
                    contentDescription = tr("Chat", "Chat"),
                    compact = compactActions,
                    onClick = { showChatSheet = true }
                )
                QuickActionButton(
                    icon = Icons.Filled.VisibilityOff,
                    contentDescription = tr("Tela preta", "Black screen"),
                    compact = compactActions,
                    onClick = { stealth = true }
                )
            }

            if (settings.showChatOverlay && messages.isNotEmpty()) {
                ChatOverlay(
                    messages = messages,
                    showTimestamps = settings.chat.showTimestamps,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 86.dp, bottom = 196.dp)
                        .fillMaxWidth(0.82f)
                )
            }
        }

        if (stealth) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { stealth = false },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr("Tela oculta\nToque para voltar", "Screen hidden\nTap to return"),
                    color = Color.White.copy(alpha = 0.28f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showChatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChatSheet = false },
            containerColor = Panel,
            contentColor = TextPrimary
        ) {
            ChatSheetContent(
                viewModel = viewModel,
                messages = messages,
                status = chatStatus,
                chat = settings.chat,
                showTimestamps = settings.chat.showTimestamps
            )
        }
    }

    if (showAudioSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAudioSheet = false },
            containerColor = Panel,
            contentColor = TextPrimary
        ) {
            AudioInputSheet(
                options = audioDevices,
                selectedId = settings.audioInputId,
                onSelect = {
                    viewModel.selectAudioInput(it)
                    showAudioSheet = false
                },
                onRefresh = viewModel::refreshAudioDevices
            )
        }
    }
}

@Composable
private fun AudioInputSheet(
    options: List<AudioInputOption>,
    selectedId: Int,
    onSelect: (AudioInputOption?) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(tr("Entrada de áudio", "Audio input"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            tr(
                "A troca é aplicada em tempo real, mesmo durante a transmissão. " +
                    "Se a câmera USB tiver microfone, ele é selecionado automaticamente.",
                "Switching is applied in real time, even during the stream. " +
                    "If the USB camera has a microphone, it is selected automatically."
            ),
            color = TextMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        AudioInputSheetRow(
            title = tr("Automático", "Automatic"),
            subtitle = tr("Microfone interno do aparelho", "Device built-in microphone"),
            selected = selectedId < 0,
            badge = null,
            onClick = { onSelect(null) }
        )
        options.forEach { option ->
            AudioInputSheetRow(
                title = option.label,
                subtitle = option.detail,
                selected = selectedId == option.id,
                badge = if (option.isUsb) "USB" else null,
                onClick = { onSelect(option) }
            )
        }
        TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 4.dp)) {
            Text(tr("Atualizar lista", "Refresh list"), color = Mint, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AudioInputSheetRow(
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
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = if (selected) TextPrimary else TextMuted,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                )
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = Mint.copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            badge,
                            color = Mint,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BottomControls(
    state: StreamUiState,
    target: StreamTarget?,
    settings: AppSettings,
    elapsedMillis: Long,
    micLevel: StateFlow<Float>,
    p2p: Boolean,
    onToggle: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenAudio: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MicLevelMeter(level = micLevel, muted = state.muted, onClick = onOpenAudio)

        Spacer(Modifier.height(10.dp))

        Surface(
            color = Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(50),
            modifier = Modifier.clickable(onClick = onOpenTargets)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill(
                    text = target?.badge ?: tr("SEM DESTINO", "NO TARGET"),
                    color = if (target != null) Mint else Warn
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = target?.name ?: tr("Adicionar destino", "Add target"),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GoLiveButton(state = state, onClick = onToggle)

        Spacer(Modifier.height(8.dp))

        Text(
            text = when (state.status) {
                StreamStatus.IDLE -> if (target?.kind == TargetKind.VDO_P2P) {
                    tr("Toque para transmitir via P2P", "Tap to stream via P2P")
                } else {
                    tr("Toque para transmitir", "Tap to stream")
                }
                StreamStatus.CONNECTING -> tr("Conectando...", "Connecting...")
                StreamStatus.LIVE -> tr(
                    "No ar • ${formatDuration(elapsedMillis)}",
                    "Live • ${formatDuration(elapsedMillis)}"
                )
                StreamStatus.RECONNECTING -> tr(
                    "Reconectando (${state.reconnectAttempt})...",
                    "Reconnecting (${state.reconnectAttempt})..."
                )
                StreamStatus.ERROR -> tr("Transmissão encerrada", "Stream ended")
            },
            color = if (state.status == StreamStatus.ERROR) LiveRed else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = if (p2p) {
                tr("Estúdio P2P em segundo plano", "P2P studio in the background")
            } else {
                buildString {
                    append(settings.resolution.label)
                    append(" • ")
                    append(settings.fps)
                    append(" fps")
                    if (state.isLive) {
                        append(" • ")
                        append(formatBitrate(state.bitrateBps))
                        if (state.fps > 0) {
                            append(" • ")
                            append(state.fps)
                            append(tr(" fps reais", " real fps"))
                        }
                    }
                }
            },
            color = TextMuted,
            fontSize = 10.sp
        )

        if (state.recording) {
            Spacer(Modifier.height(4.dp))
            Text("● " + tr("REC", "REC"), color = LiveRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TextButton(onClick = onOpenChat) {
                Icon(Icons.Filled.Chat, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Chat", "Chat"), color = TextPrimary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MicLevelMeter(level: StateFlow<Float>, muted: Boolean, onClick: () -> Unit) {
    // PT: A coleta fica confinada a este composable: a tela não recompõe a cada nível.
    // EN: Collection is confined to this composable: the screen does not recompose on every level.
    val value by level.collectAsStateWithLifecycle()
    val shown = if (muted) 0f else value

    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = if (muted) tr("Microfone mudo", "Microphone muted") else tr("Nível do microfone", "Microphone level"),
                tint = if (muted) LiveRed else TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            MicLevelBar(
                level = shown,
                modifier = Modifier
                    .width(112.dp)
                    .height(7.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    muted -> tr("MUDO", "MUTED")
                    shown <= 0f -> "—"
                    else -> "${(shown * 60f - 60f).roundToInt()} dB"
                },
                color = when {
                    muted -> LiveRed
                    shown >= 0.88f -> LiveRed
                    shown >= 0.68f -> Warn
                    else -> TextMuted
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(38.dp)
            )
        }
    }
}

@Composable
private fun MicLevelBar(level: Float, modifier: Modifier = Modifier) {
    val segments = 18
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val segmentWidth = (size.width - gap * (segments - 1)) / segments
        val radius = CornerRadius(segmentWidth / 2f, segmentWidth / 2f)
        val lit = (level.coerceIn(0f, 1f) * segments).roundToInt()
        for (index in 0 until segments) {
            val fraction = (index + 1f) / segments
            val color = when {
                fraction > 0.88f -> LiveRed
                fraction > 0.68f -> Warn
                else -> Mint
            }
            drawRoundRect(
                color = if (index < lit) color else color.copy(alpha = 0.16f),
                topLeft = Offset(index * (segmentWidth + gap), 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = radius
            )
        }
    }
}

@Composable
private fun GoLiveButton(state: StreamUiState, onClick: () -> Unit) {
    val live = state.status == StreamStatus.LIVE || state.status == StreamStatus.RECONNECTING
    val connecting = state.status == StreamStatus.CONNECTING

    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(90.dp)
                .border(
                    width = 3.dp,
                    color = if (live) LiveRed.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.25f),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (live) LiveRed else Color.White)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            when {
                connecting -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = LiveRed,
                    strokeWidth = 3.dp
                )

                live -> Icon(
                    Icons.Filled.Stop,
                    contentDescription = tr("Encerrar transmissão", "End stream"),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )

                else -> Icon(
                    Icons.Filled.Videocam,
                    contentDescription = tr("Iniciar transmissão", "Start stream"),
                    tint = Ink,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomPresets(zoom: Float, maxZoom: Float, onSelect: (Float) -> Unit) {
    val presets = remember(maxZoom) {
        listOf(1f, 2f, 3f, 5f, 8f).filter { it <= maxZoom + 0.01f }.distinct()
    }
    if (presets.size <= 1) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = kotlin.math.abs(zoom - preset) < 0.15f,
                onClick = { onSelect(preset) },
                label = { Text("${preset.toInt()}x", fontSize = 11.sp) },
                modifier = Modifier.padding(horizontal = 3.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.4f),
                    labelColor = Color.White,
                    selectedContainerColor = LiveRed,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun LiveStatusPill(state: StreamUiState, elapsedMillis: Long, p2p: Boolean) {
    Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (state.status == StreamStatus.RECONNECTING) Warn else LiveRed,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = when (state.status) {
                    StreamStatus.LIVE -> tr("AO VIVO", "LIVE")
                    StreamStatus.CONNECTING -> tr("CONECTANDO", "CONNECTING")
                    StreamStatus.RECONNECTING -> tr("RECONECTANDO", "RECONNECTING")
                    else -> tr("ERRO", "ERROR")
                },
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            if (state.status == StreamStatus.LIVE) {
                Text("  ${formatDuration(elapsedMillis)}", color = TextPrimary, fontSize = 11.sp)
                Text(
                    if (p2p) "  P2P" else "  ${formatBitrate(state.bitrateBps)}",
                    color = Mint,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun IdlePill(target: StreamTarget?) {
    Surface(color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(TextMuted, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                text = "IRL STREAMING",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            if (target != null) {
                Text("  ${target.name}", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = Color(0xFF2A0F14),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = Color(0xFFFFB3BE),
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("OK", color = Color.White)
            }
        }
    }
}

@Composable
private fun PermissionPlaceholder(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Videocam,
            contentDescription = null,
            tint = Mint,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            tr("Câmera e microfone", "Camera and microphone"),
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr(
                "O IRL Streaming precisa dessas permissões para transmitir ao vivo.",
                "IRL Streaming needs these permissions to stream live."
            ),
            color = TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) {
            Text(tr("Conceder acesso", "Grant access"))
        }
        TextButton(onClick = onOpenSettings) {
            Text(tr("Abrir ajustes do app", "Open app settings"))
        }
    }
}

@Composable
private fun rememberElapsed(startedAt: Long?): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return if (startedAt == null) 0L else (now - startedAt).coerceAtLeast(0L)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
