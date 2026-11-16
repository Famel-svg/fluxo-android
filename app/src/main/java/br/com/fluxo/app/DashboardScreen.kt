package br.com.fluxo.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.fluxo.core.BenefitBalance
import br.com.fluxo.core.BenefitCalculator
import br.com.fluxo.core.BenefitSettings
import br.com.fluxo.core.Budget
import br.com.fluxo.core.BudgetPace
import br.com.fluxo.core.Category
import br.com.fluxo.core.PaceStatus
import br.com.fluxo.core.RecurringDetector
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.SpendingAnalytics
import br.com.fluxo.core.Transaction
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle

private val HIDDEN = "••••"

private fun cents(value: BigInteger): String = money(BigDecimal(value, 2))

/** Number part of a money string without the currency symbol, e.g. "1.284,60" or "-1.284,60". */
private fun plainMoney(value: BigInteger): String =
    money(BigDecimal(value.abs(), 2)).replace("R$", "").trim().let { if (value.signum() < 0) "−$it" else it }

private fun percent(part: BigInteger, total: BigInteger): Int =
    if (total.signum() <= 0 || part.signum() <= 0) 0
    else (part.multiply(BigInteger.valueOf(100)) + total.shiftRight(1)).divide(total).toInt()

@Composable
internal fun Dashboard(
    rows: List<Transaction>, month: YearMonth, active: Boolean,
    previous: () -> Unit, next: () -> Unit, settings: () -> Unit,
    showAll: () -> Unit, review: () -> Unit, add: () -> Unit,
    edit: (Transaction) -> Unit,
    allRows: List<Transaction> = rows,
    greetingName: String = "",
    benefits: BenefitSettings = BenefitSettings(),
    budgets: List<Budget> = emptyList(),
    salaryMonthlyCents: Long = 0,
    cardClosingDay: Int? = null,
    cardDueDay: Int? = null,
    homeCards: List<HomeCard> = HomeCard.entries,
    hiddenHomeCards: Set<HomeCard> = emptySet(),
    openCategory: (Category) -> Unit = {},
    setCardCycle: (Int?, Int?) -> Unit = { _, _ -> },
    setHomeCards: (List<HomeCard>, Set<HomeCard>) -> Unit = { _, _ -> }
) {
    // Baseline accounting: only confirmed, non-transfer records enter totals; credit stays apart from cash.
    // Voucher (VR, and legacy VT) rows stay out of every spending and income figure here: they are read in the VR block.
    val confirmed = rows.filter { it.status == ReviewStatus.CONFIRMED && it.transferId == null && !it.method.isVoucher }
    fun total(predicate: (Transaction) -> Boolean): BigInteger = confirmed.filter(predicate)
        .fold(BigInteger.ZERO) { sum, row -> sum + BigInteger.valueOf(row.amountCents ?: 0) }
    val income = total { it.direction == Direction.INCOME }
    val cash = total { it.direction == Direction.EXPENSE && it.method.affectsCash }
    val credit = total { it.direction == Direction.EXPENSE && it.method == PaymentMethod.CREDIT_CARD }
    val pending = rows.count { it.status == ReviewStatus.REVIEW }
    val pendingCredit = rows.count { it.status == ReviewStatus.REVIEW && it.method == PaymentMethod.CREDIT_CARD }
    var hidden by rememberSaveable { mutableStateOf(false) }
    fun amount(value: BigInteger) = if (hidden) HIDDEN else cents(value)

    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today) == month
    // The 7-day series uses every transaction so the first days of a month still see the previous month.
    val daily = SpendingAnalytics.recentDaysForMonth(allRows.filter { !it.method.isVoucher }, month, today)
    val dailyValues = daily.map { it.totalCents }
    val weekTotal = dailyValues.fold(BigInteger.ZERO, BigInteger::add)
    val byCategory = SpendingAnalytics.byCategory(rows.filter { !it.method.isVoucher }, month).entries.sortedByDescending { it.value }
    val monthExpenses = byCategory.fold(BigInteger.ZERO) { sum, e -> sum + e.value }
    val share = SpendingAnalytics.captureShare(rows, month)
    // Voucher balances are estimates for the selected month; spending is read from the same month slice.
    val benefitEstimate = BenefitCalculator.estimate(month, benefits)
    val zone = ZoneId.systemDefault()
    val vrUsage = if (benefits.vrDailyCents > 0) BenefitBalance.remaining(benefitEstimate.vrCents, rows, PaymentMethod.MEAL_VOUCHER, month, zone) else null
    val fontScale = LocalDensity.current.fontScale
    var chartsOpen by rememberSaveable { mutableStateOf(fontScale < 1.3f) }
    var customizing by rememberSaveable { mutableStateOf(false) }
    var cycleDialog by rememberSaveable { mutableStateOf(false) }
    val monthBudgets = budgets.filter { it.month == month.toString() && it.limitCents > 0 }
    val budgetSum = monthBudgets.fold(0L) { sum, b -> Math.addExact(sum, b.limitCents) }
    val paceBudget = if (budgetSum > 0) budgetSum else salaryMonthlyCents.coerceAtLeast(0)
    val fixedUpcoming = if (currentMonth) {
        RecurringDetector.detect(allRows, today, zone)
            .filter { !it.paidThisMonth && YearMonth.from(it.nextDue) == month }
            .fold(0L) { sum, c -> Math.addExact(sum, c.amountCents) }
    } else 0L
    val spentLong = (cash + credit).min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    val pace = BudgetPace.monthPace(spentLong, paceBudget, month, today, fixedUpcoming)
    val overruns = BudgetPace.overruns(rows, monthBudgets, month, zone)
    // The open cycle is read from all rows: the visible month may end before today's cycle starts.
    val openCredit = cardClosingDay?.let { closing ->
        val start = openCycleStart(today, closing)
        allRows.filter { it.status == ReviewStatus.CONFIRMED && it.transferId == null && !it.method.isVoucher && it.direction == Direction.EXPENSE && it.method == PaymentMethod.CREDIT_CARD }
            .filter { r -> Instant.ofEpochMilli(r.occurredAt).atZone(zone).toLocalDate().let { d -> !d.isBefore(start) && !d.isAfter(today) } }
            .fold(BigInteger.ZERO) { sum, row -> sum + BigInteger.valueOf(row.amountCents ?: 0) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard.neon"),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (greetingName.isNotBlank()) item {
            Text("Olá, $greetingName", color = FluxoColors.Mute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                modifier = Modifier.entrance(0).padding(horizontal = 4.dp).testTag("dashboard.greeting"))
        }
        item {
            FluxoMonthBar(month, previous, next, Modifier.entrance(1)) {
                IconButton(onClick = { customizing = true }, modifier = Modifier.size(40.dp, 44.dp).testTag("dashboard.customize")) {
                    Icon(Icons.Default.Tune, "Personalizar início", modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { hidden = !hidden }, modifier = Modifier.size(40.dp, 44.dp).testTag("dashboard.visibility")) {
                    Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) "Mostrar valores do resumo" else "Ocultar valores do resumo", modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = add, modifier = Modifier.size(40.dp).clip(CircleShape).background(FluxoColors.Petrol)) {
                    Icon(Icons.Default.Add, "Adicionar lançamento manual", tint = FluxoColors.OnPetrol, modifier = Modifier.size(22.dp))
                }
            }
        }
        item {
            val spent = cash + credit
            FluxoHero(
                label = "Entrou x saiu no mês",
                value = if (hidden) HIDDEN else plainMoney(income - spent),
                currency = if (hidden) null else "R$",
                valueCents = if (hidden) null else (income - spent).toLong(),
                note = "Sem VR · não é saldo bancário",
                modifier = Modifier.entrance(2).testTag("dashboard.inout")
            ) {
                val peak = maxOf(income, spent)
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeroBar("Entradas", if (hidden) HIDDEN else cents(income), if (hidden) 0f else chartFraction(income, peak), 0.95f)
                    HeroBar("Saídas", if (hidden) HIDDEN else cents(spent), if (hidden) 0f else chartFraction(spent, peak), 0.55f)
                }
            }
        }
        val shownCards = homeCards.filter { it !in hiddenHomeCards }
        shownCards.forEachIndexed { index, card ->
            val order = 4 + index
            when (card) {
                HomeCard.PACE -> item(key = "home.PACE") {
                    PaceCard(pace, paceBudget, budgetSum <= 0, overruns, hidden, openCategory, Modifier.entrance(order))
                }
                HomeCard.CAPTURE -> item(key = "home.CAPTURE") {
            val auto = share.automatic
            val detail = when {
                !active -> "Escolha bancos ou permita outros apps, conceda acesso e ative a captura."
                auto == 0 -> "Nenhum lançamento automático este mês"
                auto == 1 -> "1 lançamento automático este mês"
                else -> "$auto lançamentos automáticos este mês"
            }
            Column(Modifier.entrance(order)) {
                FluxoCaptureStrip(
                    title = if (active) "Captura ativa" else "Configure a captura",
                    detail = detail,
                    live = active,
                    modifier = if (active) Modifier else Modifier.clickable(role = Role.Button, onClick = settings),
                    action = if (pending > 0) {
                        { FluxoPillButton("$pending para revisar", review, style = FluxoButtonStyle.Review) }
                    } else null
                )
                if (!active) Text(
                    "Processamento local · texto bruto não armazenado", style = FluxoType.Caption,
                    color = FluxoColors.Mute, modifier = Modifier.padding(start = 6.dp, top = 4.dp)
                )
            }
                }
                HomeCard.CREDIT -> item(key = "home.CREDIT") {
                    CreditCard(amount(credit), openCredit?.let { amount(it) }, amount(cash), cardClosingDay, cardDueDay, { cycleDialog = true }, Modifier.entrance(order))
                }
                HomeCard.VR -> if (vrUsage != null) item(key = "home.VR") {
            Column(Modifier.entrance(order), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BenefitBalanceBlock(vrUsage, benefitEstimate.vrDays, benefits.vrDailyCents, hidden)
            }
                }
                HomeCard.RECENT -> item(key = "home.RECENT") {
            Column(Modifier.entrance(order)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    FluxoSectionHeading("Últimos lançamentos", Modifier.weight(1f))
                    Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = showAll).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterEnd) {
                        Text("Todos", style = FluxoType.Caption.copy(fontSize = 12.sp), color = FluxoColors.Mute)
                    }
                }
                if (rows.isEmpty()) FluxoEmptyState("Seu mês começa aqui", "Adicione um lançamento ou configure a captura. Nenhum histórico anterior será importado.")
                else if (hidden) Text("Lançamentos ocultos neste resumo.", style = MaterialTheme.typography.bodyMedium, color = FluxoColors.Mute)
                else rows.take(3).forEach { key(it.id) { TransactionRow(it, today, edit) } }
            }
                }
                HomeCard.CHARTS -> item(key = "home.CHARTS") {
                    Column(Modifier.entrance(order), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { chartsOpen = !chartsOpen }.padding(horizontal = 2.dp).testTag("dashboard.charts"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FluxoSectionHeading("Gráficos", Modifier.weight(1f))
                            Icon(if (chartsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (chartsOpen) "Recolher gráficos" else "Mostrar gráficos")
                        }
                        if (chartsOpen) {
            val topCategory = byCategory.firstOrNull()
            val shown = if (byCategory.size > 6) byCategory.take(5) else byCategory
            val rest = if (byCategory.size > 6) byCategory.drop(5).fold(BigInteger.ZERO) { s, e -> s + e.value } else BigInteger.ZERO
            val legend = shown.map { Triple(it.key.label, it.value, fluxoCategoryColor(it.key)) } +
                (if (rest.signum() > 0) listOf(Triple("Demais", rest, FluxoColors.Gray)) else emptyList())
            val donutText = if (legend.isEmpty()) "Sem gastos confirmados neste mês" else
                "Despesas por categoria: " + legend.joinToString(", ") { "${it.first} ${percent(it.second, monthExpenses)}%" + if (hidden) "" else " (${cents(it.second)})" }
            val peak = dailyValues.withIndex().maxByOrNull { it.value }?.takeIf { it.value.signum() > 0 }
            val lastDay = daily.last()
            val dayLabels = daily.map { it.day.dayOfWeek.getDisplayName(DayTextStyle.SHORT, ptBr).trimEnd('.').replaceFirstChar { c -> c.titlecase(ptBr) } }
            val barText = "Gastos dos últimos 7 dias; " + (if (currentMonth) "hoje " else "último dia ") +
                (if (hidden) HIDDEN else cents(lastDay.totalCents)) +
                (if (!hidden && peak != null) ", pico ${cents(peak.value)} em ${dayLabels[peak.index]}" else "")
            val autoPct = percent(BigInteger.valueOf(share.automatic.toLong()), BigInteger.valueOf(share.total.toLong()))
            val splitText = "${share.automatic} lançamentos automáticos e ${share.manual} manuais" +
                (if (share.total > 0) ", $autoPct% automático" else "")

            val donutTile: @Composable () -> Unit = {
                FluxoTile(Modifier.fillMaxWidth()) {
                    Text("Despesas do mês", style = TileTitle)
                    Text("inclui crédito", style = FluxoType.Caption.copy(fontSize = 12.sp), color = FluxoColors.Mute)
                    Text(amount(monthExpenses), style = FluxoType.number(16.sp))
                    DonutChart(
                        slices = legend.map { DonutSlice(it.second, it.third) },
                        contentDescription = donutText + (topCategory?.let { "; maior: ${it.key.label}" } ?: ""),
                        modifier = Modifier.padding(vertical = 4.dp).size(80.dp).align(Alignment.CenterHorizontally),
                        strokeWidth = 12.dp,
                        onSliceClick = { i -> shown.getOrNull(i)?.let { openCategory(it.key) } }
                    ) {
                        if (topCategory != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${percent(topCategory.value, monthExpenses)}%", style = FluxoType.number(14.sp), color = FluxoColors.Ink)
                            Text(
                                donutShortLabel(topCategory.key),
                                style = FluxoType.Caption.copy(fontSize = 12.sp, lineHeight = 14.sp), color = FluxoColors.Mute, maxLines = 1
                            )
                        }
                    }
                    if (legend.isEmpty()) Text("Sem gastos confirmados neste mês.", style = FluxoType.Caption, color = FluxoColors.Mute)
                    legend.forEach { (name, value, color) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 13.5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
                            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = LegendStyle)
                            Text(
                                (if (hidden) "" else "${compactMoney(value.toLong())} · ") + "${percent(value, monthExpenses)}%",
                                maxLines = 1, style = LegendStyle.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
            }
            val barTile: @Composable (Dp) -> Unit = { chartHeight ->
                FluxoTile(Modifier.fillMaxWidth(), role = FluxoTileRole.Outlined) {
                    Text("Gastos por dia", style = TileTitle)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(amount(lastDay.totalCents), style = FluxoType.number(16.sp), maxLines = 1)
                        Text(if (currentMonth) "hoje" else lastDay.day.format(DateTimeFormatter.ofPattern("dd/MM")), style = FluxoType.Caption.copy(fontSize = 12.sp), color = FluxoColors.Mute)
                    }
                    BarChart(
                        entries = daily.mapIndexed { i, d ->
                            BarEntry(d.totalCents, dayLabels[i], if (!hidden && d.totalCents.signum() > 0) compactMoney(d.totalCents.toLong()) else null)
                        },
                        contentDescription = barText,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(chartHeight),
                        highlightedIndex = if (currentMonth) daily.lastIndex else null,
                        showValueLabels = !hidden,
                        labelAll = true
                    )
                }
            }
            val splitTile: @Composable () -> Unit = {
                FluxoTile(Modifier.fillMaxWidth(), role = FluxoTileRole.Accent) {
                    Text("Automático vs manual", style = TileTitle)
                    SplitBar(
                        share.automatic.toLong(), share.manual.toLong(), splitText,
                        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 5.dp).height(12.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        Text(if (share.total > 0) "$autoPct%" else "—", style = FluxoType.number(13.sp))
                        Text(
                            if (share.total > 0) "${share.automatic} auto · ${share.manual} manual" else "Sem lançamentos",
                            style = FluxoType.Caption.copy(fontSize = 12.sp), color = FluxoColors.Ink
                        )
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Wider phones give extra width to the donut tile and a little more height to the bars (84dp at 320dp).
                val available = maxWidth
                val chartHeight = (84.dp + (available - 320.dp).coerceAtLeast(0.dp) * 0.25f).coerceAtMost(120.dp)
                if (maxWidth < 300.dp || fontScale >= 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { donutTile(); barTile(84.dp); splitTile() }
                } else {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width((available * 0.34f).coerceIn(148.dp, 190.dp)).fillMaxHeight()) { donutTile() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { barTile(chartHeight); splitTile() }
                    }
                }
            }
                        }
                    }
                }
            }
        }
        if (pendingCredit > 0) item {
            Text("$pendingCredit lançamento(s) de crédito pendente(s) de revisão. Fatura, limite e vencimento não estão disponíveis; confira no emissor do cartão.", style = FluxoType.Caption, color = FluxoColors.Mute, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
    if (customizing) CustomizeDialog(homeCards, hiddenHomeCards, setHomeCards) { customizing = false }
    if (cycleDialog) CardCycleDialog(cardClosingDay, cardDueDay, setCardCycle) { cycleDialog = false }
}

/** Thin proportional bar on the hero: OnHero tones keep it legible on the petrol fill in both themes. */
@Composable
private fun HeroBar(label: String, value: String, fraction: Float, fillAlpha: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(60.dp), maxLines = 1, color = FluxoColors.OnHero, style = LegendStyle.copy(fontSize = 12.sp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(FluxoColors.OnHero.copy(alpha = 0.18f))) {
            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(FluxoColors.OnHero.copy(alpha = fillAlpha)))
        }
        Text(value, maxLines = 1, color = FluxoColors.OnHero, style = FluxoType.number(13.sp))
    }
}

