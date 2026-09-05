package br.com.fluxo.core

import java.text.Normalizer
import java.util.Locale

object TransactionSearch {
    private val bankNames = mapOf(
        "com.c6bank.app" to "C6 Bank", "com.nu.production" to "Nubank",
        "com.itau" to "Itaú", "manual" to "Manual",
    )
    private val accents = Regex("\\p{M}+")

    fun filter(
        rows: List<Transaction>,
        query: String = "",
        bankPackage: String? = null,
        method: PaymentMethod? = null,
        category: Category? = null,
    ): List<Transaction> {
        val needle = normalized(query.trim())
        return rows.filter { row ->
            (bankPackage == null || row.bankPackage == bankPackage) &&
                (method == null || row.method == method) &&
                (category == null || row.category == category) &&
                (needle.isEmpty() || listOf(
                    row.description, bankNames[row.bankPackage] ?: row.bankPackage, row.category.label,
                ).any { normalized(it).contains(needle) })
        }
    }

    private fun normalized(value: String): String =
        accents.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
}
