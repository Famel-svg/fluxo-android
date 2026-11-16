package br.com.fluxo.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.ceil
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.min

// Money enters every chart as integer cents (Long or BigInteger). Floats appear only for geometry
// fractions derived with BigDecimal; nothing here parses or formats currency.

/** One donut slice: [value] in cents (negatives count as zero), its [color] and an optional spoken [label]. */
internal data class DonutSlice(val value: BigInteger, val color: Color, val label: String? = null)

/** Long-cents convenience constructor for [DonutSlice]. */
internal fun DonutSlice(value: Long, color: Color, label: String? = null) = DonutSlice(BigInteger.valueOf(value), color, label)

/**
 * One vertical bar: [value] in cents, optional [axisLabel] under the bar and caller-formatted
 * [valueLabel] above it (the chart never formats money itself).
 */
internal data class BarEntry(val value: BigInteger, val axisLabel: String? = null, val valueLabel: String? = null)

/** Long-cents convenience constructor for [BarEntry]. */
internal fun BarEntry(value: Long, axisLabel: String? = null, valueLabel: String? = null) =
    BarEntry(BigInteger.valueOf(value), axisLabel, valueLabel)

/** Converts a list of Long cents to the BigInteger list taken by [BarChart] and [Sparkline]. */
internal fun Iterable<Long>.asCents(): List<BigInteger> = map { BigInteger.valueOf(it) }

/** part/total as a 0..1 geometry fraction; 0 when total or part is not positive. Never divides by zero. */
internal fun chartFraction(part: BigInteger, total: BigInteger): Float {
    if (total.signum() <= 0 || part.signum() <= 0) return 0f
    return BigDecimal(part).divide(BigDecimal(total), 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
}

/** spent/limit as a ratio clamped to 0..[cap]; 0 when [limit] is not positive. */
internal fun budgetRatio(spent: BigInteger, limit: BigInteger, cap: Float = 1.2f): Float {
    if (limit.signum() <= 0 || spent.signum() <= 0) return 0f
    return BigDecimal(spent).divide(BigDecimal(limit), 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, cap)
}

/**
 * 0..1 entry progress played once per composition over [durationMs] (FastOutSlowIn unless [easing] is overridden);
 * already 1 when the system "remove animations" setting is on. Read `.value` in draw blocks only.
 */
@Composable
private fun rememberEntryProgress(durationMs: Int): Animatable<Float, *> {
    val enabled = rememberAnimationsEnabled()
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return progress
}

private fun chartLabelStyle(color: Color, bold: Boolean = false) = TextStyle(
    fontFamily = FluxoType.Body, fontSize = 10.sp, color = color,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFeatureSettings = "tnum"
)

private fun DrawScope.drawLabel(
    measurer: TextMeasurer, text: String, style: TextStyle, centerX: Float, top: Float, maxWidth: Float
) {
    if (maxWidth < 1f) return
    val layout = measurer.measure(
        text, style, overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1))
    )
    drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, top))
}

/**
 * Donut: slices drawn clockwise from 12 o'clock proportional to value; an empty or all-zero list
 * draws only the [trackColor] ring. [center] is laid over the hole. The size comes from
 * [modifier]. [contentDescription] replaces everything inside for accessibility, so it must
 * also state what [center] shows.
 */
