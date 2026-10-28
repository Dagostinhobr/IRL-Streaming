package app.streamlink.chat

import java.util.UUID

enum class ChatStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val user: String = "",
    val displayName: String = "",
    val text: String = "",
    val colorHex: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isMod: Boolean = false,
    val isBroadcaster: Boolean = false,
    val isSubscriber: Boolean = false,
    val isSystem: Boolean = false,
    val isDeleted: Boolean = false,
    val badges: List<String> = emptyList()
)
