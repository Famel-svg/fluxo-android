package br.com.fluxo.core

import java.math.BigInteger
import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class BenefitCalculatorTest {
    private val september = YearMonth.of(2026, 9)

    @Test fun `weekdays include holidays and handle leap years and year change`() {
        mapOf("2026-09" to 22, "2024-02" to 21, "2025-02" to 20,
            "2026-12" to 23, "2027-01" to 21).forEach { (month, expected) ->
            assertEquals(month, expected, BenefitCalculator.automaticDays(YearMonth.parse(month)))
        }
    }

    @Test fun `defaults are zero with automatic independent counts`() {
        val result = BenefitCalculator.estimate(september, BenefitSettings())
        assertEquals(22, result.automaticDays)
        assertEquals(22, result.vrDays)
        assertEquals(22, result.vtDays)
        assertEquals(BigInteger.ZERO, result.totalCents)
    }

    @Test fun `separate overrides belong to their month and allow zero and last day`() {
        val settings = BenefitSettings(2500, 1000, mapOf(
            september to BenefitDays(0, 30), september.plusMonths(1) to BenefitDays(5, null)))
        val result = BenefitCalculator.estimate(september, settings)
        assertEquals(0, result.vrDays)
        assertEquals(30, result.vtDays)
        assertEquals(BigInteger.ZERO, result.vrCents)
        assertEquals(BigInteger.valueOf(30000), result.vtCents)
        val october = BenefitCalculator.estimate(september.plusMonths(1), settings)
        assertEquals(5, october.vrDays)
        assertEquals(22, october.vtDays)
        assertEquals(22, BenefitCalculator.estimate(september.plusYears(1), settings).vrDays)
    }

    @Test fun `clearing each override restores automatic independently`() {
        val cleared = BenefitCalculator.parseDays(" ", september)
        assertNull(cleared)
        assertEquals(0, BenefitCalculator.parseDays("0", september))
        assertEquals(30, BenefitCalculator.parseDays("30", september))
        val vrAuto = BenefitCalculator.estimate(september,
            BenefitSettings(100, 200, mapOf(september to BenefitDays(cleared, 3))))
        assertEquals(22, vrAuto.vrDays)
        assertEquals(3, vrAuto.vtDays)
        val vtAuto = BenefitCalculator.estimate(september,
            BenefitSettings(100, 200, mapOf(september to BenefitDays(2, cleared))))
        assertEquals(2, vtAuto.vrDays)
        assertEquals(22, vtAuto.vtDays)
        assertEquals(BigInteger.valueOf(4600), vtAuto.totalCents)
    }

    @Test fun `days reject negatives fractions malformed input and month overflow`() {
        listOf("-1", "31", "1,5", "1.5", "a", "+1", "9999999999999").forEach {
            rejects { BenefitCalculator.parseDays(it, september) }
        }
        assertEquals(29, BenefitCalculator.parseDays("29", YearMonth.of(2024, 2)))
        rejects { BenefitCalculator.parseDays("29", YearMonth.of(2025, 2)) }
        listOf(BenefitDays(-1, null), BenefitDays(null, -1), BenefitDays(31, 0), BenefitDays(0, 31)).forEach {
            rejects { BenefitSettings(overrides = mapOf(september to it)) }
        }
    }

    @Test fun `pt BR rates are exact nonnegative and range checked`() {
        mapOf("0" to 0L, "0,01" to 1L, " 25,5 " to 2550L, "1.234,56" to 123456L,
            "92233720368547758,07" to Long.MAX_VALUE).forEach { (text, cents) ->
            assertEquals(text, cents, BenefitCalculator.parseDailyCents(text))
        }
        listOf("", " ", "-1", "+1", "NaN", "1e3", "1.50", "1,234", "1.23,45",
            "R$ 10,00", "1 000,00", "92233720368547758,08", "9".repeat(1000)).forEach {
            assertNull(it, BenefitCalculator.parseDailyCents(it))
        }
        rejects { BenefitSettings(vrDailyCents = -1) }
        rejects { BenefitSettings(vtDailyCents = -1) }
    }

    @Test fun `both multiplication and sum exceed Long without overflow`() {
        val month = YearMonth.of(2026, 12)
        val result = BenefitCalculator.estimate(month,
            BenefitSettings(Long.MAX_VALUE, Long.MAX_VALUE, mapOf(month to BenefitDays(31, 31))))
        val product = BigInteger("285924533142498050017")
        assertEquals(product, result.vrCents)
        assertEquals(product, result.vtCents)
        assertEquals(BigInteger("571849066284996100034"), result.totalCents)
    }

    @Test fun `each benefit counts its own weekdays`() {
        val monThu = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
        val settings = BenefitSettings(3900, 1080, vtWeekdays = monThu)
        // September 2026 starts on a Tuesday: 22 Monday-Friday days, 18 Monday-Thursday days.
        val sep = BenefitCalculator.estimate(september, settings)
        assertEquals(22, sep.vrAutomaticDays)
        assertEquals(18, sep.vtAutomaticDays)
        assertEquals(BigInteger.valueOf(85800), sep.vrCents)
        assertEquals(BigInteger.valueOf(19440), sep.vtCents)
        assertEquals(BigInteger.valueOf(105240), sep.totalCents)
        // October 2026 starts on a Thursday: 22 Monday-Friday days but only 17 Monday-Thursday days.
        val oct = BenefitCalculator.estimate(YearMonth.of(2026, 10), settings)
        assertEquals(22, oct.vrDays)
        assertEquals(17, oct.vtDays)
        assertEquals(BigInteger.valueOf(85800), oct.vrCents)
        assertEquals(BigInteger.valueOf(18360), oct.vtCents)
        assertEquals(BigInteger.valueOf(104160), oct.totalCents)
    }

    @Test fun `scheduled days cover leap february and weekends`() {
        val feb2024 = YearMonth.of(2024, 2)
        assertEquals(21, BenefitCalculator.scheduledDays(feb2024, BenefitCalculator.WORKDAYS))
        assertEquals(29, BenefitCalculator.scheduledDays(feb2024, DayOfWeek.values().toSet()))
        assertEquals(4, BenefitCalculator.scheduledDays(feb2024, setOf(DayOfWeek.SATURDAY)))
        assertEquals(5, BenefitCalculator.scheduledDays(feb2024, setOf(DayOfWeek.THURSDAY)))
        assertEquals(20, BenefitCalculator.scheduledDays(YearMonth.of(2025, 2), BenefitCalculator.WORKDAYS))
    }

    @Test fun `empty weekday set is valid and yields zero`() {
        val result = BenefitCalculator.estimate(september,
            BenefitSettings(3900, 1080, vrWeekdays = emptySet()))
        assertEquals(0, result.vrDays)
        assertEquals(22, result.vtDays)
        assertEquals(BigInteger.ZERO, result.vrCents)
        assertEquals(0, BenefitCalculator.scheduledDays(september, emptySet()))
    }

    @Test fun `manual override wins over the weekday schedule`() {
        val settings = BenefitSettings(3900, 1080, mapOf(september to BenefitDays(vr = 3, vt = 0)),
            vrWeekdays = setOf(DayOfWeek.MONDAY), vtWeekdays = BenefitCalculator.WORKDAYS)
        val result = BenefitCalculator.estimate(september, settings)
        assertEquals(4, result.vrAutomaticDays)
        assertEquals(3, result.vrDays)
        assertEquals(0, result.vtDays)
        assertEquals(22, result.vtAutomaticDays)
        assertEquals(BigInteger.valueOf(11700), result.totalCents)
    }

    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected IllegalArgumentException") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }
}