@Composable
internal fun DonutChart(
    slices: List<DonutSlice>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    trackColor: Color = FluxoColors.Pale,
    sliceGapDegrees: Float = 1.5f,
    onSliceClick: ((Int) -> Unit)? = null,
    center: @Composable BoxScope.() -> Unit = {}
) {
    val entry = rememberEntryProgress(700)
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val slop = with(LocalDensity.current) { 12.dp.toPx() }
    Box(
        modifier.clearAndSetSemantics {
            this.contentDescription = contentDescription; role = Role.Image
            if (onSliceClick != null) customActions = slices.mapIndexedNotNull { i, s ->
                if (s.value.signum() <= 0) null
                else CustomAccessibilityAction(s.label ?: "Fatia ${i + 1}") { onSliceClick(i); true }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        val tap = if (onSliceClick == null) Modifier else Modifier.pointerInput(slices, strokePx) {
            detectTapGestures { off -> donutSliceAt(slices, off, size, strokePx, slop)?.let(onSliceClick) }
        }
        Canvas(Modifier.fillMaxSize().then(tap)) {
            // Sweep reveals slices clockwise: only the first `limit` degrees of the ring are drawn.
            val limit = FastOutSlowInEasing.transform(entry.value) * 360f
            val stroke = strokeWidth.toPx()
            val diameter = min(size.width, size.height) - stroke
            if (diameter <= 0f) return@Canvas
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            val positive = slices.filter { it.value.signum() > 0 }
            val total = positive.fold(BigInteger.ZERO) { acc, s -> acc + s.value }
            if (total.signum() <= 0) return@Canvas
            val gap = if (positive.size > 1) sliceGapDegrees else 0f
            var start = -90f
            var done = 0f
            positive.forEach { slice ->
                val sweep = chartFraction(slice.value, total) * 360f
                val drawn = max(sweep - gap, 0.5f.coerceAtMost(sweep))
                val shown = min(drawn, (limit - done - (sweep - drawn) / 2f).coerceAtLeast(0f))
                if (shown > 0f) drawArc(
                    slice.color, start + (sweep - drawn) / 2f, shown, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke)
                )
                start += sweep
                done += sweep
            }
        }
        center()
    }
}

/**
 * Index in [slices] of the slice under [offset] (ring of [strokePx] centred in [size], widened by [slop]), or null.
 * Angles run clockwise from 12 o'clock, matching the drawing; zero/negative slices are skipped.
 */
internal fun donutSliceAt(slices: List<DonutSlice>, offset: Offset, size: IntSize, strokePx: Float, slop: Float): Int? {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val mid = (min(size.width, size.height) - strokePx) / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val dist = hypot(dx, dy)
    if (mid <= 0f || dist < mid - strokePx / 2f - slop || dist > mid + strokePx / 2f + slop) return null
    val total = slices.fold(BigInteger.ZERO) { acc, s -> acc + s.value.max(BigInteger.ZERO) }
    if (total.signum() <= 0) return null
    val angle = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f) % 360f + 360f) % 360f
    var end = 0f
    var last: Int? = null
    slices.forEachIndexed { i, s ->
        if (s.value.signum() <= 0) return@forEachIndexed
        end += chartFraction(s.value, total) * 360f
        last = i
        if (angle < end) return i
    }
    return last
}

/**
 * Vertical bars scaled to the largest value (zero when all are zero). [highlightedIndex] uses
 * [highlightColor] and bold labels. Value labels sit above bars, axis labels below; both are
 * ellipsized to the slot width. [labelAll] labels every bar (value and axis) within its own slot width so
 * neighbours never overlap, even without [showValueLabels]. Needs a bounded width and height from [modifier].
 */
