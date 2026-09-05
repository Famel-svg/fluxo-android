package br.com.fluxo.core

import org.junit.Assert.*
import org.junit.Test

class NotificationParserTest {
    @Test fun `synthetic bank Pix receipts are income`() {
        listOf("Nubank Você recebeu um Pix de R$ 1.234,56", "C6 Bank Pix recebido: R$ 10,01", "Itaú Pix recebido no valor de R$ 300,00").forEach {
            val result = requireNotNull(NotificationParser.parse(it))
            assertEquals(Direction.INCOME, result.direction)
            assertEquals(PaymentMethod.PIX, result.method)
            assertEquals(ReviewStatus.CONFIRMED, result.status)
        }
        assertEquals(123456L, NotificationParser.parse("Você recebeu Pix R$ 1.234,56")?.amountCents)
    }

    @Test fun `Pix sent is cash expense`() {
        val result = requireNotNull(NotificationParser.parse("Pix enviado R$ 50,90"))
        assertEquals(5090L, result.amountCents)
        assertEquals(Direction.EXPENSE, result.direction)
        assertEquals(PaymentMethod.PIX, result.method)
        assertEquals(ReviewStatus.CONFIRMED, result.status)
    }

    @Test fun `credit and debit purchases have separate methods`() {
        mapOf("crédito" to PaymentMethod.CREDIT_CARD, "débito" to PaymentMethod.DEBIT_CARD).forEach { (label, method) ->
            val result = requireNotNull(NotificationParser.parse("Compra aprovada no $label de R$ 75,12"))
            assertEquals(Direction.EXPENSE, result.direction)
            assertEquals(method, result.method)
            assertEquals(ReviewStatus.CONFIRMED, result.status)
        }
    }

    @Test fun `credit in account does not mean credit card`() {
        val result = requireNotNull(NotificationParser.parse("Crédito em conta R$ 100,00"))
        assertEquals(Direction.INCOME, result.direction)
        assertEquals(PaymentMethod.UNKNOWN, result.method)
        assertEquals(ReviewStatus.REVIEW, result.status)
    }

    @Test fun `unconfirmed and debt events never enter totals`() {
        listOf("Pix agendado", "Pix enviado não concluído", "Pix pendente", "Compra recusada no crédito", "Compra não autorizada no débito", "Pagamento de fatura realizado", "Estorno de compra no crédito", "Devolução de Pix recebido", "Pix enviado em processamento", "Solicitação de Pix", "Pix não foi enviado", "Pix será recebido").forEach {
            assertEquals(it, ReviewStatus.REVIEW, NotificationParser.parse("$it R$ 25,00")?.status)
        }
    }

    @Test fun `multiple amounts even identical require review`() {
        listOf("Pix recebido R$ 10,00 saldo R$ 100,00", "Pix recebido R$ 10,00 R$ 10,00").forEach {
            val result = requireNotNull(NotificationParser.parse(it))
            assertNull(result.amountCents)
            assertEquals(ReviewStatus.REVIEW, result.status)
        }
    }

    @Test fun `analysis and contextual negations override positive movement words`() {
        listOf(
            "Pix recebido em análise R$ 25,00",
            "Compra aprovada no crédito sob análise R$ 25,00",
            "Compra aprovada no débito em verificação R$ 25,00",
            "Pix recebido não confirmado R$ 25,00",
            "Pix recebido não foi confirmado R$ 25,00",
            "Pix recebido não foi ainda confirmado R$ 25,00",
            "Pix enviado ainda não está confirmado R$ 25,00",
            "Compra aprovada no crédito sem confirmação R$ 25,00",
            "Você não recebeu Pix R$ 25,00",
            "Você nunca enviou Pix R$ 25,00",
            "Você jamais pagou no débito esta compra R$ 25,00",
            "Pix recebido, mas nem foi efetivado R$ 25,00",
        ).forEach { text ->
            val result = requireNotNull(NotificationParser.parse(text))
            assertEquals(text, 2500L, result.amountCents)
            assertEquals(text, ReviewStatus.REVIEW, result.status)
            assertTrue(text, result.reason.isNotBlank())
        }
    }

