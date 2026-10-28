package app.streamlink.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class VideoOrientation(val label: String) {
    AUTO("Automático (sensor)"),
    PORTRAIT("Retrato (vertical)"),
    LANDSCAPE("Paisagem (horizontal)")
}

@Serializable
enum class CodecSetting(val label: String) {
    H264("H.264"),
    H265("H.265 (HEVC)")
}

@Serializable
enum class ResolutionPreset(
    val label: String,
    val width: Int,
    val height: Int
) {
    SD_480("480p", 854, 480),
    HD_720("720p", 1280, 720),
    FHD_1080("1080p", 1920, 1080)
}

@Serializable
enum class StreamProtocol {
    RTMP, RTMPS, SRT, RTSP, UDP, WHIP
}

@Serializable
enum class ChatProvider(val label: String) {
    TWITCH("Twitch"),
    DISABLED("Desativado")
}

/** Como o destino é publicado: encoder nativo do app ou estúdio P2P no navegador. */
@Serializable
enum class TargetKind(val label: String) {
    NATIVE("Nativo"),
    VDO_P2P("VDO.Ninja P2P")
}

@Serializable
data class StreamTarget(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Novo destino",
    val url: String = "",
    val streamKey: String = "",
    val backupUrl: String = "",
    val kind: TargetKind = TargetKind.NATIVE
) {
    val protocol: StreamProtocol
        get() = if (kind == TargetKind.VDO_P2P) {
            // O destino P2P não usa o encoder nativo; RTMP mantém o preview neutro.
            StreamProtocol.RTMP
        } else {
            when {
                url.startsWith("rtmps", ignoreCase = true) -> StreamProtocol.RTMPS
                url.startsWith("rtmp", ignoreCase = true) -> StreamProtocol.RTMP
                url.startsWith("srt", ignoreCase = true) -> StreamProtocol.SRT
                url.startsWith("rtsp", ignoreCase = true) -> StreamProtocol.RTSP
                url.startsWith("udp", ignoreCase = true) -> StreamProtocol.UDP
                url.startsWith("http", ignoreCase = true) -> StreamProtocol.WHIP
                else -> StreamProtocol.RTMP
            }
        }

    /** Rótulo curto usado nos cartões/pílulas da interface. */
    val badge: String
        get() = if (kind == TargetKind.VDO_P2P) "P2P" else protocol.name

    /** URL final usada pelo encoder. Para SRT/RTSP o campo streamKey é ignorado. */
    fun endpoint(): String {
        val base = url.trim()
        if (base.isEmpty()) return ""
        val usesKey = protocol == StreamProtocol.RTMP ||
            protocol == StreamProtocol.RTMPS ||
            protocol == StreamProtocol.WHIP
        if (!usesKey || streamKey.isBlank()) return base
        return base.trimEnd('/') + "/" + streamKey.trim().trimStart('/')
    }

    /** Versão segura para exibição em tela (esconde a chave). */
    fun displayEndpoint(): String {
        if (kind == TargetKind.VDO_P2P) return "Estúdio WebRTC (P2P)"
        val base = url.trim()
        if (base.isEmpty()) return "URL não configurada"
        return if (streamKey.isBlank()) base else "$base/••••••••"
    }
}

@Serializable
data class ChatSettings(
    val provider: ChatProvider = ChatProvider.TWITCH,
    val channel: String = "",
    val username: String = "",
    val oauthToken: String = "",
    val textToSpeech: Boolean = false,
    val showOverlay: Boolean = true,
    val showTimestamps: Boolean = false
)

/**
 * Preferências da integração com o VDO.Ninja.
 * O app gera os links de publicação/visualização (P2P via navegador) e também
 * pode publicar nativamente via WHIP usando a câmera e o microfone do aparelho.
 */
