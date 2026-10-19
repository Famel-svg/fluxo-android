package br.com.fluxo.app

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.fluxo.core.NotificationTriage
import br.com.fluxo.core.TriageDecision
import br.com.fluxo.core.TriageItem
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Provisional triage: owner decides which observed notifications become transactions and exports samples. */
class TriageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as FluxoApplication).repository
    val enabled = repository.triageEnabled
    val items = repository.triage
    private var snapshot: List<TriageItem>? = null
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private fun execute(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Não foi possível concluir. Tente novamente." }
        }
    }

    fun setEnabled(value: Boolean) = execute { repository.setTriageEnabled(value) }
    fun keep(id: Long) = execute { repository.keepTriage(id) }
    fun discard(id: Long) = execute { repository.discardTriage(id) }
    fun clear() = execute { repository.clearTriage(); _message.value = "Triagem apagada." }

    fun prepare(): Boolean {
        if (!_busy.compareAndSet(false, true)) return false
        snapshot = items.value.toList()
        _message.value = null
        return true
    }

    fun pickerUnavailable() {
        snapshot = null
        _busy.value = false
        _message.value = "Não foi possível abrir o seletor de arquivos."
    }

    fun destination(uri: Uri?) {
        val rows = snapshot
        snapshot = null
        if (uri == null || rows == null) {
            _busy.value = false
            _message.value = if (uri == null) "Exportação cancelada." else "A sessão foi reiniciada. Inicie uma nova exportação."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Destino indisponível")
                stream.bufferedWriter(Charsets.UTF_8).use { NotificationTriage.writeJson(rows, it, System.currentTimeMillis()) }
                _message.value = "JSON salvo: ${rows.size} notificação(ões)."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _message.value = "Não foi possível concluir. O destino pode conter um arquivo incompleto; tente exportar novamente."
            } finally {
                _busy.value = false
            }
        }
    }
}

private val triageTime = DateTimeFormatter.ofPattern("dd/MM HH:mm")

private fun TriageDecision.label() = when (this) {
    TriageDecision.PENDING -> "pendente"
    TriageDecision.KEPT -> "guardada"
    TriageDecision.DISCARDED -> "descartada"
    TriageDecision.AUTO_STORED -> "reconhecida"
}

