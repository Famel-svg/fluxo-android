package br.com.fluxo.app

import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

internal val ptBr = Locale.forLanguageTag("pt-BR")
internal fun money(cents: Long?) = cents?.let { money(BigDecimal.valueOf(it, 2)) } ?: "Valor pendente"
internal fun money(value: BigDecimal) = NumberFormat.getCurrencyInstance(ptBr).format(value)
internal fun Direction.label() = when (this) { Direction.INCOME -> "Entrada"; Direction.EXPENSE -> "Saída"; Direction.UNKNOWN -> "Não definida" }
internal fun PaymentMethod.label() = when (this) { PaymentMethod.PIX -> "Pix"; PaymentMethod.CREDIT_CARD -> "Cartão de crédito"; PaymentMethod.DEBIT_CARD -> "Cartão de débito"; PaymentMethod.UNKNOWN -> "Outro / não informado" }
internal fun bankName(packageName: String) = supportedBanks.firstOrNull { it.packageName == packageName }?.name
    ?: if (packageName == "manual") "Manual" else "Outro app"
/** Compact bank name for list rows, chips and tickets ("C6 Bank" is shown as "C6", like the prototype). */
internal fun bankShortName(packageName: String) = bankName(packageName).removeSuffix(" Bank")
