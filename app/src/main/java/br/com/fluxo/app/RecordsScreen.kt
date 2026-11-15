package br.com.fluxo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
    model: LedgerViewModel = viewModel(),
    allRows: List<Transaction> = rows
) {
    val shortBank: (String) -> String = { bankLabel(it).removeSuffix(" Bank") }
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
    val clearFilters = { query = ""; bank = null; method = null; category = null; setFilter(0); setAllMonths(false) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val reviewing = remember(filtered) { filtered.filter { it.status == ReviewStatus.REVIEW } }
    val groups = remember(filtered, zone) {
        filtered.filter { it.status != ReviewStatus.REVIEW }
            .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
    }
    val reviewCount = remember(rows) { rows.count { it.status == ReviewStatus.REVIEW } }
    // Celebrate only the >0 -> 0 transition seen while the screen is open; not persisted.
    var celebrate by remember { mutableStateOf(false) }
    var previousReviewCount by remember { mutableIntStateOf(reviewCount) }
    LaunchedEffect(reviewCount) {
        celebrate = previousReviewCount > 0 && reviewCount == 0
        previousReviewCount = reviewCount
    }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm", ptBr) }
    val weekCap = remember(rows, allRows, allMonths, month, zone, today) {
        if (allMonths) {
            val latest = rows.maxOfOrNull { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            val end = if (latest == null || !latest.isBefore(today.minusDays(6))) today else latest
            SpendingAnalytics.dailyExpenses(rows.filter { !it.method.isVoucher }, end, 7, zone)
        } else {
            // All transactions, not just the month slice: early in a month the window reaches into the previous one.
            SpendingAnalytics.recentDaysForMonth(allRows.filter { !it.method.isVoucher }, month, today, 7, zone)
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = FluxoColors.Paper) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                val addButton: @Composable RowScope.() -> Unit = {
                    Box(
                        Modifier.size(44.dp).clip(FluxoShapes.Pill).border(2.dp, FluxoColors.ChipBorder, FluxoShapes.Pill)
                            .clickable(role = Role.Button, onClick = add),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, "Adicionar lançamento", tint = FluxoColors.Ink, modifier = Modifier.size(20.dp)) }
                }
                if (allMonths) Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Todos os meses", Modifier.weight(1f), color = FluxoColors.Ink, maxLines = 1,
                        style = MaterialTheme.typography.titleMedium)
                    addButton()
                } else FluxoMonthBar(month, previousMonth, nextMonth, trailing = addButton)
            }
            item {
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FluxoColors.Ink, unfocusedBorderColor = FluxoColors.ChipBorder,
                    focusedContainerColor = FluxoColors.Paper, unfocusedContainerColor = FluxoColors.Paper,
                    focusedPlaceholderColor = FluxoColors.Mute, unfocusedPlaceholderColor = FluxoColors.Mute,
                    focusedLeadingIconColor = FluxoColors.Ink, unfocusedLeadingIconColor = FluxoColors.Ink,
                    cursorColor = FluxoColors.Ink
                )
                val fieldText = MaterialTheme.typography.bodyMedium.copy(color = FluxoColors.Ink)
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
                // Primary type filter only; bank, method and category live in the collapsible panel.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    FluxoChip("Todos", filter == 0, { setFilter(0) })
                    FluxoChip("Entradas", filter == 1, { setFilter(if (filter == 1) 0 else 1) })
                    FluxoChip("Saídas", filter == 2, { setFilter(if (filter == 2) 0 else 2) })
                    FluxoChip("Revisar ($reviewCount)", filter == 3, { setFilter(if (filter == 3) 0 else 3) }, tone = FluxoChipTone.Review)
                }
            }
            item {
                val anyFilter = query.isNotBlank() || bank != null || method != null || category != null || filter != 0 || allMonths
                val panelCount = listOf(bank != null, method != null, category != null).count { it }
                // FlowRow keeps the count and actions readable at large font sizes.
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${filtered.size} lançamento(s)", color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (anyFilter) CompactAction("Limpar filtros", FluxoColors.Mute) { clearFilters() }
                        CompactAction(if (allMonths) "Só este mês" else "Todos os meses", FluxoColors.Ink) { setAllMonths(!allMonths) }
                        CompactAction(
                            if (advanced) "Ocultar filtros" else if (panelCount > 0) "Filtros ($panelCount)" else "Filtros",
                            FluxoColors.Ink, description = "Banco, meio e categoria"
                        ) { advanced = !advanced }
                    }
                }
            }
            if (advanced) {
                item { Choice("Banco", bank, bankOptions, { it?.let(bankLabel) ?: "Todos" }) { bank = it } }
                item { Choice("Meio", method, listOf<PaymentMethod?>(null) + PaymentMethod.entries.filter { it != PaymentMethod.TRANSPORT_VOUCHER || method == it || rows.any { r -> r.method == it } }, { it?.let(methodLabel) ?: "Todos" }) { method = it } }
                item { Choice("Categoria", category, listOf<Category?>(null) + Category.entries, { it?.label ?: "Todas" }) { category = it } }
                item { WeekStrip(weekCap) }
            }
            if (filtered.isEmpty()) item {
                if (rows.isEmpty()) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 16.dp).size(88.dp).clip(CircleShape).background(FluxoColors.Soft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Receipt, null, tint = FluxoColors.Mute, modifier = Modifier.size(44.dp))
                    }
                    FluxoEmptyState(
                        "Nenhum lançamento ainda",
                        "Ative a captura de notificações em Ajustes ou toque em + para lançar manualmente."
                    )
                    CompactAction("Lançar manualmente", FluxoColors.Ink) { add() }
                } else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    FluxoEmptyState("Nenhum resultado com estes filtros", "Altere a busca ou limpe os filtros para ver todos os lançamentos.")
                    CompactAction("Limpar filtros", FluxoColors.Ink) { clearFilters() }
                }
            }
            if (celebrate && reviewCount == 0) item(key = "review-done") { ReviewDoneCard(Modifier.animateItem()) }
            if (reviewing.isNotEmpty()) {
                item {
                    FluxoSectionHeading("Revisar", Modifier.padding(start = 2.dp, top = 6.dp, end = 2.dp, bottom = 2.dp), trailing = "um toque resolve")
                }
                item {
                    val progress by animateFloatAsState(
                        if (rows.isEmpty()) 0f else 1f - reviewCount / rows.size.toFloat(), tween(300, easing = FastOutSlowInEasing), label = "reviewProgress"
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(FluxoShapes.Pill),
                            color = FluxoColors.Income, trackColor = FluxoColors.Soft, gapSize = 0.dp, drawStopIndicator = {}
                        )
                        Text("${reviewing.size} para revisar · deslize → para confirmar", color = FluxoColors.Mute, style = FluxoType.Caption)
                    }
                }
                if (editError != null) item {
                    Text(editError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 2.dp))
                }
                items(reviewing, key = { "review-" + it.id }) { row ->
                    Box(Modifier.animateItem(fadeInSpec = ROW_FADE_IN, placementSpec = ROW_PLACE, fadeOutSpec = ROW_FADE_OUT).padding(bottom = 5.dp)) {
                        ReviewTicket(row, saving, edit, { model.save(it) {} }, shortBank, directionLabel, methodLabel, zone, timeFormat)
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
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.08.em),
                        modifier = Modifier.padding(start = 2.dp, top = 8.dp, end = 2.dp, bottom = 1.dp).semantics { heading() }
                    )
                }
                items(transactions, key = { it.id }) { transaction ->
                    Box(Modifier.animateItem(fadeInSpec = ROW_FADE_IN, placementSpec = ROW_PLACE, fadeOutSpec = ROW_FADE_OUT)) {
                        RecordRow(transaction, edit, shortBank, moneyLabel)
                    }
                }
            }
        }
    }
}