@Composable
internal fun BarChart(
    entries: List<BarEntry>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highlightedIndex: Int? = null,
    showValueLabels: Boolean = false,
    showAxisLabels: Boolean = true,
    barColor: Color = FluxoColors.PetrolMid,
    highlightColor: Color = FluxoColors.Amber,
    baselineColor: Color = FluxoColors.Line,
    labelColor: Color = FluxoColors.Mute,
    highlightLabelColor: Color = FluxoColors.Ink,
    labelAll: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val stagger = 40
    val entry = rememberEntryProgress(500 + stagger * (entries.size - 1).coerceAtLeast(0))
    Canvas(modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }) {
        if (entries.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val totalMs = 500f + stagger * (entries.size - 1)
        val normal = chartLabelStyle(labelColor)
        val bold = chartLabelStyle(highlightLabelColor, bold = true)
        val labelHeight = measurer.measure("0", normal).size.height.toFloat()
        val pad = 3.dp.toPx()
        val topReserve = if (showValueLabels || labelAll) labelHeight + pad else 0f
        val bottomReserve = if (showAxisLabels && entries.any { it.axisLabel != null }) labelHeight + pad * 1.5f else 0f
        val baseline = size.height - bottomReserve - 0.5f
        val barsHeight = (baseline - topReserve).coerceAtLeast(0f)
        val slot = size.width / entries.size
        val barWidth = slot * 0.65f
        val maxValue = entries.maxOf { it.value.max(BigInteger.ZERO) }
        val radius = CornerRadius(min(3.dp.toPx(), barWidth / 2f))
        drawLine(baselineColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())
        entries.forEachIndexed { i, bar ->
            val highlighted = i == highlightedIndex
            val h = chartFraction(bar.value, maxValue) * barsHeight
            val x = slot * i + (slot - barWidth) / 2f
            val cx = slot * i + slot / 2f
            val grow = FastOutSlowInEasing.transform(((entry.value * totalMs - stagger * i) / 500f).coerceIn(0f, 1f))
            val barHeight = (if (bar.value.signum() > 0) max(h, 2.dp.toPx().coerceAtMost(barsHeight)) else 0f) * grow
            if (barHeight > 0f) {
                drawRoundRect(
                    if (highlighted) highlightColor else barColor,
                    topLeft = Offset(x, baseline - barHeight), size = Size(barWidth, barHeight),
                    cornerRadius = radius, style = Fill
                )
            }
            if ((showValueLabels || labelAll) && bar.valueLabel != null && grow >= 1f) {
                val style = if (highlighted) bold else normal
                val top = (baseline - barHeight - labelHeight - pad / 2f).coerceAtLeast(0f)
                drawLabel(measurer, bar.valueLabel, style, cx, top, if (labelAll) slot else slot * 2.6f)
            }
            if (bottomReserve > 0f && bar.axisLabel != null) {
                drawLabel(measurer, bar.axisLabel, if (highlighted) bold else normal, cx, baseline + pad, if (labelAll) slot else slot * 1.6f)
            }
        }
    }
}

/**
 * Polyline over the zero-based range of [values] with an area fill and an emphasised last point.
 * Defaults suit the petrol hero block. Empty data draws nothing; all-zero data draws a flat line.
 */
@Composable
internal fun Sparkline(
    values: List<BigInteger>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    lineColor: Color = FluxoColors.OnPetrolSoft,
    fillColor: Color? = FluxoColors.PetrolMid,
    pointColor: Color = FluxoColors.Amber,
    pointBorderColor: Color = FluxoColors.Hero,
    strokeWidth: Dp = 2.dp,
    pointRadius: Dp = 3.dp,
) {
    Canvas(modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }) {
        if (values.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val inset = pointRadius.toPx() + 1.dp.toPx()
        val w = (size.width - 2 * inset).coerceAtLeast(0f)
        val h = (size.height - 2 * inset).coerceAtLeast(0f)
        val maxValue = values.maxOf { it.max(BigInteger.ZERO) }
        val points = values.mapIndexed { i, v ->
            val x = if (values.size == 1) size.width / 2f else inset + w * i / (values.size - 1)
            Offset(x, inset + h * (1f - chartFraction(v, maxValue)))
        }
        val line = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
        if (fillColor != null && points.size > 1) {
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, inset + h); lineTo(points.first().x, inset + h); close()
            }
            drawPath(area, fillColor, style = Fill)
        }
        if (points.size > 1) {
            drawPath(line, lineColor, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val last = points.last()
        drawCircle(pointColor, pointRadius.toPx(), last, style = Fill)
        drawCircle(pointBorderColor, pointRadius.toPx(), last, style = Stroke(1.dp.toPx()))
    }
}

/**
 * Two-part proportional capsule: [first] then [second] (cents), separated by a small gap.
 * A zero part is omitted; both zero draws an empty [trackColor] capsule. Height comes from [modifier].
 */
