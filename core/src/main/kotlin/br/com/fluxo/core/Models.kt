package br.com.fluxo.core

enum class Direction { INCOME, EXPENSE, UNKNOWN }
/** Names are persisted as text (SQLite and backups): never rename or reorder; append only. */
enum class PaymentMethod {
    PIX, CREDIT_CARD, DEBIT_CARD, UNKNOWN, MEAL_VOUCHER, TRANSPORT_VOUCHER;

    /** Voucher balances (VR/VT) are a separate pot, not bank cash. */
    val isVoucher: Boolean get() = this == MEAL_VOUCHER || this == TRANSPORT_VOUCHER

    /** False for credit-card and voucher spending: it is consumption, but does not leave the bank account. */
    val affectsCash: Boolean get() = this != CREDIT_CARD && !isVoucher

    /** Full pt-BR name. */
    val label: String get() = when (this) {
        PIX -> "Pix"; CREDIT_CARD -> "Cartão de crédito"; DEBIT_CARD -> "Cartão de débito"
        UNKNOWN -> "Outro / não informado"; MEAL_VOUCHER -> "Vale-refeição"; TRANSPORT_VOUCHER -> "Vale-transporte"
    }

    /** Compact pt-BR name for lists and chips. */
    val shortLabel: String get() = when (this) {
        PIX -> "Pix"; CREDIT_CARD -> "crédito"; DEBIT_CARD -> "débito"
        UNKNOWN -> "outro"; MEAL_VOUCHER -> "VR"; TRANSPORT_VOUCHER -> "VT"
    }
}
enum class ReviewStatus { CONFIRMED, REVIEW }

enum class Category(val label: String) {
    UNCATEGORIZED("Sem categoria"), FOOD("Alimentação"), HOUSING("Moradia"),
    TRANSPORT("Transporte"), HEALTH("Saúde"), EDUCATION("Educação"), LEISURE("Lazer"),
    SHOPPING("Compras"), SERVICES("Serviços"), SALARY("Salário"), OTHER("Outros"),
}

data class Budget(val month: String, val category: Category, val limitCents: Long)

enum class AccountType(val label: String) { CHECKING("Conta corrente"), SAVINGS("Poupança"), CASH("Dinheiro"), CREDIT_CARD("Cartão de crédito"), OTHER("Outra") }

data class Account(val id: String, val name: String, val bankPackage: String,
    val openingCents: Long, val openingAt: Long, val archived: Boolean = false,
    val type: AccountType = AccountType.CHECKING)

data class ParsedNotification(
    val amountCents: Long?,
    val direction: Direction,
    val method: PaymentMethod,
    val status: ReviewStatus,
    val reason: String,
    // Transient parser evidence only; never persist or interpret as an inferred amount.
    @Transient val hasAmountCandidate: Boolean = amountCents != null,
    @Transient val hasEventStateSignal: Boolean = false,
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
    val category: Category = Category.UNCATEGORIZED,
    val accountId: String? = null,
    val transferId: String? = null,
)
