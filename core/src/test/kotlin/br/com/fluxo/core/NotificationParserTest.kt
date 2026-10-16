package br.com.fluxo.core

import org.junit.Assert.*
import org.junit.Test

class NotificationParserTest {
    @Test fun `unprefixed decimal candidate is transient review evidence without inferred amount`() {
        listOf("10,00", "1.234,56", "(10,00)", "10,00.").forEach { token ->
            val result = requireNotNull(NotificationParser.parse("Pix recebido análise $token"))
            assertTrue(token, result.hasAmountCandidate)
            assertNull(token, result.amountCents)
            assertEquals(token, Direction.INCOME, result.direction)
            assertEquals(token, ReviewStatus.REVIEW, result.status)
        }
        val noAmount = requireNotNull(NotificationParser.parse("Pix recebido valor oculto"))
        assertFalse(noAmount.hasAmountCandidate)
        assertNull(noAmount.amountCents)
        assertEquals(Direction.INCOME, noAmount.direction)
        assertEquals(ReviewStatus.REVIEW, noAmount.status)
    }

    @Test fun `unprefixed candidates require one whole well formed decimal token`() {
        listOf("10,00e3", "10,00abc", "10,00/20,00", "10,00%", "10,00..",
            "10,00_20", "10,00+20,00", "-10,00", "+10,00", "1.23,45", "1,234",
            "100", "1.00", "10,00 20,00").forEach { token ->
            val result = requireNotNull(NotificationParser.parse("Pix recebido $token"))
            assertFalse(token, result.hasAmountCandidate)
            assertNull(token, result.amountCents)
            assertEquals(token, ReviewStatus.REVIEW, result.status)
        }
    }

    @Test fun `currency evidence does not imply valid money or a known direction`() {
        listOf("R$ 10,00 saldo R$ 100,00", "R$ 10,00abc", "R$").forEach { token ->
            val result = requireNotNull(NotificationParser.parse("Pix recebido $token"))
            assertTrue(token, result.hasAmountCandidate)
            assertNull(token, result.amountCents)
            assertEquals(token, ReviewStatus.REVIEW, result.status)
        }
        listOf("Pix R$10,00", "Pix pendente R$ 50,00").forEach { text ->
            val result = requireNotNull(NotificationParser.parse(text))
            assertTrue(text, result.hasAmountCandidate)
            assertEquals(text, Direction.UNKNOWN, result.direction)
            assertEquals(text, ReviewStatus.REVIEW, result.status)
            assertEquals(text, text.contains("pendente"), result.hasEventStateSignal)
        }
        assertNull(NotificationParser.parse("Receba um Pix de 10,00 nesta promoção"))
        assertNull(NotificationParser.parse("Mensagem de contato 10,00"))
    }

    @Test fun `existing constructor calls derive default candidate from amount only`() {
        val withAmount = ParsedNotification(1000L, Direction.INCOME, PaymentMethod.PIX,
            ReviewStatus.CONFIRMED, "")
        val withoutAmount = ParsedNotification(null, Direction.INCOME, PaymentMethod.PIX,
            ReviewStatus.REVIEW, "Valor ausente")
        assertTrue(withAmount.hasAmountCandidate)
        assertFalse(withoutAmount.hasAmountCandidate)
    }

    @Test fun `numeric suffixes cannot become truncated confirmed amounts`() {
        listOf("10,00e3", "10,00abc", "10,00/20,00", "10,00%", "10,00..", "10,00_20", "10,00+20,00").forEach { token ->
            val result = requireNotNull(NotificationParser.parse("Pix recebido R$ $token"))
            assertNull(token, result.amountCents)
            assertEquals(token, ReviewStatus.REVIEW, result.status)
        }
    }

    @Test fun `future and conditional movements need review`() {
        listOf(
            "Pix recebido será confirmado", "Pix recebido sera confirmado",
            "Pix recebido será ainda confirmado", "Pix recebido será efetivado",
            "Compra aprovada no crédito será realizada", "Pix recebido seria confirmado",
            "Se você recebeu Pix", "Caso tenha recebido Pix", "Pix recebido se confirmado",
        ).forEach { prefix ->
            val result = requireNotNull(NotificationParser.parse("$prefix R$ 10,00"))
            assertEquals(prefix, 1000L, result.amountCents)
            assertEquals(prefix, ReviewStatus.REVIEW, result.status)
        }
    }