@Composable
internal fun SplitBar(
    first: BigInteger,
    second: BigInteger,
    contentDescription: String,
    modifier: Modifier = Modifier,
    firstColor: Color = FluxoColors.Petrol,
    secondColor: Color = FluxoColors.Mute,
    trackColor: Color = FluxoColors.Soft,
    gap: Dp = 1.5.dp,
) {
    val entry = rememberEntryProgress(500)
    Canvas(modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val reveal = FastOutSlowInEasing.transform(entry.value)
        val a = first.max(BigInteger.ZERO)
        val b = second.max(BigInteger.ZERO)
        val total = a + b
        val r = CornerRadius(size.height / 2f)
        if (total.signum() <= 0) {
            drawRoundRect(trackColor, size = size, cornerRadius = r)
            return@Canvas
        }
        // Width animates from the start edge: the capsule is revealed by clipping to `reveal` of its width.
        drawRoundRect(trackColor, size = size, cornerRadius = r)
        clipRect(right = size.width * reveal) {
            if (a.signum() == 0 || b.signum() == 0) {
                drawRoundRect(if (a.signum() > 0) firstColor else secondColor, size = size, cornerRadius = r)
                return@clipRect
            }
            val g = gap.toPx()
            val usable = (size.width - g).coerceAtLeast(0f)
            val wa = usable * chartFraction(a, total)
            drawRoundRect(firstColor, size = Size(wa, size.height), cornerRadius = r)
            drawRoundRect(secondColor, topLeft = Offset(wa + g, 0f), size = Size(usable - wa, size.height), cornerRadius = r)
        }
    }
}

/** Long-cents overload of [SplitBar]. */
@Composable
internal fun SplitBar(
    first: Long,
    second: Long,
    contentDescription: String,
    modifier: Modifier = Modifier,
    firstColor: Color = FluxoColors.Petrol,
    secondColor: Color = FluxoColors.Mute,
    trackColor: Color = FluxoColors.Soft,
    gap: Dp = 1.5.dp,
) = SplitBar(
    BigInteger.valueOf(first), BigInteger.valueOf(second), contentDescription, modifier,
    firstColor, secondColor, trackColor, gap
)

/**
 * Budget progress on a 0-120% scale: the track is 120% wide, a tick marks 100% of [limit], and
 * spending beyond the limit is drawn in the expense colour up to 120%. A non-positive [limit]
 * draws only the track and tick. Height comes from [modifier]; the bar is 8dp thick (or less).
 */
@Composable
internal fun BudgetBar(
    spent: BigInteger,
    limit: BigInteger,
    contentDescription: String,
    modifier: Modifier = Modifier,
    barColor: Color = FluxoColors.Petrol,
    overflowColor: Color = FluxoColors.Expense,
    trackColor: Color = FluxoColors.Soft,
    markerColor: Color = FluxoColors.Ink,
    thickness: Dp = 8.dp,
) {
    // The bar eases to a new ratio when the data changes; the first composition draws the target directly.
    val ratio by animateFloatAsState(budgetRatio(spent, limit), tween(300, easing = FastOutSlowInEasing), label = "budgetRatio")
    Canvas(modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val t = min(thickness.toPx(), size.height)
        val top = (size.height - t) / 2f
        val r = CornerRadius(t / 2f)
        val x100 = size.width / 1.2f
        drawRoundRect(trackColor, Offset(0f, top), Size(size.width, t), r)
        if (ratio > 0f) {
            val within = x100 * min(ratio, 1f)
            if (ratio > 1f) {
                val end = size.width * ratio / 1.2f
                drawRoundRect(overflowColor, Offset(x100 - t / 2f, top), Size(end - x100 + t / 2f, t), r)
            }
            drawRoundRect(barColor, Offset(0f, top), Size(within, t), r)
            if (ratio > 1f) drawRect(barColor, Offset(within - t / 2f, top), Size(t / 2f, t))
        }
        val m = 2.dp.toPx()
        drawRoundRect(markerColor, Offset(x100 - m / 2f, 0f), Size(m, size.height), CornerRadius(m / 2f))
    }
}

