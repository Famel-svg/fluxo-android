package br.com.fluxo.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.fluxo.core.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID

private val accountDate = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
private fun parseDate(value: String) = runCatching { LocalDate.parse(value, accountDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
private fun parseMoney(value: String): Long? = runCatching {
    require(value.matches(Regex("-?[0-9]+([,.][0-9]{1,2})?")))
    BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact()
}.getOrNull()
private fun accountMoney(value: java.math.BigInteger) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(value, 2))

@Composable
fun AccountsPanel(accounts: List<Account>, rows: List<Transaction>, model: LedgerViewModel,
    saving: Boolean, error: String?) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var newAccount by rememberSaveable { mutableStateOf(false) }
    var transfer by rememberSaveable { mutableStateOf(false) }
    var linkOut by rememberSaveable { mutableStateOf<String?>(null) }
    var linkIn by rememberSaveable { mutableStateOf<String?>(null) }
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Contas e transferências", style = MaterialTheme.typography.titleMedium)
        Text("Saldo informado + movimentos de caixa confirmados desde a data inicial. Cobertura parcial; confira com seu banco.")
        accounts.forEach { account ->
            Text("${account.name}${if (account.archived) " (arquivada)" else ""}: ${AccountBalances.observed(account, rows)?.let(::accountMoney) ?: "Saldo inicial futuro"}")
            TextButton(onClick = { model.resetEditError(); editing = account.id }) { Text("Editar ${account.name}") }
        }
        OutlinedButton(onClick = { model.resetEditError(); newAccount = true }) { Text("Adicionar conta") }
        OutlinedButton(enabled = accounts.count { !it.archived } >= 2, onClick = { model.resetEditError(); transfer = true }) { Text("Registrar transferência própria") }
        val candidates = remember(rows) {
            val available = rows.filter { it.transferId == null && it.accountId != null && it.status == ReviewStatus.CONFIRMED && it.method != PaymentMethod.CREDIT_CARD }
            val outgoing = available.filter { it.direction == Direction.EXPENSE }.take(50)
            val incoming = available.filter { it.direction == Direction.INCOME }.take(50)
            outgoing.flatMap { out -> incoming.filter { it.accountId != out.accountId && it.amountCents == out.amountCents &&
                kotlin.math.abs(it.occurredAt - out.occurredAt) <= 3 * 86400000L }.map { out to it } }.take(10)
        }
        if (candidates.isNotEmpty()) {
            Text("Possíveis transferências entre registros recentes. Valor e data semelhantes não comprovam vínculo.")
            candidates.forEach { (out, input) ->
                TextButton(onClick = { model.resetEditError(); linkOut = out.id; linkIn = input.id }) {
                    Text("Vincular ${accounts.firstOrNull { it.id == out.accountId }?.name} → ${accounts.firstOrNull { it.id == input.accountId }?.name}: ${accountMoney(java.math.BigInteger.valueOf(out.amountCents ?: 0))}")
                }
            }
        }
    } }
    val account = accounts.firstOrNull { it.id == editing }
    if (newAccount || account != null) AccountEditor(account, saving, error, { newAccount = false; editing = null }) {
        model.saveAccount(it) { newAccount = false; editing = null }
    }
    if (transfer) TransferEditor(accounts.filter { !it.archived }, saving, error, { transfer = false }) { from, to, cents, date ->
        model.createTransfer(from, to, cents, date) { transfer = false }
    }
    if (linkOut != null && linkIn != null) AlertDialog(onDismissRequest = { if (!saving) { linkOut = null; linkIn = null } },
        title = { Text("Vincular como transferência própria?") },
        text = { Text(error ?: "Confirme que ambos representam a mesma movimentação entre suas contas. Não criará novos registros. O valor deixará de contar como renda/consumo; saldos por conta serão preservados. Para desfazer, abra um dos lançamentos.") },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            model.linkTransfer(linkOut!!, linkIn!!) { linkOut = null; linkIn = null }
        }) { Text("Confirmar vínculo") } }, dismissButton = { TextButton(enabled = !saving, onClick = { linkOut = null; linkIn = null }) { Text("Cancelar") } })
}

