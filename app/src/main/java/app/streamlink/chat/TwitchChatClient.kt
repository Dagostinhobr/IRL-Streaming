package app.streamlink.chat

import app.streamlink.i18n.L10n
import app.streamlink.data.ChatSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * PT: Cliente de chat da Twitch via IRC sobre WebSocket.
 *
 * PT: - Leitura anônima: não precisa de token (NICK justinfanNNNN).
 * PT: - Envio/moderação: informe usuário + token OAuth (chat:read chat:edit).
 *
 * PT: Referência: https://dev.twitch.tv/docs/chat/irc/
 *
 * EN: Twitch chat client via IRC over WebSocket.
 *
 * EN: - Anonymous reading: no token needed (NICK justinfanNNNN).
 * EN: - Sending/moderation: provide username + OAuth token (chat:read chat:edit).
 *
 * EN: Reference: https://dev.twitch.tv/docs/chat/irc/
 */
class TwitchChatClient(
    private val settings: ChatSettings,
    private val listener: Listener
) {

    interface Listener {
        fun onMessage(message: ChatMessage)
        fun onStatus(status: ChatStatus)
        fun onSystemMessage(text: String)
        fun onClearAll()
        fun onClearUser(user: String)
        fun onDeleteMessage(messageId: String)
        fun onClosed()
    }

    private val channel = settings.channel.trim().removePrefix("#").lowercase(Locale.US)
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    @Volatile
    private var closedByUser = false

    fun connect() {
        closedByUser = false
        listener.onStatus(ChatStatus.CONNECTING)
        val request = Request.Builder()
            .url(URL)
            .build()
        webSocket = httpClient.newWebSocket(request, SocketListener())
    }

    fun close() {
        closedByUser = true
        webSocket?.close(NORMAL_CLOSE, null)
        webSocket = null
        httpClient.dispatcher.executorService.shutdown()
        listener.onStatus(ChatStatus.DISCONNECTED)
    }

    fun sendMessage(message: String): Boolean {
        if (settings.oauthToken.isBlank() || settings.username.isBlank()) return false
        val clean = message.trim().replace('\r', ' ').replace('\n', ' ')
        if (clean.isEmpty()) return false
        return webSocket?.send("PRIVMSG #$channel :$clean") == true
    }

    fun canSend(): Boolean = settings.oauthToken.isNotBlank() && settings.username.isNotBlank()

    // ---------------------------------------------------------------------

    private fun sendRaw(command: String): Boolean = webSocket?.send(command) == true

    private fun handleLine(raw: String) {
        val line = raw.trimEnd('\r')
        if (line.isEmpty()) return

        if (line.startsWith("PING")) {
            sendRaw("PONG :tmi.twitch.tv")
            return
        }
        if (line.contains(" 001 ")) {
            listener.onStatus(ChatStatus.CONNECTED)
            return
        }
        if (line.contains(" RECONNECT")) {
            webSocket?.close(NORMAL_CLOSE, null)
            return
        }
        if (line.startsWith(":tmi.twitch.tv NOTICE")) {
            val text = line.substringAfter(" :", "")
            if (text.contains("authentication failed", true) || text.contains("login unsuccessful", true)) {
                listener.onSystemMessage(
                    L10n.t("Falha no login da Twitch: $text", "Twitch login failed: $text")
                )
            }
            return
        }

        var tags: Map<String, String> = emptyMap()
        var rest = line
        if (line.startsWith("@")) {
            val space = line.indexOf(' ')
            if (space <= 1) return
            tags = parseTags(line.substring(1, space))
            rest = line.substring(space + 1)
        }

        when {
            rest.contains(" PRIVMSG ") -> handlePrivmsg(tags, rest)
            rest.contains(" USERNOTICE ") -> handleUserNotice(tags, rest)
            rest.contains(" CLEARMSG ") -> tags["target-msg-id"]?.let(listener::onDeleteMessage)
            rest.contains(" CLEARCHAT ") -> {
                val target = rest.substringAfter(" :", "").trim()
                if (target.isEmpty()) listener.onClearAll() else listener.onClearUser(target)
            }
        }
    }

    private fun handlePrivmsg(tags: Map<String, String>, rest: String) {
        val index = rest.indexOf(" PRIVMSG ")
        if (index <= 0) return
        val prefix = rest.substring(1, index)
        val login = prefix.substringBefore('!').ifBlank { "twitch" }
        val after = rest.substring(index + " PRIVMSG ".length)
        val messageStart = after.indexOf(" :")
        if (messageStart < 0) return
        val text = after.substring(messageStart + 2)
        val displayName = tags["display-name"]?.takeIf { it.isNotBlank() } ?: login
        val badges = tags["badges"].orEmpty()
        val badgeList = badges.split(',').mapNotNull { it.substringBefore('/').takeIf(String::isNotBlank) }

        listener.onMessage(
            ChatMessage(
                id = tags["id"] ?: UUID.randomUUID().toString(),
                user = login,
                displayName = displayName,
                text = text,
                colorHex = tags["color"]?.takeIf { it.matches(HEX_COLOR) },
                timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis(),
                isMod = tags["mod"] == "1" || badgeList.contains("moderator") || badgeList.contains("broadcaster"),
                isBroadcaster = badgeList.contains("broadcaster"),
                isSubscriber = tags["subscriber"] == "1" || badgeList.contains("subscriber"),
                badges = badgeList
            )
        )
    }

    private fun handleUserNotice(tags: Map<String, String>, rest: String) {
        val systemText = tags["system-msg"]?.let(::unescape)?.takeIf { it.isNotBlank() }
            ?: rest.substringAfter(" :", "").takeIf { it.isNotBlank() }
            ?: return
        listener.onSystemMessage(systemText)
    }

    private fun parseTags(raw: String): Map<String, String> {
        val map = HashMap<String, String>()
        raw.split(';').forEach { pair ->
            val index = pair.indexOf('=')
            if (index > 0) {
                map[pair.substring(0, index)] = unescape(pair.substring(index + 1))
            }
        }
        return map
    }

    private fun unescape(value: String): String = value
        .replace("\\s", " ")
        .replace("\\:", ";")
        .replace("\\\\", "\\")
        .replace("\\r", "")
        .replace("\\n", "")

    private fun loginNick(): String {
        val hasAuth = settings.oauthToken.isNotBlank() && settings.username.isNotBlank()
        return if (hasAuth) {
            settings.username.trim().lowercase(Locale.US)
        } else {
            "justinfan${Random.nextInt(10000, 99999)}"
        }
    }

    private fun passValue(): String =
        if (settings.oauthToken.isBlank()) "SCHMOOPIIE"
        else "oauth:" + settings.oauthToken.trim().removePrefix("oauth:")

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sendRaw("CAP REQ :twitch.tv/tags twitch.tv/commands")
            sendRaw("PASS ${passValue()}")
            sendRaw("NICK ${loginNick()}")
            sendRaw("JOIN #$channel")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            text.split("\r\n").forEach(::handleLine)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closedByUser) return
            listener.onStatus(ChatStatus.DISCONNECTED)
            listener.onClosed()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (closedByUser) return
            listener.onStatus(ChatStatus.DISCONNECTED)
            listener.onClosed()
        }
    }

    companion object {
        private const val URL = "wss://irc-ws.chat.twitch.tv:443"
        private const val NORMAL_CLOSE = 1000
        private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")
    }
}