/** Fade + 12dp slide-up, delayed 40ms per index, played once per item (state survives scrolling off-screen). */
private fun Modifier.entrance(index: Int): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    val offset = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(40L * index.coerceAtMost(8))
            progress.animateTo(1f, tween(240))
            played = true
        }
    }
    graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * offset }
}

/** Short category label that fits the 80dp donut centre without clipping. */
private fun donutShortLabel(category: br.com.fluxo.core.Category): String = when (category) {
    br.com.fluxo.core.Category.FOOD -> "Alim."
    br.com.fluxo.core.Category.TRANSPORT -> "Transp."
    br.com.fluxo.core.Category.SERVICES -> "Serv."
    br.com.fluxo.core.Category.EDUCATION -> "Educ."
    br.com.fluxo.core.Category.UNCATEGORIZED -> "Sem cat."
    else -> category.label.let { if (it.length > 8) it.take(7) + "." else it }
}

private val TileTitle: TextStyle get() = TextStyle(fontFamily = FluxoType.Body, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = FluxoColors.Ink)
private val LegendStyle: TextStyle get() = TextStyle(fontFamily = FluxoType.Body, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal, color = FluxoColors.Ink)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionRow(row: Transaction, today: LocalDate, edit: (Transaction) -> Unit) {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(row.occurredAt).atZone(zone)
    val day = moment.toLocalDate()
    val whenText = when (day) {
        today -> "hoje " + moment.format(DateTimeFormatter.ofPattern("HH:mm"))
        today.minusDays(1) -> "ontem"
        else -> day.format(DateTimeFormatter.ofPattern("dd/MM"))
    }
    val methodText = row.method.shortLabel
    val automatic = row.sourceKey != null
    val sign = when (row.direction) { Direction.INCOME -> "+"; Direction.EXPENSE -> "−"; Direction.UNKNOWN -> "" }
    val full = money(row.amountCents)
    // Início keeps the currency symbol on recent rows, as in the prototype; Registros drops it.
    val amountText = if (row.amountCents == null) full else sign + full
    val spoken = when (row.direction) { Direction.INCOME -> "mais $full"; Direction.EXPENSE -> "menos $full"; Direction.UNKNOWN -> full }
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { edit(row) }.padding(start = 2.dp, end = 0.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tone = fluxoCategoryColor(row.category)
        Box(Modifier.size(32.dp).clip(CircleShape).background(tone.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            FluxoDot(tone, size = 10.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.description.ifBlank { row.method.label() }, maxLines = if (largeFont) 2 else 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, lineHeight = 15.sp)
            )
            // FlowRow: when the text does not fit, the badges drop to their own line instead of squeezing it.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp), itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${bankShortName(row.bankPackage)} · $methodText · $whenText",
                    style = FluxoType.Caption, color = FluxoColors.Mute
                )
                FluxoBadge(if (automatic) "automático" else "manual", kind = if (automatic) FluxoBadgeKind.Automatic else FluxoBadgeKind.Manual)
                if (row.status == ReviewStatus.REVIEW) Text(
                    "revisar", maxLines = 1, color = FluxoColors.Ink,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.clip(FluxoShapes.Badge).background(FluxoColors.Amber).padding(horizontal = 6.dp)
                )
            }
        }
        Text(
            amountText, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.semantics { contentDescription = spoken },
            style = FluxoType.RowNumber, color = if (row.direction == Direction.INCOME) FluxoColors.Income else FluxoColors.Ink
        )
    }
}

