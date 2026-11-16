package br.com.fluxo.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SmartAlertsTest {
    private val utc = ZoneId.of("UTC")
    private fun tx(id: String, day: String, cents: Long, category: Category = Category.FOOD,
               status: ReviewStatus = ReviewStatus.CONFIRMED, hour: Int = 12) = Transaction(
        id, null, "com.itau", cents, Direction.EXPENSE, PaymentMethod.PIX, status,
        Instant.parse("${day}T%02d:00:00Z".format(hour)).toEpochMilli(), "x", category = category,
    )
    private fun input(
        now: String = "2026-11-12T12:00", rows: List<Transaction> = emptyList(), budgets: List<Budget> = emptyList(),
        recurring: List<RecurringCharge> = emptyList(), captureEnabled: Boolean = true, captureHealthy: Boolean = true,
        lastCaptureAt: Long? = null, settings: AlertSettings = AlertSettings(), sent: Set<String> = emptySet(), sentToday: Int = 0,
    ) = AlertInput(LocalDateTime.parse(now), utc, rows, budgets, recurring, captureEnabled, captureHealthy, lastCaptureAt, settings, sent, sentToday)

    private val reviewRow = tx("r", "2026-11-12", 1_000, status = ReviewStatus.REVIEW)

    @Test fun `silence without triggers`() {
        assertTrue(SmartAlerts.evaluate(input()).isEmpty())
    }

    @Test fun `quiet hours silence everything including wrapping window`() {
        val budgets = listOf(Budget("2026-11", Category.FOOD, 1_000))
        val rows = listOf(tx("a", "2026-11-12", 2_000))
        assertTrue(SmartAlerts.evaluate(input(now = "2026-11-12T23:00", rows = rows, budgets = budgets)).isEmpty())
        assertTrue(SmartAlerts.evaluate(input(now = "2026-11-12T07:00", rows = rows, budgets = budgets)).isEmpty())
        assertEquals(1, SmartAlerts.evaluate(input(now = "2026-11-12T09:00", rows = rows, budgets = budgets)).size)
    }

    @Test fun `review digest only from 19h once per day and grouped`() {
        val rows = listOf(reviewRow, reviewRow.copy(id = "r2"), reviewRow.copy(id = "r3"))
        assertTrue(SmartAlerts.evaluate(input(now = "2026-11-12T18:59", rows = rows)).isEmpty())
        val alert = SmartAlerts.evaluate(input(now = "2026-11-12T19:00", rows = rows)).single()
        assertEquals(AlertKind.REVIEW_DIGEST, alert.kind)
        assertTrue(alert.body.startsWith("3 "))
        assertTrue(SmartAlerts.evaluate(input(now = "2026-11-12T20:00", rows = rows, sent = setOf(alert.key))).isEmpty())
        assertEquals(1, SmartAlerts.evaluate(input(now = "2026-11-13T20:00", rows = rows, sent = setOf(alert.key))).size)
    }

    @Test fun `budget 80 then over with stable per month keys and dedupe`() {
        val budgets = listOf(Budget("2026-11", Category.FOOD, 10_000))
        val at80 = SmartAlerts.evaluate(input(rows = listOf(tx("a", "2026-11-10", 8_500)), budgets = budgets)).single()
        assertEquals(AlertKind.BUDGET_80, at80.kind)
        assertEquals("budget80:2026-11:FOOD", at80.key)
        val over = SmartAlerts.evaluate(input(rows = listOf(tx("a", "2026-11-10", 10_000)), budgets = budgets)).single()
        assertEquals(AlertKind.BUDGET_OVER, over.kind)
        assertEquals("budgetover:2026-11:FOOD", over.key)
        assertTrue(SmartAlerts.evaluate(input(rows = listOf(tx("a", "2026-11-10", 10_000)), budgets = budgets, sent = setOf(over.key))).isEmpty())
    }

    @Test fun `bill due tomorrow once`() {
        val bill = RecurringCharge("aluguel", "Aluguel", Category.HOUSING, 120_000, 13, java.time.LocalDate.of(2026, 10, 13),
            java.time.LocalDate.of(2026, 11, 13), 3, false)
        val alert = SmartAlerts.evaluate(input(recurring = listOf(bill))).single()
        assertEquals(AlertKind.BILL_DUE, alert.kind)
        assertTrue(SmartAlerts.evaluate(input(recurring = listOf(bill), sent = setOf(alert.key))).isEmpty())
        assertTrue(SmartAlerts.evaluate(input(recurring = listOf(bill.copy(nextDue = java.time.LocalDate.of(2026, 11, 14))))).isEmpty())
    }

    @Test fun `unusual spend above 3x median and R$ 50 once per transaction`() {
        val history = listOf("2026-09-20", "2026-10-02", "2026-10-15", "2026-11-01").mapIndexed { i, d -> tx("h$i", d, 4_000) }
        val big = tx("big", "2026-11-12", 20_000)
        val alert = SmartAlerts.evaluate(input(rows = history + big)).single()
        assertEquals("unusual:big", alert.key)
        assertTrue(SmartAlerts.evaluate(input(rows = history + big, sent = setOf(alert.key))).isEmpty())
        assertTrue(SmartAlerts.evaluate(input(rows = history + tx("mid", "2026-11-12", 11_000))).isEmpty()) // under 3x
        val tiny = history.map { it.copy(amountCents = 500) } + tx("t", "2026-11-12", 4_900)
        assertTrue(SmartAlerts.evaluate(input(rows = tiny)).isEmpty()) // below R$ 50 floor
    }

    @Test fun `capture down after 48h only when enabled and unhealthy`() {
        val now = LocalDateTime.parse("2026-11-12T12:00").atZone(utc).toInstant().toEpochMilli()
        val stale = now - 49L * 3_600_000
        assertEquals(AlertKind.CAPTURE_DOWN, SmartAlerts.evaluate(input(captureHealthy = false, lastCaptureAt = stale)).single().kind)
        assertTrue(SmartAlerts.evaluate(input(captureHealthy = true, lastCaptureAt = stale)).isEmpty())
        assertTrue(SmartAlerts.evaluate(input(captureEnabled = false, captureHealthy = false, lastCaptureAt = stale)).isEmpty())
        assertTrue(SmartAlerts.evaluate(input(captureHealthy = false, lastCaptureAt = now - 47L * 3_600_000)).isEmpty())
    }

    @Test fun `weekly summary on sunday from 18h`() {
        val rows = listOf(tx("a", "2026-11-12", 10_000))
        assertTrue(SmartAlerts.evaluate(input(now = "2026-11-15T17:00", rows = rows)).isEmpty())
        val alert = SmartAlerts.evaluate(input(now = "2026-11-15T18:00", rows = rows)).single()
        assertEquals(AlertKind.WEEKLY_SUMMARY, alert.kind)
        assertTrue(alert.body.contains("R$ 100,00"))
        assertTrue(SmartAlerts.evaluate(input(now = "2026-11-14T18:00", rows = rows)).isEmpty()) // saturday
    }

    @Test fun `disabled kinds are skipped`() {
        val settings = AlertSettings(enabled = setOf(AlertKind.WEEKLY_SUMMARY))
        val budgets = listOf(Budget("2026-11", Category.FOOD, 1_000))
        assertTrue(SmartAlerts.evaluate(input(rows = listOf(tx("a", "2026-11-10", 5_000)), budgets = budgets, settings = settings)).isEmpty())
    }

    @Test fun `cut by maxPerDay minus sentToday keeps highest priority`() {
        val budgets = listOf(Budget("2026-11", Category.FOOD, 1_000))
        val bill = RecurringCharge("luz", "Luz", Category.HOUSING, 9_000, 13, java.time.LocalDate.of(2026, 10, 13),
            java.time.LocalDate.of(2026, 11, 13), 3, false)
        val rows = listOf(tx("a", "2026-11-10", 5_000), reviewRow)
        val all = SmartAlerts.evaluate(input(now = "2026-11-12T20:00", rows = rows, budgets = budgets, recurring = listOf(bill)))
        assertEquals(listOf(AlertKind.BUDGET_OVER, AlertKind.BILL_DUE, AlertKind.REVIEW_DIGEST), all.map { it.kind })
        val cut = SmartAlerts.evaluate(input(now = "2026-11-12T20:00", rows = rows, budgets = budgets, recurring = listOf(bill), sentToday = 2))
        assertEquals(listOf(AlertKind.BUDGET_OVER), cut.map { it.kind })
        assertTrue(SmartAlerts.evaluate(input(now = "2026-11-12T20:00", rows = rows, budgets = budgets, sentToday = 3)).isEmpty())
    }
}
