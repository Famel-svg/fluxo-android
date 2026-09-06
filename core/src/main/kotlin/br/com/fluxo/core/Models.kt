package br.com.fluxo.core

enum class Direction { INCOME, EXPENSE, UNKNOWN }
enum class PaymentMethod { PIX, CREDIT_CARD, DEBIT_CARD, UNKNOWN }
enum class ReviewStatus { CONFIRMED, REVIEW }

enum class Category(val label: String) {
    UNCATEGORIZED("Sem categoria"), FOOD("Alimentação"), HOUSING("Moradia"),
    TRANSPORT("Transporte"), HEALTH("Saúde"), EDUCATION("Educação"), LEISURE("Lazer"),
    SHOPPING("Compras"), SERVICES("Serviços"), SALARY("Salário"), OTHER("Outros"),
}

data class Budget(val month: String, val category: Category, val limitCents: Long)

enum class AccountType(val label: String) { CHECKING("Conta corrente"), SAVINGS("Poupança"), CASH("Dinheiro"), OTHER("Outra") }

data class Account(val id: String, val name: String, val bankPackage: String,
    val openingCents: Long, val openingAt: Long, val archived: Boolean = false,
    val type: AccountType = AccountType.CHECKING)

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
    val category: Category = Category.UNCATEGORIZED,
    val accountId: String? = null,
    val transferId: String? = null,
)
