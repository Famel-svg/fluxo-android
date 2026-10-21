package br.com.fluxo.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
    val cents = remember(amount) { parseAmountInput(amount) }
    val parsedDate = remember(date) { runCatching { LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)) }.getOrNull() }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Slides up a little while fading in; the dialog window itself is removed at once on close.
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
        Surface(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * size.height * .06f
            },
            color = FluxoColors.Paper
        ) {
            // Centred column so wide layouts do not stretch the form and the save button edge to edge.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = { if (!saving) onDismiss() }, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                    Image(painterResource(if (FluxoColors.IsDark) R.drawable.ic_fluxo_mark_dark else R.drawable.ic_fluxo_mark), contentDescription = null, modifier = Modifier.size(36.dp))
                    Text(
                        if (existing == null) "Novo lançamento" else "Revisar lançamento",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (existing?.status == ReviewStatus.REVIEW) FluxoTicket {
                        Text(existing.reason.ifBlank { "Confira os dados antes de confirmar." }, modifier = Modifier.padding(vertical = 6.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    if (editError != null) Text(editError, color = MaterialTheme.colorScheme.error)
                    FluxoPanel(accent = FluxoColors.Petrol) {
                        Text("Valor", style = MaterialTheme.typography.titleMedium, color = FluxoColors.Mute)
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Valor em reais") },
                            placeholder = { Text("R$ 0,00") },
                            supportingText = { Text("Ex.: 125,90 · sem separador de milhar") },
                            textStyle = FluxoType.number(32.sp, FontWeight.ExtraBold),
                            shape = RoundedCornerShape(16.dp), colors = editorFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = cents == null && (amount.isNotEmpty() || existing != null),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Direction.EXPENSE, Direction.INCOME).forEach { option ->
                                FluxoChip(option.label(), direction == option, { direction = option }, Modifier.weight(1f))
                            }
                        }
                    }
                    FluxoPanel(accent = FluxoColors.PetrolMid) {
                        FluxoSectionHeading("Detalhes", icon = Icons.AutoMirrored.Filled.ReceiptLong, accent = FluxoColors.Petrol)
                        OutlinedTextField(value = description, onValueChange = { description = it.take(120) }, label = { Text("Descrição") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = editorFieldColors(), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = date, onValueChange = { date = formatDateInput(it) }, label = { Text("Data (dd/mm/aaaa)") }, isError = parsedDate == null, singleLine = true, shape = RoundedCornerShape(16.dp), colors = editorFieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    }
                    FluxoPanel(accent = FluxoColors.Petrol) {
                        FluxoSectionHeading("Classificação", icon = Icons.Default.PieChart, accent = FluxoColors.Petrol)
                        Choice("Categoria", category, Category.entries, { it.label }) { category = it }
                        Choice("Meio", method, PaymentMethod.entries, { it.label() }) { method = it }
                        Choice("Origem", bank, listOf("manual") + supportedBanks.map { it.packageName }, ::bankName) { bank = it; accountId = null }
                        Choice("Conta", accountId, listOf<String?>(null) + accounts.filter { !it.archived || it.id == accountId }.map { it.id },
                            { id -> accounts.firstOrNull { it.id == id }?.name ?: "Não atribuída" }) { id -> accountId = id; accounts.firstOrNull { it.id == id }?.let { bank = it.bankPackage } }
                        if (method == PaymentMethod.CREDIT_CARD) Text("Compras no crédito ficam separadas das saídas de caixa.", style = MaterialTheme.typography.bodySmall, color = FluxoColors.Mute)
                        if (method.isVoucher) Text("Gastos com vale ficam fora das saídas de caixa e abatem o saldo estimado do vale.", style = MaterialTheme.typography.bodySmall, color = FluxoColors.Mute)
                        if (existing != null) TextButton(enabled = !saving, onClick = { deleting = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Excluir lançamento", color = FluxoColors.Expense, style = MaterialTheme.typography.labelLarge) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // Say what still blocks saving instead of leaving a silently disabled button.
                val blockers = buildList {
                    if (cents == null) add(if (amount.isBlank()) "informe o valor" else "valor inválido (ex.: 125,90)")
                    if (direction == Direction.UNKNOWN) add("escolha Saída ou Entrada")
                    if (parsedDate == null) add("informe uma data válida")
                }
                if (blockers.isNotEmpty() && !saving) Text("Para salvar: " + blockers.joinToString("; ") + ".",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp))
                FluxoPillButton(
                    if (saving) "Salvando…" else "Salvar lançamento",
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 56.dp),
                    enabled = !saving && cents != null && direction != Direction.UNKNOWN && parsedDate != null,
                    onClick = {
        val validDate = parsedDate ?: return@FluxoPillButton
        val originalDate = existing?.let { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
        val occurred = if (existing != null && originalDate == validDate) existing.occurredAt else validDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        onSave(Transaction(existing?.id ?: UUID.randomUUID().toString(), existing?.sourceKey, bank, cents, direction, method, ReviewStatus.CONFIRMED, occurred, description.ifBlank { "Lançamento manual" }, "", category, accountId))
                    }
                )
            }
            }
        }
    }
    if (deleting && existing != null) AlertDialog(onDismissRequest = { if (!saving) deleting = false }, title = { Text("Excluir lançamento?") }, text = { Text(editError ?: "O lançamento será removido dos totais. Sua notificação original não recriará este registro.") }, confirmButton = { TextButton(enabled = !saving, onClick = { onDelete(existing.id) }) { Text(if (saving) "Excluindo…" else "Excluir") } }, dismissButton = { TextButton(enabled = !saving, onClick = { deleting = false }) { Text("Cancelar") } })
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FluxoColors.Ink, unfocusedBorderColor = FluxoColors.Ink,
    focusedContainerColor = FluxoColors.Paper, unfocusedContainerColor = FluxoColors.Paper,
    focusedLabelColor = FluxoColors.Mute, unfocusedLabelColor = FluxoColors.Mute,
    cursorColor = FluxoColors.Ink
)

/** Dropdown choice shown as an outlined pill row; the single text node keeps the "label: value" form. */
@Composable
internal fun <T> Choice(label: String, selected: T, options: List<T>, display: (T) -> String, change: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(FluxoShapes.Pill).background(FluxoColors.Paper)
                .border(2.dp, FluxoColors.ChipBorder, FluxoShapes.Pill)
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$label: ${display(selected)}", modifier = Modifier.weight(1f), color = FluxoColors.Ink,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
            Icon(Icons.Default.ArrowDropDown, null, tint = FluxoColors.Ink)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = FluxoColors.Paper) {
            options.forEach { option -> DropdownMenuItem(text = { Text(display(option)) }, onClick = { change(option); expanded = false }) }
        }
    }
}

/**
 * Keeps the date field friendly to the numeric keyboard: only digits and "/" survive, and when the
 * text is digits only the slashes after day and month are inserted automatically (typing "/" still works).
 */
internal fun formatDateInput(raw: String): String {
    val kept = raw.filter { it.isDigit() || it == '/' }.take(10)
    if ('/' in kept) return kept
    val digits = kept
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('/')
            append(c)
        }
    }
}

/** Amount typed by the owner in reais: accepts "125,90", "125.90", "1.234,56" and an "R$" prefix; null when invalid. */
internal fun parseAmountInput(input: String): Long? = runCatching {
    val clean = input.replace("R$", "").replace(" ", "").replace("\u00a0", "")
        .let { if (',' in it) it.replace(".", "") else it }
    require(clean.matches(Regex("[0-9]+([,.][0-9]{1,2})?")))
    BigDecimal(clean.replace(',', '.')).movePointRight(2).longValueExact().also { require(it > 0) }
}.getOrNull()
