package br.com.fluxo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.fluxo.core.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

internal fun planningMoney(cents: BigInteger): String = money(BigDecimal(cents, 2))

/** pt-BR number without currency symbol; [trimWhole] drops ",00" for whole reais (chart labels). */
private fun planningNumber(cents: BigInteger, trimWhole: Boolean = false): String {
    val whole = trimWhole && cents.mod(BigInteger.valueOf(100)).signum() == 0
    val format = NumberFormat.getNumberInstance(ptBr).apply {
        minimumFractionDigits = if (whole) 0 else 2
        maximumFractionDigits = if (whole) 0 else 2
    }
    return format.format(BigDecimal(cents, 2))
}

private fun planningMonthName(month: YearMonth): String =
    month.month.getDisplayName(java.time.format.TextStyle.FULL, ptBr).replaceFirstChar { it.uppercase(ptBr) }

/** "Outubro de 2026": the only form of a month shown to users. */
internal fun planningMonthLabel(month: YearMonth): String = "${planningMonthName(month)} de ${month.year}"

private fun planningMonthShort(month: YearMonth): String =
    month.month.getDisplayName(java.time.format.TextStyle.SHORT, ptBr).trimEnd('.').replaceFirstChar { it.uppercase(ptBr) }

/** Signed one-decimal percentage of the change, or null when the base is not positive. */
private fun planningVariation(total: BigInteger, previous: BigInteger): String? {
    if (previous.signum() <= 0) return null
    val difference = total - previous
    val value = BigDecimal(difference.abs()).multiply(BigDecimal(100))
        .divide(BigDecimal(previous), 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
    return when {
        difference.signum() > 0 -> "+$value%"
        difference.signum() < 0 -> "\u2212$value%"
        else -> "0,0%"
    }
}

@Composable
internal fun PlanningScreen(
    transactions: List<Transaction>, budgets: List<Budget>, month: YearMonth,
    saving: Boolean, editError: String?, resetError: () -> Unit,
    save: (Budget, () -> Unit) -> Unit,
    delete: (String, Category, () -> Unit) -> Unit,
    benefits: BenefitSettings,
    saveBenefits: (YearMonth, Long, Long, BenefitDays, Set<DayOfWeek>, Set<DayOfWeek>, () -> Unit) -> Unit,
    previousMonth: (() -> Unit)? = null,
    nextMonth: (() -> Unit)? = null,
    salaryMonthlyCents: Long = 0
) {
    val totals = remember(transactions, month) { SpendingAnalytics.byCategory(transactions, month) }
    val monthly = remember(transactions, month) { SpendingAnalytics.monthlyExpenses(transactions, month, 6) }
    val total = totals.values.fold(BigInteger.ZERO, BigInteger::add)
    val previousTotal = monthly.getOrNull(monthly.size - 2)?.totalCents ?: BigInteger.ZERO
    val monthBudgets = budgets.filter { it.month == month.toString() }.sortedBy { it.category.ordinal }
    val counts = remember(transactions, month) {
        transactions.filter {
            it.status == ReviewStatus.CONFIRMED && it.transferId == null && it.direction == Direction.EXPENSE && (it.amountCents ?: 0) > 0 &&
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
    var benefitDialogMonth by rememberSaveable { mutableStateOf<String?>(null) }
    val detailCategories = Category.entries.filter { (totals[it] ?: BigInteger.ZERO) > BigInteger.ZERO || monthBudgets.any { budget -> budget.category == it } }
    LazyColumn(
        modifier = Modifier.background(FluxoColors.Paper),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "header") {
            if (previousMonth != null && nextMonth != null) FluxoMonthBar(month, previousMonth, nextMonth)
        }
        item(key = "hero") { PlanningHero(total, previousTotal, month) }
        if (salaryMonthlyCents > 0) item(key = "income") { ExpectedIncomeBlock(month, salaryMonthlyCents, benefits, total) }
        item(key = "monthly") { MonthlyExpensesTile(monthly) }
        item(key = "budgets-heading") { PlanningSectionTitle("Orçamentos") { BudgetMarkerLegend() } }
        if (monthBudgets.isEmpty()) item(key = "budgets-empty") {
            Text("Nenhum limite definido para este mês.", style = MaterialTheme.typography.bodyMedium, color = FluxoColors.Mute,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp))
        }
        items(monthBudgets, key = { "budget-" + it.category.name }) { budget ->
            BudgetRow(budget, totals[budget.category] ?: BigInteger.ZERO) { open(budget) }
        }
        item(key = "define") { FluxoPillButton("Definir limite mensal", onClick = { open(null) }, modifier = Modifier.fillMaxWidth()) }
        item(key = "benefits") {
            BenefitSummary(month, benefits) {
                resetError()
                benefitDialogMonth = month.toString()
            }
        }
        if (totals.isEmpty() && monthBudgets.isEmpty()) item(key = "empty") { FluxoEmptyState("Sem despesas ou limites neste mês.", "Defina um limite ou categorize seus lançamentos.") }
        if (detailCategories.isNotEmpty()) item(key = "details-heading") { PlanningSectionTitle("Categorias") { Text("detalhe do mês", style = FluxoType.Caption.copy(fontSize = 11.5.sp), color = FluxoColors.Mute) } }
        items(detailCategories, key = { "detail-" + it.name }) { category ->
            CategoryDetail(
                category, totals[category] ?: BigInteger.ZERO, total, counts[category] ?: 0,
                monthBudgets.firstOrNull { it.category == category }
            ) { open(it) }
        }
        item(key = "disclaimer") {
            Text("${planningMonthLabel(month)} · inclui compras no crédito; itens em revisão e transferências próprias ficam fora. Dados reconhecidos podem ser parciais; valores não representam saldo nem fatura.",
                style = FluxoType.Caption, color = FluxoColors.Mute, modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp))
        }
    }
    benefitDialogMonth?.let { selected ->
        val benefitMonth = YearMonth.parse(selected)
        BenefitEditor(benefitMonth, benefits, saving, editError,
            dismiss = { benefitDialogMonth = null },
            save = { vr, vt, days, vrWeekdays, vtWeekdays -> saveBenefits(benefitMonth, vr, vt, days, vrWeekdays, vtWeekdays) { benefitDialogMonth = null } })
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
                Text("Mês: ${planningMonthLabel(YearMonth.parse(targetMonth))}")
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
        if (deleting) AlertDialog(onDismissRequest = { if (!saving) deleting = false }, title = { Text("Remover este limite?") }, text = { Text(editError ?: "Somente o limite de ${dialogCategory.label} em ${planningMonthLabel(YearMonth.parse(targetMonth))} será removido. Os lançamentos serão mantidos.") }, confirmButton = { TextButton(enabled = !saving, onClick = { delete(targetMonth, dialogCategory) { deleting = false; dialogMonth = null } }) { Text(if (saving) "Removendo…" else "Remover") } }, dismissButton = { TextButton(enabled = !saving, onClick = { deleting = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun PlanningSectionTitle(title: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        FluxoSectionHeading(title, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun BudgetMarkerLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(2.dp).height(12.dp).clip(RoundedCornerShape(1.dp)).background(FluxoColors.Ink))
        Text("100% do limite", style = FluxoType.Caption.copy(fontSize = 11.5.sp), color = FluxoColors.Mute)
    }
}

@Composable
private fun PlanningHero(total: BigInteger, previousTotal: BigInteger, month: YearMonth) {
    val previousMonth = month.minusMonths(1)
    val variation = planningVariation(total, previousTotal)
    FluxoHero(label = "Gastos do mês", value = planningNumber(total)) {
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${planningMonthName(previousMonth)} ${planningMoney(previousTotal)}", color = FluxoColors.OnHero,
                style = FluxoType.Caption.copy(fontSize = 12.5.sp, fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f, fill = false))
            if (variation != null) {
                Text(variation, color = FluxoColors.Ink, maxLines = 1,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(FluxoColors.Soft).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}

/**
 * "Renda prevista do mês": salary + VR + VT estimate and the share of the salary already spent. A planning aid
 * only; it never feeds income, expenses, cash or the observed result.
 */
@Composable
private fun ExpectedIncomeBlock(month: YearMonth, salaryCents: Long, benefits: BenefitSettings, spent: BigInteger) {
    val estimate = BenefitCalculator.estimate(month, benefits)
    val salary = BigInteger.valueOf(salaryCents)
    val expected = salary + estimate.totalCents
    val percent = SalaryPlanner.percentSpent(salaryCents, spent) ?: 0
    Column(
        Modifier.fillMaxWidth().testTag("planning.income").clip(RoundedCornerShape(16.dp)).background(FluxoColors.Pale)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Renda prevista do mês", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() })
        Text("Salário ${planningMoney(salary)} + VR ${planningMoney(estimate.vrCents)} + VT ${planningMoney(estimate.vtCents)}",
            style = FluxoType.Caption.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"), color = FluxoColors.Ink)
        Text("= ${planningMoney(expected)}", style = FluxoType.number(18.sp, FontWeight.ExtraBold), color = FluxoColors.Ink)
        Text("Gastos do mês: $percent% do salário", color = if (percent > 100) FluxoColors.Expense else FluxoColors.Ink,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 4.dp).testTag("planning.salaryShare"))
        BudgetBar(
            spent = spent, limit = salary,
            contentDescription = "Gastos do mês: $percent% do salário",
            modifier = Modifier.fillMaxWidth().height(10.dp), thickness = 6.dp
        )
        Text("Estimativa para planejamento: não altera renda, gastos, caixa nem o resultado observado.",
            style = FluxoType.Caption.copy(fontSize = 11.5.sp, lineHeight = 15.sp), color = FluxoColors.Mute)
    }
}

@Composable
private fun MonthlyExpensesTile(monthly: List<MonthlyExpense>) {
    val last = monthly.lastIndex
    val description = "Despesas por mês em reais: " + monthly.joinToString(", ") {
        "${it.month.month.getDisplayName(java.time.format.TextStyle.FULL, ptBr)} ${planningNumber(it.totalCents, trimWhole = true)}"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FluxoColors.Soft).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Despesas por mês", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text("últimos 6 meses", style = FluxoType.Caption, color = FluxoColors.Mute)
        }
        Spacer(Modifier.height(2.dp))
        BarChart(
            entries = monthly.map {
                BarEntry(it.totalCents, planningMonthShort(it.month), if (it.totalCents.signum() > 0) planningNumber(it.totalCents, trimWhole = true) else null)
            },
            contentDescription = description,
            modifier = Modifier.fillMaxWidth().height(116.dp),
            highlightedIndex = last.takeIf { it >= 0 },
            showValueLabels = true,
            baselineColor = FluxoColors.PetrolLight
        )
    }
}

@Composable
private fun BudgetRow(budget: Budget, spent: BigInteger, edit: () -> Unit) {
    val limit = BigInteger.valueOf(budget.limitCents)
    val over = spent - limit
    val percent = if (limit.signum() > 0) spent.multiply(BigInteger.valueOf(100)).divide(limit) else BigInteger.ZERO
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val pill: @Composable () -> Unit = {
        Text("estourou ${planningNumber(over)}", color = FluxoColors.OnExpense, maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(FluxoColors.Expense).padding(horizontal = 9.dp, vertical = 1.dp))
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = "Editar limite de ${budget.category.label}", role = Role.Button, onClick = edit)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluxoDot(fluxoCategoryColor(budget.category))
                Text(budget.category.label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (over.signum() > 0 && !stacked) pill()
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = FluxoColors.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)) { append(planningNumber(spent)) }
                    append(" de ${planningNumber(limit)}")
                },
                color = FluxoColors.Mute, style = FluxoType.number(13.sp, FontWeight.SemiBold), maxLines = 1
            )
        }
        if (over.signum() > 0 && stacked) Box(Modifier.padding(top = 2.dp)) { pill() }
        BudgetBar(
            spent = spent, limit = limit,
            contentDescription = "${budget.category.label} a $percent% do orçamento",
            modifier = Modifier.fillMaxWidth().height(14.dp).padding(top = 2.dp)
        )
    }
}

