package br.com.fluxo.core

import java.math.BigInteger
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PlanningTest {
    private val month = YearMonth.of(2026, 9)
    private val utc = ZoneId.of("UTC")
    private fun expense(id: String = "one") = Transaction(
        id, null, "com.itau", 6000L, Direction.EXPENSE, PaymentMethod.CREDIT_CARD,
        ReviewStatus.CONFIRMED, Instant.parse("2026-09-05T12:00:00Z").toEpochMilli(), "Almoço",
        category = Category.FOOD,
    )

    @Test fun `confirmed credit and debit commitments sum together excluding review income and other months`() {
        val row = expense()
        val totals = SpendingAnalytics.byCategory(listOf(row,
            row.copy(method = PaymentMethod.DEBIT_CARD),
            row.copy(status = ReviewStatus.REVIEW), row.copy(direction = Direction.INCOME),
            row.copy(occurredAt = Instant.parse("2026-08-31T12:00:00Z").toEpochMilli()),
            row.copy(amountCents = null), row.copy(direction = Direction.UNKNOWN),
        ), month, utc)
        assertEquals(mapOf(Category.FOOD to BigInteger.valueOf(12000)), totals)
        assertEquals(BigInteger.valueOf(-2000), BigInteger.valueOf(10000) - totals.getValue(Category.FOOD))
    }

    @Test fun `totals never overflow and uncategorized expenses remain visible`() {
        val row = expense().copy(amountCents = Long.MAX_VALUE, category = Category.UNCATEGORIZED)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE) * BigInteger.TWO,
            SpendingAnalytics.byCategory(listOf(row, row), month, utc)[Category.UNCATEGORIZED])
        assertTrue(SpendingAnalytics.byCategory(emptyList(), month, utc).isEmpty())
    }

    @Test fun `month boundaries follow provided timezone`() {
        val row = expense().copy(occurredAt = Instant.parse("2026-09-01T01:00:00Z").toEpochMilli())
        assertTrue(SpendingAnalytics.byCategory(listOf(row), month, ZoneId.of("America/Sao_Paulo")).isEmpty())
        assertEquals(BigInteger.valueOf(6000), SpendingAnalytics.byCategory(listOf(row), month, utc)[Category.FOOD])
    }

    @Test fun `search ignores accents and case in description bank and category`() {
        val rows = listOf(expense())
        listOf(" itau ", "ALMOCO", "alimentacao").forEach { query ->
            assertEquals(query, rows, TransactionSearch.filter(rows, query))
        }
        assertEquals(listOf(expense().copy(bankPackage = "com.nu.production")),
            TransactionSearch.filter(listOf(expense().copy(bankPackage = "com.nu.production")), "nubank"))
        assertTrue(TransactionSearch.filter(rows, "unknown").isEmpty())
    }

    @Test fun `filters combine with AND and clearing preserves input order across months`() {
        val target = expense("target")
        val rows = listOf(target.copy(id = "bank", bankPackage = "com.c6bank.app"),
            target.copy(id = "method", method = PaymentMethod.PIX),
            target.copy(id = "category", category = Category.OTHER), target,
            target.copy(id = "old", occurredAt = 1L))
        assertEquals(listOf(target, rows.last()), TransactionSearch.filter(rows, "almoco", "com.itau",
            PaymentMethod.CREDIT_CARD, Category.FOOD))
        assertEquals(rows, TransactionSearch.filter(rows))
    }
}