/** Long-cents overload of [BudgetBar]. */
@Composable
internal fun BudgetBar(
    spentCents: Long,
    limitCents: Long,
    contentDescription: String,
    modifier: Modifier = Modifier,
    barColor: Color = FluxoColors.Petrol,
    overflowColor: Color = FluxoColors.Expense,
    trackColor: Color = FluxoColors.Soft,
    markerColor: Color = FluxoColors.Ink,
    thickness: Dp = 8.dp,
) = BudgetBar(
    BigInteger.valueOf(spentCents), BigInteger.valueOf(limitCents), contentDescription, modifier,
    barColor, overflowColor, trackColor, markerColor, thickness
)

// ---------------------------------------------------------------------------------------------
// Pace, cash flow, trend, heatmap and bill calendar
// ---------------------------------------------------------------------------------------------

/** "3,4 mil" / "1,2 mi" for |cents| >= R$ 1.000, plain reais below; for tight labels only. */
internal fun compactMoney(cents: Long): String {
    val reais = BigDecimal.valueOf(cents, 2)
    val (div, suffix) = when {
        reais.abs() >= BigDecimal(1_000_000) -> BigDecimal(1_000_000) to " mi"
        reais.abs() >= BigDecimal(1_000) -> BigDecimal(1_000) to " mil"
        else -> return reais.setScale(0, RoundingMode.HALF_UP).toPlainString()
    }
    val text = reais.divide(div, 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',').removeSuffix(",0")
    return text + suffix
}

/** Budget pace: fill = [spentCents] of [budgetCents]; a tick marks [expectedByTodayCents]. Over the tick = Expense, else Income. */
@Composable
internal fun PaceBar(spentCents: Long, expectedByTodayCents: Long, budgetCents: Long, modifier: Modifier = Modifier) {
    val entry = rememberEntryProgress(500)
    val over = spentCents > expectedByTodayCents
    val desc = "Gasto ${money(spentCents)} de ${money(budgetCents)}. Esperado até hoje ${money(expectedByTodayCents)}. " +
        if (over) "Acima do ritmo." else "Dentro do ritmo."
    Canvas(modifier.semantics { contentDescription = desc; role = Role.Image }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val t = min(8.dp.toPx(), size.height)
        val top = (size.height - t) / 2f
        val r = CornerRadius(t / 2f)
        drawRoundRect(FluxoColors.Soft, Offset(0f, top), Size(size.width, t), r)
        val budget = BigInteger.valueOf(budgetCents)
        val fill = size.width * chartFraction(BigInteger.valueOf(spentCents), budget) * FastOutSlowInEasing.transform(entry.value)
        if (fill > 0f) drawRoundRect(if (over) FluxoColors.Expense else FluxoColors.Income, Offset(0f, top), Size(max(fill, t), t), r)
        if (budgetCents > 0 && expectedByTodayCents > 0) {
            val x = (size.width * chartFraction(BigInteger.valueOf(expectedByTodayCents), budget)).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
            drawRect(FluxoColors.Paper, Offset(x - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height))
            drawRoundRect(FluxoColors.Ink, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height), CornerRadius(1.dp.toPx()))
        }
    }
}

/**
 * Per period: income and expense bars side by side from the zero line plus a line for the net (income - expense,
 * may go below zero). Lists are cut to the shortest of the three; [labels] sit under the bars (every other one when
 * crowded). The tallest bar is labelled with its value ([money], or "3,4 mil" when it does not fit).
 */
