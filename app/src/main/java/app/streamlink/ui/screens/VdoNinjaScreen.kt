package app.streamlink.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.streamlink.i18n.L10n
import app.streamlink.i18n.tr
import app.streamlink.ui.StreamViewModel
import app.streamlink.ui.components.ChoiceChips
import app.streamlink.ui.components.SectionDivider
import app.streamlink.ui.components.SettingsSection
import app.streamlink.ui.components.SwitchSettingRow
import app.streamlink.ui.theme.Ink
import app.streamlink.ui.theme.Mint
import app.streamlink.ui.theme.TextMuted
import app.streamlink.ui.theme.TextPrimary
import app.streamlink.ui.theme.Warn
import app.streamlink.util.VdoNinja

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VdoNinjaScreen(
    viewModel: StreamViewModel,
    onBack: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onTargetReady: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val vdo = settings.vdoNinja
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (vdo.room.isBlank() && vdo.streamId.isBlank()) viewModel.regenerateVdoNinjaRoom()
    }

    val pushUrl = VdoNinja.pushUrl(vdo)
    val studioUrl = VdoNinja.studioUrl(vdo)
    val viewUrl = VdoNinja.viewUrl(vdo)
    val whipEndpoint = VdoNinja.whipEndpoint(vdo)
    val whipViewUrl = VdoNinja.whipViewUrl(vdo)

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("VDO.Ninja", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Voltar", "Back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    tr(
                        "Crie uma sala e gere links de publicação/visualização. Você pode abrir o " +
                            "estúdio P2P dentro do app (WebRTC no navegador) ou transmitir nativamente " +
                            "via WHIP usando a câmera e o microfone do aparelho.",
                        "Create a room and generate publish/view links. You can open the P2P " +
                            "studio inside the app (WebRTC in the browser) or stream natively " +
                            "via WHIP using the device camera and microphone."
                    ),
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                SettingsSection(tr("Identificação", "Identification")) {
                    Text(
                        tr(
                            "Informe um ID de ligação para usar o padrão do VDO.Ninja " +
                                "(?view=ID&p=senha) ou uma sala para agrupar convidados. O ID tem prioridade.",
                            "Provide a stream ID to use the VDO.Ninja pattern " +
                                "(?view=ID&p=password) or a room to group guests. The ID takes priority."
                        ),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vdo.streamId,
                        onValueChange = { value ->
                            viewModel.updateVdoNinja {
                                it.copy(streamId = value.filterNot(Char::isWhitespace))
                            }
                        },
                        label = { Text(tr("ID de ligação (opcional)", "Stream ID (optional)")) },
                        placeholder = { Text(tr("ex.: meu-stream-7f3a", "e.g. meu-stream-7f3a")) },
                        supportingText = {
                            Text(
                                tr(
                                    "Vira ?push=ID nos links de publicação e ?view=ID no player",
                                    "Becomes ?push=ID in publish links and ?view=ID in the player"
                                ),
                                fontSize = 10.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = viewModel::regenerateVdoNinjaId) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = Mint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Gerar novo ID", "Generate new ID"), color = Mint, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(vdo.streamId))
                                toast(context, L10n.t("ID copiado", "ID copied"))
                            },
                            enabled = vdo.streamId.isNotBlank()
                        ) {
                            Text(tr("Copiar ID", "Copy ID"), color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = vdo.room,
                        onValueChange = { value ->
                            viewModel.updateVdoNinja { it.copy(room = value.filterNot(Char::isWhitespace)) }
                        },
                        label = { Text(tr("Sala (opcional)", "Room (optional)")) },
                        placeholder = { Text(tr("ex.: irl-casa-9f2", "e.g. irl-home-9f2")) },
                        supportingText = {
                            Text(
                                tr(
                                    "Agrupa vários convidados quando não há ID de ligação",
                                    "Groups several guests when there is no stream ID"
                                ),
                                fontSize = 10.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = viewModel::regenerateVdoNinjaRoom) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = Mint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Gerar nova sala", "Generate new room"), color = Mint, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(vdo.room))
                                toast(context, L10n.t("Sala copiada", "Room copied"))
                            },
                            enabled = vdo.room.isNotBlank()
                        ) {
                            Text(tr("Copiar sala", "Copy room"), color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = vdo.label,
                        onValueChange = { value -> viewModel.updateVdoNinja { it.copy(label = value) } },
                        label = { Text(tr("Nome exibido (opcional)", "Display name (optional)")) },
                        placeholder = { Text(tr("ex.: Câmera da rua", "e.g. Street camera")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = vdo.password,
                        onValueChange = { value -> viewModel.updateVdoNinja { it.copy(password = value) } },
                        label = { Text(tr("Senha (opcional)", "Password (optional)")) },
                        supportingText = {
                            Text(
                                tr(
                                    "Usada como &password= ao publicar e &p= ao assistir",
                                    "Used as &password= when publishing and &p= when viewing"
                                ),
                                fontSize = 10.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val effectiveId = VdoNinja.pushId(vdo)
                    if (effectiveId.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr(
                                "Identificador usado nos links: $effectiveId",
                                "Identifier used in the links: $effectiveId"
                            ),
                            color = Mint,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    TextButton(
                        onClick = {
                            val raw = clipboard.getText()?.text
                            val parsed = raw?.let(VdoNinja::parseLink)
                            if (parsed == null) {
                                toast(
                                    context,
                                    L10n.t(
                                        "Copie um link do VDO.Ninja (ex.: ?view=ID&p=senha)",
                                        "Copy a VDO.Ninja link (e.g. ?view=ID&p=password)"
                                    )
                                )
                            } else {
                                viewModel.updateVdoNinja {
                                    it.copy(
                                        host = parsed.host,
                                        streamId = parsed.streamId,
                                        room = parsed.room,
                                        password = parsed.password,
                                        whipToken = ""
                                    )
                                }
                                toast(
                                    context,
                                    L10n.t(
                                        "Link importado: ${parsed.streamId.ifBlank { parsed.room }}",
                                        "Link imported: ${parsed.streamId.ifBlank { parsed.room }}"
                                    )
                                )
                            }
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(tr("Colar link do VDO.Ninja e preencher", "Paste VDO.Ninja link and fill in"), color = Mint, fontSize = 12.sp)
                    }
                }
            }

            item {
                SettingsSection(tr("Qualidade do link P2P", "P2P link quality")) {
                    SettingLabel(tr("Bitrate de vídeo", "Video bitrate"))
                    ChoiceChips(
                        options = VDO_BITRATES,
                        selected = vdo.videoBitrateKbps,
                        label = { kbpsLabel(it) },
                        onSelect = { value -> viewModel.updateVdoNinja { it.copy(videoBitrateKbps = value) } }
                    )
                    SectionDivider()
                    SettingLabel(tr("Bitrate de áudio", "Audio bitrate"))
                    ChoiceChips(
                        options = listOf(64, 128, 192),
                        selected = vdo.audioBitrateKbps,
                        label = { "$it kbps" },
                        onSelect = { value -> viewModel.updateVdoNinja { it.copy(audioBitrateKbps = value) } }
                    )
                    SectionDivider()
                    SwitchSettingRow(
                        title = tr("Estéreo", "Stereo"),
                        checked = vdo.stereo,
                        onCheckedChange = { value -> viewModel.updateVdoNinja { it.copy(stereo = value) } }
                    )
                    SectionDivider()
                    SwitchSettingRow(
                        title = tr("Iniciar automaticamente", "Start automatically"),
                        subtitle = tr(
                            "O link já abre publicando, sem pedir confirmação",
                            "The link opens already publishing, without asking for confirmation"
                        ),
                        checked = vdo.autostart,
                        onCheckedChange = { value -> viewModel.updateVdoNinja { it.copy(autostart = value) } }
                    )
                    SectionDivider()
                    SwitchSettingRow(
                        title = tr("Saída limpa", "Clean output"),
                        subtitle = tr(
                            "Esconde menus e botões do VDO.Ninja",
                            "Hides VDO.Ninja menus and buttons"
                        ),
                        checked = vdo.cleanOutput,
                        onCheckedChange = { value -> viewModel.updateVdoNinja { it.copy(cleanOutput = value) } }
                    )
                    SectionDivider()
                    SwitchSettingRow(
                        title = tr("Somente áudio", "Audio only"),
                        subtitle = tr(
                            "Publica apenas o microfone (útil para convidados)",
                            "Publishes only the microphone (useful for guests)"
                        ),
                        checked = vdo.audioOnly,
                        onCheckedChange = { value -> viewModel.updateVdoNinja { it.copy(audioOnly = value) } }
                    )
                    SectionDivider()
                    SwitchSettingRow(
                        title = tr("Forçar H.264", "Force H.264"),
                        subtitle = tr(
                            "Melhor compatibilidade com OBS e WHIP",
                            "Better compatibility with OBS and WHIP"
                        ),
                        checked = vdo.forceH264,
                        onCheckedChange = { value -> viewModel.updateVdoNinja { it.copy(forceH264 = value) } }
                    )
                }
            }

            item {
                SettingsSection(tr("Links P2P (navegador)", "P2P links (browser)")) {
                    LinkBlock(
                        title = tr("Publicar (celular/convidado)", "Publish (phone/guest)"),
                        url = pushUrl,
                        onCopy = {
                            clipboard.setText(AnnotatedString(pushUrl))
                            toast(context, L10n.t("Link de publicação copiado", "Publish link copied"))
                        },
                        onShare = { shareLink(context, L10n.t("Publicar no VDO.Ninja", "Publish on VDO.Ninja"), pushUrl) }
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (viewModel.applyVdoP2PTarget()) onTargetReady()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr("Usar como destino (P2P)", "Use as target (P2P)"), fontSize = 13.sp)
                    }
                    Text(
                        tr(
                            "Ao tocar em transmitir, o app abre o estúdio WebRTC com este link. " +
                                "O microfone do sistema é usado automaticamente (USB/fone têm " +
                                "prioridade) — o VDO.Ninja não pede para selecionar de novo. " +
                                "Este destino não usa WHIP.",
                            "When you tap stream, the app opens the WebRTC studio with this link. " +
                                "The system microphone is used automatically (USB/headset have " +
                                "priority) — VDO.Ninja does not ask you to select it again. " +
                                "This target does not use WHIP."
                        ),
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onOpenWeb(studioUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Abrir estúdio", "Open studio"), fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onOpenWeb(viewUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(tr("Abrir player", "Open player"), fontSize = 12.sp)
                        }
                    }

                    SectionDivider()
                    LinkBlock(
                        title = tr("Assistir", "Watch"),
                        url = viewUrl,
                        onCopy = {
                            clipboard.setText(AnnotatedString(viewUrl))
                            toast(context, L10n.t("Link de visualização copiado", "View link copied"))
                        },
                        onShare = { shareLink(context, L10n.t("Assistir no VDO.Ninja", "Watch on VDO.Ninja"), viewUrl) }
                    )
                    Text(
                        tr(
                            "Dica: o estúdio no app usa WebRTC (P2P) direto do navegador interno. " +
                                "Para usar microfone USB nele, conecte o dispositivo antes de abrir — o " +
                                "Android o utiliza como entrada padrão.",
                            "Tip: the in-app studio uses WebRTC (P2P) directly in the built-in browser. " +
                                "To use a USB microphone there, connect the device before opening — " +
                                "Android uses it as the default input."
                        ),
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item {
                SettingsSection(tr("Publicação nativa via WHIP (experimental)", "Native WHIP publishing (experimental)")) {
                    Text(
                        tr(
                            "Usa a câmera e o microfone selecionado no app, com a mesma qualidade da " +
                                "transmissão RTMP. O token é o identificador do seu stream no VDO.Ninja " +
                                "(por padrão, o ID de ligação ou o nome da sala).",
                            "Uses the camera and microphone selected in the app, with the same quality " +
                                "as the RTMP stream. The token is your stream identifier on VDO.Ninja " +
                                "(by default, the stream ID or the room name)."
                        ),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(
                            "⚠️ O WHIP do VDO.Ninja é experimental e pode ficar indisponível. " +
                                "Para transmitir agora, use o estúdio P2P acima — ele já funciona.",
                            "⚠️ VDO.Ninja WHIP is experimental and may be unavailable. " +
                                "To stream right now, use the P2P studio above — it already works."
                        ),
                        color = Warn,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = vdo.whipToken,
                        onValueChange = { value -> viewModel.updateVdoNinja { it.copy(whipToken = value.trim()) } },
                        label = { Text(tr("Token WHIP (opcional)", "WHIP token (optional)")) },
                        placeholder = { Text(VdoNinja.pushId(vdo)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("Endpoint: $whipEndpoint", "Endpoint: $whipEndpoint"),
                        color = Mint,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (viewModel.applyVdoNinjaTarget()) onTargetReady()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr("Usar como destino WHIP (experimental)", "Use as WHIP target (experimental)"))
                    }
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(whipViewUrl))
                            toast(context, L10n.t("Link WHIP (OBS) copiado", "WHIP (OBS) link copied"))
                        }
                    ) {
                        Text(tr("Copiar link para assistir no OBS", "Copy link to watch in OBS"), color = Mint, fontSize = 12.sp)
                    }
                    Text(
                        tr(
                            "No OBS, adicione uma fonte de navegador com o link acima, ou use " +
                                "“WHIP” com o endpoint ao lado.",
                            "In OBS, add a browser source with the link above, or use " +
                                "“WHIP” with the endpoint shown."
                        ),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkBlock(
    title: String,
    url: String,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = url,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = tr("Copiar link", "Copy link"), tint = TextPrimary)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = tr("Compartilhar link", "Share link"), tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

private fun shareLink(context: Context, title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
        clipData = ClipData.newPlainText("VDO.Ninja", url)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, title)) }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private val VDO_BITRATES = listOf(1_500, 2_500, 3_500, 6_000)

private fun kbpsLabel(kbps: Int): String =
    if (kbps % 1000 == 0) "${kbps / 1000} Mbps"
    else String.format(java.util.Locale.US, "%.1f Mbps", kbps / 1000.0)
