package app.streamlink.chat

import app.streamlink.data.ChatProvider
import app.streamlink.data.ChatSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Orquestra a conexão de chat, histórico e reconexão automática.
 * Outros provedores (Kick, YouTube) podem ser adicionados implementando o mesmo contrato.
 */
class ChatManager(private val scope: CoroutineScope) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow(ChatStatus.DISCONNECTED)
    val status: StateFlow<ChatStatus> = _status.asStateFlow()

    private var client: TwitchChatClient? = null
    private var currentSettings: ChatSettings? = null
    private var reconnectJob: Job? = null

    fun connect(settings: ChatSettings) {
        if (settings.provider == ChatProvider.DISABLED || settings.channel.isBlank()) {
            disconnect()
            return
        }
        if (currentSettings == settings &&
            (_status.value == ChatStatus.CONNECTED || _status.value == ChatStatus.CONNECTING)
        ) {
            return
        }
        closeClient()
        currentSettings = settings
        open(settings)
    }

    fun disconnect() {
        currentSettings = null
        closeClient()
        _status.value = ChatStatus.DISCONNECTED
    }

    fun send(text: String): Boolean = client?.sendMessage(text) == true

    fun canSend(): Boolean = client?.canSend() == true

    fun clearHistory() {
        _messages.value = emptyList()
    }

    private fun open(settings: ChatSettings) {
        _status.value = ChatStatus.CONNECTING
        val newClient = TwitchChatClient(settings, object : TwitchChatClient.Listener {
            override fun onMessage(message: ChatMessage) {
                _messages.update { (it + message).takeLast(MAX_MESSAGES) }
            }

            override fun onStatus(status: ChatStatus) {
                _status.value = status
            }

            override fun onSystemMessage(text: String) {
                _messages.update {
                    (it + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        displayName = "Sistema",
                        text = text,
                        isSystem = true
                    )).takeLast(MAX_MESSAGES)
                }
            }

            override fun onClearAll() {
                _messages.update { list -> list.map { it.copy(isDeleted = true) } }
            }

            override fun onClearUser(user: String) {
                _messages.update { list ->
                    list.map {
                        if (it.user.equals(user, ignoreCase = true)) it.copy(isDeleted = true) else it
                    }
                }
            }

            override fun onDeleteMessage(messageId: String) {
                _messages.update { list ->
                    list.map { if (it.id == messageId) it.copy(isDeleted = true) else it }
                }
            }

            override fun onClosed() {
                if (currentSettings != null) scheduleReconnect()
            }
        })
        client = newClient
        newClient.connect()
    }

    private fun scheduleReconnect() {
        val settings = currentSettings ?: return
        if (reconnectJob?.isActive == true) return
        _status.value = ChatStatus.RECONNECTING
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (currentSettings != null) open(settings)
        }
    }

    private fun closeClient() {
        reconnectJob?.cancel()
        reconnectJob = null
        client?.close()
        client = null
    }

    companion object {
        private const val MAX_MESSAGES = 250
        private const val RECONNECT_DELAY_MS = 5_000L
    }
}
