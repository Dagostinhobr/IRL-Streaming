package app.streamlink.util

import app.streamlink.i18n.L10n
import app.streamlink.data.CodecSetting
import app.streamlink.data.StreamTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder

data class DeepLinkImport(
    val targets: List<StreamTarget>,
    val codec: CodecSetting? = null
)

/**
 * PT: Importa configurações no formato do Moblin:
 * PT:   moblin://?<JSON codificado>
 * PT:   streamlink://?<JSON codificado>
 *
 * PT: Exemplo:
 * PT:   moblin://?{"streams":[{"name":"Casa","url":"rtmp://live.twitch.tv/app/CHAVE"}]}
 *
 * EN: Imports settings in the Moblin format:
 * EN:   moblin://?<encoded JSON>
 * EN:   streamlink://?<encoded JSON>
 *
 * EN: Example:
 * EN:   moblin://?{"streams":[{"name":"Casa","url":"rtmp://live.twitch.tv/app/KEY"}]}
 */
object DeepLinkParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(uri: String): DeepLinkImport? {
        val scheme = when {
            uri.startsWith("moblin://", ignoreCase = true) -> "moblin://"
            uri.startsWith("streamlink://", ignoreCase = true) -> "streamlink://"
            uri.startsWith("irlstream://", ignoreCase = true) -> "irlstream://"
            else -> null
        }

        val payload = if (scheme != null) uri.substringAfter('?', "") else uri
        if (payload.isBlank()) return null

        val decoded = runCatching { URLDecoder.decode(payload, "UTF-8") }.getOrDefault(payload)
        val root = runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null
        val streams = root["streams"]?.jsonArray ?: return null
        if (streams.isEmpty()) return null

        var codec: CodecSetting? = null
        val targets = streams.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (url.isEmpty()) return@mapNotNull null

            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: L10n.t("Destino importado", "Imported target")

            val codecText = obj["video"]?.jsonObject?.get("codec")?.jsonPrimitive?.contentOrNull
            if (codecText != null) {
                codec = when {
                    codecText.contains("265", true) || codecText.contains("hevc", true) -> CodecSetting.H265
                    codecText.contains("264", true) || codecText.contains("avc", true) -> CodecSetting.H264
                    else -> codec
                }
            }

            // PT: O Moblin normalmente coloca a stream key dentro da própria URL.
            // EN: Moblin usually places the stream key inside the URL itself.
            StreamTarget(name = name, url = url)
        }

        if (targets.isEmpty()) return null
        return DeepLinkImport(targets = targets, codec = codec)
    }
}
