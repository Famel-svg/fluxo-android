package br.com.fluxo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.fluxo.core.Category
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.SpendingAnalytics
import br.com.fluxo.core.Transaction
import br.com.fluxo.core.TransactionSearch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val weekdayShort = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

/** Parser wording for rows whose only open question is the payment method. */
private const val METHOD_ONLY_REASON = "Meio de pagamento não identificado"

/**
 * Presentation of the records. The caller supplies the month slice and the existing edit action;
 * one-tap review actions save through the same activity-scoped [LedgerViewModel.save] path (and its
 * saving/error state) that the editor uses.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordsScreen(
    rows: List<Transaction>,
    filter: Int,
    setFilter: (Int) -> Unit,
    allMonths: Boolean,
    setAllMonths: (Boolean) -> Unit,
    edit: (Transaction) -> Unit,
    bankOptions: List<String?>,
    bankLabel: (String) -> String,
    moneyLabel: (Long?) -> String,
    directionLabel: (Direction) -> String,
    methodLabel: (PaymentMethod) -> String,
    month: java.time.YearMonth = java.time.YearMonth.now(),
    previousMonth: () -> Unit = {},
    nextMonth: () -> Unit = {},
    add: () -> Unit = {},
    model: LedgerViewModel = viewModel()
) {
    val saving by model.saving.collectAsStateWithLifecycle()
    val editError by model.editError.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var bank by rememberSaveable { mutableStateOf<String?>(null) }
    var method by rememberSaveable { mutableStateOf<PaymentMethod?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(rows, filter, query, bank, method, category) {
        TransactionSearch.filter(rows.filter { when (filter) { 1 -> it.direction == Direction.INCOME; 2 -> it.direction == Direction.EXPENSE; 3 -> it.status == ReviewStatus.REVIEW; else -> true } }, query, bank, method, category).sortedByDescending { it.occurredAt }
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val reviewing = remember(filtered) { filtered.filter { it.status == ReviewStatus.REVIEW } }
    val groups = remember(filtered, zone) {
        filtered.filter { it.status != ReviewStatus.REVIEW }
            .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
    }
    val reviewCount = remember(rows) { rows.count { it.status == ReviewStatus.REVIEW } }
    val chipBanks = remember(rows, bankOptions) {
        val present = rows.map { it.bankPackage }.toSet()
        bankOptions.filterNotNull().filter { it in present }
    }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm", ptBr) }
    val weekCap = remember(rows, zone, today) {
        val latest = rows.maxOfOrNull { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
        val end = if (latest == null || !latest.isBefore(today.minusDays(6))) today else latest
        SpendingAnalytics.dailyExpenses(rows, end, 7, zone)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = FluxoColors.Paper) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                val addButton: @Composable RowScope.() -> Unit = {
                    Box(
                        Modifier.size(44.dp).clip(FluxoShapes.Pill).border(2.dp, FluxoColors.Ink, FluxoShapes.Pill)
                            .clickable(role = Role.Button, onClick = add),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, "Adicionar lançamento", tint = FluxoColors.Ink, modifier = Modifier.size(20.dp)) }
                }
                if (allMonths) Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Todos os meses", Modifier.weight(1f), color = FluxoColors.Ink, maxLines = 1,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold))
                    addButton()
                } else FluxoMonthBar(month, previousMonth, nextMonth, trailing = addButton)
            }
            item {
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FluxoColors.Ink, unfocusedBorderColor = FluxoColors.Ink,
                    focusedContainerColor = FluxoColors.Paper, unfocusedContainerColor = FluxoColors.Paper,
                    focusedPlaceholderColor = FluxoColors.Mute, unfocusedPlaceholderColor = FluxoColors.Mute,
                    focusedLeadingIconColor = FluxoColors.Ink, unfocusedLeadingIconColor = FluxoColors.Ink,
                    cursorColor = FluxoColors.Ink
                )
                val fieldText = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.5.sp, color = FluxoColors.Ink)
                androidx.compose.foundation.text.BasicTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    singleLine = true, textStyle = fieldText,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(FluxoColors.Ink),
                    interactionSource = interaction,
                    decorationBox = { inner ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = query, innerTextField = inner, enabled = true, singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                            interactionSource = interaction,
                            placeholder = { Text("Buscar descrição, banco ou categoria", maxLines = 1, overflow = TextOverflow.Ellipsis, style = fieldText.copy(color = FluxoColors.Mute)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp)) },
                            colors = fieldColors,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = true, isError = false, interactionSource = interaction, colors = fieldColors,
                                    shape = RoundedCornerShape(22.dp), focusedBorderThickness = 2.dp, unfocusedBorderThickness = 2.dp
                                )
                            }
                        )
                    }
                )
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    FluxoChip("Todos", filter == 0 && bank == null, { setFilter(0); bank = null })
                    FluxoChip("Entradas", filter == 1, { setFilter(if (filter == 1) 0 else 1) })
                    FluxoChip("Saídas", filter == 2, { setFilter(if (filter == 2) 0 else 2) })
                    FluxoChip("Revisar ($reviewCount)", filter == 3, { setFilter(if (filter == 3) 0 else 3) }, tone = FluxoChipTone.Review)
                    chipBanks.forEach { code ->
                        FluxoChip(bankLabel(code), bank == code, { bank = if (bank == code) null else code })
                    }
                }
            }
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    FluxoChip("Todos os meses", allMonths, { setAllMonths(!allMonths) })
                    FluxoPillButton(if (advanced) "Ocultar filtros" else "Banco, meio e categoria", { advanced = !advanced }, style = FluxoButtonStyle.Quiet)
                }
            }
            if (advanced) {
                item { Choice("Banco", bank, bankOptions, { it?.let(bankLabel) ?: "Todos" }) { bank = it } }
                item { Choice("Meio", method, listOf<PaymentMethod?>(null) + PaymentMethod.entries, { it?.let(methodLabel) ?: "Todos" }) { method = it } }
                item { Choice("Categoria", category, listOf<Category?>(null) + Category.entries, { it?.label ?: "Todas" }) { category = it } }
            }
            item {
                WeekStrip(weekCap)
            }
            item {
                // Vertical layout keeps the count and action readable at large font sizes.
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text("${filtered.size} lançamento(s)", color = FluxoColors.Mute, style = MaterialTheme.typography.labelMedium)
                    FluxoPillButton(
                        "Limpar filtros",
                        { query = ""; bank = null; method = null; category = null; setFilter(0); setAllMonths(false) },
                        style = FluxoButtonStyle.Quiet
                    )
                }
            }
            if (filtered.isEmpty()) item {
                FluxoEmptyState("Nenhum lançamento", "Nenhum registro corresponde aos filtros. Altere a busca ou escolha Todos os meses.")
            }
            if (reviewing.isNotEmpty()) {
                item {
                    FluxoSectionHeading("Revisar", Modifier.padding(start = 2.dp, top = 6.dp, end = 2.dp, bottom = 2.dp), trailing = "um toque resolve")
                }
                if (editError != null) item {
                    Text(editError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 2.dp))
                }
                items(reviewing, key = { "review-" + it.id }) { row ->
                    Box(Modifier.padding(bottom = 5.dp)) {
                        ReviewTicket(row, saving, edit, { model.save(it) {} }, bankLabel, directionLabel, methodLabel, zone, timeFormat)
                    }
                }
            }
            groups.forEach { (date, transactions) ->
                item(key = "day-$date") {
                    val title = when (date) {
                        today -> "Hoje"
                        today.minusDays(1) -> "Ontem"
                        else -> date.format(dateFormat)
                    }
                    Text(
                        title.uppercase(ptBr), color = FluxoColors.Mute,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em),
                        modifier = Modifier.padding(start = 2.dp, top = 8.dp, end = 2.dp, bottom = 1.dp).semantics { heading() }
                    )
                }
                items(transactions, key = { it.id }) { transaction ->
                    RecordRow(transaction, edit, bankLabel, moneyLabel)
                }
            }
        }
    }
}

/** Slim soft strip: seven-day spending total beside seven small bars. */
@Composable
private fun WeekStrip(days: List<br.com.fluxo.core.DailyExpense>) {
    val total = days.fold(java.math.BigInteger.ZERO) { sum, day -> sum + day.totalCents }
    val totalText = money(BigDecimal(total, 2))
    val description = "Gastos por dia nos últimos 7 dias, total $totalText; " +
        days.joinToString(", ") { weekdayShort[it.day.dayOfWeek.value - 1] + " " + money(BigDecimal(it.totalCents, 2)) }
    val entries = days.map { BarEntry(it.totalCents, weekdayShort[it.day.dayOfWeek.value - 1]) }
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val label: @Composable () -> Unit = {
        Column {
            Text("Gastos · 7 dias", color = FluxoColors.Mute, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            Text(totalText, style = FluxoType.number(16.sp), maxLines = 1)
        }
    }
    val chart: @Composable (Modifier) -> Unit = { modifier ->
        BarChart(entries, description, modifier, highlightedIndex = entries.lastIndex)
    }
    Box(Modifier.padding(top = 2.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FluxoColors.Soft).padding(horizontal = 8.dp, vertical = 6.dp)) {
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { label(); chart(Modifier.fillMaxWidth().height(72.dp)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.widthIn(min = 92.dp)) { label() }
                chart(Modifier.weight(1f).height(48.dp))
            }
        }
    }
}

