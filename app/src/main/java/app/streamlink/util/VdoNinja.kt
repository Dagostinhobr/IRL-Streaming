package app.streamlink.util

import android.net.Uri
import app.streamlink.data.VdoNinjaSettings
import kotlin.random.Random

/**
 * PT: Monta os links do VDO.Ninja a partir das preferências locais.
 *
 * PT: O VDO.Ninja aceita duas formas de identificação:
 * PT: - **Sala**: agrupa vários convidados (`?push=SALA`, `?view=SALA`).
 * PT: - **ID de ligação** (Stream ID): liga direto a um stream (`?push=ID`,
 * PT:   `?view=ID&p=SENHA`). Quando o ID está preenchido, ele tem prioridade.
 *
 * PT: - Publicação P2P no navegador: https://vdo.ninja/?push=ID
 * PT: - Visualização:               https://vdo.ninja/?view=ID&p=SENHA
 * PT: - Publicação nativa (WHIP):   https://whip.vdo.ninja/TOKEN
 * PT: - Visualização de WHIP:       https://vdo.ninja/?whipview=TOKEN
 *
 * EN: Builds the VDO.Ninja links from the local preferences.
 *
 * EN: VDO.Ninja accepts two forms of identification:
 * EN: - **Room**: groups several guests (`?push=ROOM`, `?view=ROOM`).
 * EN: - **Connection ID** (Stream ID): connects directly to a stream (`?push=ID`,
 * EN:   `?view=ID&p=PASSWORD`). When the ID is filled in, it takes priority.
 *
 * EN: - P2P publishing in the browser: https://vdo.ninja/?push=ID
 * EN: - Viewing:                     https://vdo.ninja/?view=ID&p=PASSWORD
 * EN: - Native publishing (WHIP):    https://whip.vdo.ninja/TOKEN
 * EN: - WHIP viewing:                https://vdo.ninja/?whipview=TOKEN
 */
object VdoNinja {

    const val DEFAULT_HOST = "https://vdo.ninja"
    const val DEFAULT_WHIP_HOST = "https://whip.vdo.ninja"

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    /**
     * PT: Gera uma sala aleatória (9 caracteres), evitando caracteres ambíguos.
     * EN: Generates a random room (9 characters), avoiding ambiguous characters.
     */
    fun newRoom(length: Int = 9): String = buildString(length) {
        repeat(length) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
    }

    fun effectiveHost(settings: VdoNinjaSettings): String =
        settings.host.trim().ifEmpty { DEFAULT_HOST }

    fun effectiveWhipHost(settings: VdoNinjaSettings): String =
        settings.whipHost.trim().ifEmpty { DEFAULT_WHIP_HOST }

    /**
     * PT: Identificador usado nos links: ID de ligação ou, se vazio, a sala.
     * EN: Identifier used in the links: connection ID or, if empty, the room.
     */
    fun pushId(settings: VdoNinjaSettings): String =
        settings.streamId.trim().ifBlank { settings.room.trim() }

    fun token(settings: VdoNinjaSettings): String =
        settings.whipToken.trim().ifBlank { pushId(settings) }

