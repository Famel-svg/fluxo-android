package br.com.fluxo.core

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Expense total of one calendar day, in cents. */
data class DailyExpense(val day: LocalDate, val totalCents: BigInteger)

/** Expense total of one calendar month, in cents. */
data class MonthlyExpense(val month: YearMonth, val totalCents: BigInteger)

/**
 * Capture mix for one month: [automatic] rows came from a notification (sourceKey != null),
 * [manual] rows were typed by the user, [review] is how many of those rows still await review.
 */
data class CaptureShare(val automatic: Int, val manual: Int, val review: Int) {
    val total: Int get() = automatic + manual
}

object SpendingAnalytics {
    private fun Transaction.countsAsExpense(): Boolean {
        val amount = amountCents
        return status == ReviewStatus.CONFIRMED && direction == Direction.EXPENSE &&
            transferId == null && amount != null && amount > 0
    }

    private fun Transaction.dateIn(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()

    /** Confirmed commitments, including credit purchases; this is not a bank balance. */
    fun byCategory(
        rows: List<Transaction>,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<Category, BigInteger> {
        val totals = linkedMapOf<Category, BigInteger>()
        rows.forEach { row ->
            val amount = row.amountCents
            if (row.countsAsExpense() && amount != null && YearMonth.from(row.dateIn(zone)) == month) {
                totals[row.category] = (totals[row.category] ?: BigInteger.ZERO) + BigInteger.valueOf(amount)
            }
        }
        return totals
    }

    /** One entry per day ending at [endDay] (inclusive), oldest first, zero days included. */
    fun dailyExpenses(
        rows: List<Transaction>,
        endDay: LocalDate,
        days: Int = 7,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DailyExpense> {
        require(days > 0) { "days must be positive" }
        val start = endDay.minusDays(days - 1L)
        val totals = HashMap<LocalDate, BigInteger>()
        rows.forEach { row ->
            val amount = row.amountCents
            if (row.countsAsExpense() && amount != null) {
                val day = row.dateIn(zone)
                if (!day.isBefore(start) && !day.isAfter(endDay)) {
                    totals[day] = (totals[day] ?: BigInteger.ZERO) + BigInteger.valueOf(amount)
                }
            }
        }
        return (0 until days).map { i ->
            val day = start.plusDays(i.toLong())
            DailyExpense(day, totals[day] ?: BigInteger.ZERO)
        }
    }

    /** One entry per month ending at [endMonth] (inclusive), oldest first, zero months included. */
    fun monthlyExpenses(
        rows: List<Transaction>,
        endMonth: YearMonth,
        months: Int = 6,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthlyExpense> {
        require(months > 0) { "months must be positive" }
        val start = endMonth.minusMonths(months - 1L)
        val totals = HashMap<YearMonth, BigInteger>()
        rows.forEach { row ->
            val amount = row.amountCents
            if (row.countsAsExpense() && amount != null) {
                val month = YearMonth.from(row.dateIn(zone))
                if (!month.isBefore(start) && !month.isAfter(endMonth)) {
                    totals[month] = (totals[month] ?: BigInteger.ZERO) + BigInteger.valueOf(amount)
                }
            }
        }
        return (0 until months).map { i ->
            val month = start.plusMonths(i.toLong())
            MonthlyExpense(month, totals[month] ?: BigInteger.ZERO)
        }
    }

    /** Automatic vs manual rows of [month] among non-transfer rows of any direction or status. */
    fun captureShare(
        rows: List<Transaction>,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CaptureShare {
        var automatic = 0
        var manual = 0
        var review = 0
        rows.forEach { row ->
            if (row.transferId == null && YearMonth.from(row.dateIn(zone)) == month) {
                if (row.sourceKey != null) automatic++ else manual++
                if (row.status == ReviewStatus.REVIEW) review++
            }
        }
        return CaptureShare(automatic, manual, review)
    }
}
