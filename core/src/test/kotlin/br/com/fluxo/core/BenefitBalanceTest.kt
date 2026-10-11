package br.com.fluxo.core

import java.math.BigInteger
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class BenefitBalanceTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val october = YearMonth.of(2026, 10)
    private val estimate = BigInteger.valueOf(85800)
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun row(
        iso: String, cents: Long? = 4000, method: PaymentMethod = PaymentMethod.MEAL_VOUCHER,
        direction: Direction = Direction.EXPENSE, status: ReviewStatus = ReviewStatus.CONFIRMED, transfer: String? = null,
    ) = Transaction(iso + method + cents, null, "manual", cents, direction, method, status, at(iso), "Almoço", transferId = transfer)

    @Test fun `remaining is estimate minus confirmed voucher expenses`() {
        val rows = listOf(row("2026-10-05T15:00:00Z", 7000), row("2026-10-06T15:00:00Z", 5000))
        val usage = BenefitBalance.remaining(estimate, rows, PaymentMethod.MEAL_VOUCHER, october, sp)
        assertEquals(BigInteger.valueOf(12000), usage.spentCents)
        assertEquals(BigInteger.valueOf(73800), usage.remainingCents)
        assertEquals(estimate, usage.depositCents)
        assertFalse(usage.overspent)
    }

    @Test fun `month boundaries use the given zone`() {
        // 2026-11-01T02:00Z is still 31 October in Sao Paulo (UTC-3); 2026-10-01T02:00Z is still 30 September.
        val rows = listOf(
            row("2026-11-01T02:00:00Z", 1000), row("2026-10-01T02:00:00Z", 2000),
            row("2026-10-01T03:00:00Z", 4000), row("2026-11-01T03:00:00Z", 8000),
        )
        val usage = BenefitBalance.remaining(estimate, rows, PaymentMethod.MEAL_VOUCHER, october, sp)
        assertEquals(BigInteger.valueOf(5000), usage.spentCents)
    }

    @Test fun `review income transfer unknown amount and other methods are ignored`() {
        val rows = listOf(
            row("2026-10-05T15:00:00Z", status = ReviewStatus.REVIEW),
            row("2026-10-05T15:00:00Z", direction = Direction.INCOME),
            row("2026-10-05T15:00:00Z", direction = Direction.UNKNOWN),
            row("2026-10-05T15:00:00Z", transfer = "t"),
            row("2026-10-05T15:00:00Z", cents = null),
            row("2026-10-05T15:00:00Z", cents = 0),
            row("2026-10-05T15:00:00Z", method = PaymentMethod.TRANSPORT_VOUCHER),
            row("2026-10-05T15:00:00Z", method = PaymentMethod.CREDIT_CARD),
            row("2026-10-07T15:00:00Z", cents = 1500),
        )
        val usage = BenefitBalance.remaining(estimate, rows, PaymentMethod.MEAL_VOUCHER, october, sp)
        assertEquals(BigInteger.valueOf(1500), usage.spentCents)
        val vt = BenefitBalance.remaining(estimate, rows, PaymentMethod.TRANSPORT_VOUCHER, october, sp)
        assertEquals(BigInteger.valueOf(4000), vt.spentCents)
    }

    @Test fun `overspend is negative and not clamped`() {
        val usage = BenefitBalance.remaining(BigInteger.valueOf(10000), listOf(row("2026-10-05T15:00:00Z", 12550)),
            PaymentMethod.MEAL_VOUCHER, october, sp)
        assertEquals(BigInteger.valueOf(-2550), usage.remainingCents)
        assertTrue(usage.overspent)
    }

    @Test fun `zero estimate with spending is overspent and without spending is zero`() {
        val none = BenefitBalance.remaining(BigInteger.ZERO, emptyList(), PaymentMethod.MEAL_VOUCHER, october, sp)
        assertEquals(BigInteger.ZERO, none.remainingCents)
        assertFalse(none.overspent)
        val some = BenefitBalance.remaining(BigInteger.ZERO, listOf(row("2026-10-05T15:00:00Z", 100)), PaymentMethod.MEAL_VOUCHER, october, sp)
        assertEquals(BigInteger.valueOf(-100), some.remainingCents)
    }

    @Test fun `sums do not overflow long`() {
        val rows = listOf(row("2026-10-05T15:00:00Z", Long.MAX_VALUE), row("2026-10-06T15:00:00Z", Long.MAX_VALUE))
        val usage = BenefitBalance.remaining(BigInteger.ZERO, rows, PaymentMethod.MEAL_VOUCHER, october, sp)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), usage.spentCents)
    }

    @Test fun `non voucher method is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BenefitBalance.remaining(estimate, emptyList(), PaymentMethod.CREDIT_CARD, october, sp)
        }
    }

    @Test fun `vouchers stay out of cash balances but count as consumption`() {
        val at = YearMonth.of(2026, 10).atDay(1).atStartOfDay(sp).toInstant().toEpochMilli()
        val account = Account("a", "Conta", "manual", 100000, at)
        fun tx(method: PaymentMethod) = Transaction("x$method", null, "manual", 3000, Direction.EXPENSE, method,
            ReviewStatus.CONFIRMED, at + 1000, "Compra", category = Category.FOOD, accountId = "a")
        val rows = listOf(tx(PaymentMethod.MEAL_VOUCHER), tx(PaymentMethod.TRANSPORT_VOUCHER), tx(PaymentMethod.CREDIT_CARD))
        assertEquals(BigInteger.valueOf(100000), AccountBalances.observed(account, rows, at + 5000))
        assertEquals(BigInteger.valueOf(97000), AccountBalances.observed(account, listOf(tx(PaymentMethod.PIX)), at + 5000))
        assertEquals(BigInteger.valueOf(9000), SpendingAnalytics.byCategory(rows, october, sp)[Category.FOOD])
        assertEquals(BigInteger.valueOf(9000), SpendingAnalytics.monthlyExpenses(rows, october, 1, sp).single().totalCents)
    }

    @Test fun `new enum values keep legacy names and labels`() {
        assertEquals(listOf("PIX", "CREDIT_CARD", "DEBIT_CARD", "UNKNOWN", "MEAL_VOUCHER", "TRANSPORT_VOUCHER"), PaymentMethod.entries.map { it.name })
        assertEquals("Vale-refeição", PaymentMethod.MEAL_VOUCHER.label)
        assertEquals("Vale-transporte", PaymentMethod.TRANSPORT_VOUCHER.label)
        assertEquals("VR", PaymentMethod.MEAL_VOUCHER.shortLabel)
        assertEquals("VT", PaymentMethod.TRANSPORT_VOUCHER.shortLabel)
        assertEquals("Cartão de crédito", PaymentMethod.CREDIT_CARD.label)
    }
}
