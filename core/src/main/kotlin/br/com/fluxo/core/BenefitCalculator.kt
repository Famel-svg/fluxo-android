package br.com.fluxo.core

import java.math.BigInteger
import java.time.DayOfWeek
import java.time.YearMonth

data class BenefitDays(val vr: Int? = null, val vt: Int? = null)

data class BenefitSettings(
    val vrDailyCents: Long = 0,
    val vtDailyCents: Long = 0,
    val overrides: Map<YearMonth, BenefitDays> = emptyMap(),
    val vrWeekdays: Set<DayOfWeek> = BenefitCalculator.WORKDAYS,
    val vtWeekdays: Set<DayOfWeek> = BenefitCalculator.WORKDAYS,
) {
    init {
        require(vrDailyCents >= 0 && vtDailyCents >= 0) { "As tarifas não podem ser negativas." }
        overrides.forEach { (month, days) -> BenefitCalculator.validateDays(month, days) }
    }
}

data class BenefitEstimate(
    val vrAutomaticDays: Int,
    val vtAutomaticDays: Int,
    val vrDays: Int,
    val vtDays: Int,
    val vrCents: BigInteger,
    val vtCents: BigInteger,
) {
    val totalCents: BigInteger get() = vrCents + vtCents
    /** Kept for callers that only need the Monday-Friday style count of VR. */
    val automaticDays: Int get() = vrAutomaticDays
}

/** Calendar estimate only: no holidays, attendance, salary deductions or ledger entries. */
object BenefitCalculator {
    val WORKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    private val ratePattern = Regex("(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)(?:,[0-9]{1,2})?")

    /** Strict pt-BR decimal input, optional correctly grouped thousands; never floating point. */
    fun parseDailyCents(input: String): Long? {
        val text = input.trim()
        if (text.length !in 1..32 || !ratePattern.matches(text)) return null
        val parts = text.replace(".", "").split(',')
        val cents = BigInteger(parts[0]) * BigInteger.valueOf(100) +
            BigInteger(parts.getOrElse(1) { "0" }.padEnd(2, '0'))
        return if (cents <= BigInteger.valueOf(Long.MAX_VALUE)) cents.toLong() else null
    }

    /** Blank means automatic; malformed or out-of-month counts are rejected, never clamped. */
    fun parseDays(input: String, month: YearMonth): Int? {
        val text = input.trim()
        if (text.isEmpty()) return null
        require(text.matches(Regex("[0-9]{1,2}"))) { "Informe dias inteiros ou deixe vazio." }
        return text.toInt().also {
            require(it in 0..month.lengthOfMonth()) { "Informe de 0 a ${month.lengthOfMonth()} dias." }
        }
    }

    fun validateDays(month: YearMonth, days: BenefitDays) {
        listOf(days.vr, days.vt).forEach {
            require(it == null || it in 0..month.lengthOfMonth()) { "Dias fora do mês selecionado." }
        }
    }

    /** Days of [month] that fall on one of [weekdays]; an empty set is valid and yields zero. */
    fun scheduledDays(month: YearMonth, weekdays: Set<DayOfWeek>): Int =
        (1..month.lengthOfMonth()).count { month.atDay(it).dayOfWeek in weekdays }

    fun automaticDays(month: YearMonth): Int = scheduledDays(month, WORKDAYS)

    fun estimate(month: YearMonth, settings: BenefitSettings): BenefitEstimate {
        val vrAutomatic = scheduledDays(month, settings.vrWeekdays)
        val vtAutomatic = scheduledDays(month, settings.vtWeekdays)
        val overrides = settings.overrides[month] ?: BenefitDays()
        validateDays(month, overrides)
        val vrDays = overrides.vr ?: vrAutomatic
        val vtDays = overrides.vt ?: vtAutomatic
        return BenefitEstimate(vrAutomatic, vtAutomatic, vrDays, vtDays,
            BigInteger.valueOf(settings.vrDailyCents) * BigInteger.valueOf(vrDays.toLong()),
            BigInteger.valueOf(settings.vtDailyCents) * BigInteger.valueOf(vtDays.toLong()))
    }
}