@Composable
fun TriageCard(model: TriageViewModel = viewModel()) {
    val enabled by model.enabled.collectAsStateWithLifecycle()
    val items by model.items.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var confirmEnable by rememberSaveable { mutableStateOf(false) }
    var confirmExport by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), model::destination)
    val pending = items.count { it.decision == TriageDecision.PENDING }

    FluxoSettingsCard(Modifier.testTag("settings.triage")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Triagem de notificações", color = FluxoColors.Ink,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold))
                Text("Provisório. Guarda o texto de todas as notificações para você decidir o que vira lançamento.",
                    color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = enabled, onCheckedChange = { if (it) confirmEnable = true else model.setEnabled(false) },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("settings.triage.switch")
                    .semantics { contentDescription = "Triagem de notificações" }
            )
        }
        if (enabled || items.isNotEmpty()) {
            FluxoDataRow(
                "Revisar notificações", onClick = { reviewing = true }, modifier = Modifier.testTag("settings.triage.open"),
                caption = "$pending pendente(s) de ${items.size} guardada(s) no aparelho."
            )
            FluxoDataRow(
                if (busy) "Exportando…" else "Exportar JSON", onClick = { confirmExport = true }, enabled = !busy && items.isNotEmpty(),
                caption = "Lista o id de cada app e o texto recebido, para ajustar a captura por app."
            )
            FluxoDataRow("Apagar triagem", onClick = { confirmClear = true }, destructive = true, enabled = items.isNotEmpty())
        }
        message?.let { Text(it, color = FluxoColors.Ink, style = MaterialTheme.typography.bodySmall) }
    }

    if (reviewing) TriageReviewDialog(items, onKeep = model::keep, onDiscard = model::discard, onDismiss = { reviewing = false })
    if (confirmEnable) AlertDialog(
        onDismissRequest = { confirmEnable = false },
        title = { Text("Ativar triagem?") },
        text = { Text("Enquanto ativa, o Fluxo guarda neste aparelho o texto completo de todas as notificações recebidas (até ${NotificationTriage.MAX_ITEMS}), inclusive mensagens pessoais. Nada é enviado à internet nem entra no backup. Desative e apague a triagem quando terminar.") },
        confirmButton = { TextButton(onClick = { confirmEnable = false; model.setEnabled(true) }) { Text("Ativar") } },
        dismissButton = { TextButton(onClick = { confirmEnable = false }) { Text("Cancelar") } },
    )
    if (confirmExport) AlertDialog(
        onDismissRequest = { confirmExport = false },
        title = { Text("Exportar notificações?") },
        text = { Text("O arquivo JSON contém o texto completo das notificações guardadas, sem criptografia, e pode incluir valores, nomes e mensagens pessoais. Escolha um destino de sua confiança e apague o arquivo depois de usar.") },
        confirmButton = { TextButton(onClick = {
            confirmExport = false
            if (model.prepare()) {
                try { launcher.launch("fluxo-triagem-${LocalDate.now()}.json") }
                catch (_: Exception) { model.pickerUnavailable() }
            }
        }) { Text("Escolher destino") } },
        dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("Cancelar") } },
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Apagar a triagem?") },
        text = { Text("Apaga o texto de todas as notificações guardadas na triagem. Lançamentos já guardados continuam no histórico.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; model.clear() }) { Text("Apagar", color = FluxoColors.Expense) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun TriageReviewDialog(
    items: List<TriageItem>, onKeep: (Long) -> Unit, onDiscard: (Long) -> Unit, onDismiss: () -> Unit,
) {
    var onlyPending by rememberSaveable { mutableStateOf(true) }
    val shown = if (onlyPending) items.filter { it.decision == TriageDecision.PENDING } else items
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = FluxoColors.Paper) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                    Text("Triagem", style = FluxoType.number(18.sp, FontWeight.ExtraBold), color = FluxoColors.Ink)
                }
                Row(Modifier.padding(horizontal = 14.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FluxoChip("Pendentes", selected = onlyPending, onClick = { onlyPending = true })
                    FluxoChip("Todas", selected = !onlyPending, onClick = { onlyPending = false })
                }
                if (shown.isEmpty()) FluxoEmptyState(
                    "Nada para revisar",
                    "Notificações recebidas com a triagem ativa aparecem aqui.",
                    Modifier.padding(horizontal = 14.dp)
                ) else LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("triage.list")
                ) {
                    items(shown, key = { it.id }) { item -> TriageRow(item, onKeep, onDiscard) }
                }
            }
        }
    }
}

@Composable
private fun TriageRow(item: TriageItem, onKeep: (Long) -> Unit, onDiscard: (Long) -> Unit) {
    FluxoTicket {
        FluxoTicketHeader(item.appLabel ?: item.packageName, badge = item.decision.label())
        Text(item.packageName + " · " + Instant.ofEpochMilli(item.postedAt).atZone(ZoneId.systemDefault()).format(triageTime),
            color = FluxoColors.Mute, style = FluxoType.Caption.copy(fontSize = 11.5.sp))
        Text(item.text, color = FluxoColors.Ink, maxLines = 6, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp))
        Text("Leitor: ${item.verdict}", color = FluxoColors.Mute, style = FluxoType.Caption.copy(fontSize = 11.sp))
        if (item.decision == TriageDecision.PENDING) FluxoTicketActions {
            FluxoPillButton("Descartar", onClick = { onDiscard(item.id) }, style = FluxoButtonStyle.Secondary,
                modifier = Modifier.weight(1f).testTag("triage.discard"))
            FluxoPillButton("Guardar", onClick = { onKeep(item.id) }, modifier = Modifier.weight(1f).testTag("triage.keep"))
        }
    }
}
