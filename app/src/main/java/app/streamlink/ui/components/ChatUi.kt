package app.streamlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.streamlink.chat.ChatMessage
import app.streamlink.chat.ChatStatus
import app.streamlink.data.ChatSettings
import app.streamlink.i18n.tr
import app.streamlink.ui.StreamViewModel
import app.streamlink.ui.theme.LiveRed
import app.streamlink.ui.theme.Mint
import app.streamlink.ui.theme.Panel
import app.streamlink.ui.theme.PanelHigh
import app.streamlink.ui.theme.TextMuted
import app.streamlink.ui.theme.TextPrimary
import app.streamlink.util.formatClock

private val OverlayTextStyle = TextStyle(
    color = Color.White,
    fontSize = 13.sp,
    shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 4f)
)

@Composable
fun ChatOverlay(
    messages: List<ChatMessage>,
    showTimestamps: Boolean,
    modifier: Modifier = Modifier
) {
    val visible = remember(messages) {
        messages.filterNot { it.isDeleted || it.isSystem }.takeLast(6)
    }
    if (visible.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        visible.forEach { message ->
            Row {
                if (showTimestamps) {
                    Text(
                        text = formatClock(message.timestamp),
                        style = OverlayTextStyle.copy(color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    text = message.displayName,
                    style = OverlayTextStyle.copy(color = chatColor(message.colorHex), fontWeight = FontWeight.Bold)
                )
                Text(text = ": ", style = OverlayTextStyle)
                Text(
                    text = message.text,
                    style = OverlayTextStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChatSheetContent(
    viewModel: StreamViewModel,
    messages: List<ChatMessage>,
    status: ChatStatus,
    chat: ChatSettings,
    showTimestamps: Boolean
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val canSend = chat.username.isNotBlank() && chat.oauthToken.isNotBlank()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Chat", "Chat"), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = when (status) {
                        ChatStatus.CONNECTED -> tr(
                            "Conectado a #${chat.channel}",
                            "Connected to #${chat.channel}"
                        )
                        ChatStatus.CONNECTING -> tr("Conectando...", "Connecting...")
                        ChatStatus.RECONNECTING -> tr("Reconectando...", "Reconnecting...")
                        ChatStatus.DISCONNECTED -> tr("Desconectado", "Disconnected")
                    },
                    color = when (status) {
                        ChatStatus.CONNECTED -> Mint
                        ChatStatus.CONNECTING, ChatStatus.RECONNECTING -> Color(0xFFFFB020)
                        ChatStatus.DISCONNECTED -> TextMuted
                    },
                    fontSize = 12.sp
                )
            }
            if (chat.channel.isNotBlank()) {
                TextButton(onClick = {
                    if (status == ChatStatus.DISCONNECTED) viewModel.connectChat()
                    else viewModel.disconnectChat()
                }) {
                    Text(if (status == ChatStatus.DISCONNECTED) tr("Conectar", "Connect") else tr("Desconectar", "Disconnect"))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (chat.channel.isBlank())
                        tr(
                            "Configure o canal da Twitch em Ajustes > Chat.",
                            "Set the Twitch channel in Settings > Chat."
                        )
                    else tr("Nenhuma mensagem ainda.", "No messages yet."),
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatSheetLine(message, showTimestamps)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                enabled = canSend,
                placeholder = {
                    Text(
                            if (canSend) tr("Enviar mensagem", "Send message")
                            else tr("Token OAuth necessário", "OAuth token required"),
                            fontSize = 13.sp
                        )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    viewModel.sendChatMessage(input)
                    input = ""
                },
                enabled = canSend && input.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(if (canSend && input.isNotBlank()) LiveRed else PanelHigh, CircleShape)
            ) {
                Icon(Icons.Filled.Send, contentDescription = tr("Enviar", "Send"), tint = Color.White)
            }
        }

        if (!canSend) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = tr(
                    "Para enviar mensagens informe usuário e token OAuth (chat:read chat:edit) em Ajustes > Chat.",
                    "To send messages, provide the username and OAuth token (chat:read chat:edit) in Settings > Chat."
                ),
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ChatSheetLine(message: ChatMessage, showTimestamps: Boolean) {
    if (message.isSystem) {
        Text(
            text = message.text,
            color = Mint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .background(Mint.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
        return
    }

    Row {
        if (showTimestamps) {
            Text(
                text = formatClock(message.timestamp),
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 3.dp, end = 5.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.isBroadcaster) Badge("STREAMER", LiveRed)
                else if (message.isMod) Badge("MOD", Mint)
                else if (message.isSubscriber) Badge("SUB", Color(0xFF9146FF))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = message.displayName,
                    color = chatColor(message.colorHex),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = if (message.isDeleted) tr("mensagem removida", "message removed") else message.text,
                color = if (message.isDeleted) TextMuted else TextPrimary,
                fontSize = 14.sp,
                textDecoration = if (message.isDeleted) TextDecoration.LineThrough else TextDecoration.None
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

private fun chatColor(hex: String?): Color {
    if (hex == null) return Color(0xFF9BB4FF)
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color(0xFF9BB4FF))
}