// Row insert / remove / reorder motion: short tweens (never springs) so the list settles quickly and predictably.
private val ROW_FADE_IN = tween<Float>(180, easing = FastOutSlowInEasing)
private val ROW_FADE_OUT = tween<Float>(120, easing = FastOutSlowInEasing)
private val ROW_PLACE = tween<androidx.compose.ui.unit.IntOffset>(220, easing = FastOutSlowInEasing)

/** Shown once when the review queue reaches zero; springs in, or appears static with animations off. */
@Composable
private fun ReviewDoneCard(modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val animate = rememberAnimationsEnabled()
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        if (animate) spring(dampingRatio = 0.55f, stiffness = 300f) else snap(), label = "reviewDone"
    )
    Column(
        modifier.fillMaxWidth().padding(vertical = 8.dp)
            .graphicsLayer { val k = 0.6f + 0.4f * progress; scaleX = k; scaleY = k; alpha = progress.coerceIn(0f, 1f) }
            .clip(FluxoShapes.Ticket).background(FluxoColors.Soft).padding(16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(FluxoColors.Income), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(28.dp))
        }
        Text("Tudo revisado", color = FluxoColors.Ink, style = MaterialTheme.typography.titleMedium)
        Text("Seus lançamentos estão em dia", color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
    }
}