    @Test fun `ordinary punctuation around valid amounts stays supported`() {
        listOf("10,00", "10,00.", "10,00!", "10,00;", "10,00)").forEach { token ->
            val result = requireNotNull(NotificationParser.parse("Pix recebido R$ $token"))
            assertEquals(token, 1000L, result.amountCents)
            assertEquals(token, ReviewStatus.CONFIRMED, result.status)
        }
    }

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

    @Test fun `common bank wording keeps its amount and direction`() {
        data class Case(val text: String, val cents: Long, val direction: Direction,
            val method: PaymentMethod?, val status: ReviewStatus)
        listOf(
            Case("Compra no crédito aprovada\nSua compra no cartão final 1234 no valor de R$ 35,00, dia 10/10 às 12:30, em RESTAURANTE Y, foi aprovada.",
                3500, Direction.EXPENSE, PaymentMethod.CREDIT_CARD, ReviewStatus.CONFIRMED),
            Case("Itaú\nCompra aprovada no cartão final 5678, R$ 120,00, POSTO BR, 10/10 14:22.",
                12000, Direction.EXPENSE, null, ReviewStatus.REVIEW),
            Case("Compra no débito\nCompra no débito de R$ 18,90 em FARMACIA Z aprovada.",
                1890, Direction.EXPENSE, PaymentMethod.DEBIT_CARD, ReviewStatus.CONFIRMED),
            Case("Pix realizado\nPix de R$ 75,00 para LOJA ABC realizado com sucesso.",
                7500, Direction.EXPENSE, PaymentMethod.PIX, ReviewStatus.CONFIRMED),
            Case("Boleto pago\nPagamento de boleto no valor de R$ 230,45 efetuado.",
                23045, Direction.EXPENSE, null, ReviewStatus.REVIEW),
            Case("Débito automático\nDébito de R$ 110,00 referente a CONTA DE LUZ realizado.",
                11000, Direction.EXPENSE, null, ReviewStatus.REVIEW),
            Case("Salário\nCrédito de salário de R$ 3.500,00 em sua conta.",
                350000, Direction.INCOME, null, ReviewStatus.REVIEW),
            Case("Compra internacional aprovada\nCompra de US$ 10,00 (R$ 55,30) aprovada em AMAZON.",
                5530, Direction.EXPENSE, null, ReviewStatus.REVIEW),
            Case("Pagamento de fatura\nRecebemos o pagamento da sua fatura no valor de R$ 900,00.",
                90000, Direction.UNKNOWN, null, ReviewStatus.REVIEW),
        ).forEach { case ->
            val result = requireNotNull(NotificationParser.parse(case.text)) { case.text }
            assertEquals(case.text, case.cents, result.amountCents)
            assertEquals(case.text, case.direction, result.direction)
            case.method?.let { assertEquals(case.text, it, result.method) }
            assertEquals(case.text, case.status, result.status)
            // Every case must pass the same gate that LedgerRepository.ingest applies.
            assertTrue(case.text, result.hasAmountCandidate)
            assertTrue(case.text, result.direction != Direction.UNKNOWN || result.hasEventStateSignal)
        }
        // Invoice wording keeps its dedicated reason even though it is now review evidence.
        assertEquals("Fatura, estorno ou devolução exige classificação manual.",
            NotificationParser.parse("Pagamento de fatura\nRecebemos o pagamento da sua fatura no valor de R$ 900,00.")?.reason)
    }

    @Test fun `wider bank wording never confirms events that did not happen`() {
        listOf(
            "Compra de R$ 40,00 em LOJA Q não foi aprovada.",
            "Seu Pix de R$ 75,00 para LOJA ABC será realizado amanhã.",
            "Pagamento de boleto no valor de R$ 230,45 não efetuado.",
            "Compra de R$ 10,00 em LOJA recusada. Tente outra forma de pagamento aprovada.",
            "Compra aprovada\nR$ 50 em UBER no cartão final 1234.",
        ).forEach { text ->
            val result = requireNotNull(NotificationParser.parse(text)) { text }
            assertEquals(text, ReviewStatus.REVIEW, result.status)
            assertTrue(text, result.reason.isNotBlank())
        }
        assertNull(NotificationParser.parse("Compra aprovada\nR$ 50 em UBER no cartão final 1234.")?.amountCents)
        assertNull(NotificationParser.parse("Aproveite: limite de crédito pré-aprovado de R$ 5.000,00"))
    }

