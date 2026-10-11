package br.com.fluxo.app

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.fluxo.core.CsvExport
import br.com.fluxo.core.Transaction
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CsvExportViewModel(application: Application) : AndroidViewModel(application) {
    private var snapshot: List<Transaction>? = null
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun prepare(rows: List<Transaction>): Boolean {
        if (!_busy.compareAndSet(false, true)) return false
        snapshot = rows.toList()
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
        if (uri == null) {
            _busy.value = false
            _message.value = "Exportação cancelada."
            return
        }
        if (rows == null) {
            _busy.value = false
            _message.value = "A sessão foi reiniciada. Inicie uma nova exportação."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Destino indisponível")
                stream.bufferedWriter(Charsets.UTF_8).use { writer -> CsvExport.write(rows, writer) }
                _message.value = "CSV salvo: ${rows.size} lançamento(s)."
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

@Composable
fun ExportCsvCard(transactions: List<Transaction>, model: CsvExportViewModel = viewModel()) {
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), model::destination)
    Column(Modifier.fillMaxWidth()) {
        FluxoDataRow(
            if (busy) "Exportando…" else "Exportar CSV", onClick = { confirm = true }, enabled = !busy,
            caption = "Exporte todos os meses, incluindo itens em revisão. Lançamentos excluídos ficam de fora. O CSV não serve para restaurar o aplicativo."
        )
        message?.let { Text(it, color = FluxoColors.Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Exportar dados financeiros?") },
        text = { Text("O arquivo contém valores, datas, bancos, descrições e categorias, sem criptografia. Escolha um destino de sua confiança; um provedor em nuvem pode sincronizar o arquivo. Nenhum texto bruto de notificação será incluído.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            if (model.prepare(transactions)) {
                try { launcher.launch("fluxo-${LocalDate.now()}.csv") }
                catch (_: Exception) { model.pickerUnavailable() }
            }
        }) { Text("Escolher destino") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
    )
}
