package br.com.fluxo.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.fluxo.core.Direction
import br.com.fluxo.core.Account
import br.com.fluxo.core.Category
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
internal fun TransactionEditor(existing: Transaction?, saving: Boolean, editError: String?, accounts: List<Account>, onUnlink: (String) -> Unit, onDismiss: () -> Unit, onSave: (Transaction) -> Unit, onDelete: (String) -> Unit) {
    val transferId = existing?.transferId
    if (transferId != null) {
        AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Transferência vinculada") },
            text = { Text(editError ?: "Os dois lançamentos compõem uma transferência própria e não entram em renda/consumo. Para editar ou excluir, desvincule primeiro. Isso mantém ambos os registros e volta a tratá-los como entrada e saída comuns.") },
            confirmButton = { TextButton(enabled = !saving, onClick = { onUnlink(transferId) }) { Text("Desvincular transferência") } },
            dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Fechar") } })
        return
    }
    var amount by rememberSaveable(existing?.id) { mutableStateOf(existing?.amountCents?.let { BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') } ?: "") }
    var description by rememberSaveable(existing?.id) { mutableStateOf(existing?.description ?: "") }
    var direction by rememberSaveable(existing?.id) { mutableStateOf(existing?.direction ?: Direction.EXPENSE) }
    var method by rememberSaveable(existing?.id) { mutableStateOf(existing?.method ?: PaymentMethod.UNKNOWN) }
    var bank by rememberSaveable(existing?.id) { mutableStateOf(existing?.bankPackage ?: "manual") }
    var accountId by rememberSaveable(existing?.id) { mutableStateOf(existing?.accountId) }
    var category by rememberSaveable(existing?.id) { mutableStateOf(existing?.category ?: Category.UNCATEGORIZED) }
    var date by rememberSaveable(existing?.id) { mutableStateOf((existing?.let { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val cents = remember(amount) { runCatching { require(amount.matches(Regex("[0-9]+([,.][0-9]{1,2})?"))); BigDecimal(amount.replace(',', '.')).movePointRight(2).longValueExact().also { require(it > 0) } }.getOrNull() }
    val parsedDate = remember(date) { runCatching { LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)) }.getOrNull() }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = { if (!saving) onDismiss() }, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                    Image(painterResource(R.drawable.ic_fluxo_mark), contentDescription = null, modifier = Modifier.size(36.dp))
                    Text(
                        if (existing == null) "Novo lançamento" else "Revisar lançamento",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (existing?.status == ReviewStatus.REVIEW) Text(existing.reason.ifBlank { "Confira os dados antes de confirmar." }, color = MaterialTheme.colorScheme.error)
                    if (editError != null) Text(editError, color = MaterialTheme.colorScheme.error)
                    FluxoPanel(
                        accent = FluxoDesignTokens.Mint,
                        gradient = listOf(Color(0xFF003C3D), Color(0xFF06213A))
                    ) {
                        Text("Valor", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Valor em reais") },
                            placeholder = { Text("R$ 0,00") },
                            supportingText = { Text("Ex.: 125,90 · sem separador de milhar") },
                            textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = amount.isNotEmpty() && cents == null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(Direction.EXPENSE, Direction.INCOME).forEach { option ->
                                val selected = direction == option
                                Button(
                                    onClick = { direction = option },
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) FluxoDesignTokens.Mint else Color.Transparent,
                                        contentColor = if (selected) Color(0xFF002D26) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) { Text(option.label(), fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                    FluxoPanel(accent = FluxoDesignTokens.Blue) {
                        FluxoSectionHeading("Detalhes", icon = Icons.AutoMirrored.Filled.ReceiptLong)
                        OutlinedTextField(value = description, onValueChange = { description = it.take(120) }, label = { Text("Descrição") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Data (dd/mm/aaaa)") }, isError = parsedDate == null, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
                    }
                    FluxoPanel(accent = FluxoDesignTokens.Mint) {
                        FluxoSectionHeading("Classificação", icon = Icons.Default.PieChart)
                        Choice("Categoria", category, Category.entries, { it.label }) { category = it }
                        Choice("Pagamento", method, PaymentMethod.entries, { it.label() }) { method = it }
                        Choice("Origem", bank, listOf("manual") + supportedBanks.map { it.packageName }, ::bankName) { bank = it; accountId = null }
                        Choice("Conta", accountId, listOf<String?>(null) + accounts.filter { !it.archived || it.id == accountId }.map { it.id },
                            { id -> accounts.firstOrNull { it.id == id }?.name ?: "Não atribuída" }) { id -> accountId = id; accounts.firstOrNull { it.id == id }?.let { bank = it.bankPackage } }
                        if (method == PaymentMethod.CREDIT_CARD) Text("Compras no crédito ficam separadas das saídas de caixa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (existing != null) TextButton(enabled = !saving, onClick = { deleting = true }) { Text("Excluir lançamento", color = MaterialTheme.colorScheme.error) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Button(
                    enabled = !saving && cents != null && direction != Direction.UNKNOWN && parsedDate != null,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FluxoDesignTokens.Mint, contentColor = Color(0xFF002D26)),
                    onClick = {
        val validDate = parsedDate ?: return@Button
        val originalDate = existing?.let { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
        val occurred = if (existing != null && originalDate == validDate) existing.occurredAt else validDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        onSave(Transaction(existing?.id ?: UUID.randomUUID().toString(), existing?.sourceKey, bank, cents, direction, method, ReviewStatus.CONFIRMED, occurred, description.ifBlank { "Lançamento manual" }, "", category, accountId))
                    }
                ) { Text(if (saving) "Salvando…" else "Salvar lançamento", fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (deleting && existing != null) AlertDialog(onDismissRequest = { if (!saving) deleting = false }, title = { Text("Excluir lançamento?") }, text = { Text(editError ?: "O lançamento será removido dos totais. Sua notificação original não recriará este registro.") }, confirmButton = { TextButton(enabled = !saving, onClick = { onDelete(existing.id) }) { Text(if (saving) "Excluindo…" else "Excluir") } }, dismissButton = { TextButton(enabled = !saving, onClick = { deleting = false }) { Text("Cancelar") } })
}

@Composable
internal fun <T> Choice(label: String, selected: T, options: List<T>, display: (T) -> String, change: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${display(selected)}", modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(display(option)) }, onClick = { change(option); expanded = false }) } }
    }
}
