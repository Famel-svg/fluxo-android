package br.com.fluxo.core

import org.junit.Assert.*
import org.junit.Test

/** Layouts from an owner triage export; merchants and card digits are synthetic. */
class MerchantCategorizerTest {
    private val none: (String) -> Category? = { null }

    @Test fun `merchant comes from issuer purchase notifications`() {
        assertEquals("SUPER EXEMPLO MERCADO", MerchantCategorizer.merchant("com.c6bank.app",
            "Compra no crédito aprovada\nSua compra no cartão final 1234 no valor de R$ 7,98, dia 20/10/2026 às 13:16, em SUPER EXEMPLO MERCADO    SAO PAULO     BRA, foi aprovada."))
        assertEquals("MERCADO EXEMPLO", MerchantCategorizer.merchant("com.nu.production",
            "Compra no crédito aprovada\nCompra de R$ 16,97 APROVADA em MERCADO EXEMPLO para o cartão com final 5678."))
        assertEquals("FARMACIA Z", MerchantCategorizer.merchant("com.itau",
            "Compra no débito\nCompra no débito de R$ 18,90 em FARMACIA Z aprovada."))
    }

    @Test fun `merchant comes from the wallet receipt title`() {
        assertEquals("FARMACIA EXEMPLO", MerchantCategorizer.merchant(WalletReceipt.GOOGLE_WALLET,
            "FARMACIA EXEMPLO\nR$ 12,00 com Cartão C6 Mastercard ••1234"))
        assertNull(MerchantCategorizer.merchant(WalletReceipt.GOOGLE_WALLET, "Cartão adicionado\nPronto para pagar"))
    }

    @Test fun `pix income and states are not merchants`() {
        assertNull(MerchantCategorizer.merchant("com.nu.production", "Pix enviado\nVocê enviou R$ 50,00 em Pix para Ana"))
        assertNull(MerchantCategorizer.merchant("com.nu.production", "Pix recebido\nVocê recebeu R$ 50,00 em sua conta"))
        assertNull(MerchantCategorizer.merchant("com.c6bank.app", "Compra aprovada\nCompra de R$ 9,00 em processamento"))
    }

    @Test fun `keywords suggest a category`() {
        assertEquals(Category.FOOD, MerchantCategorizer.suggest("SUPER EXEMPLO MERCADO", none))
        assertEquals(Category.FOOD, MerchantCategorizer.suggest("Padaria Pão Quente", none))
        assertEquals(Category.SHOPPING, MerchantCategorizer.suggest("MERCADOLIVRE*LOJA", none))
        assertEquals(Category.TRANSPORT, MerchantCategorizer.suggest("AUTO POSTO EXEMPLO", none))
        assertEquals(Category.HEALTH, MerchantCategorizer.suggest("DROGARIA EXEMPLO", none))
        assertEquals(Category.LEISURE, MerchantCategorizer.suggest("NETFLIX.COM", none))
        assertEquals(Category.UNCATEGORIZED, MerchantCategorizer.suggest("LOJA XYZ 123", none))
        assertEquals(Category.UNCATEGORIZED, MerchantCategorizer.suggest(null, none))
        // A word containing a keyword is not that keyword.
        assertEquals(Category.UNCATEGORIZED, MerchantCategorizer.suggest("IMPOSTO MUNICIPAL", none))
    }

    @Test fun `owner choice for a merchant wins over keywords`() {
        val learned = mapOf(MerchantCategorizer.key("Mercado Exemplo") to Category.OTHER)
        assertEquals(Category.OTHER, MerchantCategorizer.suggest("MERCADO  EXEMPLO", learned::get))
        assertEquals("mercado exemplo", MerchantCategorizer.key(" Mércado   EXEMPLO "))
    }
}