/** Amber-striped ticket with real one-tap actions; nothing is confirmed without amount and direction. */
@Composable
private fun ReviewTicket(
    row: Transaction,
    saving: Boolean,
    edit: (Transaction) -> Unit,
    save: (Transaction) -> Unit,
    bankLabel: (String) -> String,
    directionLabel: (Direction) -> String,
    methodLabel: (PaymentMethod) -> String,
    zone: ZoneId,
    timeFormat: DateTimeFormatter
) {
    val amount = row.amountCents
    val known = amount != null && amount > 0 && row.direction != Direction.UNKNOWN
    val methodOnly = known && row.direction == Direction.EXPENSE && row.method == PaymentMethod.UNKNOWN &&
        row.reason.startsWith(METHOD_ONLY_REASON)
    fun confirmed(method: PaymentMethod = row.method) = row.copy(status = ReviewStatus.CONFIRMED, reason = "", method = method)
    FluxoTicket {
        FluxoTicketHeader(
            (row.description.ifBlank { methodLabel(row.method) }) + " · " + money(amount),
            badge = bankLabel(row.bankPackage)
        )
        Text(
            row.reason.ifBlank { "Confirme valor e classificação" } + if (!known) " Informe valor e direção em Editar." else "",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            directionLabel(row.direction) + " · " + methodLabel(row.method) + " · " +
                Instant.ofEpochMilli(row.occurredAt).atZone(zone).format(timeFormat),
            color = FluxoColors.Mute, style = FluxoType.Caption
        )
        FluxoTicketActions {
            if (methodOnly) {
                FluxoPillButton("Crédito", { save(confirmed(PaymentMethod.CREDIT_CARD)) }, Modifier.weight(1f), FluxoButtonStyle.Primary, enabled = !saving)
                FluxoPillButton("Débito", { save(confirmed(PaymentMethod.DEBIT_CARD)) }, Modifier.weight(1f), FluxoButtonStyle.Secondary, enabled = !saving)
            } else {
                FluxoPillButton("Confirmar", { save(confirmed()) }, Modifier.weight(1f), FluxoButtonStyle.Primary, enabled = known && !saving)
                FluxoPillButton("Editar", { edit(row) }, Modifier.weight(1f), FluxoButtonStyle.Secondary)
            }
        }
    }
}

