package br.com.fluxo.core

import java.math.BigInteger

/** Strict pt-BR money text to integer cents. Shared by the daily benefit rates and the monthly salary. */
object MoneyParser {
    private val pattern = Regex("(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)(?:,[0-9]{1,2})?")

    /** Optional correctly grouped thousands, optional 1-2 decimal digits after a comma; never floating point. */
    fun parseCents(input: String): Long? {
        val text = input.trim()
        if (text.length !in 1..32 || !pattern.matches(text)) return null
        val parts = text.replace(".", "").split(',')
        val cents = BigInteger(parts[0]) * BigInteger.valueOf(100) +
            BigInteger(parts.getOrElse(1) { "0" }.padEnd(2, '0'))
        return if (cents <= BigInteger.valueOf(Long.MAX_VALUE)) cents.toLong() else null
    }
}

/** Planning aids around the monthly salary. Nothing here touches income, expenses or cash. */
object SalaryPlanner {
    /** Valid monthly amount: a strict pt-BR decimal strictly greater than zero, otherwise null. */
    fun parseMonthlyCents(input: String): Long? = MoneyParser.parseCents(input)?.takeIf { it > 0 }

    /**
     * Whole percentage (rounded down) of [salaryCents] represented by [spentCents]. Null when no salary is set
     * (zero or negative); negative spending counts as zero; the result saturates at [Int.MAX_VALUE].
     */
    fun percentSpent(salaryCents: Long, spentCents: BigInteger): Int? {
        if (salaryCents <= 0) return null
        val percent = spentCents.max(BigInteger.ZERO).multiply(BigInteger.valueOf(100))
            .divide(BigInteger.valueOf(salaryCents))
        return if (percent > BigInteger.valueOf(Int.MAX_VALUE.toLong())) Int.MAX_VALUE else percent.toInt()
    }
}
