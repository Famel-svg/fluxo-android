package br.com.fluxo.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RecurringDetectorTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 15)
    private fun tx(id: String, day: String, cents: Long, desc: String = "Netflix") = Transaction(
        id, null, "com.itau", cents, Direction.EXPENSE, PaymentMethod.CREDIT_CARD, ReviewStatus.CONFIRMED,
        Instant.parse("${day}T12:00:00Z").toEpochMilli(), desc, category = Category.LEISURE,
    )

    @Test fun `detects monthly charge with day within 4 and amount within 15 percent`() {
        val rows = listOf(tx("1", "2026-09-10", 5_000), tx("2", "2026-10-13", 5_500, "NETFLIX"), tx("3", "2026-11-12", 5_700, "netflix"))
        val found = RecurringDetector.detect(rows, today, utc).single()
        assertEquals("netflix", found.key)
        assertEquals(12, found.dayOfMonth)
        assertEquals(3, found.occurrences)
        assertTrue(found.paidThisMonth)
        assertEquals(LocalDate.of(2026, 12, 12), found.nextDue)
    }

    @Test fun `unpaid this month is due this month`() {
        val rows = listOf(tx("1", "2026-09-20", 5_000), tx("2", "2026-10-20", 5_000))
        val found = RecurringDetector.detect(rows, today, utc).single()
        assertFalse(found.paidThisMonth)
        assertEquals(LocalDate.of(2026, 11, 20), found.nextDue)
    }

    @Test fun `single occurrence is not recurring`() {
        assertTrue(RecurringDetector.detect(listOf(tx("1", "2026-10-10", 5_000)), today, utc).isEmpty())
    }

    @Test fun `day or amount outside tolerance is not recurring`() {
        assertTrue(RecurringDetector.detect(listOf(tx("1", "2026-10-02", 5_000), tx("2", "2026-11-12", 5_000)), today, utc).isEmpty())
        assertTrue(RecurringDetector.detect(listOf(tx("1", "2026-10-10", 5_000), tx("2", "2026-11-10", 7_000)), today, utc).isEmpty())
    }

    @Test fun `two charges in the same month alone do not count and old months are ignored`() {
        assertTrue(RecurringDetector.detect(listOf(tx("1", "2026-11-10", 5_000), tx("2", "2026-11-11", 5_000)), today, utc).isEmpty())
        assertTrue(RecurringDetector.detect(listOf(tx("1", "2026-07-10", 5_000), tx("2", "2026-08-10", 5_000)), today, utc).isEmpty())
    }
}
