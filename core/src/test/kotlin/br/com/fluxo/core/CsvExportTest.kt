package br.com.fluxo.core

import java.io.StringWriter
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CsvExportTest {
    private fun row(description: String = "Café") = Transaction(
        "secret-id", "private-fingerprint", "com.itau", 12345, Direction.EXPENSE,
        PaymentMethod.CREDIT_CARD, ReviewStatus.CONFIRMED, 0, description,
    )
    private fun export(rows: List<Transaction>) = StringWriter().also { CsvExport.write(rows, it, ZoneId.of("UTC")) }.toString()

    @Test fun `exports exact decimal comma UTF8 header and localized values`() {
        val csv = export(listOf(row()))
        assertTrue(csv.startsWith("\uFEFF\"Data\";\"Banco\""))
        assertTrue(csv.contains("\"1970-01-01 00:00:00Z\";\"Itaú\";\"Café\""))
        assertTrue(csv.contains("\"123,45\";\"Saída\";\"Cartão de crédito\";\"Confirmado\""))
        assertTrue(csv.endsWith("\r\n"))
        assertFalse(csv.contains("secret-id"))
        assertFalse(csv.contains("private-fingerprint"))
    }

    @Test fun `quotes delimiters newlines and embedded quotes without corrupting cells`() {
        assertTrue(export(listOf(row("Café; \"especial\"\nLoja"))).contains("\"Café; \"\"especial\"\"\nLoja\""))
    }

    @Test fun `neutralizes spreadsheet formulas including whitespace prefixes`() {
        listOf("=1+1", "+SUM(A1)", "-1+2", "@SUM(A1)", "  =1", "\t=1", "\r=1", "\n=1").forEach {
            assertTrue(it, export(listOf(row(it))).contains("\"'$it\""))
        }
    }

    @Test fun `review has blank amount and includes reason`() {
        val csv = export(listOf(row().copy(amountCents = null, status = ReviewStatus.REVIEW, reason = "Valor oculto")))
        assertTrue(csv.contains(";\"\";\"Saída\""))
        assertTrue(csv.contains("\"Revisar\";\"Valor oculto\""))
    }

    @Test fun `preserves Long precision and writes headers for empty ledger`() {
        assertTrue(export(listOf(row().copy(amountCents = Long.MAX_VALUE))).contains("\"92233720368547758,07\""))
        assertEquals(1, export(emptyList()).split("\r\n").count { it.isNotEmpty() })
    }
}
