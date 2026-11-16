package br.com.fluxo.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class BudgetPaceTest {
    private val utc = ZoneId.of("UTC")
    private val month = YearMonth.of(2026, 11)
    private fun tx(id: String, day: String, cents: Long, category: Category, method: PaymentMethod = PaymentMethod.DEBIT_CARD,
                   status: ReviewStatus = ReviewStatus.CONFIRMED, transferId: String? = null) = Transaction(
        id, null, "com.itau", cents, Direction.EXPENSE, method, status,
        Instant.parse("${day}T12:00:00Z").toEpochMilli(), "Compra", category = category, transferId = transferId,
    )

    @Test fun `pace under on track over and no budget`() {
        val today = LocalDate.of(2026, 11, 15) // 15 of 30 days: expected is half
        assertEquals(PaceStatus.UNDER, BudgetPace.monthPace(40_000, 100_000, month, today).status)
        assertEquals(PaceStatus.ON_TRACK, BudgetPace.monthPace(50_000, 100_000, month, today).status)
        assertEquals(PaceStatus.OVER, BudgetPace.monthPace(60_000, 100_000, month, today).status)
        assertEquals(PaceStatus.OVER, BudgetPace.monthPace(101_000, 100_000, month, LocalDate.of(2026, 11, 30)).status)
        assertEquals(PaceStatus.NO_BUDGET, BudgetPace.monthPace(1_000, 0, month, today).status)
        assertEquals(50_000, BudgetPace.monthPace(40_000, 100_000, month, today).expectedByTodayCents)
    }

    @Test fun `projection extrapolates linearly and adds upcoming fixed bills`() {
        val today = LocalDate.of(2026, 11, 15)
        assertEquals(100_000, BudgetPace.monthPace(50_000, 100_000, month, today).projectedEndCents)
        assertEquals(130_000, BudgetPace.monthPace(50_000, 100_000, month, today, fixedUpcomingCents = 30_000).projectedEndCents)
    }

    @Test fun `overruns list categories at 80 percent or more and ignore other months review transfer and voucher`() {
        val rows = listOf(
            tx("a", "2026-11-03", 8_000, Category.FOOD),
            tx("b", "2026-11-04", 12_000, Category.TRANSPORT),
            tx("c", "2026-11-05", 7_900, Category.LEISURE),
            tx("d", "2026-11-05", 50_000, Category.LEISURE, status = ReviewStatus.REVIEW),
            tx("e", "2026-11-05", 50_000, Category.LEISURE, transferId = "t"),
            tx("f", "2026-11-05", 50_000, Category.LEISURE, method = PaymentMethod.MEAL_VOUCHER),
            tx("g", "2026-10-30", 50_000, Category.LEISURE),
        )
        val budgets = listOf(
            Budget("2026-11", Category.FOOD, 10_000), Budget("2026-11", Category.TRANSPORT, 10_000),
            Budget("2026-11", Category.LEISURE, 10_000), Budget("2026-10", Category.HEALTH, 100),
        )
        val result = BudgetPace.overruns(rows, budgets, month, utc)
        assertEquals(listOf(Category.TRANSPORT, Category.FOOD), result.map { it.category })
        assertEquals(12_000, result.first().spentCents)
    }
}
