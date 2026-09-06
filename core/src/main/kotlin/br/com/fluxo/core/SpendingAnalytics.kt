package br.com.fluxo.core

import java.math.BigInteger
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

object SpendingAnalytics {
    /** Confirmed commitments, including credit purchases; this is not a bank balance. */
    fun byCategory(
        rows: List<Transaction>,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<Category, BigInteger> {
        val totals = linkedMapOf<Category, BigInteger>()
        rows.forEach { row ->
            val amount = row.amountCents
            if (row.status == ReviewStatus.CONFIRMED && row.direction == Direction.EXPENSE &&
                row.transferId == null && amount != null && amount > 0 &&
                YearMonth.from(Instant.ofEpochMilli(row.occurredAt).atZone(zone)) == month
            ) {
                totals[row.category] = (totals[row.category] ?: BigInteger.ZERO) + BigInteger.valueOf(amount)
            }
        }
        return totals
    }
}
