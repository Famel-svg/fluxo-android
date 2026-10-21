package br.com.fluxo.core

import java.io.StringWriter
import org.junit.Assert.*
import org.junit.Test

class NotificationTriageTest {
    private fun item(pkg: String, decision: TriageDecision, text: String = "Compra aprovada", at: Long = 0) =
        TriageItem(1, pkg, null, "Banco", text, at, "NO_AMOUNT", decision)

    private fun export(items: List<TriageItem>) = StringWriter().also { NotificationTriage.writeJson(items, it, 0) }.toString()

    @Test fun `approval always needs review and keeps parsed amount`() {
        val parsed = NotificationTriage.approved("com.nu.production", "Pix recebido de R$ 50,00")
        assertEquals(5000L, parsed.amountCents)
        assertEquals(Direction.INCOME, parsed.direction)
        assertEquals(ReviewStatus.REVIEW, parsed.status)
        assertTrue(parsed.reason.startsWith(NotificationTriage.APPROVED_REASON))
    }

    @Test fun `approval of unrecognized text has no invented amount`() {
        val parsed = NotificationTriage.approved("com.example.vr", "Você usou seu saldo")
        assertNull(parsed.amountCents)
        assertEquals(Direction.UNKNOWN, parsed.direction)
        assertEquals(PaymentMethod.UNKNOWN, parsed.method)
        assertEquals(NotificationTriage.APPROVED_REASON, parsed.reason)
    }

    @Test fun `verdict names parser outcome`() {
        assertEquals("ACCEPTED", NotificationTriage.verdict("com.nu.production", "Pix recebido de R$ 50,00"))
        assertEquals(IgnoreReason.NO_FINANCIAL_WORD.name, NotificationTriage.verdict("com.whatsapp", "Nova mensagem de Ana"))
    }

    @Test fun `json groups apps and escapes content`() {
        val json = export(listOf(
            item("com.b", TriageDecision.KEPT, "linha \"1\"\nlinha\t2\\", at = 2),
            item("com.a", TriageDecision.DISCARDED, at = 1),
            item("com.b", TriageDecision.PENDING, "ctrl\u0001", at = 3),
        ))
        assertTrue(json.contains("\"format\": \"fluxo-triage\""))
        assertTrue(json.indexOf("\"packageName\": \"com.a\"") < json.indexOf("\"packageName\": \"com.b\""))
        assertTrue(json.contains("\"total\": 2, \"decisions\": {\"PENDING\": 1, \"KEPT\": 1, \"DISCARDED\": 0, \"AUTO_STORED\": 0}"))
        assertTrue(json.contains("\"text\": \"linha \\\"1\\\"\\nlinha\\t2\\\\\""))
        assertTrue(json.contains("\"text\": \"ctrl\\u0001\""))
        assertTrue(json.contains("\"appLabel\": null"))
    }

    @Test fun `empty export is valid`() {
        val json = export(emptyList())
        assertTrue(json.contains("\"apps\": [],"))
        assertTrue(json.contains("\"notifications\": []\n}"))
    }
}