    @Test fun `promotional footers preserve possible transactions for review`() {
        mapOf(
            "Compra aprovada no crédito R$ 75,12. Aproveite nossas ofertas!" to PaymentMethod.CREDIT_CARD,
            "Ganhe cashback! Compra aprovada no débito R$ 75,12" to PaymentMethod.DEBIT_CARD,
            "Pix recebido R$ 75,12. Conheça esta promoção" to PaymentMethod.PIX,
            "Pix enviado R$ 75,12. Simule seu empréstimo" to PaymentMethod.PIX,
        ).forEach { (text, method) ->
            val result = requireNotNull(NotificationParser.parse(text))
            assertEquals(text, 7512L, result.amountCents)
            assertEquals(text, method, result.method)
            assertEquals(text, ReviewStatus.REVIEW, result.status)
            assertTrue(text, result.reason.contains("oferta"))
        }
        val multiple = requireNotNull(NotificationParser.parse(
            "Compra aprovada no crédito R$ 75,12. Ganhe cashback de R$ 5,00",
        ))
        assertNull(multiple.amountCents)
        assertEquals(ReviewStatus.REVIEW, multiple.status)
    }

    @Test fun `isolated promotions without movement evidence remain ignored`() {
        listOf(
            "Aproveite cashback na compra no crédito de R$ 75,12",
            "Receba um Pix de R$ 25,00 nesta promoção",
            "Limite aprovado para compra no crédito R$ 500,00",
        ).forEach { assertNull(it, NotificationParser.parse(it)) }
    }

    @Test fun `invalid numeric amounts are never partially parsed`() {
        listOf("0,00", "-1,00", "+1,00", "1.23,45", "1,2", "1,234", "100", "1.00", "9.223.372.036.854.775.808,00", "1,00,25").forEach {
            val result = requireNotNull(NotificationParser.parse("Pix recebido R$ $it"))
            assertNull(it, result.amountCents)
            assertEquals(it, ReviewStatus.REVIEW, result.status)
        }
    }

    @Test fun `integer conversion handles cent and Long boundary`() {
        assertEquals(1L, NotificationParser.parse("Pix recebido R$ 0,01")?.amountCents)
        assertEquals(Long.MAX_VALUE, NotificationParser.parse("Pix recebido R$ 92.233.720.368.547.758,07")?.amountCents)
        assertNull(NotificationParser.parse("Pix recebido R$ 92.233.720.368.547.758,08")?.amountCents)
    }

    @Test fun `missing direction and mixed methods require review`() {
        listOf("Pix R$ 10,00", "Compra no cartão R$ 10,00", "Pix recebido e enviado R$ 10,00", "Compra aprovada no crédito e no débito R$ 10,00", "Pix recebido cartão de crédito R$ 10,00", "Pix recebido valor oculto").forEach {
            assertEquals(it, ReviewStatus.REVIEW, NotificationParser.parse(it)?.status)
        }
    }

    @Test fun `marketing and unrelated texts are ignored`() {
        listOf("", "Mensagem de contato R$ 100,00", "Receba Pix e ganhe R$ 10,00", "Aproveite compra com desconto R$ 30,00", "Limite disponível para compra R$ 500,00", "a".repeat(20_000)).forEach {
            assertNull(NotificationParser.parse(it))
        }
    }

    @Test fun `questions collection requests and ineffective Pix require review`() {
        listOf(
            "Pix enviado? Confira como acompanhar R$ 50,00",
            "Pix recebido não efetivado R$ 50,00",
            "Pix recebido não foi efetivado R$ 50,00",
            "Cobrança Pix recebido R$ 50,00",
            "Pedido de pagamento Pix recebido R$ 50,00",
            "Compra aprovada R$ 50,00",
        ).forEach {
            assertEquals(it, ReviewStatus.REVIEW, NotificationParser.parse(it)?.status)
        }
        assertEquals(PaymentMethod.UNKNOWN, NotificationParser.parse("Compra aprovada R$ 50,00")?.method)
    }

    @Test fun `unicode whitespace and uppercase preserve correct amount`() {
        val result = requireNotNull(NotificationParser.parse("PIX\nRECEBIDO R$\u00a01.000,99"))
        assertEquals(100099L, result.amountCents)
        assertEquals(ReviewStatus.CONFIRMED, result.status)
        assertEquals(1000L, NotificationParser.parse("Pix recebido R$ 10,00.")?.amountCents)
    }
}