@Serializable
data class VdoNinjaSettings(
    val host: String = "https://vdo.ninja",
    val whipHost: String = "https://whip.vdo.ninja",
    /**
     * ID de ligação (Stream ID do VDO.Ninja). Quando preenchido, os links usam
     * `?push=ID` / `?view=ID` e a sala fica apenas como agrupamento opcional.
     */
    val streamId: String = "",
    val room: String = "",
    val label: String = "",
    val password: String = "",
    val whipToken: String = "",
    val videoBitrateKbps: Int = 2500,
    val audioBitrateKbps: Int = 128,
    val stereo: Boolean = true,
    val autostart: Boolean = true,
    val cleanOutput: Boolean = true,
    val audioOnly: Boolean = false,
    val forceH264: Boolean = true
)

/**
 * Posição da janela PiP dentro do vídeo transmitido.
 * As coordenadas são aplicadas sobre a imagem final (retrato ou paisagem).
 */
@Serializable
enum class PipPosition(val label: String) {
    TOP_LEFT("Superior esquerdo"),
    TOP_RIGHT("Superior direito"),
    BOTTOM_LEFT("Inferior esquerdo"),
    BOTTOM_RIGHT("Inferior direito"),
    CENTER("Centro")
}

/**
 * Composição de duas câmeras no mesmo vídeo (câmera principal + janela PiP).
 *
 * Só funciona em aparelhos que suportam abrir duas câmeras ao mesmo tempo
 * (CameraManager.getConcurrentCameraIds). O RootEncoder desenha a segunda
 * câmera como um filtro de superfície sobre a imagem principal.
 */
@Serializable
data class DualCameraSettings(
    val enabled: Boolean = false,
    /** ID da câmera principal. Vazio = câmera padrão (traseira). */
    val mainCameraId: String = "",
    /** ID da câmera da janela. Vazio = a primeira câmera diferente da principal. */
    val pipCameraId: String = "",
    val position: PipPosition = PipPosition.TOP_RIGHT,
    /** Largura da janela em % da largura do vídeo (10–50). */
    val sizePercent: Int = 30,
    /** Margem em % da largura, usada para afastar a janela da borda. */
    val marginPercent: Int = 2,
    /** Rotação da imagem da janela (0, 90, 180 ou 270). */
    val rotation: Int = 90
)

@Serializable
data class AppSettings(
    val targets: List<StreamTarget> = defaultTargets(),
    val selectedTargetId: String? = null,
    val resolution: ResolutionPreset = ResolutionPreset.HD_720,
    val fps: Int = 30,
    val videoBitrateKbps: Int = 3500,
    val codec: CodecSetting = CodecSetting.H264,
    val orientation: VideoOrientation = VideoOrientation.PORTRAIT,
    /** ID da câmera principal (Camera2) ou `usb:<deviceName>` para uma câmera USB UVC. */
    val cameraId: String = "",
    val cameraLabel: String = "",
    val audioBitrateKbps: Int = 128,
    val sampleRate: Int = 44100,
    val stereo: Boolean = true,
    val echoCancellation: Boolean = false,
    val noiseSuppression: Boolean = true,
    val audioInputId: Int = -1,
    val audioInputLabel: String = "",
    val autoReconnect: Boolean = true,
    val keepScreenOn: Boolean = true,
    val showChatOverlay: Boolean = true,
    val chat: ChatSettings = ChatSettings(),
    val vdoNinja: VdoNinjaSettings = VdoNinjaSettings(),
    val dualCamera: DualCameraSettings = DualCameraSettings()
) {
    fun selectedTarget(): StreamTarget? =
        targets.firstOrNull { it.id == selectedTargetId } ?: targets.firstOrNull()

    fun normalizedSelectedId(): String? = selectedTarget()?.id
}

fun defaultTargets(): List<StreamTarget> = listOf(
    StreamTarget(
        name = "Twitch",
        url = "rtmp://live.twitch.tv/app",
        streamKey = ""
    ),
    StreamTarget(
        name = "YouTube",
        url = "rtmp://a.rtmp.youtube.com/live2",
        streamKey = ""
    ),
    StreamTarget(
        name = "Kick",
        url = "rtmps://fa723fc1b171.global-contribute.live-video.net:443/app",
        streamKey = ""
    )
)