/** First day of the open card cycle: the day after the most recent closing before [today]. */
internal fun openCycleStart(today: LocalDate, closingDay: Int): LocalDate {
    fun closing(m: YearMonth) = m.atDay(minOf(closingDay.coerceIn(1, 31), m.lengthOfMonth()))
    val thisClose = closing(YearMonth.from(today))
    val last = if (today.isAfter(thisClose)) thisClose else closing(YearMonth.from(today).minusMonths(1))
    return last.plusDays(1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaceCard(
    pace: br.com.fluxo.core.Pace, budgetCents: Long, fromSalary: Boolean,
    overruns: List<br.com.fluxo.core.CategoryOverrun>, hidden: Boolean,
    openCategory: (Category) -> Unit, modifier: Modifier = Modifier
) {
    fun v(cents: Long) = if (hidden) HIDDEN else money(cents)
    FluxoTile(modifier.fillMaxWidth().testTag("dashboard.pace"), role = FluxoTileRole.Outlined) {
        Text("Ritmo do mês", style = TileTitle)
        if (budgetCents <= 0) {
            Text("Defina orçamentos em Planejar para acompanhar o ritmo do mês.", style = FluxoType.Caption, color = FluxoColors.Mute)
            return@FluxoTile
        }
        Text(
            "Gasto ${v(pace.spentCents)} de ${v(budgetCents)} · esperado até hoje ${v(pace.expectedByTodayCents)}",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
        )
        if (!hidden) PaceBar(pace.spentCents, pace.expectedByTodayCents, budgetCents, Modifier.fillMaxWidth().padding(vertical = 4.dp).height(14.dp))
        Text(
            "Projeção do mês: ${v(pace.projectedEndCents)}",
            style = FluxoType.Caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = if (pace.status == PaceStatus.OVER) FluxoColors.Expense else FluxoColors.Ink
        )
        if (fromSalary) Text("Sem orçamentos: usando o salário como referência.", style = FluxoType.Caption, color = FluxoColors.Mute)
        if (overruns.isNotEmpty()) FlowRow(
            Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            overruns.forEach { o ->
                val over = o.spentCents >= o.limitCents
                val pct = percent(BigInteger.valueOf(o.spentCents), BigInteger.valueOf(o.limitCents))
                Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { openCategory(o.category) }, contentAlignment = Alignment.Center) {
                    Text(
                        "${o.category.label} $pct%", maxLines = 1,
                        style = LegendStyle.copy(fontWeight = FontWeight.Bold),
                        color = if (over) FluxoColors.Expense else FluxoColors.Ink,
                        modifier = Modifier.clip(FluxoShapes.Badge)
                            .background(if (over) FluxoColors.Expense.copy(alpha = 0.16f) else FluxoColors.Amber)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditCard(
    credit: String, openCycle: String?, cash: String, closingDay: Int?, dueDay: Int?,
    configure: () -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Compras no crédito", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
                Text("Compromisso separado do caixa", style = FluxoType.Caption, color = FluxoColors.Mute)
            }
            Text(credit, style = FluxoType.number(18.sp))
        }
        if (closingDay != null && openCycle != null) {
            Text("Cartão de crédito · fecha dia $closingDay" + (dueDay?.let { " · vence dia $it" } ?: ""), style = FluxoType.Caption, color = FluxoColors.Ink, modifier = Modifier.padding(top = 4.dp))
            Text("Fatura aberta $openCycle vs caixa do mês $cash", style = FluxoType.Caption, color = FluxoColors.Mute)
            Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = configure).padding(end = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text("Alterar fechamento e vencimento", style = FluxoType.Caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = FluxoColors.Ink)
            }
        } else {
            FluxoPillButton("Informar fechamento e vencimento", configure, Modifier.padding(top = 4.dp), style = FluxoButtonStyle.Quiet)
        }
    }
}

@Composable
private fun CardCycleDialog(closing: Int?, due: Int?, save: (Int?, Int?) -> Unit, dismiss: () -> Unit) {
    var closingText by rememberSaveable { mutableStateOf(closing?.toString().orEmpty()) }
    var dueText by rememberSaveable { mutableStateOf(due?.toString().orEmpty()) }
    val c = closingText.toIntOrNull()?.takeIf { it in 1..31 }
    val d = dueText.toIntOrNull()?.takeIf { it in 1..31 }
    val valid = c != null && (dueText.isBlank() || d != null)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Fatura do cartão") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Informe os dias do mês (1 a 31). Fica só neste aparelho.", style = FluxoType.Caption, color = FluxoColors.Mute)
                OutlinedTextField(
                    closingText, { closingText = it.filter(Char::isDigit).take(2) }, label = { Text("Dia do fechamento") }, singleLine = true,
                    isError = closingText.isNotBlank() && c == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("dashboard.cardClosing")
                )
                OutlinedTextField(
                    dueText, { dueText = it.filter(Char::isDigit).take(2) }, label = { Text("Dia do vencimento (opcional)") }, singleLine = true,
                    isError = dueText.isNotBlank() && d == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("dashboard.cardDue")
                )
            }
        },
        confirmButton = { TextButton({ save(c, d); dismiss() }, enabled = valid) { Text("Salvar") } },
        dismissButton = {
            Row {
                if (closing != null) TextButton({ save(null, null); dismiss() }) { Text("Remover") }
                TextButton(dismiss) { Text("Cancelar") }
            }
        }
    )
}

