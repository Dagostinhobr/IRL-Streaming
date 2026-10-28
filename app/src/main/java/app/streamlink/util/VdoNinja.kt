package app.streamlink.util

import android.net.Uri
import app.streamlink.data.VdoNinjaSettings
import kotlin.random.Random

/**
 * Monta os links do VDO.Ninja a partir das preferências locais.
 *
 * O VDO.Ninja aceita duas formas de identificação:
 * - **Sala**: agrupa vários convidados (`?push=SALA`, `?view=SALA`).
 * - **ID de ligação** (Stream ID): liga direto a um stream (`?push=ID`,
 *   `?view=ID&p=SENHA`). Quando o ID está preenchido, ele tem prioridade.
 *
 * - Publicação P2P no navegador: https://vdo.ninja/?push=ID
 * - Visualização:               https://vdo.ninja/?view=ID&p=SENHA
 * - Publicação nativa (WHIP):   https://whip.vdo.ninja/TOKEN
 * - Visualização de WHIP:       https://vdo.ninja/?whipview=TOKEN
 */
object VdoNinja {

    const val DEFAULT_HOST = "https://vdo.ninja"
    const val DEFAULT_WHIP_HOST = "https://whip.vdo.ninja"

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    /** Gera uma sala aleatória (9 caracteres), evitando caracteres ambíguos. */
    fun newRoom(length: Int = 9): String = buildString(length) {
        repeat(length) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
    }

    fun effectiveHost(settings: VdoNinjaSettings): String =
        settings.host.trim().ifEmpty { DEFAULT_HOST }

    fun effectiveWhipHost(settings: VdoNinjaSettings): String =
        settings.whipHost.trim().ifEmpty { DEFAULT_WHIP_HOST }

    /** Identificador usado nos links: ID de ligação ou, se vazio, a sala. */
    fun pushId(settings: VdoNinjaSettings): String =
        settings.streamId.trim().ifBlank { settings.room.trim() }

    fun token(settings: VdoNinjaSettings): String =
        settings.whipToken.trim().ifBlank { pushId(settings) }

    /** Link que o convidado/câmera abre para publicar via WebRTC (P2P). */
    fun pushUrl(settings: VdoNinjaSettings): String {
        val builder = Uri.parse(effectiveHost(settings)).buildUpon()
            .appendQueryParameter("push", pushId(settings))
        if (settings.label.isNotBlank()) {
            builder.appendQueryParameter("label", settings.label.trim())
        }
        if (settings.password.isNotBlank()) {
            builder.appendQueryParameter("password", settings.password.trim())
        }
        if (settings.autostart) builder.appendQueryParameter("autostart", "1")
        if (settings.cleanOutput) builder.appendQueryParameter("cleanoutput", "1")
        if (settings.stereo) builder.appendQueryParameter("stereo", "1")
        if (settings.audioOnly) {
            builder.appendQueryParameter("novideo", "1")
        } else {
            builder.appendQueryParameter("videobitrate", settings.videoBitrateKbps.toString())
            // Seleciona "Share your Camera" automaticamente e esconde o compartilhamento de tela.
            builder.appendQueryParameter("webcam", "1")
        }
        if (settings.audioBitrateKbps > 0) {
            builder.appendQueryParameter("audiobitrate", settings.audioBitrateKbps.toString())
        }
        if (settings.forceH264) builder.appendQueryParameter("codec", "h264")
        return builder.build().toString()
    }

    /**
     * Link usado pelo estúdio interno (WebView). O `audiodevice=1` faz o
     * VDO.Ninja selecionar o microfone padrão automaticamente e **esconder** o
     * menu de escolha de microfone — sem obrigar a selecionar de novo.
     * O microfone padrão é roteado pelo app para o dispositivo escolhido
     * (ver `AudioRouting`). Para links partilhados, use [pushUrl].
     */
    fun studioUrl(settings: VdoNinjaSettings): String =
        Uri.parse(pushUrl(settings)).buildUpon()
            .appendQueryParameter("audiodevice", "1")
            // O estúdio é embutido por baixo da Home: sem cabeçalho nem barra
            // de controlos do VDO.Ninja, para não competir com os do app.
            .appendQueryParameter("cleanoutput", "1")
            .build()
            .toString()

