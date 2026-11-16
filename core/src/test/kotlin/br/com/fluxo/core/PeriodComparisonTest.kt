package br.com.fluxo.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PeriodComparisonTest {
    private val utc = ZoneId.of("UTC")
    private val month = YearMonth.of(2026, 11)
    private fun tx(id: String, day: String, cents: Long, category: Category = Category.FOOD, dir: Direction = Direction.EXPENSE,
                   method: PaymentMethod = PaymentMethod.PIX, transferId: String? = null) = Transaction(
        id, null, "com.itau", cents, dir, method, ReviewStatus.CONFIRMED,
        Instant.parse("${day}T12:00:00Z").toEpochMilli(), "x", category = category, transferId = transferId,
    )

    @Test fun `month over month computes percent and null when previous month is zero`() {
        val rows = listOf(
            tx("a", "2026-10-10", 10_000, Category.FOOD), tx("b", "2026-11-10", 15_000, Category.FOOD),
            tx("c", "2026-11-11", 3_000, Category.LEISURE),
            tx("d", "2026-10-11", 4_000, Category.HEALTH),
            tx("t", "2026-11-12", 99_000, Category.FOOD, transferId = "x"),
            tx("v", "2026-11-12", 99_000, Category.FOOD, method = PaymentMethod.MEAL_VOUCHER),
        )
        val deltas = PeriodComparison.monthOverMonth(rows, month, utc).associateBy { it.category }
        assertEquals(50, deltas.getValue(Category.FOOD).deltaPercent)
        assertNull(deltas.getValue(Category.LEISURE).deltaPercent)
        assertEquals(-100, deltas.getValue(Category.HEALTH).deltaPercent)
        assertEquals(15_000, deltas.getValue(Category.FOOD).currentCents)
    }

    @Test fun `cashflow covers six months with zero months and excludes credit transfer and voucher from out`() {
        val rows = listOf(
            tx("i", "2026-11-05", 100_000, dir = Direction.INCOME), tx("o", "2026-11-06", 40_000),
            tx("c", "2026-11-06", 40_000, method = PaymentMethod.CREDIT_CARD),
            tx("t", "2026-11-06", 40_000, transferId = "t"),
            tx("j", "2026-07-05", 20_000, dir = Direction.INCOME),
            tx("old", "2026-05-05", 20_000, dir = Direction.INCOME),
        )
        val flow = PeriodComparison.cashflow(rows, month, 6, utc)
        assertEquals(6, flow.size)
        assertEquals(YearMonth.of(2026, 6), flow.first().month)
        assertEquals(month, flow.last().month)
        assertEquals(100_000, flow.last().incomeCents)
        assertEquals(40_000, flow.last().expenseCents)
        assertEquals(60_000, flow.last().netCents)
        assertEquals(20_000, flow.first { it.month == YearMonth.of(2026, 7) }.incomeCents)
        assertEquals(0, flow.first { it.month == YearMonth.of(2026, 8) }.incomeCents)
    }

    @Test fun `category trend lists one value per month oldest first`() {
        val rows = listOf(tx("a", "2026-11-10", 700), tx("b", "2026-09-10", 300), tx("c", "2026-09-11", 200, Category.LEISURE))
        assertEquals(listOf(0L, 0L, 0L, 300L, 0L, 700L), PeriodComparison.categoryTrend(rows, Category.FOOD, month, 6, utc))
    }

    @Test fun `daily heat sums per day of the month only for spending days`() {
        val rows = listOf(
            tx("a", "2026-11-10", 700), tx("b", "2026-11-10", 300), tx("c", "2026-11-12", 50),
            tx("i", "2026-11-13", 900, dir = Direction.INCOME), tx("o", "2026-10-31", 900),
        )
        val heat = PeriodComparison.dailyHeat(rows, month, utc)
        assertEquals(mapOf(LocalDate.of(2026, 11, 10) to 1_000L, LocalDate.of(2026, 11, 12) to 50L), heat)
    }
}
