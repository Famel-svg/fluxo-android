package br.com.fluxo.core

import java.math.BigInteger
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class AccountBalancesTest {
    private val at = YearMonth.of(2026, 9).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val first = Account("a", "Conta A", "manual", 100000, at)
    private val second = Account("b", "Conta B", "manual", 20000, at)
    private fun row(id: String, direction: Direction, account: String, transfer: String? = null) =
        Transaction(id, null, "manual", 50000, direction, PaymentMethod.PIX, ReviewStatus.CONFIRMED,
            at, "Sintético", category = Category.OTHER, accountId = account, transferId = transfer)

    @Test fun ownTransferConservesMoneyAndDoesNotConsumeBudget() {
        val rows = listOf(row("out", Direction.EXPENSE, "a", "pair"), row("in", Direction.INCOME, "b", "pair"))
        assertEquals(BigInteger.valueOf(50000), AccountBalances.observed(first, rows))
        assertEquals(BigInteger.valueOf(70000), AccountBalances.observed(second, rows))
        assertEquals(BigInteger.valueOf(120000), requireNotNull(AccountBalances.observed(first, rows)) + requireNotNull(AccountBalances.observed(second, rows)))
        assertTrue(SpendingAnalytics.byCategory(rows, YearMonth.of(2026, 9), ZoneOffset.UTC).isEmpty())
        val unlinked = rows.map { it.copy(transferId = null) }
        assertEquals(BigInteger.valueOf(50000), SpendingAnalytics.byCategory(unlinked, YearMonth.of(2026, 9), ZoneOffset.UTC)[Category.OTHER])
    }

    @Test fun openingDateAndCashClassificationControlBalance() {
        val rows = listOf(row("before", Direction.EXPENSE, "a").copy(occurredAt = at - 1),
            row("pending", Direction.EXPENSE, "a").copy(status = ReviewStatus.REVIEW),
            row("credit", Direction.EXPENSE, "a").copy(method = PaymentMethod.CREDIT_CARD),
            row("other", Direction.EXPENSE, "b"), row("real", Direction.EXPENSE, "a"))
        assertEquals(BigInteger.valueOf(50000), AccountBalances.observed(first, rows))
    }

    @Test fun balancesDoNotOverflowLongAggregation() {
        val large = first.copy(openingCents = Long.MAX_VALUE)
        val incoming = row("large", Direction.INCOME, "a").copy(amountCents = Long.MAX_VALUE)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), AccountBalances.observed(large, listOf(incoming)))
    }

    @Test fun futureMovementsAndFutureOpeningAreNotObservedMoney() {
        val future = row("future", Direction.EXPENSE, "a").copy(occurredAt = at + 86400000)
        assertEquals(BigInteger.valueOf(100000), AccountBalances.observed(first, listOf(future), at))
        assertNull(AccountBalances.observed(first.copy(openingAt = at + 1), emptyList(), at))
    }
}
