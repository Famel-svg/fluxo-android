package br.com.fluxo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.fluxo.core.BenefitCalculator
import java.time.DayOfWeek
import java.time.YearMonth

/** Bit 0 = Monday ... bit 6 = Sunday; the compact form kept in preferences and saved UI state. */
internal fun weekdayMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

internal fun weekdaysOfMask(mask: Int): Set<DayOfWeek> =
    DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

internal const val WEEKDAY_MASK_ALL = 0b1111111

private val shortNames = mapOf(
    DayOfWeek.MONDAY to "Seg", DayOfWeek.TUESDAY to "Ter", DayOfWeek.WEDNESDAY to "Qua", DayOfWeek.THURSDAY to "Qui",
    DayOfWeek.FRIDAY to "Sex", DayOfWeek.SATURDAY to "Sáb", DayOfWeek.SUNDAY to "Dom",
)
private val longNames = mapOf(
    DayOfWeek.MONDAY to "Segunda-feira", DayOfWeek.TUESDAY to "Terça-feira", DayOfWeek.WEDNESDAY to "Quarta-feira",
    DayOfWeek.THURSDAY to "Quinta-feira", DayOfWeek.FRIDAY to "Sexta-feira", DayOfWeek.SATURDAY to "Sábado",
    DayOfWeek.SUNDAY to "Domingo",
)

/** Compact schedule text: "seg–sex", "seg–qui", "seg, qua, sex" or "nenhum dia". */
internal fun weekdaysLabel(days: Set<DayOfWeek>): String {
    if (days.isEmpty()) return "nenhum dia"
    val sorted = DayOfWeek.values().filter { it in days }
    val parts = mutableListOf<String>()
    var start = 0
    while (start < sorted.size) {
        var end = start
        while (end + 1 < sorted.size && sorted[end + 1].value == sorted[end].value + 1) end++
        fun name(day: DayOfWeek) = shortNames.getValue(day).lowercase()
        if (end - start >= 2) parts += "${name(sorted[start])}–${name(sorted[end])}"
        else for (i in start..end) parts += name(sorted[i])
        start = end + 1
    }
    return parts.joinToString(", ")
}

/** "22 dias (seg–sex)" style fragment; singular for one day. */
internal fun daysCountLabel(count: Int) = if (count == 1) "1 dia" else "$count dias"

/** One live line: "Outubro de 2026: 22 dias × R$ 39,00 = R$ 858,00" (exact integer cents). */
internal fun benefitLine(month: YearMonth, weekdays: Set<DayOfWeek>, dailyCents: Long): String {
    val days = BenefitCalculator.scheduledDays(month, weekdays)
    val total = java.math.BigInteger.valueOf(dailyCents) * java.math.BigInteger.valueOf(days.toLong())
    return "${planningMonthLabel(month)}: ${daysCountLabel(days)} × ${money(dailyCents)} = ${planningMoney(total)}"
}

/**
 * Seven multi-select weekday pills (wrapping on narrow widths or large fonts). Each chip exposes checkbox
 * semantics and the tag "[tagPrefix].N" with N the ISO day number (1 = Monday).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekdayChips(
    selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit, tagPrefix: String,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        DayOfWeek.values().forEach { day ->
            val on = day in selected
            val fill = if (on) FluxoColors.Ink else FluxoColors.Paper
            val text = if (on) Color.White else FluxoColors.Ink
            Box(
                Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).testTag("$tagPrefix.${day.value}")
                    .semantics { contentDescription = longNames.getValue(day) }
                    .toggleable(value = on, enabled = enabled, role = Role.Checkbox,
                        onValueChange = { onChange(if (it) selected + day else selected - day) }),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(fill)
                        .border(2.dp, FluxoColors.Ink, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(shortNames.getValue(day), color = text, maxLines = 1,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
