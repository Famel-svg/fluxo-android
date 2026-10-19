package br.com.fluxo.core

/** Recursos do Fluxo Pro. Perder o Pro só bloqueia CRIAR itens novos: nunca oculta, apaga nem impede exportar. */
enum class ProFeature { UNLIMITED_ACCOUNTS, RECURRENCES, CASH_PROJECTION, WIDGET, AUTO_RULES, STATEMENT_IMPORT }

interface Entitlements { val isPro: Boolean }

object FeaturePolicy {
    const val MAX_CONTAS_FREE = 2

    fun canCreateAccount(existing: Int, isPro: Boolean): Boolean = isPro || existing < MAX_CONTAS_FREE

    fun isAvailable(feature: ProFeature, isPro: Boolean): Boolean = isPro
}