/** Small text action (>= 44dp touch target) for the compact filter line. */
@Composable
private fun CompactAction(text: String, color: androidx.compose.ui.graphics.Color, description: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, maxLines = 1, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
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
            Text("Gastos · 7 dias", color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
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
    val amountKnown = amount != null && amount > 0
    val directionKnown = row.direction != Direction.UNKNOWN
    val known = amountKnown && directionKnown
    val missing = when {
        !amountKnown && !directionKnown -> " Informe valor e direção em Editar."
        !amountKnown -> " Informe o valor em Editar."
        !directionKnown -> " Informe a direção em Editar."
        else -> ""
    }
    val methodOnly = known && row.direction == Direction.EXPENSE && row.method == PaymentMethod.UNKNOWN &&
        row.reason.startsWith(METHOD_ONLY_REASON)
    val haptic = LocalHapticFeedback.current
    val saveWithHaptic: (Transaction) -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); save(it) }
    fun confirmed(method: PaymentMethod = row.method) = row.copy(status = ReviewStatus.CONFIRMED, reason = "", method = method)
    // Same action as the first button; ambiguous ones (Completar) open the editor.
    val primary: () -> Unit = when {
        methodOnly -> { { save(confirmed(PaymentMethod.CREDIT_CARD)) } }
        known -> { { save(confirmed()) } }
        else -> { { edit(row) } }
    }
    val primaryLabel = if (methodOnly) "Crédito" else if (known) "Confirmar" else "Completar"
    // The state is remembered once; read the latest callbacks through it.
    val onPrimary by rememberUpdatedState(primary)
    val onEdit by rememberUpdatedState({ edit(row) })
    val swipe = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.35f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onPrimary()
                SwipeToDismissBoxValue.EndToStart -> onEdit()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // snap back; a confirmed row leaves the list through animateItem
        }
    )
    // One tick each time the drag crosses the commit threshold (target flips), not on every frame.
    LaunchedEffect(swipe.targetValue) {
        if (swipe.targetValue != SwipeToDismissBoxValue.Settled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = !saving,
        backgroundContent = {
            val toConfirm = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val toEdit = swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val width = with(LocalDensity.current) { 320.dp.toPx() }
            val fraction = (kotlin.math.abs(runCatching { swipe.requireOffset() }.getOrDefault(0f)) / (width * 0.35f)).coerceIn(0f, 1f)
            val reveal = 0.8f + 0.3f * fraction
            if (toConfirm || toEdit) Row(
                Modifier.fillMaxSize().clip(FluxoShapes.Ticket)
                    .background((if (toConfirm) FluxoColors.Income else FluxoColors.Petrol).copy(alpha = 0.45f + 0.55f * fraction)).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toConfirm) Arrangement.Start else Arrangement.End
            ) {
                val tint = androidx.compose.ui.graphics.Color.White
                Row(Modifier.graphicsLayer { scaleX = reveal; scaleY = reveal }, verticalAlignment = Alignment.CenterVertically) {
                    if (toConfirm) Icon(Icons.Default.Check, null, tint = tint)
                    Text(if (toConfirm) primaryLabel else "Editar", Modifier.padding(horizontal = 8.dp), color = tint,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    if (toEdit) Icon(Icons.Default.Edit, null, tint = tint)
                }
            }
        }
    ) {
    FluxoTicket {
        FluxoTicketHeader(
            money(amount),
            Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(primaryLabel) { if (!saving) primary(); true },
                    CustomAccessibilityAction("Editar") { edit(row); true }
                )
            },
            badge = bankLabel(row.bankPackage)
        )
        Text(row.description.ifBlank { methodLabel(row.method) }, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = FluxoColors.Mute, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
        Text(
            row.reason.ifBlank { "Confirme valor e classificação" } + missing,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            directionLabel(row.direction) + " · " + methodLabel(row.method) + " · " +
                Instant.ofEpochMilli(row.occurredAt).atZone(zone).format(timeFormat),
            color = FluxoColors.Mute, style = FluxoType.Caption
        )
        FluxoTicketActions {
            if (methodOnly) {
                // Two compact choices (VR or credit); FlowRow wraps to a second line instead of clipping at 320dp or large fonts.
                FlowRow(
                    Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    FluxoPillButton("VR", { saveWithHaptic(confirmed(PaymentMethod.MEAL_VOUCHER)) }, Modifier.weight(1f), FluxoButtonStyle.Secondary, enabled = !saving)
                    FluxoPillButton("Crédito", { saveWithHaptic(confirmed(PaymentMethod.CREDIT_CARD)) }, Modifier.weight(1f), FluxoButtonStyle.Primary, enabled = !saving)
                }
            } else {
                // Without amount or direction there is nothing to confirm yet: the primary action opens the editor instead.
                FluxoPillButton(if (known) "Confirmar" else "Completar", { if (known) saveWithHaptic(confirmed()) else edit(row) },
                    Modifier.weight(1f), FluxoButtonStyle.Primary, enabled = !saving)
                FluxoPillButton("Editar", { edit(row) }, Modifier.weight(1f), FluxoButtonStyle.Secondary)
            }
        }
    }
    }
}

