package br.com.fluxo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.fluxo.core.BenefitUsage
import java.math.BigDecimal
import java.math.BigInteger

private const val HIDDEN_VALUE = "••••"

private fun brl(value: BigInteger): String = money(BigDecimal(value, 2))

private fun daysText(days: Int) = if (days == 1) "1 dia" else "$days dias"

/**
 * "Vale-refeição" block of Início: remaining estimate as the emphasised number, thin progress bar and a caption.
 * It is an estimate for the selected month only (no carry-over) and never enters cash totals.
 */
@Composable
internal fun BenefitBalanceBlock(
    usage: BenefitUsage, days: Int, dailyCents: Long, hidden: Boolean, modifier: Modifier = Modifier
) {
    val over = usage.overspent
    val overAmount = usage.remainingCents.abs()
    fun v(value: BigInteger) = if (hidden) HIDDEN_VALUE else brl(value)
    val spoken = if (hidden) "Vale-refeição: valores ocultos" else {
        (if (over) "Vale-refeição: ${brl(overAmount)} acima da estimativa" else "Vale-refeição disponível: ${brl(usage.remainingCents)}") +
            " de ${brl(usage.depositCents)} no mês, gasto ${brl(usage.spentCents)}. Estimativa do mês, sem saldo de meses anteriores."
    }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).testTag("dashboard.vr")
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (over) "VR" else "VR disponível", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
                val big = FluxoType.number(22.sp)
                val shown = if (hidden) AnnotatedString(HIDDEN_VALUE) else if (over) buildAnnotatedString {
                    withStyle(SpanStyle(color = FluxoColors.Expense)) { append(brl(overAmount)) }
                    withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = FluxoColors.Expense)) { append(" acima da estimativa") }
                } else AnnotatedString(brl(usage.remainingCents))
                Crossfade(shown, animationSpec = tween(200), label = "vrRemaining") { text ->
                    Text(text, style = big, modifier = Modifier.testTag("dashboard.vrRemaining"))
                }
            }
            Text(
                "de ${v(usage.depositCents)} no mês", color = FluxoColors.Mute, style = FluxoType.Caption,
                textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 140.dp).padding(bottom = 3.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(FluxoColors.Soft)) {
            val target = if (over) 1f else chartFraction(usage.spentCents, usage.depositCents)
            val fraction by animateFloatAsState(target, tween(300, easing = FastOutSlowInEasing), label = "vrFraction")
            if (fraction > 0f) Box(
                Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                    .background(if (over) FluxoColors.Expense else FluxoColors.Petrol)
            )
        }
        // Two parts so a narrow screen wraps between them instead of leaving an orphaned amount on the second line.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Estimativa: ${daysText(days)} × ${v(BigInteger.valueOf(dailyCents))}",
                color = FluxoColors.Mute, style = FluxoType.Caption, modifier = Modifier.weight(1f)
            )
            Text("gasto ${v(usage.spentCents)}", color = FluxoColors.Mute, style = FluxoType.Caption, textAlign = TextAlign.End)
        }
    }
}

/** One-line VT equivalent shown under the VR block; smaller emphasis, same logic. */
@Composable
internal fun TransportBalanceRow(usage: BenefitUsage, hidden: Boolean, modifier: Modifier = Modifier) {
    val over = usage.overspent
    val overAmount = usage.remainingCents.abs()
    fun v(value: BigInteger) = if (hidden) HIDDEN_VALUE else brl(value)
    val spoken = if (hidden) "Vale-transporte: valores ocultos" else {
        (if (over) "Vale-transporte: ${brl(overAmount)} acima da estimativa" else "Vale-transporte disponível: ${brl(usage.remainingCents)}") +
            " de ${brl(usage.depositCents)} no mês. Estimativa do mês."
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("dashboard.vt")
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("VT disponível", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp))
        Text("de ${v(usage.depositCents)}", color = FluxoColors.Mute, style = FluxoType.Caption)
        Text(
            if (hidden) HIDDEN_VALUE else if (over) "${brl(overAmount)} acima da estimativa" else brl(usage.remainingCents),
            color = if (over && !hidden) FluxoColors.Expense else FluxoColors.Ink,
            style = FluxoType.number(14.sp)
        )
    }
}
