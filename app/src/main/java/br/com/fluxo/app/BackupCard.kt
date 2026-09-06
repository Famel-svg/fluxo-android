package br.com.fluxo.app

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.fluxo.core.BackupCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BackupState(val mode: String? = null, val busy: Boolean = false,
    val message: String? = null, val preview: BackupSummary? = null)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as FluxoApplication).repository
    private val mutable = MutableStateFlow(BackupState())
    val state = mutable.asStateFlow()
    private var document: Uri? = null
    private var pendingRestore: ByteArray? = null

    fun selected(uri: Uri?, exporting: Boolean) {
        if (mutable.value.busy) return
        discard()
        document = uri
        mutable.value = if (uri == null) BackupState(message = "Operação cancelada.")
            else BackupState(mode = if (exporting) "export" else "import")
    }

    fun unavailable() { discard(); mutable.value = BackupState(message = "Seletor de arquivos indisponível.") }
    fun cancel() { if (!mutable.value.busy) { discard(); mutable.value = BackupState(message = "Operação cancelada.") } }

    fun submit(password: CharArray) {
        val before = mutable.value
        val uri = document
        if (before.busy || before.mode == null || uri == null) { password.fill('\u0000'); return }
        mutable.value = BackupState(busy = true)
        viewModelScope.launch(Dispatchers.IO) {
            var plain: ByteArray? = null
            try {
                val resolver = getApplication<Application>().contentResolver
                if (before.mode == "export") {
                    plain = repository.exportBackupPayload()
                    val encrypted = BackupCipher.encrypt(plain, password)
                    try {
                        val output = resolver.openOutputStream(uri, "wt") ?: error("Destino indisponível")
                        output.use { it.write(encrypted) }
                        mutable.value = BackupState(message = "Backup criptografado salvo. Guarde a senha separadamente e teste a restauração.")
                    } finally { encrypted.fill(0) }
                } else {
                    val encrypted = resolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val count = input.read(chunk)
                            if (count == -1) break
                            require(output.size() + count <= BackupCipher.MAX_BLOB_BYTES)
                            output.write(chunk, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("Arquivo indisponível")
                    plain = try { BackupCipher.decrypt(encrypted, password) } finally { encrypted.fill(0) }
                    val summary = repository.inspectBackupPayload(plain)
                    pendingRestore = plain
                    plain = null
                    mutable.value = BackupState(preview = summary)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.value = BackupState(message = if (before.mode == "export")
                    "Não foi possível salvar. O destino pode conter arquivo incompleto; exporte novamente."
                    else "Não foi possível abrir: senha incorreta, arquivo inválido ou versão incompatível. Histórico mantido.")
            } finally {
                plain?.fill(0)
                password.fill('\u0000')
                document = null
            }
        }
    }

    fun restore() {
        val payload = pendingRestore ?: return
        if (mutable.value.busy || mutable.value.preview == null) return
        pendingRestore = null
        mutable.value = BackupState(busy = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.restoreBackupPayload(payload)
                mutable.value = BackupState(message = "Histórico restaurado. Captura pausada; confira dados e bancos antes de reativar.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = BackupState(message = "Restauração não concluída. Histórico anterior preservado; captura pode estar pausada.") }
            finally { payload.fill(0) }
        }
    }

    private fun discard() { document = null; pendingRestore?.fill(0); pendingRestore = null }
    override fun onCleared() { discard(); super.onCleared() }
}

data class BackupActions(val state: BackupState, val export: () -> Unit, val import: () -> Unit)

/** Register pickers outside LazyColumn so scrolling cannot unregister active result callbacks. */
@Composable
fun rememberBackupActions(model: BackupViewModel = viewModel()): BackupActions {
    val state by model.state.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { model.selected(it, true) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.selected(it, false) }
    var password by remember(state.mode) { mutableStateOf("") }
    var repeated by remember(state.mode) { mutableStateOf("") }
    if (state.mode != null) AlertDialog(
        onDismissRequest = model::cancel,
        title = { Text(if (state.mode == "export") "Proteger backup" else "Desbloquear backup") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.mode == "export") "Use pelo menos 12 caracteres. Sem a senha, não será possível recuperar este arquivo."
                else "Informe a senha usada ao criar o backup. Você verá uma prévia antes de substituir dados.")
            OutlinedTextField(password, { password = it.take(256) }, label = { Text("Senha do backup") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true)
            if (state.mode == "export") OutlinedTextField(repeated, { repeated = it.take(256) }, label = { Text("Repetir senha") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true)
        } },
        confirmButton = { TextButton(enabled = password.isNotEmpty() && (state.mode != "export" || (password.length >= 12 && password == repeated)), onClick = {
            val secret = password.toCharArray(); password = ""; repeated = ""; model.submit(secret)
        }) { Text(if (state.mode == "export") "Criar backup" else "Verificar arquivo") } },
        dismissButton = { TextButton(onClick = model::cancel) { Text("Cancelar") } },
    )
    state.preview?.let { preview -> AlertDialog(
        onDismissRequest = model::cancel,
        title = { Text("Substituir histórico pelo backup?") },
        text = { Text("Arquivo verificado: ${preview.transactions} lançamento(s), ${preview.budgets} orçamento(s), ${preview.accounts} conta(s) e ${preview.deleted} exclusão(ões) protegida(s). O histórico atual será substituído, sem mesclar. Exporte um backup atual antes se precisar conservá-lo. A captura ficará pausada; permissões e seleção de bancos deste aparelho serão mantidas.") },
        confirmButton = { TextButton(onClick = model::restore) { Text("Substituir e restaurar") } },
        dismissButton = { TextButton(onClick = model::cancel) { Text("Cancelar") } },
    ) }
    return BackupActions(state,
        export = { try { export.launch("fluxo-${LocalDate.now()}.fluxobkp") } catch (_: Exception) { model.unavailable() } },
        import = { try { import.launch(arrayOf("application/octet-stream", "*/*")) } catch (_: Exception) { model.unavailable() } },
    )
}

@Composable
fun BackupCard(actions: BackupActions) {
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Backup e recuperação", style = MaterialTheme.typography.titleMedium)
        Text("Guarde histórico, orçamentos e identificadores contra duplicatas em arquivo criptografado. Diferente do CSV, permite restaurar o aplicativo. Até 50 mil lançamentos e arquivo de 16 MiB.")
        val enabled = !actions.state.busy && actions.state.mode == null && actions.state.preview == null
        OutlinedButton(onClick = actions.export, enabled = enabled) { Text("Criar backup protegido") }
        OutlinedButton(onClick = actions.import, enabled = enabled) { Text("Restaurar backup") }
        if (actions.state.busy) Text("Processando backup…")
        actions.state.message?.let { Text(it) }
    } }
}