/** Day-list row: category dot, title, "categoria · banco · meio" line with origin badge, signed amount. */
@Composable
private fun RecordRow(
    row: Transaction,
    edit: (Transaction) -> Unit,
    bankLabel: (String) -> String,
    moneyLabel: (Long?) -> String
) {
    val shortMethod = when (row.method) {
        PaymentMethod.PIX -> "Pix"
        PaymentMethod.CREDIT_CARD -> "crédito"
        PaymentMethod.DEBIT_CARD -> "débito"
        PaymentMethod.UNKNOWN -> "meio não informado"
    }
    val signed = when (row.direction) {
        Direction.INCOME -> "+" + moneyLabel(row.amountCents)
        Direction.EXPENSE -> "−" + moneyLabel(row.amountCents)
        Direction.UNKNOWN -> moneyLabel(row.amountCents)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { edit(row) }.padding(horizontal = 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        FluxoDot(fluxoCategoryColor(row.category))
        Column(Modifier.weight(1f)) {
            Text(
                row.description.ifBlank { shortMethod }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    row.category.label + " · " + bankLabel(row.bankPackage) + " · " + shortMethod,
                    color = FluxoColors.Mute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = FluxoType.Caption, modifier = Modifier.weight(1f, fill = false)
                )
                if (row.sourceKey != null) FluxoBadge("automático") else FluxoBadge("manual", kind = FluxoBadgeKind.Manual)
            }
        }
        Text(
            signed, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 2.dp),
            color = if (row.direction == Direction.INCOME) FluxoColors.Income else FluxoColors.Ink,
            style = FluxoType.RowNumber
        )
    }
}
