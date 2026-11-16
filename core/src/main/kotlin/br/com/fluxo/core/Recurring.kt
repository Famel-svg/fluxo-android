package br.com.fluxo.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.math.BigInteger
import kotlin.math.abs

data class RecurringCharge(
    val key: String, val label: String, val category: Category, val amountCents: Long,
    val dayOfMonth: Int, val lastSeen: LocalDate, val nextDue: LocalDate, val occurrences: Int, val paidThisMonth: Boolean,
    val affectsCash: Boolean = true,
)

object RecurringDetector {
    private const val AMOUNT_TOLERANCE_PERCENT = 15L
    private const val DAY_TOLERANCE = 4

    /** Circular distance between two days of a month of [len] days (30 and 2 of a 30-day month are 2 apart). */
    private fun dayDistance(a: Int, b: Int, len: Int): Int = abs(a - b).let { minOf(it, len - it) }

    /**
     * Same normalized description, confirmed expense in at least 2 of the last 3 calendar months (current included),
     * amount within 15% and day of month within 4 of the latest occurrence, which sets the reference.
     */
    fun detect(rows: List<Transaction>, today: LocalDate, zone: ZoneId): List<RecurringCharge> {
        val thisMonth = YearMonth.from(today)
        val firstMonth = thisMonth.minusMonths(2)
        val groups = rows.filter {
            val date = it.localDate(zone)
            it.countableExpense() && !date.isAfter(today) && !YearMonth.from(date).isBefore(firstMonth)
        }.groupBy { MerchantCategorizer.key(it.description) }
        return groups.mapNotNull { (key, group) ->
            if (key.isEmpty()) return@mapNotNull null
            val latest = group.maxBy { it.occurredAt }
            val ref = latest.amountCents!!
            val refDay = latest.localDate(zone).dayOfMonth
            val refLen = YearMonth.from(latest.localDate(zone)).lengthOfMonth()
            val matching = group.filter {
                BigInteger.valueOf(abs(it.amountCents!! - ref)).multiply(BigInteger.valueOf(100)) <=
                    BigInteger.valueOf(ref).multiply(BigInteger.valueOf(AMOUNT_TOLERANCE_PERCENT)) &&
                    dayDistance(it.localDate(zone).dayOfMonth, refDay, refLen) <= DAY_TOLERANCE
            }
            val months = matching.map { YearMonth.from(it.localDate(zone)) }.toSet()
            if (months.size < 2) return@mapNotNull null
            val lastSeen = latest.localDate(zone)
            val paid = YearMonth.from(lastSeen) == thisMonth
            val dueMonth = if (paid) thisMonth.plusMonths(1) else thisMonth
            RecurringCharge(
                key, latest.description, latest.category, ref, refDay, lastSeen,
                dueMonth.atDay(refDay.coerceAtMost(dueMonth.lengthOfMonth())), months.size, paid, latest.method.affectsCash,
            )
        }.sortedWith(compareBy({ it.nextDue }, { it.key }))
    }
}
