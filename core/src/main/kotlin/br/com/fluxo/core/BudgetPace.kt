package br.com.fluxo.core

import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class PaceStatus { NO_BUDGET, UNDER, ON_TRACK, OVER }

data class Pace(
    val spentCents: Long, val budgetCents: Long, val expectedByTodayCents: Long,
    val projectedEndCents: Long, val status: PaceStatus,
)

/** Category whose confirmed spending reached 80% of its monthly limit. */
data class CategoryOverrun(val category: Category, val spentCents: Long, val limitCents: Long)

object BudgetPace {
    private fun big(v: Long) = BigInteger.valueOf(v)

    private fun scale(value: Long, num: Long, den: Long): Long =
        big(value).multiply(big(num)).divide(big(den)).min(big(Long.MAX_VALUE)).toLong()

    /**
     * Linear pace by day of month. Status: OVER when spent passed the budget or is more than 10% above the expected
     * amount, UNDER when more than 10% below, otherwise ON_TRACK. [fixedUpcomingCents] is added to the projection.
     */
    fun monthPace(
        spentCents: Long, budgetCents: Long, month: YearMonth, today: LocalDate,
        fixedUpcomingCents: Long = 0, fixedPaidCents: Long = 0,
    ): Pace {
        val length = month.lengthOfMonth().toLong()
        val day = when {
            YearMonth.from(today) == month -> today.dayOfMonth.toLong()
            today.isBefore(month.atDay(1)) -> 0L
            else -> length
        }
        val spent = spentCents.coerceAtLeast(0)
        val expected = if (budgetCents > 0) scale(budgetCents, day, length) else 0L
        val fixed = fixedUpcomingCents.coerceAtLeast(0)
        // Only variable spending is extrapolated: fixed bills already paid count once, fixed bills still due are added once.
        val paid = fixedPaidCents.coerceIn(0, spent)
        val variable = spent - paid
        val rest = if (day == 0L) 0L else scale(variable, length - day, day)
        val projected = Math.addExact(Math.addExact(spent, rest), fixed)
        val status = when {
            budgetCents <= 0 -> PaceStatus.NO_BUDGET
            spent > budgetCents || big(spent).multiply(big(100)) > big(expected).multiply(big(110)) -> PaceStatus.OVER
            big(spent).multiply(big(100)) < big(expected).multiply(big(90)) -> PaceStatus.UNDER
            else -> PaceStatus.ON_TRACK
        }
        return Pace(spent, budgetCents, expected, projected, status)
    }

    /** Categories at or above 80% of their limit in [month] (all confirmed consumption), highest ratio first. */
    fun overruns(rows: List<Transaction>, budgets: List<Budget>, month: YearMonth, zone: ZoneId): List<CategoryOverrun> {
        val spent = HashMap<Category, Long>()
        rows.forEach {
            if (it.countableExpense() && YearMonth.from(it.localDate(zone)) == month) {
                spent[it.category] = Math.addExact(spent[it.category] ?: 0L, it.amountCents!!)
            }
        }
        return budgets.filter { it.month == month.toString() && it.limitCents > 0 }
            .map { CategoryOverrun(it.category, spent[it.category] ?: 0L, it.limitCents) }
            .filter { big(it.spentCents).multiply(big(100)) >= big(it.limitCents).multiply(big(80)) }
            .sortedByDescending { it.spentCents.toDouble() / it.limitCents }
    }
}
