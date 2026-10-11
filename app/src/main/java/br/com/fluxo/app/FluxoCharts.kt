package br.com.fluxo.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

/** One donut slice: [value] in cents (negatives count as zero) and its [color]. */
internal data class DonutSlice(val value: BigInteger, val color: Color)

/** Long-cents convenience constructor for [DonutSlice]. */
internal fun DonutSlice(value: Long, color: Color) = DonutSlice(BigInteger.valueOf(value), color)

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
    center: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier.clearAndSetSemantics { this.contentDescription = contentDescription; role = Role.Image },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
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
            positive.forEach { slice ->
                val sweep = chartFraction(slice.value, total) * 360f
                val drawn = max(sweep - gap, 0.5f.coerceAtMost(sweep))
                drawArc(
                    slice.color, start + (sweep - drawn) / 2f, drawn, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke)
                )
                start += sweep
            }
        }
        center()
    }
}

/**
 * Vertical bars scaled to the largest value (zero when all are zero). [highlightedIndex] uses
 * [highlightColor] and bold labels. Value labels sit above bars, axis labels below; both are
 * ellipsized to the slot width. Needs a bounded width and height from [modifier].
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
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }) {
        if (entries.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas
        val normal = chartLabelStyle(labelColor)
        val bold = chartLabelStyle(highlightLabelColor, bold = true)
        val labelHeight = measurer.measure("0", normal).size.height.toFloat()
        val pad = 3.dp.toPx()
        val topReserve = if (showValueLabels) labelHeight + pad else 0f
        val bottomReserve = if (showAxisLabels && entries.any { it.axisLabel != null }) labelHeight + pad * 1.5f else 0f
        val baseline = size.height - bottomReserve - 0.5f
        val barsHeight = (baseline - topReserve).coerceAtLeast(0f)
        val slot = size.width / entries.size
        val barWidth = slot * 0.65f
        val maxValue = entries.maxOf { it.value.max(BigInteger.ZERO) }
        val radius = CornerRadius(min(3.dp.toPx(), barWidth / 2f))
        drawLine(baselineColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())
        entries.forEachIndexed { i, entry ->
            val highlighted = i == highlightedIndex
            val h = chartFraction(entry.value, maxValue) * barsHeight
            val x = slot * i + (slot - barWidth) / 2f
            val cx = slot * i + slot / 2f
            val barHeight = if (entry.value.signum() > 0) max(h, 2.dp.toPx().coerceAtMost(barsHeight)) else 0f
            if (barHeight > 0f) {
                drawRoundRect(
                    if (highlighted) highlightColor else barColor,
                    topLeft = Offset(x, baseline - barHeight), size = Size(barWidth, barHeight),
                    cornerRadius = radius, style = Fill
                )
            }
            if (showValueLabels && entry.valueLabel != null) {
                val style = if (highlighted) bold else normal
                val top = (baseline - barHeight - labelHeight - pad / 2f).coerceAtLeast(0f)
                drawLabel(measurer, entry.valueLabel, style, cx, top, slot * 2.6f)
            }
            if (bottomReserve > 0f && entry.axisLabel != null) {
                drawLabel(measurer, entry.axisLabel, if (highlighted) bold else normal, cx, baseline + pad, slot * 1.6f)
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
    pointBorderColor: Color = FluxoColors.Petrol,
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
    Canvas(modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val a = first.max(BigInteger.ZERO)
        val b = second.max(BigInteger.ZERO)
        val total = a + b
        val r = CornerRadius(size.height / 2f)
        if (total.signum() <= 0) {
            drawRoundRect(trackColor, size = size, cornerRadius = r)
            return@Canvas
        }
        if (a.signum() == 0 || b.signum() == 0) {
            drawRoundRect(if (a.signum() > 0) firstColor else secondColor, size = size, cornerRadius = r)
            return@Canvas
        }
        val g = gap.toPx()
        val usable = (size.width - g).coerceAtLeast(0f)
        val wa = usable * chartFraction(a, total)
        drawRoundRect(firstColor, size = Size(wa, size.height), cornerRadius = r)
        drawRoundRect(secondColor, topLeft = Offset(wa + g, 0f), size = Size(usable - wa, size.height), cornerRadius = r)
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
    Canvas(modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val t = min(thickness.toPx(), size.height)
        val top = (size.height - t) / 2f
        val r = CornerRadius(t / 2f)
        val x100 = size.width / 1.2f
        drawRoundRect(trackColor, Offset(0f, top), Size(size.width, t), r)
        val ratio = budgetRatio(spent, limit)
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
