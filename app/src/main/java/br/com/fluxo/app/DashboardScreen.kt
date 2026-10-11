package br.com.fluxo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.SpendingAnalytics
import br.com.fluxo.core.Transaction
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle

// Thin aliases kept so SettingsScreen keeps compiling; they now resolve to the petrol design system.
internal val NeonMint = FluxoColors.Petrol
internal val NeonBlue = FluxoColors.PetrolMid
internal val NeonLime = FluxoColors.PetrolLight
internal val NeonAmber = FluxoColors.Amber

@Composable
internal fun NeonPanel(
    modifier: Modifier = Modifier,
    accent: Color = NeonBlue,
    background: List<Color>? = null,
    content: @Composable ColumnScope.() -> Unit
) = FluxoPanel(modifier = modifier, accent = accent, gradient = background, content = content)

@Composable
internal fun NeonHeading(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color = NeonMint) =
    FluxoSectionHeading(title, icon = icon, accent = accent)

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
    edit: (Transaction) -> Unit
) {
    // Baseline accounting: only confirmed, non-transfer records enter totals; credit stays apart from cash.
    val confirmed = rows.filter { it.status == ReviewStatus.CONFIRMED && it.transferId == null }
    fun total(predicate: (Transaction) -> Boolean): BigInteger = confirmed.filter(predicate)
        .fold(BigInteger.ZERO) { sum, row -> sum + BigInteger.valueOf(row.amountCents ?: 0) }
    val income = total { it.direction == Direction.INCOME }
    val cash = total { it.direction == Direction.EXPENSE && it.method != PaymentMethod.CREDIT_CARD }
    val credit = total { it.direction == Direction.EXPENSE && it.method == PaymentMethod.CREDIT_CARD }
    val result = income - cash
    val pending = rows.count { it.status == ReviewStatus.REVIEW }
    val pendingCredit = rows.count { it.status == ReviewStatus.REVIEW && it.method == PaymentMethod.CREDIT_CARD }
    var hidden by rememberSaveable { mutableStateOf(false) }
    fun amount(value: BigInteger) = if (hidden) HIDDEN else cents(value)

    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today) == month
    val endDay = if (currentMonth) today else month.atEndOfMonth()
    val daily = SpendingAnalytics.dailyExpenses(rows, endDay)
    val dailyValues = daily.map { it.totalCents }
    val weekTotal = dailyValues.fold(BigInteger.ZERO, BigInteger::add)
    val byCategory = SpendingAnalytics.byCategory(rows, month).entries.sortedByDescending { it.value }
    val monthExpenses = byCategory.fold(BigInteger.ZERO) { sum, e -> sum + e.value }
    val share = SpendingAnalytics.captureShare(rows, month)

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard.neon"),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FluxoMonthBar(month, previous, next) {
                IconButton(onClick = { hidden = !hidden }, modifier = Modifier.size(36.dp, 44.dp).testTag("dashboard.visibility")) {
                    Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) "Mostrar valores do resumo" else "Ocultar valores do resumo", modifier = Modifier.size(22.dp))
                }
                FluxoPillButton("Manual", add, style = FluxoButtonStyle.Quiet, icon = Icons.Default.Add)
            }
        }
        item {
            FluxoHero(
                label = "Resultado observado do mês",
                value = if (hidden) HIDDEN else plainMoney(result),
                currency = if (hidden) null else "R$",
                equation = "Entradas ${amount(income)} − saídas de caixa ${amount(cash)}",
                note = "Cobertura parcial — não é seu saldo bancário.",
                trailing = if (hidden) null else {
                    {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Sparkline(
                                dailyValues,
                                "Gastos por dia nos últimos 7 dias: " + daily.joinToString(", ") { "${it.day.dayOfMonth}: ${cents(it.totalCents)}" },
                                Modifier.size(width = 84.dp, height = 36.dp)
                            )
                            Text("gasto · 7 dias", color = FluxoColors.OnPetrolSoft, style = FluxoType.Caption.copy(fontSize = 10.sp))
                        }
                    }
                }
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Compras no crédito", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
                    Text("Compromisso separado do caixa", style = FluxoType.Caption, color = FluxoColors.Mute)
                }
                Text(amount(credit), style = FluxoType.number(18.sp))
            }
        }
        item {
            val auto = share.automatic
            val detail = when {
                !active -> "Escolha bancos ou permita outros apps, conceda acesso e ative a captura."
                auto == 0 -> "Nenhum lançamento automático este mês"
                auto == 1 -> "1 lançamento automático este mês"
                else -> "$auto lançamentos automáticos este mês"
            }
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
        item {
            val topCategory = byCategory.firstOrNull()
            val shown = if (byCategory.size > 6) byCategory.take(5) else byCategory
            val rest = if (byCategory.size > 6) byCategory.drop(5).fold(BigInteger.ZERO) { s, e -> s + e.value } else BigInteger.ZERO
            val legend = shown.map { Triple(it.key.label, it.value, fluxoCategoryColor(it.key)) } +
                (if (rest.signum() > 0) listOf(Triple("Demais", rest, FluxoColors.Gray)) else emptyList())
            val donutText = if (legend.isEmpty()) "Sem gastos confirmados neste mês" else
                "Despesas por categoria: " + legend.joinToString(", ") { "${it.first} ${percent(it.second, monthExpenses)}%" }
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
                    Text(amount(monthExpenses), style = FluxoType.number(16.sp))
                    DonutChart(
                        slices = legend.map { DonutSlice(it.second, it.third) },
                        contentDescription = donutText + (topCategory?.let { "; maior: ${it.key.label}" } ?: ""),
                        modifier = Modifier.padding(vertical = 4.dp).size(80.dp).align(Alignment.CenterHorizontally),
                        strokeWidth = 12.dp
                    ) {
                        if (topCategory != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${percent(topCategory.value, monthExpenses)}%", style = FluxoType.number(14.sp), color = FluxoColors.Ink)
                            Text(
                                topCategory.key.label.let { if (it.length > 6) it.take(5) + "." else it },
                                style = FluxoType.Caption.copy(fontSize = 9.sp, lineHeight = 11.sp), color = FluxoColors.Mute, maxLines = 1
                            )
                        }
                    }
                    if (legend.isEmpty()) Text("Sem gastos confirmados neste mês.", style = FluxoType.Caption, color = FluxoColors.Mute)
                    legend.forEach { (name, value, color) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 13.5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
                            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = LegendStyle)
                            Text("${percent(value, monthExpenses)}%", maxLines = 1, style = LegendStyle.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
                        }
                    }
                }
            }
            val barTile: @Composable () -> Unit = {
                FluxoTile(Modifier.fillMaxWidth(), role = FluxoTileRole.Outlined) {
                    Text("Gastos por dia", style = TileTitle)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(amount(lastDay.totalCents), style = FluxoType.number(16.sp), maxLines = 1)
                        Text(if (currentMonth) "hoje" else lastDay.day.format(DateTimeFormatter.ofPattern("dd/MM")), style = FluxoType.Caption.copy(fontSize = 10.5.sp), color = FluxoColors.Mute)
                    }
                    BarChart(
                        entries = daily.mapIndexed { i, d ->
                            BarEntry(d.totalCents, dayLabels[i], if (!hidden && peak != null && peak.index == i) plainMoney(d.totalCents).removePrefix("−") else null)
                        },
                        contentDescription = barText,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(84.dp),
                        highlightedIndex = if (currentMonth) daily.lastIndex else null,
                        showValueLabels = !hidden
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
                            style = FluxoType.Caption.copy(fontSize = 10.5.sp), color = FluxoColors.Ink
                        )
                    }
                }
            }
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 300.dp || fontScale >= 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { donutTile(); barTile(); splitTile() }
                } else {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(148.dp).fillMaxHeight()) { donutTile() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { barTile(); splitTile() }
                    }
                }
            }
        }
        item {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    FluxoSectionHeading("Últimos lançamentos", Modifier.weight(1f))
                    Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = showAll).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterEnd) {
                        Text("Todos", style = FluxoType.Caption.copy(fontSize = 11.5.sp), color = FluxoColors.Mute)
                    }
                }
                if (rows.isEmpty()) FluxoEmptyState("Seu mês começa aqui", "Adicione um lançamento ou configure a captura. Nenhum histórico anterior será importado.")
                else if (hidden) Text("Lançamentos ocultos neste resumo.", style = MaterialTheme.typography.bodyMedium, color = FluxoColors.Mute)
                else rows.take(3).forEach { key(it.id) { TransactionRow(it, today, edit) } }
            }
        }
        if (pendingCredit > 0) item {
            Text("$pendingCredit lançamento(s) de crédito pendente(s) de revisão. Fatura, limite e vencimento não estão disponíveis; confira no emissor do cartão.", style = FluxoType.Caption, color = FluxoColors.Mute, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

private val TileTitle: TextStyle = TextStyle(fontFamily = FluxoType.Body, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = FluxoColors.Ink)
private val LegendStyle: TextStyle = TextStyle(fontFamily = FluxoType.Body, fontSize = 11.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.Normal, color = FluxoColors.Ink)

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
    val methodText = when (row.method) {
        PaymentMethod.PIX -> "Pix"; PaymentMethod.CREDIT_CARD -> "crédito"
        PaymentMethod.DEBIT_CARD -> "débito"; PaymentMethod.UNKNOWN -> "outro"
    }
    val automatic = row.sourceKey != null
    val sign = when (row.direction) { Direction.INCOME -> "+"; Direction.EXPENSE -> "−"; Direction.UNKNOWN -> "" }
    val full = money(row.amountCents)
    // Início keeps the currency symbol on recent rows, as in the prototype; Registros drops it.
    val amountText = if (row.amountCents == null) full else sign + full
    val spoken = when (row.direction) { Direction.INCOME -> "mais $full"; Direction.EXPENSE -> "menos $full"; Direction.UNKNOWN -> full }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { edit(row) }.padding(start = 2.dp, end = 0.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FluxoDot(fluxoCategoryColor(row.category))
        Column(Modifier.weight(1f)) {
            Text(
                row.description.ifBlank { row.method.label() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, lineHeight = 15.sp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "${bankShortName(row.bankPackage)} · $methodText · $whenText", Modifier.weight(1f, fill = false),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = FluxoType.Caption, color = FluxoColors.Mute
                )
                FluxoBadge(if (automatic) "automático" else "manual", kind = if (automatic) FluxoBadgeKind.Automatic else FluxoBadgeKind.Manual)
                if (row.status == ReviewStatus.REVIEW) Text(
                    "revisar", maxLines = 1, color = FluxoColors.Ink,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold),
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
