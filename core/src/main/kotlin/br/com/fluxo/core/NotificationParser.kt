package br.com.fluxo.core

import java.text.Normalizer
import java.util.Locale

/** Conservative rules, based on synthetic examples, not a bank API contract. */
object NotificationParser {
    // Consume the complete token: a valid numeric prefix is not a valid amount.
    private val currency = Regex("r\\$\\s*([+-]?\\s*[^\\s]+)")
    private val validAmount = Regex("(?:[0-9]+|[1-9][0-9]{0,2}(?:\\.[0-9]{3})+),[0-9]{2}")
    private val financial = Regex("\\b(pix|compra|pagamento|transferencia|deposito|credito em conta|credito de salario|debito automatico|creditad[oa]|debitad[oa]|estorno|devolucao|fatura)\\b")
    private val marketing = Regex("\\b(oferta|promocao|aproveite|contrate|ganhe|cashback|pre-aprovad[oa]|simule|receba|limite disponivel|limite aprovado)\\b")
    private val unconfirmed = Regex("\\b(recusad[oa]|negad[oa]|cancelad[oa]|agendad[oa]|agendamento|pendente|processando|em processamento|nao autorizad[oa]|nao realizad[oa]|nao conclu[ií]d[oa]|nao aprovad[oa]|falhou|falha|tentativa|solicitacao|solicitad[oa]|aguardando|vencimento|vencera)\\b")
    private val settlement = Regex("\\b(fatura|estorno|devolucao|reembolso|contestacao)\\b")
    private val notExecuted = Regex("\\b(nao (?:foi |foi possivel )?(?:enviad[oa]|recebid[oa]|efetuad[oa]|efetivad[oa]|realizad[oa]|concluid[oa]|autorizad[oa]|aprovad[oa]|enviar|realizar)|sera (?:enviad[oa]|recebid[oa]|debitad[oa]|creditad[oa]))\\b")
    private val collection = Regex("\\b(cobranca|pedido de pagamento|lembrete de pagamento)\\b")
    // Up to ten words inside one sentence: a period ends the clause unless it is a thousands separator.
    private const val gap = "(?:(?:[^ .!?;]|\\.(?=[0-9])){1,40} ){0,10}"
    private val incoming = Regex("\\b(recebeu|recebido|recebida|creditado|creditada|credito em conta|credito de salario|entrou na sua conta)\\b")
    private val outgoing = Regex(
        "\\b(enviou|enviado|enviada|debitado|debitada|pagou|pagamento realizado|pagamento efetuado|transferencia realizada|transferencia efetuada" +
            "|(?:pix|pagamento) ${gap}(?:realizad[oa]|efetuad[oa])|debito (?:automatico|de) ${gap}(?:realizad[oa]|efetuad[oa]))\\b",
    )
    private val completedPurchase = Regex("\\b(compra ${gap}(?:aprovada|realizada|efetuada)|comprou|pagamento aprovado)\\b")
    private val foreignCurrency = Regex("(?:\\bus\\$|\\b(?:usd|eur|gbp)\\b|[€£])")
    private val underReview = Regex("\\b(em analise|sob analise|em verificacao|aguardando confirmacao)\\b")
    private val futureOrConditional = Regex(
        """\b(?:sera|serao|seria|seriam|vai ser|podera ser|pode ser)\s+(?:[a-z]+\s+){0,4}(?:confirmad[oa]|aprovad[oa]|autorizad[oa]|concluid[oa]|efetivad[oa]|efetuad[oa]|realizad[oa]|enviad[oa]|recebid[oa]|creditad[oa]|debitad[oa])\b|\b(?:se|caso)\s+(?:[a-z]+\s+){0,4}(?:recebeu|enviou|pagou|comprou|recebid[oa]|enviad[oa]|confirmad[oa]|aprovad[oa]|realizad[oa])\b""",
    )
    // Limit the context to one clause; negation may precede auxiliaries and adverbs.
    private val negatedMovement = Regex(
        """\b(?:nao|nunca|jamais|nem|sem)\s+(?:[a-z]+\s+){0,4}(?:confirmad[oa]|confirmacao|aprovad[oa]|aprovacao|autorizad[oa]|autorizacao|concluid[oa]|conclusao|efetivad[oa]|efetuad[oa]|realizad[oa]|enviad[oa]|recebid[oa]|creditad[oa]|debitad[oa]|recebeu|enviou|pagou|comprou)\b""",
    )

