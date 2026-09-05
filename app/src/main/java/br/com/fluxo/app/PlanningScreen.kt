package br.com.fluxo.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.fluxo.core.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

private fun planningMoney(cents: BigInteger): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(cents, 2))

@Composable
internal fun PlanningScreen(
    transactions: List<Transaction>, budgets: List<Budget>, month: YearMonth,
    saving: Boolean, editError: String?, resetError: () -> Unit,
    save: (Budget, () -> Unit) -> Unit,
    delete: (String, Category, () -> Unit) -> Unit
) {
    val totals = remember(transactions, month) { SpendingAnalytics.byCategory(transactions, month) }
    val previous = remember(transactions, month) { SpendingAnalytics.byCategory(transactions, month.minusMonths(1)) }
    val total = totals.values.fold(BigInteger.ZERO, BigInteger::add)
    val previousTotal = previous.values.fold(BigInteger.ZERO, BigInteger::add)
    val monthBudgets = budgets.filter { it.month == month.toString() }
    val counts = remember(transactions, month) {
        transactions.filter {
            it.status == ReviewStatus.CONFIRMED && it.direction == Direction.EXPENSE && (it.amountCents ?: 0) > 0 &&
                YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())) == month
        }.groupingBy { it.category }.eachCount()
    }
    var dialogMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogCategory by rememberSaveable { mutableStateOf(Category.UNCATEGORIZED) }
    var dialogLimit by rememberSaveable { mutableStateOf("") }
    var existingBudget by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    fun open(budget: Budget?) {
        resetError()
        dialogMonth = month.toString()
        dialogCategory = budget?.category ?: Category.UNCATEGORIZED
        dialogLimit = budget?.let { BigDecimal.valueOf(it.limitCents, 2).toPlainString().replace('.', ',') } ?: ""
        existingBudget = budget != null
        deleting = false
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Despesas por categoria", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${planningMoney(total)} em despesas confirmadas", style = MaterialTheme.typography.titleLarge)
            Text("Inclui compromissos no crédito. Itens em revisão não entram. Cobertura parcial das notificações; não representa saldo nem fatura.", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Comparação com mês anterior", style = MaterialTheme.typography.titleMedium)
                Text("${month.minusMonths(1)}: ${planningMoney(previousTotal)}")
                val difference = total - previousTotal
                Text(when {
                    previousTotal == BigInteger.ZERO && total == BigInteger.ZERO -> "Sem despesas confirmadas nos dois meses."
                    previousTotal == BigInteger.ZERO -> "Sem base no mês anterior para calcular variação percentual."
                    difference == BigInteger.ZERO -> "Mesmo total de despesas do mês anterior."
                    else -> {
                        val percentage = BigDecimal(difference.abs()).multiply(BigDecimal(100)).divide(BigDecimal(previousTotal), 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                        "${if (difference.signum() > 0) "Aumento" else "Redução"} de ${planningMoney(difference.abs())} ($percentage%)."
                    }
                })
            } }
        }
        item { Button(onClick = { open(null) }, modifier = Modifier.fillMaxWidth()) { Text("Definir limite mensal") } }
        if (totals.isEmpty() && monthBudgets.isEmpty()) item { Text("Sem despesas ou limites neste mês. Defina um limite ou categorize seus lançamentos.") }
        items(Category.entries.filter { (totals[it] ?: BigInteger.ZERO) > BigInteger.ZERO || monthBudgets.any { budget -> budget.category == it } }, key = { it.name }) { category ->
            val spent = totals[category] ?: BigInteger.ZERO
            val budget = monthBudgets.firstOrNull { it.category == category }
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(category.label, style = MaterialTheme.typography.titleMedium)
                Text(planningMoney(spent), style = MaterialTheme.typography.titleLarge)
                val percentage = if (total.signum() == 0) "0" else BigDecimal(spent).multiply(BigDecimal(100)).divide(BigDecimal(total), 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                Text("${counts[category] ?: 0} lançamento(s) · $percentage% das despesas")
                if (budget != null) {
                    val limit = BigInteger.valueOf(budget.limitCents)
                    val remaining = limit - spent
                    Text("Limite: ${planningMoney(limit)}")
                    LinearProgressIndicator(progress = { BigDecimal(spent).divide(BigDecimal(limit), 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(if (remaining.signum() < 0) "Excesso: ${planningMoney(remaining.abs())}" else "Restante: ${planningMoney(remaining)}", color = if (remaining.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { open(budget) }) { Text("Editar limite de ${category.label}") }
                } else Text("Sem limite definido")
            } }
        }
    }
    val targetMonth = dialogMonth
    if (targetMonth != null) {
        val cents = remember(dialogLimit) {
            runCatching {
                require(dialogLimit.matches(Regex("[0-9]+([,.][0-9]{1,2})?")))
                BigDecimal(dialogLimit.replace(',', '.')).movePointRight(2).longValueExact().also { require(it > 0) }
            }.getOrNull()
        }
        AlertDialog(onDismissRequest = { if (!saving) dialogMonth = null }, title = { Text(if (existingBudget) "Editar limite mensal" else "Novo limite mensal") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mês: $targetMonth")
                if (existingBudget) Text("Categoria: ${dialogCategory.label}")
                else Choice("Categoria", dialogCategory, Category.entries, { it.label }) { dialogCategory = it }
                OutlinedTextField(dialogLimit, { dialogLimit = it }, label = { Text("Limite em reais") }, supportingText = { Text("Ex.: 500,00 · sem separador de milhar") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = dialogLimit.isNotEmpty() && cents == null)
                if (!existingBudget && budgets.any { it.category == dialogCategory && it.month == targetMonth }) Text("Salvar substituirá o limite já definido para esta categoria e mês.")
                if (editError != null) Text(editError, color = MaterialTheme.colorScheme.error)
                if (existingBudget) TextButton(enabled = !saving, onClick = { deleting = true }) { Text("Remover limite", color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(enabled = !saving && cents != null, onClick = {
            val limit = cents ?: return@TextButton
            save(Budget(targetMonth, dialogCategory, limit)) { dialogMonth = null }
        }) { Text(if (saving) "Salvando…" else "Salvar limite") } }, dismissButton = { TextButton(enabled = !saving, onClick = { dialogMonth = null }) { Text("Cancelar") } })
        if (deleting) AlertDialog(onDismissRequest = { if (!saving) deleting = false }, title = { Text("Remover este limite?") }, text = { Text(editError ?: "Somente o limite de ${dialogCategory.label} em $targetMonth será removido. Os lançamentos serão mantidos.") }, confirmButton = { TextButton(enabled = !saving, onClick = { delete(targetMonth, dialogCategory) { deleting = false; dialogMonth = null } }) { Text(if (saving) "Removendo…" else "Remover") } }, dismissButton = { TextButton(enabled = !saving, onClick = { deleting = false }) { Text("Cancelar") } })
    }
}
