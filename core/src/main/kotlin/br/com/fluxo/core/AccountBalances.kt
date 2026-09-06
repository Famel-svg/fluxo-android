package br.com.fluxo.core

import java.math.BigInteger

object AccountBalances {
    /** Opening balance plus confirmed observed cash movements; never a bank-certified balance. */
    fun observed(account: Account, rows: List<Transaction>, asOf: Long = System.currentTimeMillis()): BigInteger? {
        if (account.openingAt > asOf) return null
        return rows.asSequence()
        .filter { it.accountId == account.id && it.occurredAt >= account.openingAt && it.occurredAt <= asOf &&
            it.status == ReviewStatus.CONFIRMED && it.method != PaymentMethod.CREDIT_CARD }
        .fold(BigInteger.valueOf(account.openingCents)) { total, row ->
            val amount = BigInteger.valueOf(row.amountCents ?: 0)
            when (row.direction) {
                Direction.INCOME -> total + amount
                Direction.EXPENSE -> total - amount
                Direction.UNKNOWN -> total
            }
        }
    }
}
