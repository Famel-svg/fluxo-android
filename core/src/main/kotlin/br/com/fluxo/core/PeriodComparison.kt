package br.com.fluxo.core

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Confirmed, non-transfer, non-voucher row with a positive amount; the only rows that feed visual totals. */
internal fun Transaction.isCountable(): Boolean {
    val amount = amountCents
    return status == ReviewStatus.CONFIRMED && transferId == null && !method.isVoucher && amount != null && amount > 0
}

internal fun Transaction.countableExpense(): Boolean = isCountable() && direction == Direction.EXPENSE
internal fun Transaction.countableIncome(): Boolean = isCountable() && direction == Direction.INCOME
internal fun Transaction.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()

/** Saturating percent of [part] over [whole] (whole > 0), truncated toward zero. */
internal fun percentOf(part: Long, whole: Long): Int {
    val value = BigInteger.valueOf(part).multiply(BigInteger.valueOf(100)).divide(BigInteger.valueOf(whole))
    return value.max(BigInteger.valueOf(Int.MIN_VALUE.toLong())).min(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
}

data class CategoryDelta(val category: Category, val currentCents: Long, val previousCents: Long, val deltaPercent: Int?)

data class MonthFlow(val month: YearMonth, val incomeCents: Long, val expenseCents: Long) {
    val netCents: Long get() = Math.subtractExact(incomeCents, expenseCents)
}

object PeriodComparison {
    private fun expenseByCategory(rows: List<Transaction>, month: YearMonth, zone: ZoneId): Map<Category, Long> {
        val totals = HashMap<Category, Long>()
        rows.forEach {
            if (it.countableExpense() && YearMonth.from(it.localDate(zone)) == month) {
                totals[it.category] = Math.addExact(totals[it.category] ?: 0L, it.amountCents!!)
            }
        }
        return totals
    }

    /** Expense by category (cash and credit consumption) versus the previous month; null percent when it was zero. Biggest absolute change first. */
    fun monthOverMonth(rows: List<Transaction>, month: YearMonth, zone: ZoneId): List<CategoryDelta> {
        val current = expenseByCategory(rows, month, zone)
        val previous = expenseByCategory(rows, month.minusMonths(1), zone)
        return (current.keys + previous.keys).map { category ->
            val cur = current[category] ?: 0L
            val prev = previous[category] ?: 0L
            CategoryDelta(category, cur, prev, if (prev > 0) percentOf(Math.subtractExact(cur, prev), prev) else null)
        }.filter { it.currentCents > 0 || it.previousCents > 0 }
            .sortedWith(
                compareByDescending<CategoryDelta> { BigInteger.valueOf(it.currentCents).subtract(BigInteger.valueOf(it.previousCents)).abs() }
                    .thenBy { it.category.ordinal },
            )
    }

    /** Money in and out per month, oldest first, zero months included. Out counts only what leaves the bank (credit and vouchers excluded). */
    fun cashflow(rows: List<Transaction>, endMonth: YearMonth, months: Int = 6, zone: ZoneId): List<MonthFlow> {
        require(months > 0) { "months must be positive" }
        val income = HashMap<YearMonth, Long>()
        val expense = HashMap<YearMonth, Long>()
        rows.forEach {
            val month = YearMonth.from(it.localDate(zone))
            if (it.countableIncome()) income[month] = Math.addExact(income[month] ?: 0L, it.amountCents!!)
            else if (it.countableExpense() && it.method.affectsCash) expense[month] = Math.addExact(expense[month] ?: 0L, it.amountCents!!)
        }
        return (0 until months).map {
            val month = endMonth.minusMonths(months - 1L - it)
            MonthFlow(month, income[month] ?: 0L, expense[month] ?: 0L)
        }
    }

    fun categoryTrend(rows: List<Transaction>, category: Category, endMonth: YearMonth, months: Int = 6, zone: ZoneId): List<Long> {
        require(months > 0) { "months must be positive" }
        return (0 until months).map { expenseByCategory(rows, endMonth.minusMonths(months - 1L - it), zone)[category] ?: 0L }
    }

    /** Expense per day of [month]; only days with spending appear. */
    fun dailyHeat(rows: List<Transaction>, month: YearMonth, zone: ZoneId): Map<LocalDate, Long> {
        val days = sortedMapOf<LocalDate, Long>()
        rows.forEach {
            val day = it.localDate(zone)
            if (it.countableExpense() && YearMonth.from(day) == month) days[day] = Math.addExact(days[day] ?: 0L, it.amountCents!!)
        }
        return days
    }
}
