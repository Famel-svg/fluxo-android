package br.com.fluxo.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CashProjectionTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 15)
    private fun tx(id: String, day: String, cents: Long, dir: Direction, method: PaymentMethod = PaymentMethod.PIX,
                   category: Category = Category.OTHER) = Transaction(
        id, null, "com.itau", cents, dir, method, ReviewStatus.CONFIRMED,
        Instant.parse("${day}T12:00:00Z").toEpochMilli(), "x", category = category,
    )
    private fun bill(label: String, cents: Long, due: String, paid: Boolean = false) = RecurringCharge(
        label.lowercase(), label, Category.HOUSING, cents, LocalDate.parse(due).dayOfMonth,
        LocalDate.parse(due).minusMonths(1), LocalDate.parse(due), 2, paid,
    )

    @Test fun `starts from cash net of the month and adds unpaid bills and next salary`() {
        val rows = listOf(
            tx("i", "2026-11-05", 300_000, Direction.INCOME, category = Category.SALARY),
            tx("e", "2026-11-06", 50_000, Direction.EXPENSE),
            tx("c", "2026-11-07", 90_000, Direction.EXPENSE, PaymentMethod.CREDIT_CARD), // not cash
            tx("v", "2026-11-07", 90_000, Direction.EXPENSE, PaymentMethod.MEAL_VOUCHER),
            tx("old", "2026-10-07", 90_000, Direction.EXPENSE),
        )
        val p = CashProjection.project(rows, listOf(bill("Aluguel", 100_000, "2026-11-20"), bill("Internet", 10_000, "2027-02-01")),
            300_000, 5, today, utc, 30)
        assertEquals(250_000, p.startNetCents)
        // This month's salary already arrived: only the rent and the December 5 salary.
        assertEquals(listOf("Aluguel", "Salário previsto"), p.items.map { it.label })
        assertEquals(LocalDate.of(2026, 12, 5), p.items.last().date)
        assertEquals(250_000 - 100_000 + 300_000, p.endNetCents)
    }

    @Test fun `salary not yet received this month is projected when still ahead`() {
        val p = CashProjection.project(emptyList(), emptyList(), 200_000, 20, today, utc, 30)
        assertEquals(LocalDate.of(2026, 11, 20), p.items.single().date)
        assertEquals(200_000, p.endNetCents)
    }

    @Test fun `overdue unpaid bill lands on today and no salary means no income item`() {
        val p = CashProjection.project(emptyList(), listOf(bill("Luz", 8_000, "2026-11-10")), 0, 5, today, utc, 30)
        assertEquals(today, p.items.single().date)
        assertEquals(-8_000, p.endNetCents)
    }
}