private fun HomeCard.title(): String = when (this) {
    HomeCard.PACE -> "Ritmo do mês"
    HomeCard.CAPTURE -> "Captura e revisão"
    HomeCard.CREDIT -> "Cartão de crédito"
    HomeCard.VR -> "Vale-refeição"
    HomeCard.RECENT -> "Últimos lançamentos"
    HomeCard.CHARTS -> "Gráficos"
}

@Composable
private fun CustomizeDialog(order: List<HomeCard>, hidden: Set<HomeCard>, save: (List<HomeCard>, Set<HomeCard>) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Personalizar início") },
        text = {
            Column {
                order.forEachIndexed { i, card ->
                    val off = card in hidden
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(card.title(), Modifier.weight(1f), color = if (off) FluxoColors.Mute else FluxoColors.Ink, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { save(order.toMutableList().also { it.add(i - 1, it.removeAt(i)) }, hidden) }, enabled = i > 0, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.KeyboardArrowUp, "Mover ${card.title()} para cima")
                        }
                        IconButton(onClick = { save(order.toMutableList().also { it.add(i + 1, it.removeAt(i)) }, hidden) }, enabled = i < order.lastIndex, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, "Mover ${card.title()} para baixo")
                        }
                        IconButton(onClick = { save(order, if (off) hidden - card else hidden + card) }, modifier = Modifier.size(44.dp)) {
                            Icon(if (off) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (off) "Mostrar ${card.title()}" else "Ocultar ${card.title()}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(dismiss) { Text("Concluído") } }
    )
}