    /** Link para assistir à publicação via P2P (ID de ligação ou sala). */
    fun viewUrl(settings: VdoNinjaSettings): String {
        val builder = Uri.parse(effectiveHost(settings)).buildUpon()
            .appendQueryParameter("view", pushId(settings))
            .appendQueryParameter("autoplay", "1")
        if (settings.password.isNotBlank()) {
            // `p` é o alias usado nos links de visualização do VDO.Ninja.
            builder.appendQueryParameter("p", settings.password.trim())
        }
        if (settings.cleanOutput) builder.appendQueryParameter("cleanoutput", "1")
        if (settings.audioOnly) builder.appendQueryParameter("novideo", "1")
        return builder.build().toString()
    }

    /** Endpoint WHIP usado pela transmissão nativa (câmera do app). */
    fun whipEndpoint(settings: VdoNinjaSettings): String {
        val base = effectiveWhipHost(settings).trimEnd('/')
        val token = token(settings)
        return if (token.isBlank()) base else "$base/${Uri.encode(token)}"
    }

    /** Link para quem for assistir à publicação WHIP (ex.: colar no OBS). */
    fun whipViewUrl(settings: VdoNinjaSettings): String {
        val builder = Uri.parse(effectiveHost(settings)).buildUpon()
            .appendQueryParameter("whipview", token(settings))
        if (settings.password.isNotBlank()) {
            builder.appendQueryParameter("password", settings.password.trim())
        }
        if (settings.cleanOutput) builder.appendQueryParameter("cleanoutput", "1")
        return builder.build().toString()
    }

    /** URL de um WHIP arbitrário (MediaMTX, Cloudflare, Meshcast...) informado pelo usuário. */
    fun customWhipUrl(baseUrl: String, token: String): String {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return ""
        val cleanToken = token.trim().trimStart('/')
        return if (cleanToken.isEmpty()) base else "$base/${Uri.encode(cleanToken)}"
    }

    /** Dados extraídos de um link do VDO.Ninja (ex.: `?view=meu-stream-7f3a&p=minha-senha-ficticia`). */
    data class ParsedLink(
        val host: String,
        val streamId: String,
        val room: String,
        val password: String
    )

    /**
     * Interpreta um link existente do VDO.Ninja, aceitando:
     * - URL completa (`https://vdo.ninja/?view=ID&p=senha`);
     * - query solta (`?view=ID&p=senha`);
     * - parâmetros sem `?` (`view=ID&p=senha`);
     * - apenas o ID (`meu-stream-7f3a`).
     */
    fun parseLink(raw: String): ParsedLink? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        // ID puro, sem URL nem parâmetros.
        if (!text.contains("://") && !text.startsWith("?") && !text.contains("=")) {
            return ParsedLink(DEFAULT_HOST, text, "", "")
        }

        val withScheme = when {
            text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) -> text
            text.startsWith("?") -> DEFAULT_HOST + "/" + text
            // Parâmetros soltos (`view=ID&p=senha`) vs. host sem esquema (`vdo.ninja/?view=...`).
            text.contains("=") && !text.substringBefore("?").contains(".") -> DEFAULT_HOST + "/?" + text
            else -> "https://" + text
        }
        val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null

        fun query(name: String): String = uri.getQueryParameter(name)?.trim().orEmpty()

        val streamId = query("view").ifBlank { query("push") }
        val room = query("room")
        val password = query("password").ifBlank { query("p") }
        if (streamId.isBlank() && room.isBlank()) return null

        val host = buildString {
            append(uri.scheme ?: "https")
            append("://")
            append(uri.authority ?: "vdo.ninja")
        }
        return ParsedLink(host = host, streamId = streamId, room = room, password = password)
    }
}