@Composable
internal fun CashflowChart(
    labels: List<String>, incomeCents: List<Long>, expenseCents: List<Long>, modifier: Modifier = Modifier
) {
    val n = minOf(labels.size, incomeCents.size, expenseCents.size)
    val measurer = rememberTextMeasurer()
    val entry = rememberEntryProgress(600)
    val desc = if (n == 0) "Fluxo de caixa sem dados" else "Fluxo de caixa: " + (0 until n).joinToString("; ") { i ->
        "${labels[i]}, entrou ${money(incomeCents[i])}, saiu ${money(expenseCents[i])}, saldo ${money(incomeCents[i] - expenseCents[i])}"
    }
    Canvas(modifier.semantics { contentDescription = desc; role = Role.Image }) {
        if (n == 0 || size.width <= 0f || size.height <= 0f) return@Canvas
        val style = chartLabelStyle(FluxoColors.Mute)
        val lh = measurer.measure("0", style).size.height.toFloat()
        val pad = 3.dp.toPx()
        val nets = List(n) { incomeCents[it].toDouble() - expenseCents[it].toDouble() }
        val peak = (0 until n).maxOf { max(incomeCents[it], expenseCents[it]) }.coerceAtLeast(0)
        val hi = max(peak.toDouble(), nets.max()).coerceAtLeast(0.0)
        val lo = min(0.0, nets.min())
        val range = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        val plotTop = lh + pad * 2
        val plotBottom = size.height - lh - pad * 1.5f
        if (plotBottom <= plotTop) return@Canvas
        fun y(v: Double) = plotTop + (plotBottom - plotTop) * (1f - ((v - lo) / range).toFloat())
        val zero = y(0.0)
        val grow = FastOutSlowInEasing.transform(entry.value)
        val slot = size.width / n
        val bar = slot * 0.74f / 2f - 1.dp.toPx()
        val radius = CornerRadius(min(2.dp.toPx(), bar / 2f))
        drawLine(FluxoColors.Line, Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
        val step = if (slot < 30.dp.toPx()) 2 else 1
        for (i in 0 until n) {
            val cx = slot * i + slot / 2f
            listOf(incomeCents[i] to FluxoColors.Income, expenseCents[i] to FluxoColors.Expense).forEachIndexed { k, (v, c) ->
                val h = (zero - y(v.coerceAtLeast(0).toDouble())) * grow
                if (h > 0f) {
                    val x = if (k == 0) cx - 1.dp.toPx() - bar else cx + 1.dp.toPx()
                    drawRoundRect(c, Offset(x, zero - max(h, 2.dp.toPx())), Size(bar, max(h, 2.dp.toPx())), radius)
                }
            }
            if (i % step == 0) drawLabel(measurer, labels[i], style, cx, plotBottom + pad, slot * step)
        }
        if (grow >= 1f && peak > 0) {
            val full = money(peak)
            val text = if (measurer.measure(full, style).size.width <= size.width) full else compactMoney(peak)
            drawLabel(measurer, text, style, size.width / 2f, 0f, size.width)
        }
        val pts = nets.mapIndexed { i, v -> Offset(slot * i + slot / 2f, zero + (y(v) - zero) * grow) }
        if (pts.size > 1) {
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, FluxoColors.Ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        pts.forEach {
            drawCircle(FluxoColors.Paper, 3.5.dp.toPx(), it)
            drawCircle(FluxoColors.Ink, 3.5.dp.toPx(), it, style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** Label-free line over the min..max range of [values] (cents) with an emphasised last point; revealed left to right. */
@Composable
internal fun MiniTrend(values: List<Long>, modifier: Modifier = Modifier) {
    val entry = rememberEntryProgress(500)
    val desc = when {
        values.isEmpty() -> "Tendência sem dados"
        values.size == 1 -> "Tendência: ${money(values[0])}"
        else -> "Tendência de ${money(values.first())} para ${money(values.last())}, " +
            when { values.last() > values.first() -> "em alta"; values.last() < values.first() -> "em queda"; else -> "estável" }
    }
    Canvas(modifier.semantics { contentDescription = desc; role = Role.Image }) {
        if (values.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val r = 3.dp.toPx()
        val w = (size.width - 2 * r).coerceAtLeast(0f)
        val h = (size.height - 2 * r).coerceAtLeast(0f)
        val lo = values.min().toDouble()
        val range = (values.max().toDouble() - lo).takeIf { it > 0.0 } ?: 1.0
        val pts = values.mapIndexed { i, v ->
            Offset(if (values.size == 1) size.width / 2f else r + w * i / (values.size - 1), r + h * (1f - ((v - lo) / range).toFloat()))
        }
        clipRect(right = size.width * FastOutSlowInEasing.transform(entry.value)) {
            if (pts.size > 1) {
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, FluxoColors.Petrol, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (entry.value >= 1f) {
            drawCircle(FluxoColors.Amber, r, pts.last())
            drawCircle(FluxoColors.Ink, r, pts.last(), style = Stroke(1.dp.toPx()))
        }
    }
}

private val weekdayInitials = listOf("D", "S", "T", "Q", "Q", "S", "S")

/** Sunday-first month grid: blanks before day 1 and number of week rows. */
private fun monthLead(month: YearMonth) = month.atDay(1).dayOfWeek.value % 7
private fun monthRows(month: YearMonth) = ceil((monthLead(month) + month.lengthOfMonth()) / 7.0).toInt()

/** Draws the weekday header and calls [cell] with each day's rect. The header takes 0.7 of a cell height. */
private fun DrawScope.drawMonthGrid(month: YearMonth, measurer: TextMeasurer, cell: DrawScope.(LocalDate, Rect) -> Unit) {
    val cw = size.width / 7f
    val headerH = cw * 0.7f
    val header = chartLabelStyle(FluxoColors.Mute, bold = true)
    weekdayInitials.forEachIndexed { i, d ->
        drawLabel(measurer, d, header, cw * i + cw / 2f, (headerH - measurer.measure(d, header).size.height) / 2f, cw)
    }
    val lead = monthLead(month)
    val gap = 1.5.dp.toPx()
    for (day in 1..month.lengthOfMonth()) {
        val idx = lead + day - 1
        val left = cw * (idx % 7)
        val top = headerH + cw * (idx / 7)
        cell(month.atDay(day), Rect(left + gap, top + gap, left + cw - gap, top + cw - gap))
    }
}

private fun DrawScope.drawDayNumber(
    measurer: TextMeasurer, day: Int, rect: Rect, color: Color, bold: Boolean = false, yFrac: Float = 0.5f
) {
    val style = chartLabelStyle(color, bold).copy(fontSize = 11.sp)
    val h = measurer.measure(day.toString(), style).size.height
    drawLabel(measurer, day.toString(), style, rect.center.x, rect.top + (rect.height - h) * yFrac, rect.width)
}

private fun monthTitle(month: YearMonth) = "${month.month.getDisplayName(DateTextStyle.FULL, ptBr)} de ${month.year}"

/**
 * Month grid (Sunday first) shaded by [values] (cents per day, typically spending): level 0 is an empty cell, levels 1-4
 * grow in opacity relative to the largest day, and every used day has a petrol outline so the mark never depends on shade
 * alone. Tapping a day of the month calls [onDayClick]; days with values also expose accessibility actions.
 */
@Composable
internal fun DailyHeatmap(
    month: YearMonth, values: Map<LocalDate, Long>, onDayClick: (LocalDate) -> Unit, modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val entry = rememberEntryProgress(400)
    val used = values.filter { (d, v) -> YearMonth.from(d) == month && v > 0 }
    val top = used.values.maxOrNull() ?: 0L
    val desc = "Mapa de calor de ${monthTitle(month)}: " + if (used.isEmpty()) "sem movimento." else
        "${used.size} dias com valor, maior dia ${used.maxBy { it.value }.key.dayOfMonth} com ${money(top)}."
    val lead = monthLead(month)
    Canvas(
        modifier.aspectRatio(7f / (monthRows(month) + 0.7f))
            .pointerInput(month) {
                detectTapGestures { o ->
                    val cw = size.width / 7f
                    val day = ((o.y - cw * 0.7f) / cw).toInt() * 7 + (o.x / cw).toInt() - lead + 1
                    if (o.y >= cw * 0.7f && day in 1..month.lengthOfMonth()) onDayClick(month.atDay(day))
                }
            }
            .semantics {
                contentDescription = desc; role = Role.Image
                customActions = used.keys.sorted().map { d ->
                    CustomAccessibilityAction("Dia ${d.dayOfMonth}, ${money(used.getValue(d))}") { onDayClick(d); true }
                }
            }
    ) {
        if (size.width <= 0f) return@Canvas
        val fade = entry.value
        drawMonthGrid(month, measurer) { date, rect ->
            val v = used[date] ?: 0L
            val level = if (v <= 0L || top <= 0L) 0 else 1 + (3.0 * v / top).toInt().coerceAtMost(3)
            val corner = CornerRadius(4.dp.toPx())
            if (level == 0) {
                drawRoundRect(FluxoColors.Soft.copy(alpha = fade), rect.topLeft, rect.size, corner)
            } else {
                val a = listOf(0f, 0.3f, 0.5f, 0.75f, 1f)[level] * fade
                drawRoundRect(FluxoColors.Petrol.copy(alpha = a), rect.topLeft, rect.size, corner)
                drawRoundRect(FluxoColors.Petrol.copy(alpha = fade), rect.topLeft, rect.size, corner, style = Stroke(1.dp.toPx()))
            }
            drawDayNumber(measurer, date.dayOfMonth, rect, if (level >= 3) FluxoColors.OnPetrol else FluxoColors.Ink, level > 0)
        }
    }
}

/**
 * Month grid (Sunday first) for bills: each day in [marks] (labels of what falls due) is outlined in petrol and shows up to
 * three dots plus "+" when there are more. Informational: no touch handling; the description lists every day and its items.
 */
@Composable
internal fun BillCalendar(month: YearMonth, marks: Map<LocalDate, List<String>>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val entry = rememberEntryProgress(400)
    val due = marks.filter { (d, l) -> YearMonth.from(d) == month && l.isNotEmpty() }
    val desc = "Calendário de contas de ${monthTitle(month)}: " + if (due.isEmpty()) "nenhuma conta." else
        due.entries.sortedBy { it.key }.joinToString("; ") { (d, l) -> "dia ${d.dayOfMonth}, ${l.joinToString(", ")}" }
    Canvas(modifier.aspectRatio(7f / (monthRows(month) + 0.7f)).semantics { contentDescription = desc; role = Role.Image }) {
        if (size.width <= 0f) return@Canvas
        val fade = entry.value
        drawMonthGrid(month, measurer) { date, rect ->
            val items = due[date]?.size ?: 0
            val corner = CornerRadius(4.dp.toPx())
            if (items == 0) {
                drawRoundRect(FluxoColors.Soft.copy(alpha = fade), rect.topLeft, rect.size, corner)
                drawDayNumber(measurer, date.dayOfMonth, rect, FluxoColors.Mute)
                return@drawMonthGrid
            }
            drawRoundRect(FluxoColors.PetrolSoft.copy(alpha = fade), rect.topLeft, rect.size, corner)
            drawRoundRect(FluxoColors.Petrol.copy(alpha = fade), rect.topLeft, rect.size, corner, style = Stroke(1.dp.toPx()))
            drawDayNumber(measurer, date.dayOfMonth, rect, FluxoColors.Ink, bold = true, yFrac = 0.2f)
            val r = 2.dp.toPx()
            val dots = min(items, 3)
            val startX = rect.center.x - (dots - 1) * r * 1.5f
            val dy = rect.bottom - r * 2.5f
            repeat(dots) { drawCircle(FluxoColors.Petrol.copy(alpha = fade), r, Offset(startX + it * r * 3f, dy)) }
            if (items > 3) drawLabel(measurer, "+", chartLabelStyle(FluxoColors.Petrol, true), startX + 3 * r * 3f, dy - r * 2.2f, r * 6f)
        }
    }
}