    @Test fun `card and voucher purchases without payment verbs are confirmed expenses`() {
        mapOf(
            "Compra aprovada no crédito: R$ 35,00 em PADARIA X" to PaymentMethod.CREDIT_CARD,
            "Compra no débito de R$ 12,50 aprovada" to PaymentMethod.DEBIT_CARD,
            "Você gastou R$ 48,90 no cartão de crédito" to PaymentMethod.CREDIT_CARD,
            "Transação aprovada de R$ 20,00 em LOJA X no crédito" to PaymentMethod.CREDIT_CARD,
            "Transação realizada no débito R$ 20,00" to PaymentMethod.DEBIT_CARD,
            "Compra autorizada no débito R$ 20,00" to PaymentMethod.DEBIT_CARD,
            "Pagamento aprovado R$ 25,00 com VR" to PaymentMethod.MEAL_VOUCHER,
            "Compra aprovada com VR/Alimentação R$ 30,00" to PaymentMethod.MEAL_VOUCHER,
            "Uso do benefício de R$ 22,00 no vale refeição" to PaymentMethod.MEAL_VOUCHER,
        ).forEach { (text, method) ->
            val result = requireNotNull(NotificationParser.parse(text)) { text }
            assertEquals(text, Direction.EXPENSE, result.direction)
            assertEquals(text, method, result.method)
            assertEquals(text, ReviewStatus.CONFIRMED, result.status)
            assertTrue(text, NotificationParser.screen(text) is NotificationParser.Screening.Accepted)
        }
        // Without a method the purchase is still captured, but for review.
        listOf("Transação aprovada de R$ 20,00 em LOJA X", "Uso do benefício de R$ 22,00").forEach { text ->
            val result = requireNotNull(NotificationParser.parse(text)) { text }
            assertEquals(text, Direction.EXPENSE, result.direction)
            assertEquals(text, ReviewStatus.REVIEW, result.status)
        }
    }

    @Test fun `weak spending words need an amount and never confirm unfinished events`() {
        listOf(
            "Transação recusada no crédito R$ 20,00",
            "Transação não autorizada no débito R$ 20,00",
            "Transação agendada R$ 20,00",
            "Transação pendente com VR R$ 20,00",
            "Você gastou R$ 48,90 no crédito? Confirme",
            "Você não gastou R$ 48,90 no crédito",
            "Transação no crédito será aprovada R$ 20,00",
            "Fatura: você gastou R$ 900,00 no crédito",
        ).forEach { text ->
            assertEquals(text, ReviewStatus.REVIEW, NotificationParser.parse(text)?.status)
        }
        listOf("Transação aprovada no crédito", "Você gastou muito, veja dicas", "Uso do benefício liberado")
            .forEach { assertNull(it, NotificationParser.parse(it)) }
        assertNull(NotificationParser.parse("Ganhe cashback no uso do app R$ 10,00"))
    }

    @Test fun `sanitized synthetic multi category fixtures verify financial and non financial outcomes`() {
     // Financial positive/valid cases
     val positiveFixtures = mapOf(
         "bancos" to "Pix recebido R$ 150,00",
         "carteiras pagamentos" to "Compra aprovada no débito R$ 45,90",
         "compras entregas" to "Pagamento efetuado no crédito R$ 99,00",
         "e-mail" to "Transferência enviada R$ 200,00",
         "mensageria" to "Pix enviado R$ 12,50",
         "redes sociais" to "Compra realizada no crédito R$ 30,00",
         "sistema" to "Pix recebido R$ 5,00"
     )

     positiveFixtures.forEach { (category, text) ->
         val result = NotificationParser.parse(text)
         assertNotNull("Should parse financial event for $category", result)
         assertNotNull("Should extract valid cents for $category", result?.amountCents)
         assertTrue("Amount must be positive for $category", (result?.amountCents ?: 0) > 0)
     }

     // Non-financial negative cases & promotions (must be ignored/discarded by parser)
     val nonFinancialFixtures = mapOf(
         "promoção bancária" to "Aproveite seu limite de R$ 5.000,00 pré-aprovado",
         "compras marketing" to "Oferta imperdível: R$ 50,00 de desconto no próximo pedido",
         "mensageria conversa" to "Te transferi o valor mais cedo, me avise quando receber",
         "social ad" to "Ganhe até R$ 100,00 indicando amigos para a plataforma",
         "sistema alerta" to "Sua senha foi alterada com sucesso em 10/10"
     )

     nonFinancialFixtures.forEach { (category, text) ->
         val result = NotificationParser.parse(text)
         // Should either be null OR not have inferred/valid financial transaction amounts
         if (result != null) {
             assertNull("Non-financial or marketing text should not yield an inferred amount for $category", result.amountCents)
             assertEquals("Ambiguous/non-financial events must require review", ReviewStatus.REVIEW, result.status)
         }
     }
 }
}
