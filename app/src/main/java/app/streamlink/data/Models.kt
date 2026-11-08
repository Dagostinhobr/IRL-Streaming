package app.streamlink.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AppLanguage(val tag: String, val nativeName: String) {
    PT("pt", "Português"),
    EN("en", "English")
}

@Serializable
enum class VideoOrientation(val label: String, val labelEn: String) {
    AUTO("Automático (sensor)", "Automatic (sensor)"),
    PORTRAIT("Retrato (vertical)", "Portrait (vertical)"),
    LANDSCAPE("Paisagem (horizontal)", "Landscape (horizontal)")
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
enum class ChatProvider(val label: String, val labelEn: String) {
    TWITCH("Twitch", "Twitch"),
    DISABLED("Desativado", "Disabled")
}

/**
 * PT: Como o destino é publicado: encoder nativo do app ou estúdio P2P no navegador.
 * EN: How the target is published: the app's native encoder or the P2P studio in the browser.
 */
@Serializable
enum class TargetKind(val label: String, val labelEn: String) {
    NATIVE("Nativo", "Native"),
    VDO_P2P("VDO.Ninja P2P", "VDO.Ninja P2P")
}

@Serializable
data class StreamTarget(
    val id: String = UUID.randomUUID().toString(),
    val name: String = app.streamlink.i18n.L10n.t("Novo destino", "New target"),
    val url: String = "",
    val streamKey: String = "",
    val backupUrl: String = "",
    val kind: TargetKind = TargetKind.NATIVE
) {
    val protocol: StreamProtocol
        get() = if (kind == TargetKind.VDO_P2P) {
            // PT: O destino P2P não usa o encoder nativo; RTMP mantém o preview neutro.
            // EN: The P2P target does not use the native encoder; RTMP keeps the preview neutral.
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

    /**
     * PT: Rótulo curto usado nos cartões/pílulas da interface.
     * EN: Short label used on the UI cards/pills.
     */
    val badge: String
        get() = if (kind == TargetKind.VDO_P2P) "P2P" else protocol.name

    /**
     * PT: URL final usada pelo encoder. Para SRT/RTSP o campo streamKey é ignorado.
     * EN: Final URL used by the encoder. For SRT/RTSP the streamKey field is ignored.
     */
    fun endpoint(): String {
        val base = url.trim()
        if (base.isEmpty()) return ""
        val usesKey = protocol == StreamProtocol.RTMP ||
            protocol == StreamProtocol.RTMPS ||
            protocol == StreamProtocol.WHIP
        if (!usesKey || streamKey.isBlank()) return base
        return base.trimEnd('/') + "/" + streamKey.trim().trimStart('/')
    }

    /**
     * PT: Versão segura para exibição em tela (esconde a chave).
     * EN: Safe version for on-screen display (hides the key).
     */
    fun displayEndpoint(): String {
        if (kind == TargetKind.VDO_P2P) {
            return app.streamlink.i18n.L10n.t("Estúdio WebRTC (P2P)", "WebRTC studio (P2P)")
        }
        val base = url.trim()
        if (base.isEmpty()) {
            return app.streamlink.i18n.L10n.t("URL não configurada", "URL not configured")
        }
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
 * PT: Preferências da integração com o VDO.Ninja.
 * PT: O app gera os links de publicação/visualização (P2P via navegador) e também
 * PT: pode publicar nativamente via WHIP usando a câmera e o microfone do aparelho.
 *
 * EN: VDO.Ninja integration preferences.
 * EN: The app generates the publish/view links (P2P via browser) and can also
 * EN: publish natively via WHIP using the device's camera and microphone.
 */
@Serializable
data class VdoNinjaSettings(
    val host: String = "https://vdo.ninja",
    val whipHost: String = "https://whip.vdo.ninja",
    /**
     * PT: ID de ligação (Stream ID do VDO.Ninja). Quando preenchido, os links usam
     * PT: `?push=ID` / `?view=ID` e a sala fica apenas como agrupamento opcional.
     *
     * EN: Connection ID (VDO.Ninja Stream ID). When filled in, the links use
     * EN: `?push=ID` / `?view=ID` and the room remains only as an optional grouping.
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
 * PT: Posição da janela PiP dentro do vídeo transmitido.
 * PT: As coordenadas são aplicadas sobre a imagem final (retrato ou paisagem).
 *
 * EN: Position of the PiP window inside the transmitted video.
 * EN: The coordinates are applied over the final image (portrait or landscape).
 */
@Serializable
enum class PipPosition(val label: String, val labelEn: String) {
    TOP_LEFT("Superior esquerdo", "Top left"),
    TOP_RIGHT("Superior direito", "Top right"),
    BOTTOM_LEFT("Inferior esquerdo", "Bottom left"),
    BOTTOM_RIGHT("Inferior direito", "Bottom right"),
    CENTER("Centro", "Center")
}

/**
 * PT: Composição de duas câmeras no mesmo vídeo (câmera principal + janela PiP).
 *
 * PT: Só funciona em aparelhos que suportam abrir duas câmeras ao mesmo tempo
 * PT: (CameraManager.getConcurrentCameraIds). O RootEncoder desenha a segunda
 * PT: câmera como um filtro de superfície sobre a imagem principal.
 *
 * EN: Composition of two cameras in the same video (main camera + PiP window).
 *
 * EN: Only works on devices that support opening two cameras at the same time
 * EN: (CameraManager.getConcurrentCameraIds). RootEncoder draws the second
 * EN: camera as a surface filter over the main image.
 */
@Serializable
data class DualCameraSettings(
    val enabled: Boolean = false,
    /**
     * PT: ID da câmera principal. Vazio = câmera padrão (traseira).
     * EN: Main camera ID. Empty = default camera (rear).
     */
    val mainCameraId: String = "",
    /**
     * PT: ID da câmera da janela. Vazio = a primeira câmera diferente da principal.
     * EN: Window camera ID. Empty = the first camera different from the main one.
     */
    val pipCameraId: String = "",
    val position: PipPosition = PipPosition.TOP_RIGHT,
    /**
     * PT: Largura da janela em % da largura do vídeo (10–50).
     * EN: Window width as % of the video width (10–50).
     */
    val sizePercent: Int = 30,
    /**
     * PT: Margem em % da largura, usada para afastar a janela da borda.
     * EN: Margin as % of the width, used to move the window away from the edge.
     */
    val marginPercent: Int = 2,
    /**
     * PT: Rotação da imagem da janela (0, 90, 180 ou 270).
     * EN: Window image rotation (0, 90, 180 or 270).
     */
    val rotation: Int = 90
)

/**
 * PT: Correção aplicada apenas às câmeras USB (UVC). Muitas câmeras externas
 * PT: entregam os frames rodados ou espelhados, sem metadados de orientação.
 * PT: A correção é aplicada ao preview e ao vídeo transmitido/gravado.
 *
 * EN: Correction applied only to USB (UVC) cameras. Many external cameras
 * EN: deliver frames rotated or mirrored, without orientation metadata.
 * EN: The correction is applied to both the preview and the streamed/recorded
 * EN: video.
 */
@Serializable
data class UvcTransformSettings(
    /**
     * PT: Rotação em graus (0, 90, 180 ou 270), somada à orientação da app.
     * EN: Rotation in degrees (0, 90, 180 or 270), added to the app orientation.
     */
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
)

/**
 * PT: Ajuste de brilho e contraste da imagem da câmara. O brilho pode ser
 * PT: controlado manualmente ou automaticamente pelo sensor de luz ambiente.
 *
 * EN: Camera image brightness and contrast. Brightness can be controlled
 * EN: manually or automatically by the ambient light sensor.
 */
@Serializable
data class CameraImageSettings(
    /**
     * PT: Brilho manual, de -1 (mais escuro) a 1 (mais claro). 0 = neutro.
     * EN: Manual brightness, from -1 (darker) to 1 (brighter). 0 = neutral.
     */
    val brightness: Float = 0f,
    /**
     * PT: Contraste de 0.5 (mais suave) a 1.5 (mais forte). 1 = neutro.
     * EN: Contrast from 0.5 (softer) to 1.5 (stronger). 1 = neutral.
     */
    val contrast: Float = 1f,
    /**
     * PT: Ajusta o brilho automaticamente à luz do ambiente. Quando ativo, o
     * PT: valor manual de brilho é ignorado.
     * EN: Adjusts brightness automatically to the ambient light. When enabled,
     * EN: the manual brightness value is ignored.
     */
    val autoBrightness: Boolean = false
)

/**
 * PT: Failover automático de rede durante a transmissão. O Wi-Fi é a ligação
 * PT: principal; quando cai, a app passa para os dados móveis e volta ao Wi-Fi
 * PT: quando este fica novamente estável.
 *
 * EN: Automatic network failover while streaming. Wi-Fi is the primary link;
 * EN: when it drops, the app moves to mobile data and returns to Wi-Fi once it
 * EN: becomes stable again.
 */
@Serializable
data class NetworkBondingSettings(
    val enabled: Boolean = true,
    /**
     * PT: `true` prefere Wi-Fi e usa os dados móveis como reserva; `false`
     * PT: inverte a ordem.
     * EN: `true` prefers Wi-Fi and uses mobile data as the backup; `false`
     * EN: reverses the order.
     */
    val preferWifi: Boolean = true,
    /**
     * PT: depois de mudar para a rede de reserva, volta automaticamente à rede
     * PT: preferida quando ela recupera.
     * EN: after switching to the backup network, automatically returns to the
     * EN: preferred network when it recovers.
     */
    val autoSwitchBack: Boolean = true
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
    /**
     * PT: Idioma da interface: português (padrão) ou inglês.
     * EN: UI language: Portuguese (default) or English.
     */
    val language: AppLanguage = AppLanguage.PT,
    /**
     * PT: ID da câmera principal (Camera2) ou `usb:<deviceName>` para uma câmera USB UVC.
     * EN: Main camera ID (Camera2) or `usb:<deviceName>` for a UVC USB camera.
     */
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
    val dualCamera: DualCameraSettings = DualCameraSettings(),
    /**
     * PT: Correção de posição/espelhamento da câmera USB (UVC).
     * EN: Position/mirror correction for the USB (UVC) camera.
     */
    val uvcTransform: UvcTransformSettings = UvcTransformSettings(),
    /**
     * PT: Brilho e contraste da imagem da câmara.
     * EN: Camera image brightness and contrast.
     */
    val cameraImage: CameraImageSettings = CameraImageSettings(),
    /**
     * PT: Failover automático entre Wi-Fi e dados móveis.
     * EN: Automatic failover between Wi-Fi and mobile data.
     */
    val networkBonding: NetworkBondingSettings = NetworkBondingSettings()
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
