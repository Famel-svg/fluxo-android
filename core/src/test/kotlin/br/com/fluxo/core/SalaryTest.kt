package br.com.fluxo.core

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class SalaryTest {
    @Test fun `monthly salary accepts grouped and plain pt-BR amounts as exact cents`() {
        mapOf("3.500,00" to 350000L, "3500" to 350000L, "3500,5" to 350050L, "12.345,67" to 1234567L,
            " 0,01 " to 1L, "1.000.000,00" to 100000000L).forEach { (text, cents) ->
            assertEquals(text, cents, SalaryPlanner.parseMonthlyCents(text))
        }
    }

    @Test fun `monthly salary rejects blank zero malformed and overflowing input`() {
        listOf("", "  ", "0", "0,00", "000", "-3500", "3,500.00", "35.00", "3.50", "1.23", "3500,123", "R$ 3500",
            "abc", "9".repeat(30), "92233720368547758,08").forEach {
            assertNull(it, SalaryPlanner.parseMonthlyCents(it))
        }
    }

    @Test fun `generalised parser keeps the daily rate behaviour including zero`() {
        assertEquals(0L, MoneyParser.parseCents("0"))
        assertEquals(0L, BenefitCalculator.parseDailyCents("0,00"))
        assertEquals(Long.MAX_VALUE, MoneyParser.parseCents("92.233.720.368.547.758,07"))
        assertNull(MoneyParser.parseCents("92.233.720.368.547.758,08"))
        assertEquals(MoneyParser.parseCents("39,00"), BenefitCalculator.parseDailyCents("39,00"))
    }

    @Test fun `share of salary is an integer percentage rounded down`() {
        assertEquals(50, SalaryPlanner.percentSpent(350000, BigInteger.valueOf(175000)))
        assertEquals(0, SalaryPlanner.percentSpent(350000, BigInteger.ZERO))
        assertEquals(0, SalaryPlanner.percentSpent(350000, BigInteger.valueOf(3499)))
        assertEquals(1, SalaryPlanner.percentSpent(350000, BigInteger.valueOf(3500)))
        assertEquals(99, SalaryPlanner.percentSpent(350000, BigInteger.valueOf(349999)))
        assertEquals(100, SalaryPlanner.percentSpent(350000, BigInteger.valueOf(350000)))
        assertEquals(150, SalaryPlanner.percentSpent(350000, BigInteger.valueOf(525000)))
    }

    @Test fun `share is null without a salary and safe for huge values`() {
        assertNull(SalaryPlanner.percentSpent(0, BigInteger.valueOf(100)))
        assertNull(SalaryPlanner.percentSpent(-5, BigInteger.valueOf(100)))
        assertEquals(0, SalaryPlanner.percentSpent(100, BigInteger.valueOf(-500)))
        assertEquals(Int.MAX_VALUE, SalaryPlanner.percentSpent(1, BigInteger.TEN.pow(40)))
        assertEquals(100, SalaryPlanner.percentSpent(Long.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE)))
        assertEquals(200, SalaryPlanner.percentSpent(Long.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE) * BigInteger.TWO))
    }
}