@Composable
internal fun AccountEditor(existing: Account?, saving: Boolean, error: String?, dismiss: () -> Unit, save: (Account) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var bank by rememberSaveable { mutableStateOf(existing?.bankPackage ?: "manual") }
    var opening by rememberSaveable { mutableStateOf(BigDecimal.valueOf(existing?.openingCents ?: 0, 2).toPlainString().replace('.', ',')) }
    var date by rememberSaveable { mutableStateOf(existing?.let { java.time.Instant.ofEpochMilli(it.openingAt).atZone(ZoneId.systemDefault()).toLocalDate().format(accountDate) } ?: LocalDate.now().format(accountDate)) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: AccountType.CHECKING) }
    val cents = parseMoney(opening)
    val at = parseDate(date)
    AlertDialog(onDismissRequest = { if (!saving) dismiss() }, title = { Text("Conta local") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nome da conta") })
            Choice("Tipo de conta", type, AccountType.entries, { it.label }, { type = it })
            Choice("Banco", bank, listOf("manual") + supportedBanks.map { it.packageName }, { id -> supportedBanks.firstOrNull { it.packageName == id }?.name ?: "Manual / dinheiro" }, { bank = it })
            OutlinedTextField(opening, { opening = it }, label = { Text("Saldo inicial em reais") }, supportingText = { Text("Pode ser negativo. Ex.: -50,00") }, isError = cents == null)
            OutlinedTextField(date, { date = it }, label = { Text("Saldo antes dos movimentos de (dd/mm/aaaa)") }, isError = at == null)
            Text("Movimentos anteriores à data inicial não entram neste saldo. Alterar o saldo informado muda a base do cálculo.")
            if (existing != null) Row { Text("Arquivada", Modifier.weight(1f)); Switch(checked = archived, onCheckedChange = { archived = it }) }
        }
    }, confirmButton = { TextButton(enabled = !saving && name.isNotBlank() && cents != null && at != null, onClick = {
        save(Account(existing?.id ?: UUID.randomUUID().toString(), name, bank, cents!!, at!!, archived, type))
    }) { Text("Salvar conta") } }, dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("Cancelar") } })
}

@Composable
internal fun TransferEditor(accounts: List<Account>, saving: Boolean, error: String?, dismiss: () -> Unit,
    save: (String, String, Long, Long) -> Unit) {
    var from by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var to by rememberSaveable { mutableStateOf(accounts.getOrNull(1)?.id.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().format(accountDate)) }
    val cents = parseMoney(amount)
    val at = parseDate(date)
    AlertDialog(onDismissRequest = { if (!saving) dismiss() }, title = { Text("Transferência própria") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cria saída e entrada vinculadas, sem consumo. Se os dois lançamentos já foram capturados, atribua suas contas e use o vínculo sugerido, evitando duplicatas.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Choice("Origem", from, accounts.map { it.id }, { id -> accounts.firstOrNull { it.id == id }?.name.orEmpty() }, { from = it })
            Choice("Destino", to, accounts.map { it.id }, { id -> accounts.firstOrNull { it.id == id }?.name.orEmpty() }, { to = it })
            OutlinedTextField(amount, { amount = it }, label = { Text("Valor da transferência") }, isError = amount.isNotEmpty() && (cents == null || cents <= 0))
            OutlinedTextField(date, { date = it }, label = { Text("Data da transferência (dd/mm/aaaa)") }, isError = at == null)
            Text("Eventual tarifa deve ser lançada como despesa separada.")
        }
    }, confirmButton = { TextButton(enabled = !saving && from != to && accounts.any { it.id == from } && accounts.any { it.id == to } && cents != null && cents > 0 && at != null,
        onClick = { save(from, to, cents!!, at!!) }) { Text("Registrar transferência") } },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("Cancelar") } })
}
