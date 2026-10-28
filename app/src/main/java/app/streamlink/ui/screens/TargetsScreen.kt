package app.streamlink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.streamlink.data.StreamTarget
import app.streamlink.ui.StreamViewModel
import app.streamlink.ui.components.Pill
import app.streamlink.ui.components.SettingsSection
import app.streamlink.ui.theme.Ink
import app.streamlink.ui.theme.LiveRed
import app.streamlink.ui.theme.Mint
import app.streamlink.ui.theme.Panel
import app.streamlink.ui.theme.PanelHigh
import app.streamlink.ui.theme.TextMuted
import app.streamlink.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen(
    viewModel: StreamViewModel,
    onBack: () -> Unit,
    onOpenVdoNinja: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedId = settings.normalizedSelectedId()

    var editingTarget by remember { mutableStateOf<StreamTarget?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<StreamTarget?>(null) }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Destinos", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTarget = null
                    showEditor = true
                },
                containerColor = LiveRed,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar destino")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Escolha para onde transmitir. Você pode manter várias URLs (Twitch, YouTube, Kick, servidor próprio) e trocar com um toque.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            "VDO.Ninja",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Crie salas, gere links P2P ou publique via WHIP (WebRTC).",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        TextButton(
                            onClick = onOpenVdoNinja,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text("Abrir integração", color = Mint, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (settings.targets.isEmpty()) {
                item {
                    EmptyTargets(
                        onAdd = {
                            editingTarget = null
                            showEditor = true
                        }
                    )
                }
            }

            items(settings.targets, key = { it.id }) { target ->
                TargetCard(
                    target = target,
                    selected = target.id == selectedId,
                    onSelect = { viewModel.selectTarget(target.id) },
                    onEdit = {
                        editingTarget = target
                        showEditor = true
                    },
                    onDelete = { deleteCandidate = target }
                )
            }

            item {
                SettingsSection("Formatos aceitos") {
                    Text(
                        "RTMP / RTMPS: informe a URL base e a stream key separadamente.\n" +
                            "SRT / RTSP: cole a URL completa (ex.: srt://host:5000?streamid=...).\n" +
                            "WHIP (WebRTC): cole a URL https://whip.vdo.ninja/token ou de outro servidor WHIP.\n" +
                            "A importação moblin:// adiciona as URLs automaticamente.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (showEditor) {
        TargetEditorDialog(
            initial = editingTarget,
            onDismiss = { showEditor = false },
            onSave = { target ->
                if (editingTarget == null) viewModel.addTarget(target)
                else viewModel.updateTarget(target)
                showEditor = false
            }
        )
    }

    deleteCandidate?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Remover destino?") },
            text = { Text("O destino \"${target.name}\" será removido. A transmissão atual não é afetada.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTarget(target.id)
                    deleteCandidate = null
                }) {
                    Text("Remover", color = LiveRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancelar")
                }
            },
            containerColor = Panel
        )
    }
}

@Composable
private fun TargetCard(
    target: StreamTarget,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (selected) PanelHigh else Panel,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(Modifier.weight(1f)) {
                    Text(
                        target.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        target.displayEndpoint(),
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = TextMuted)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover", tint = TextMuted)
                }
            }
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill(text = target.badge, color = Mint)
                if (selected) Pill(text = "SELECIONADO", color = LiveRed)
                if (target.endpoint().isBlank()) {
                    Text("sem URL", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyTargets(onAdd: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nenhum destino cadastrado", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Adicione a URL de ingest do seu provedor para começar.",
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Adicionar destino")
            }
        }
    }
}

@Composable
private fun TargetEditorDialog(
    initial: StreamTarget?,
    onDismiss: () -> Unit,
    onSave: (StreamTarget) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var url by remember(initial) { mutableStateOf(initial?.url ?: "") }
    var streamKey by remember(initial) { mutableStateOf(initial?.streamKey ?: "") }
    var backupUrl by remember(initial) { mutableStateOf(initial?.backupUrl ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (initial == null) "Novo destino" else "Editar destino",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    placeholder = { Text("Twitch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL de ingest") },
                    placeholder = { Text("rtmp://live.twitch.tv/app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    label = { Text("Stream key (RTMP/RTMPS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = backupUrl,
                    onValueChange = { backupUrl = it },
                    label = { Text("URL de backup (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Para SRT, cole a URL completa no campo acima e deixe a stream key vazia.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val target = (initial ?: StreamTarget()).copy(
                                name = name.trim().ifBlank { "Destino" },
                                url = url.trim(),
                                streamKey = streamKey.trim(),
                                backupUrl = backupUrl.trim()
                            )
                            onSave(target)
                        },
                        enabled = url.isNotBlank()
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}