    fun parse(text: String): ParsedNotification? {
        // Notification text is untrusted. Large messages need no expensive pattern work.
        if (text.isBlank() || text.length > 16_384) return null
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).replace('\u00a0', ' ')
            .replace(Regex("\\s+"), " ").trim()
        if (!financial.containsMatchIn(normalized)) return null

        val amounts = currency.findAll(normalized).map {
            // Permit sentence punctuation, but not repeated decimal separators or suffixes.
            // A single trailing comma or period closes the sentence, e.g. "R$ 35,00, dia 10/10".
            it.groupValues[1].trimEnd('!', '?', ';', ':', ')', ']', '}').let { token ->
                if (token.endsWith('.') || token.endsWith(',')) token.dropLast(1) else token
            }
        }.toList()
        val amount = amounts.singleOrNull()?.let(::toCents)
        // An unprefixed decimal is evidence for review, never a source of amountCents.
        // Match whole tokens to reject numeric prefixes, suffixes and arithmetic expressions.
        val hasAmountCandidate = Regex("r\\$").containsMatchIn(normalized) || normalized.split(' ').count { token ->
            validAmount.matches(token.trimStart('(', '[', '{')
                .trimEnd('!', '?', ';', ':', ')', ']', '}').removeSuffix("."))
        } == 1
        val pix = Regex("\\bpix\\b").containsMatchIn(normalized)
        val credit = Regex("\\b(no credito|cartao (?:de )?credito|funcao credito)\\b").containsMatchIn(normalized)
        val debit = Regex("\\b(no debito|cartao (?:de )?debito|funcao debito)\\b").containsMatchIn(normalized)
        val methods = listOfNotNull(
            PaymentMethod.PIX.takeIf { pix },
            PaymentMethod.CREDIT_CARD.takeIf { credit },
            PaymentMethod.DEBIT_CARD.takeIf { debit },
        )
        val method = methods.singleOrNull() ?: PaymentMethod.UNKNOWN
        val income = incoming.containsMatchIn(normalized)
        val expense = outgoing.containsMatchIn(normalized) || completedPurchase.containsMatchIn(normalized)
        val hasMarketing = marketing.containsMatchIn(normalized)
        // A promotional footer must not hide a possible transaction. Pure offers remain ignored.
        if (hasMarketing && !income && !expense) return null
        val direction = when {
            income && !expense -> Direction.INCOME
            expense && !income -> Direction.EXPENSE
            else -> Direction.UNKNOWN
        }
        val stateSignal = unconfirmed.containsMatchIn(normalized) ||
            notExecuted.containsMatchIn(normalized) || underReview.containsMatchIn(normalized) ||
            negatedMovement.containsMatchIn(normalized) || futureOrConditional.containsMatchIn(normalized)
        val reason = when {
            '?' in normalized -> "Mensagem contém pergunta; confirme se houve movimentação."
            collection.containsMatchIn(normalized) -> "Cobrança ou pedido de pagamento não comprova movimentação."
            stateSignal ->
                "Evento pendente, recusado ou agendado; confirme a movimentação."
            settlement.containsMatchIn(normalized) -> "Fatura, estorno ou devolução exige classificação manual."
            hasMarketing -> "Mensagem mistura movimentação e oferta; confirme os dados da transação."
            foreignCurrency.containsMatchIn(normalized) -> "Valor em moeda estrangeira; confirme o valor em reais."
            amounts.size > 1 -> "Mais de um valor monetário; selecione o valor da transação."
            amount == null -> "Valor ausente, inválido ou fora do limite."
            methods.size > 1 -> "Mais de uma modalidade; confirme o meio de pagamento."
            direction == Direction.UNKNOWN -> "Direção da movimentação não identificada com segurança."
            method == PaymentMethod.UNKNOWN -> "Meio de pagamento não identificado; confirme a classificação."
            method != PaymentMethod.PIX && direction == Direction.INCOME -> "Entrada relacionada a cartão exige revisão."
            else -> ""
        }
        return ParsedNotification(amount, direction, method,
            if (reason.isEmpty()) ReviewStatus.CONFIRMED else ReviewStatus.REVIEW, reason,
            hasAmountCandidate = hasAmountCandidate,
            // Invoice, refund and chargeback wording is review evidence even without a direction.
            hasEventStateSignal = stateSignal || settlement.containsMatchIn(normalized))
    }

    private fun toCents(value: String): Long? {
        if (!validAmount.matches(value)) return null
        // Parsing the digit sequence directly avoids floating point and arithmetic overflow.
        return value.replace(".", "").replace(",", "").toLongOrNull()?.takeIf { it > 0 }
    }
}