    /**
     * PT: Link que o convidado/câmera abre para publicar via WebRTC (P2P).
     * EN: The link the guest/camera opens to publish over WebRTC (P2P).
     */
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
            // PT: Seleciona "Share your Camera" automaticamente e esconde o compartilhamento de tela.
            // EN: Automatically selects "Share your Camera" and hides screen sharing.
            builder.appendQueryParameter("webcam", "1")
        }
        if (settings.audioBitrateKbps > 0) {
            builder.appendQueryParameter("audiobitrate", settings.audioBitrateKbps.toString())
        }
        if (settings.forceH264) builder.appendQueryParameter("codec", "h264")
        return builder.build().toString()
    }

    /**
     * PT: Link usado pelo estúdio interno (WebView). O `audiodevice=1` faz o
     * PT: VDO.Ninja selecionar o microfone padrão automaticamente e **esconder** o
     * PT: menu de escolha de microfone — sem obrigar a selecionar de novo.
     * PT: O microfone padrão é roteado pelo app para o dispositivo escolhido
     * PT: (ver `AudioRouting`). Para links partilhados, use [pushUrl].
     *
     * EN: Link used by the internal studio (WebView). `audiodevice=1` makes
     * EN: VDO.Ninja automatically select the default microphone and **hide** the
     * EN: microphone selection menu — without forcing a new selection.
     * EN: The default microphone is routed by the app to the chosen device
     * EN: (see `AudioRouting`). For shared links, use [pushUrl].
     */
    fun studioUrl(settings: VdoNinjaSettings): String =
        Uri.parse(pushUrl(settings)).buildUpon()
            .appendQueryParameter("audiodevice", "1")
            // PT: O estúdio é embutido por baixo da Home: sem cabeçalho nem barra
            // PT: de controlos do VDO.Ninja, para não competir com os do app.
            // EN: The studio is embedded beneath Home: no VDO.Ninja header or
            // EN: control bar, so it does not compete with the app's own.
            .appendQueryParameter("cleanoutput", "1")
            .build()
            .toString()

    /**
     * PT: Link para assistir à publicação via P2P (ID de ligação ou sala).
     * EN: Link to watch the publication via P2P (connection ID or room).
     */
    fun viewUrl(settings: VdoNinjaSettings): String {
        val builder = Uri.parse(effectiveHost(settings)).buildUpon()
            .appendQueryParameter("view", pushId(settings))
            .appendQueryParameter("autoplay", "1")
        if (settings.password.isNotBlank()) {
            // PT: `p` é o alias usado nos links de visualização do VDO.Ninja.
            // EN: `p` is the alias used in VDO.Ninja view links.
            builder.appendQueryParameter("p", settings.password.trim())
        }
        if (settings.cleanOutput) builder.appendQueryParameter("cleanoutput", "1")
        if (settings.audioOnly) builder.appendQueryParameter("novideo", "1")
        return builder.build().toString()
    }

    /**
     * PT: Endpoint WHIP usado pela transmissão nativa (câmera do app).
     * EN: WHIP endpoint used by native streaming (the app camera).
     */
    fun whipEndpoint(settings: VdoNinjaSettings): String {
        val base = effectiveWhipHost(settings).trimEnd('/')
        val token = token(settings)
        return if (token.isBlank()) base else "$base/${Uri.encode(token)}"
    }

    /**
     * PT: Link para quem for assistir à publicação WHIP (ex.: colar no OBS).
     * EN: Link for whoever will watch the WHIP publication (e.g. paste into OBS).
     */
    fun whipViewUrl(settings: VdoNinjaSettings): String {
        val builder = Uri.parse(effectiveHost(settings)).buildUpon()
            .appendQueryParameter("whipview", token(settings))
        if (settings.password.isNotBlank()) {
            builder.appendQueryParameter("password", settings.password.trim())
        }
        if (settings.cleanOutput) builder.appendQueryParameter("cleanoutput", "1")
        return builder.build().toString()
    }

    /**
     * PT: URL de um WHIP arbitrário (MediaMTX, Cloudflare, Meshcast...) informado pelo usuário.
     * EN: URL of an arbitrary WHIP (MediaMTX, Cloudflare, Meshcast...) provided by the user.
     */
    fun customWhipUrl(baseUrl: String, token: String): String {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return ""
        val cleanToken = token.trim().trimStart('/')
        return if (cleanToken.isEmpty()) base else "$base/${Uri.encode(cleanToken)}"
    }

    /**
     * PT: Dados extraídos de um link do VDO.Ninja (ex.: `?view=meu-stream-7f3a&p=minha-senha-ficticia`).
     * EN: Data extracted from a VDO.Ninja link (e.g. `?view=my-stream-7f3a&p=my-fake-password`).
     */
    data class ParsedLink(
        val host: String,
        val streamId: String,
        val room: String,
        val password: String
    )

    /**
     * PT: Interpreta um link existente do VDO.Ninja, aceitando:
     * PT: - URL completa (`https://vdo.ninja/?view=ID&p=senha`);
     * PT: - query solta (`?view=ID&p=senha`);
     * PT: - parâmetros sem `?` (`view=ID&p=senha`);
     * PT: - apenas o ID (`meu-stream-7f3a`).
     *
     * EN: Parses an existing VDO.Ninja link, accepting:
     * EN: - full URL (`https://vdo.ninja/?view=ID&p=password`);
     * EN: - loose query (`?view=ID&p=password`);
     * EN: - parameters without `?` (`view=ID&p=password`);
     * EN: - just the ID (`my-stream-7f3a`).
     */
    fun parseLink(raw: String): ParsedLink? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        // PT: ID puro, sem URL nem parâmetros.
        // EN: Plain ID, without URL or parameters.
        if (!text.contains("://") && !text.startsWith("?") && !text.contains("=")) {
            return ParsedLink(DEFAULT_HOST, text, "", "")
        }

        val withScheme = when {
            text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) -> text
            text.startsWith("?") -> DEFAULT_HOST + "/" + text
            // PT: Parâmetros soltos (`view=ID&p=senha`) vs. host sem esquema (`vdo.ninja/?view=...`).
            // EN: Loose parameters (`view=ID&p=password`) vs. host without scheme (`vdo.ninja/?view=...`).
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
