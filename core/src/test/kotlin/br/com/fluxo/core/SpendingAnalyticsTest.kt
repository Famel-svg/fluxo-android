package br.com.fluxo.core

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SpendingAnalyticsTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val utc = ZoneId.of("UTC")
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun row(iso: String, cents: Long = 1000L, id: String = iso) = Transaction(
        id, null, "com.itau", cents, Direction.EXPENSE, PaymentMethod.DEBIT_CARD,
        ReviewStatus.CONFIRMED, at(iso), "Compra",
    )

    @Test fun `daily expenses return oldest first with zero days`() {
        val end = LocalDate.of(2026, 10, 7)
        val result = SpendingAnalytics.dailyExpenses(listOf(row("2026-10-07T15:00:00Z", 500)), end, 7, sp)
        assertEquals(7, result.size)
        assertEquals(LocalDate.of(2026, 10, 1), result.first().day)
        assertEquals(end, result.last().day)
        assertEquals(BigInteger.valueOf(500), result.last().totalCents)
        assertTrue(result.dropLast(1).all { it.totalCents == BigInteger.ZERO })
    }

    @Test fun `daily edges follow the zone not UTC`() {
        val end = LocalDate.of(2026, 10, 7)
        // 02:30Z on Oct 8 is still Oct 7 in Sao Paulo (UTC-3); 02:59Z Oct 1 is Sep 30 (outside).
        val rows = listOf(row("2026-10-08T02:30:00Z", 700, "a"), row("2026-10-01T02:59:00Z", 900, "b"),
            row("2026-10-01T03:00:00Z", 100, "c"), row("2026-10-08T03:00:00Z", 300, "d"))
        val result = SpendingAnalytics.dailyExpenses(rows, end, 7, sp)
        assertEquals(BigInteger.valueOf(700), result.last().totalCents)
        assertEquals(BigInteger.valueOf(100), result.first().totalCents)
        assertEquals(BigInteger.valueOf(800), result.fold(BigInteger.ZERO) { a, d -> a + d.totalCents })
    }

    @Test fun `daily excludes transfers review income and invalid amounts and never overflows`() {
        val base = row("2026-10-07T12:00:00Z", Long.MAX_VALUE)
        val rows = listOf(base, base, base.copy(transferId = "t"), base.copy(status = ReviewStatus.REVIEW),
            base.copy(direction = Direction.INCOME), base.copy(amountCents = null), base.copy(amountCents = 0))
        val result = SpendingAnalytics.dailyExpenses(rows, LocalDate.of(2026, 10, 7), 3, utc)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE) * BigInteger.TWO, result.last().totalCents)
    }

    @Test fun `monthly expenses cover year boundary with zero months`() {
        val rows = listOf(row("2026-01-15T12:00:00Z", 400), row("2025-11-30T12:00:00Z", 250))
        val result = SpendingAnalytics.monthlyExpenses(rows, YearMonth.of(2026, 2), 4, utc)
        assertEquals(listOf(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2)),
            result.map { it.month })
        assertEquals(listOf(250L, 0L, 400L, 0L), result.map { it.totalCents.toLong() })
    }

    @Test fun `monthly edges follow the zone and skip transfers and review`() {
        val rows = listOf(row("2026-10-01T02:00:00Z", 100, "sep"), row("2026-10-01T03:00:00Z", 200, "oct"),
            row("2026-11-01T02:59:00Z", 300, "oct2"), row("2026-10-10T12:00:00Z", 999, "tr").copy(transferId = "x"),
            row("2026-10-10T12:00:00Z", 888, "rv").copy(status = ReviewStatus.REVIEW))
        val result = SpendingAnalytics.monthlyExpenses(rows, YearMonth.of(2026, 10), 2, sp)
        assertEquals(100L, result[0].totalCents.toLong())
        assertEquals(500L, result[1].totalCents.toLong())
    }

    @Test fun `empty input yields zero filled series and defaults`() {
        val days = SpendingAnalytics.dailyExpenses(emptyList(), LocalDate.of(2026, 10, 7), zone = utc)
        val months = SpendingAnalytics.monthlyExpenses(emptyList(), YearMonth.of(2026, 10), zone = utc)
        assertEquals(7, days.size); assertEquals(6, months.size)
        assertTrue(days.all { it.totalCents == BigInteger.ZERO } && months.all { it.totalCents == BigInteger.ZERO })
        assertEquals(CaptureShare(0, 0, 0), SpendingAnalytics.captureShare(emptyList(), YearMonth.of(2026, 10), utc))
    }

    @Test fun `capture share counts automatic manual and review regardless of direction`() {
        val auto = row("2026-10-05T12:00:00Z", id = "a").copy(sourceKey = "k1")
        val rows = listOf(auto, auto.copy(id = "b", direction = Direction.INCOME, sourceKey = "k2"),
            auto.copy(id = "c", sourceKey = "k3", status = ReviewStatus.REVIEW),
            auto.copy(id = "d", sourceKey = null), auto.copy(id = "e", sourceKey = null, status = ReviewStatus.REVIEW),
            auto.copy(id = "t", transferId = "x"), auto.copy(id = "o", occurredAt = at("2026-11-01T12:00:00Z")))
        assertEquals(CaptureShare(3, 2, 2), SpendingAnalytics.captureShare(rows, YearMonth.of(2026, 10), utc))
        assertEquals(5, SpendingAnalytics.captureShare(rows, YearMonth.of(2026, 10), utc).total)
    }

    @Test fun `capture share month edge follows zone`() {
        val r = row("2026-11-01T02:00:00Z").copy(sourceKey = "k")
        assertEquals(1, SpendingAnalytics.captureShare(listOf(r), YearMonth.of(2026, 10), sp).automatic)
        assertEquals(0, SpendingAnalytics.captureShare(listOf(r), YearMonth.of(2026, 10), utc).automatic)
    }
}
