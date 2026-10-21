package br.com.fluxo.core

import org.junit.Assert.*
import org.junit.Test

/** Formats seen in an owner triage export; merchants and card digits are synthetic. */
class WalletReceiptTest {
    private val wallet = WalletReceipt.GOOGLE_WALLET

    @Test fun `tap to pay receipt becomes an expense for review`() {
        val parsed = WalletReceipt.parse("MERCADO EXEMPLO\nR$ 7,98 com Cartão C6 Mastercard ••1234")!!
        assertEquals(798L, parsed.amountCents)
        assertEquals(Direction.EXPENSE, parsed.direction)
        assertEquals(PaymentMethod.UNKNOWN, parsed.method)
        assertEquals(ReviewStatus.REVIEW, parsed.status)
        assertEquals(WalletReceipt.REASON, parsed.reason)
    }

    @Test fun `thousands and card names with digits are read`() {
        assertEquals(123456L, WalletReceipt.parse("LOJA\nR$ 1.234,56 com Nubank Mastercard Gold ••5678")?.amountCents)
    }

    @Test fun `wallet text that is not a receipt is not a payment`() {
        assertNull(WalletReceipt.parse("Cartão adicionado\nSeu cartão ••1234 está pronto para pagamentos"))
        assertNull(WalletReceipt.parse("LOJA\nR$ 7,98 com Cartão ••1234 e R$ 2,00 de taxa"))
        assertNull(WalletReceipt.parse("LOJA\nR$ 7,9 com Cartão ••1234"))
        assertNull(WalletReceipt.parse(""))
    }

    @Test fun `only wallet packages use the receipt reader`() {
        val text = "MERCADO EXEMPLO\nR$ 7,98 com Cartão C6 Mastercard ••1234"
        assertTrue(WalletReceipt.screen(wallet, text) is NotificationParser.Screening.Accepted)
        assertEquals(NotificationParser.Screening.Ignored(IgnoreReason.NO_FINANCIAL_WORD),
            WalletReceipt.screen("com.whatsapp", text))
        assertEquals("ACCEPTED", NotificationTriage.verdict(wallet, text))
    }

    @Test fun `issuer purchase notifications from the export stay confirmed credit`() {
        listOf(
            "Compra no crédito aprovada\nSua compra no cartão final 1234 no valor de R$ 7,98, dia 20/10/2026 às 13:16, em MERCADO EXEMPLO    SAO PAULO     BRA, foi aprovada.",
            "Compra no crédito aprovada\nCompra de R$ 16,97 APROVADA em MERCADO EXEMPLO para o cartão com final 5678.",
        ).forEach { text ->
            val parsed = NotificationParser.parse(text)!!
            assertEquals(Direction.EXPENSE, parsed.direction)
            assertEquals(PaymentMethod.CREDIT_CARD, parsed.method)
            assertEquals(ReviewStatus.CONFIRMED, parsed.status)
        }
    }

    @Test fun `credit limit offer is not a transaction`() {
        val offer = "99：Chance de ganhar limite extra\nVocê pode aumentar seu limite em até R$1000. Acesse Empréstimos e toque em \"Limite extra\". (Suj.à análise de crédito)"
        assertFalse(WalletReceipt.screen("com.taxis99", offer) is NotificationParser.Screening.Accepted)
    }
}