/** Day-list row: category dot, title, "categoria · banco · meio" line with origin badge, signed amount. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordRow(
    row: Transaction,
    edit: (Transaction) -> Unit,
    bankLabel: (String) -> String,
    moneyLabel: (Long?) -> String
) {
    val shortMethod = if (row.method == PaymentMethod.UNKNOWN) "meio não informado" else row.method.shortLabel
    val full = moneyLabel(row.amountCents)
    // The list shows only the number (as in the prototype); screen readers still get the currency.
    val plain = full.replace("R$", "").trim()
    val signed = when (row.direction) { Direction.INCOME -> "+$plain"; Direction.EXPENSE -> "−$plain"; Direction.UNKNOWN -> plain }
    val spoken = when (row.direction) { Direction.INCOME -> "mais $full"; Direction.EXPENSE -> "menos $full"; Direction.UNKNOWN -> full }
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { edit(row) }.padding(start = 2.dp, end = 0.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FluxoDot(fluxoCategoryColor(row.category))
        Column(Modifier.weight(1f)) {
            Text(
                row.description.ifBlank { shortMethod }, maxLines = if (largeFont) 2 else 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            )
            // FlowRow: when the text does not fit, the badge drops to its own line instead of squeezing it.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.category.label + " · " + bankLabel(row.bankPackage) + " · " + shortMethod,
                    color = FluxoColors.Mute, style = FluxoType.Caption
                )
                if (row.sourceKey != null) FluxoBadge("automático") else FluxoBadge("manual", kind = FluxoBadgeKind.Manual)
            }
        }
        Text(
            signed, maxLines = 1, softWrap = false, modifier = Modifier.semantics { contentDescription = spoken },
            color = if (row.direction == Direction.INCOME) FluxoColors.Income else FluxoColors.Ink,
            style = FluxoType.RowNumber
        )
    }
}
