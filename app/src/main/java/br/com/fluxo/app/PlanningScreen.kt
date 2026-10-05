package br.com.fluxo.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    delete: (String, Category, () -> Unit) -> Unit,
    benefits: BenefitSettings,
    saveBenefits: (YearMonth, Long, Long, BenefitDays, () -> Unit) -> Unit
) {
    val totals = remember(transactions, month) { SpendingAnalytics.byCategory(transactions, month) }
    val previous = remember(transactions, month) { SpendingAnalytics.byCategory(transactions, month.minusMonths(1)) }
    val total = totals.values.fold(BigInteger.ZERO, BigInteger::add)
    val previousTotal = previous.values.fold(BigInteger.ZERO, BigInteger::add)
    val monthBudgets = budgets.filter { it.month == month.toString() }
    val counts = remember(transactions, month) {
        transactions.filter {
            it.status == ReviewStatus.CONFIRMED && it.transferId == null && it.direction == Direction.EXPENSE && (it.amountCents ?: 0) > 0 &&
                YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())) == month
        }.groupingBy { it.category }.eachCount()
    }
    val lastWeek = remember(transactions, month) {
        (0L..6L).map { offset ->
            val date = month.atEndOfMonth().minusDays(6 - offset)
            val cents = transactions.asSequence().filter {
                it.status == ReviewStatus.CONFIRMED && it.transferId == null && it.direction == Direction.EXPENSE &&
                    (it.amountCents ?: 0) > 0 &&
                    Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() == date
            }.fold(BigInteger.ZERO) { sum, transaction -> sum + BigInteger.valueOf(transaction.amountCents ?: 0) }
            date to cents
        }
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
    var benefitDialogMonth by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "benefits") {
            BenefitSummary(month, benefits) {
                resetError()
                benefitDialogMonth = month.toString()
            }
        }
        item {
            FluxoPanel(accent = FluxoDesignTokens.Blue, gradient = listOf(Color(0xFF002B3B), Color(0xFF061C34), Color(0xFF001020))) {
                FluxoSectionHeading("Despesas confirmadas", accent = FluxoDesignTokens.Blue)
                Text(planningMoney(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("$month · inclui compras no crédito; itens em revisão e transferências próprias ficam fora.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Últimos 7 dias do mês", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LastWeekSpendChart(lastWeek)
                Text("Dados reconhecidos podem ser parciais; valores não representam saldo nem fatura.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            FluxoPanel(accent = FluxoDesignTokens.Mint) {
                FluxoSectionHeading("Comparação mensal")
                Text("${month.minusMonths(1)} · ${planningMoney(previousTotal)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val difference = total - previousTotal
                Text(when {
                    previousTotal == BigInteger.ZERO && total == BigInteger.ZERO -> "Sem despesas confirmadas nos dois meses."
                    previousTotal == BigInteger.ZERO -> "Sem base no mês anterior para calcular variação percentual."
                    difference == BigInteger.ZERO -> "Mesmo total de despesas do mês anterior."
                    else -> {
                        val percentage = BigDecimal(difference.abs()).multiply(BigDecimal(100)).divide(BigDecimal(previousTotal), 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                        "${if (difference.signum() > 0) "Aumento" else "Redução"} de ${planningMoney(difference.abs())} ($percentage%)."
                    }
                }, style = MaterialTheme.typography.titleMedium)
            }
        }
        item { Button(onClick = { open(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Definir limite mensal") } }
        if (totals.isEmpty() && monthBudgets.isEmpty()) item { FluxoEmptyState("Sem despesas ou limites neste mês.", "Defina um limite ou categorize seus lançamentos.") }
        item { FluxoSectionHeading("Categorias", accent = FluxoDesignTokens.Blue) }
        items(Category.entries.filter { (totals[it] ?: BigInteger.ZERO) > BigInteger.ZERO || monthBudgets.any { budget -> budget.category == it } }, key = { it.name }) { category ->
            val spent = totals[category] ?: BigInteger.ZERO
            val budget = monthBudgets.firstOrNull { it.category == category }
            val share = if (total.signum() == 0) 0f else BigDecimal(spent).divide(BigDecimal(total), 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
            FluxoPanel(accent = if (category.ordinal % 2 == 0) FluxoDesignTokens.Mint else FluxoDesignTokens.Blue) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(category.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(planningMoney(spent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
                val percentage = if (total.signum() == 0) "0" else BigDecimal(spent).multiply(BigDecimal(100)).divide(BigDecimal(total), 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                Text("${counts[category] ?: 0} lançamento(s) · $percentage% das despesas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (budget != null) {
                    val limit = BigInteger.valueOf(budget.limitCents)
                    val remaining = limit - spent
                    Text("Limite: ${planningMoney(limit)}")
                    LinearProgressIndicator(progress = { BigDecimal(spent).divide(BigDecimal(limit), 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = FluxoDesignTokens.Blue)
                    Text(if (remaining.signum() < 0) "Excesso: ${planningMoney(remaining.abs())}" else "Restante: ${planningMoney(remaining)}", color = if (remaining.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { open(budget) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Editar limite de ${category.label}") }
                } else Text("Sem limite definido", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    benefitDialogMonth?.let { selected ->
        val benefitMonth = YearMonth.parse(selected)
        BenefitEditor(benefitMonth, benefits, saving, editError,
            dismiss = { benefitDialogMonth = null },
            save = { vr, vt, days -> saveBenefits(benefitMonth, vr, vt, days) { benefitDialogMonth = null } })
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


@Composable
internal fun BenefitSummary(month: YearMonth, settings: BenefitSettings, edit: () -> Unit) {
    val estimate = BenefitCalculator.estimate(month, settings)
    FluxoPanel(accent = FluxoDesignTokens.Mint, gradient = listOf(Color(0xFF004A42), Color(0xFF003247), Color(0xFF001020))) {
        FluxoSectionHeading("Benefícios estimados", accent = FluxoDesignTokens.Mint)
        Text("$month · ${estimate.automaticDays} dias úteis automáticos", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FluxoAdaptivePair(
            first = {
                BenefitValueCard("Vale-refeição", "VR", estimate.vrDays, settings.vrDailyCents, estimate.vrCents, FluxoDesignTokens.Mint)
            },
            second = {
                BenefitValueCard("Vale-transporte", "VT", estimate.vtDays, settings.vtDailyCents, estimate.vtCents, FluxoDesignTokens.Blue)
            }
        )
        Text("Total estimado: ${planningMoney(estimate.totalCents)}", modifier = Modifier.testTag("benefits.total"),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Feriados, ausências e escalas não são inferidos. Ajustes de dias valem só para este mês; tarifa diária vale para todos. Estimativa separada de renda, despesas, caixa e saldo.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = edit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("benefits.configure")) { Text("Configurar VR e VT") }
    }
}

@Composable
private fun BenefitValueCard(title: String, code: String, days: Int, dailyCents: Long, totalCents: BigInteger, accent: Color) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(code, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(planningMoney(totalCents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("$days dias · ${planningMoney(BigInteger.valueOf(dailyCents))}/dia",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LastWeekSpendChart(values: List<Pair<java.time.LocalDate, BigInteger>>) {
    val max = values.maxOfOrNull { it.second }?.takeIf { it.signum() > 0 } ?: BigInteger.ONE
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = values.joinToString("; ") { (date, cents) -> "${date.dayOfMonth}: ${planningMoney(cents)}" }
    Canvas(Modifier.fillMaxWidth().height(112.dp).semantics { contentDescription = "Gastos diários nos últimos sete dias do mês. $description" }) {
        val baseline = size.height - 2.dp.toPx()
        val chartHeight = size.height - 8.dp.toPx()
        val step = size.width / (values.size * 1f)
        val barWidth = step * .46f
        repeat(3) { line ->
            val y = chartHeight * (line + 1) / 4f
            drawLine(grid.copy(alpha = .45f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        values.forEachIndexed { index, (_, cents) ->
            val barHeight = BigDecimal(cents).divide(BigDecimal(max), 5, RoundingMode.HALF_UP).toFloat() * chartHeight
            if (barHeight > 0f) {
                drawRoundRect(
                    color = if (index == values.lastIndex) FluxoDesignTokens.Mint else FluxoDesignTokens.Blue,
                    topLeft = Offset(index * step + (step - barWidth) / 2f, baseline - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        values.forEach { (date, _) ->
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (values.all { it.second.signum() == 0 }) Text("Sem despesas reconhecidas neste período.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun BenefitEditor(
    month: YearMonth, settings: BenefitSettings, saving: Boolean, error: String?,
    dismiss: () -> Unit, save: (Long, Long, BenefitDays) -> Unit,
) {
    fun rateText(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
    var vr by rememberSaveable(month.toString()) { mutableStateOf(rateText(settings.vrDailyCents)) }
    var vt by rememberSaveable(month.toString()) { mutableStateOf(rateText(settings.vtDailyCents)) }
    var vrDays by rememberSaveable(month.toString()) { mutableStateOf(settings.overrides[month]?.vr?.toString() ?: "") }
    var vtDays by rememberSaveable(month.toString()) { mutableStateOf(settings.overrides[month]?.vt?.toString() ?: "") }
    val vrCents = BenefitCalculator.parseDailyCents(vr)
    val vtCents = BenefitCalculator.parseDailyCents(vt)
    val parsedVrDays = runCatching { BenefitCalculator.parseDays(vrDays, month) }
    val parsedVtDays = runCatching { BenefitCalculator.parseDays(vtDays, month) }
    val valid = vrCents != null && vtCents != null && parsedVrDays.isSuccess && parsedVtDays.isSuccess
    val preview = if (valid) BenefitCalculator.estimate(month, BenefitSettings(
        vrCents!!, vtCents!!, mapOf(month to BenefitDays(parsedVrDays.getOrNull(), parsedVtDays.getOrNull())))) else null
    AlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text("Configurar VR e VT") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mês dos dias ajustados: $month")
                Text("Tarifas diárias valem para todos os meses. Ajustes de dias valem somente para o mês indicado.")
                Text("Dias automáticos: ${BenefitCalculator.automaticDays(month)} (segunda a sexta). Feriados, ausências e escalas não são inferidos; ajuste os dias quando necessário.")
                BenefitField("VR diário em R$", "benefits.vrRate", vr, { vr = it }, saving,
                    vrCents == null, "Use 0 ou valor em R$, como 25,50. Máximo: 92.233.720.368.547.758,07.", KeyboardType.Decimal)
                BenefitField("VT diário em R$", "benefits.vtRate", vt, { vt = it }, saving,
                    vtCents == null, "Informe o total diário de transporte, incluindo ida e volta. Use vírgula para centavos.", KeyboardType.Decimal)
                BenefitField("Dias de VR em $month", "benefits.vrDays", vrDays, { vrDays = it }, saving,
                    parsedVrDays.isFailure, "De 0 a ${month.lengthOfMonth()}. Vazio restaura automático.", KeyboardType.Number)
                BenefitField("Dias de VT em $month", "benefits.vtDays", vtDays, { vtDays = it }, saving,
                    parsedVtDays.isFailure, "De 0 a ${month.lengthOfMonth()}. Vazio restaura automático.", KeyboardType.Number)
                if (preview != null) {
                    Text("VR: ${preview.vrDays} dias · ${planningMoney(preview.vrCents)}")
                    Text("VT: ${preview.vtDays} dias · ${planningMoney(preview.vtCents)}")
                    Text("Total estimado: ${planningMoney(preview.totalCents)}", modifier = Modifier.testTag("benefits.preview"))
                }
                Text("Estimativa de benefícios; não altera renda, despesas, caixa ou saldo.")
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && valid, modifier = Modifier.testTag("benefits.save"), onClick = {
                val vrValue = vrCents ?: return@TextButton
                val vtValue = vtCents ?: return@TextButton
                if (parsedVrDays.isSuccess && parsedVtDays.isSuccess)
                    save(vrValue, vtValue, BenefitDays(parsedVrDays.getOrNull(), parsedVtDays.getOrNull()))
            }) { Text(if (saving) "Salvando…" else "Salvar benefícios") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun BenefitField(
    label: String, tag: String, value: String, change: (String) -> Unit, saving: Boolean,
    invalid: Boolean, help: String, keyboard: KeyboardType,
) {
    OutlinedTextField(value = value, onValueChange = change,
        modifier = Modifier.fillMaxWidth().testTag(tag), label = { Text(label) },
        enabled = !saving, singleLine = true, isError = invalid,
        supportingText = { Text(if (invalid) "Valor inválido. $help" else help) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}
