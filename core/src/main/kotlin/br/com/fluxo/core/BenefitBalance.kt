package br.com.fluxo.core

import java.math.BigInteger
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Voucher position of one month. [remainingCents] is negative when more was spent than the estimate;
 * it is never clamped so the UI can say "acima da estimativa".
 */
data class BenefitUsage(val depositCents: BigInteger, val spentCents: BigInteger, val remainingCents: BigInteger) {
    val overspent: Boolean get() = remainingCents.signum() < 0
}

/** Estimated voucher deposit minus confirmed voucher spending; no carry-over from earlier months. */
object BenefitBalance {
    fun remaining(
        estimateCents: BigInteger,
        rows: List<Transaction>,
        method: PaymentMethod,
        month: YearMonth,
        zone: ZoneId,
    ): BenefitUsage {
        require(method.isVoucher) { "Only voucher methods have a benefit balance." }
        var spent = BigInteger.ZERO
        rows.forEach { row ->
            val amount = row.amountCents
            if (row.method == method && row.status == ReviewStatus.CONFIRMED && row.direction == Direction.EXPENSE &&
                row.transferId == null && amount != null && amount > 0 &&
                YearMonth.from(Instant.ofEpochMilli(row.occurredAt).atZone(zone)) == month
            ) spent += BigInteger.valueOf(amount)
        }
        return BenefitUsage(estimateCents, spent, estimateCents - spent)
    }
}