@Composable
private fun CategoryDetail(category: Category, spent: BigInteger, total: BigInteger, count: Int, budget: Budget?, edit: (Budget) -> Unit) {
    FluxoTile(Modifier.fillMaxWidth(), role = FluxoTileRole.Outlined) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluxoDot(fluxoCategoryColor(category))
                Text(category.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
            Text(planningMoney(spent), style = FluxoType.RowNumber)
        }
        val percentage = if (total.signum() == 0) "0" else BigDecimal(spent).multiply(BigDecimal(100)).divide(BigDecimal(total), 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
        SplitBar(
            first = spent, second = (total - spent).max(BigInteger.ZERO),
            contentDescription = "${category.label}: $percentage% das despesas",
            modifier = Modifier.fillMaxWidth().height(8.dp).padding(vertical = 0.dp),
            firstColor = fluxoCategoryColor(category), secondColor = FluxoColors.Soft
        )
        Text("$count lançamento(s) · $percentage% das despesas", style = MaterialTheme.typography.bodySmall, color = FluxoColors.Mute)
        if (budget != null) {
            val limit = BigInteger.valueOf(budget.limitCents)
            val remaining = limit - spent
            Text("Limite: ${planningMoney(limit)}", style = MaterialTheme.typography.bodySmall)
            Text(if (remaining.signum() < 0) "Excesso: ${planningMoney(remaining.abs())}" else "Restante: ${planningMoney(remaining)}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (remaining.signum() < 0) FluxoColors.Expense else FluxoColors.Ink)
            TextButton(onClick = { edit(budget) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Editar limite de ${category.label}", color = FluxoColors.Petrol) }
        } else Text("Sem limite definido", style = MaterialTheme.typography.bodySmall, color = FluxoColors.Mute)
    }
}

@Composable
internal fun BenefitSummary(month: YearMonth, settings: BenefitSettings, edit: () -> Unit) {
    val estimate = BenefitCalculator.estimate(month, settings)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PlanningSectionTitle("Benefícios") { Text("estimativa", style = FluxoType.Caption.copy(fontSize = 11.5.sp), color = FluxoColors.Mute) }
        BenefitRow("VR", "Vale-refeição", estimate.vrDays, settings.vrDailyCents, estimate.vrCents, FluxoColors.Petrol)
        BenefitRow("VT", "Vale-transporte", estimate.vtDays, settings.vtDailyCents, estimate.vtCents, FluxoColors.PetrolMid)
        Text("Dias automáticos em ${planningMonthLabel(month)}: VR: ${daysCountLabel(estimate.vrAutomaticDays)} (${weekdaysLabel(settings.vrWeekdays)}) · VT: ${daysCountLabel(estimate.vtAutomaticDays)} (${weekdaysLabel(settings.vtWeekdays)})", style = FluxoType.Caption, color = FluxoColors.Mute,
            modifier = Modifier.padding(horizontal = 2.dp))
        Text("Total estimado: ${planningMoney(estimate.totalCents)}", modifier = Modifier.padding(horizontal = 2.dp).testTag("benefits.total"),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text("Feriados, ausências e escalas não são inferidos. Ajustes de dias valem só para este mês; tarifa diária vale para todos. Estimativa separada de renda, despesas, caixa e saldo.",
            style = FluxoType.Caption, color = FluxoColors.Mute, modifier = Modifier.padding(horizontal = 2.dp))
        FluxoPillButton("Configurar VR e VT", onClick = edit, style = FluxoButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().testTag("benefits.configure"))
    }
}

@Composable
private fun BenefitRow(code: String, title: String, days: Int, dailyCents: Long, totalCents: BigInteger, badge: Color) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(FluxoColors.Soft).padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(badge), contentAlignment = Alignment.Center) {
            Text(code, color = FluxoColors.OnPetrol, style = FluxoType.number(14.sp, FontWeight.ExtraBold))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold))
            Text("$days dias \u00d7 ${planningMoney(BigInteger.valueOf(dailyCents))}", style = FluxoType.Caption.copy(fontSize = 12.sp), color = FluxoColors.Mute)
        }
        Text(planningMoney(totalCents), style = FluxoType.RowNumber, maxLines = 1)
    }
}
@Composable
internal fun BenefitEditor(
    month: YearMonth, settings: BenefitSettings, saving: Boolean, error: String?,
    dismiss: () -> Unit, save: (Long, Long, BenefitDays, Set<DayOfWeek>, Set<DayOfWeek>) -> Unit,
) {
    fun rateText(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
    var vr by rememberSaveable(month.toString()) { mutableStateOf(rateText(settings.vrDailyCents)) }
    var vt by rememberSaveable(month.toString()) { mutableStateOf(rateText(settings.vtDailyCents)) }
    var vrDays by rememberSaveable(month.toString()) { mutableStateOf(settings.overrides[month]?.vr?.toString() ?: "") }
    var vtDays by rememberSaveable(month.toString()) { mutableStateOf(settings.overrides[month]?.vt?.toString() ?: "") }
    var vrMask by rememberSaveable(month.toString()) { mutableStateOf(weekdayMask(settings.vrWeekdays)) }
    var vtMask by rememberSaveable(month.toString()) { mutableStateOf(weekdayMask(settings.vtWeekdays)) }
    val vrWeekdays = weekdaysOfMask(vrMask)
    val vtWeekdays = weekdaysOfMask(vtMask)
    val vrCents = BenefitCalculator.parseDailyCents(vr)
    val vtCents = BenefitCalculator.parseDailyCents(vt)
    val parsedVrDays = runCatching { BenefitCalculator.parseDays(vrDays, month) }
    val parsedVtDays = runCatching { BenefitCalculator.parseDays(vtDays, month) }
    val valid = vrCents != null && vtCents != null && parsedVrDays.isSuccess && parsedVtDays.isSuccess
    val preview = if (valid) BenefitCalculator.estimate(month, BenefitSettings(
        vrCents!!, vtCents!!, mapOf(month to BenefitDays(parsedVrDays.getOrNull(), parsedVtDays.getOrNull())),
        vrWeekdays, vtWeekdays)) else null
    AlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text("Configurar VR e VT") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mês dos dias ajustados: ${planningMonthLabel(month)}")
                Text("Tarifas diárias valem para todos os meses. Ajustes de dias valem somente para o mês indicado.")
                Text("Dias automáticos: VR: ${daysCountLabel(BenefitCalculator.scheduledDays(month, vrWeekdays))} (${weekdaysLabel(vrWeekdays)}) · VT: ${daysCountLabel(BenefitCalculator.scheduledDays(month, vtWeekdays))} (${weekdaysLabel(vtWeekdays)}). Feriados, ausências e escalas não são inferidos; ajuste os dias quando necessário.")
                Text("Dias de depósito do VR", style = MaterialTheme.typography.labelLarge)
                WeekdayChips(vrWeekdays, { vrMask = weekdayMask(it) }, "benefits.vrDay", enabled = !saving)
                Text("Dias de depósito do VT", style = MaterialTheme.typography.labelLarge)
                WeekdayChips(vtWeekdays, { vtMask = weekdayMask(it) }, "benefits.vtDay", enabled = !saving)
                BenefitField("VR diário em R$", "benefits.vrRate", vr, { vr = it }, saving,
                    vrCents == null, "Use 0 ou valor em R$, como 25,50. Máximo: 92.233.720.368.547.758,07.", KeyboardType.Decimal)
                BenefitField("VT diário em R$", "benefits.vtRate", vt, { vt = it }, saving,
                    vtCents == null, "Informe o total diário de transporte, incluindo ida e volta. Use vírgula para centavos.", KeyboardType.Decimal)
                BenefitField("Dias de VR em ${planningMonthLabel(month)}", "benefits.vrDays", vrDays, { vrDays = it }, saving,
                    parsedVrDays.isFailure, "De 0 a ${month.lengthOfMonth()}. Vazio restaura automático.", KeyboardType.Number)
                BenefitField("Dias de VT em ${planningMonthLabel(month)}", "benefits.vtDays", vtDays, { vtDays = it }, saving,
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
                    save(vrValue, vtValue, BenefitDays(parsedVrDays.getOrNull(), parsedVtDays.getOrNull()), vrWeekdays, vtWeekdays)
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
