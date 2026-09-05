package br.com.fluxo.core

enum class Direction { INCOME, EXPENSE, UNKNOWN }
enum class PaymentMethod { PIX, CREDIT_CARD, DEBIT_CARD, UNKNOWN }
enum class ReviewStatus { CONFIRMED, REVIEW }

data class ParsedNotification(
    val amountCents: Long?,
    val direction: Direction,
    val method: PaymentMethod,
    val status: ReviewStatus,
    val reason: String,
)

data class Transaction(
    val id: String,
    val sourceKey: String?,
    val bankPackage: String,
    val amountCents: Long?,
    val direction: Direction,
    val method: PaymentMethod,
    val status: ReviewStatus,
    val occurredAt: Long,
    val description: String,
    val reason: String = "",
)
