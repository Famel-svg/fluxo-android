package br.com.fluxo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.fluxo.core.Account
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun negativeOpeningBalanceIsAcceptedButImpossibleDateIsNot() {
        var saved: Account? = null
        compose.setContent { MaterialTheme { AccountEditor(null, false, null, {}, { saved = it }) } }
        compose.onNodeWithText("Nome da conta").performTextInput("Minha conta")
        compose.onNodeWithText("Saldo inicial em reais").performScrollTo().performTextReplacement("-50,25")
        compose.onNodeWithText("Saldo antes dos movimentos de (dd/mm/aaaa)").performScrollTo().performTextReplacement("31/02/2026")
        compose.onNodeWithText("Salvar conta").assertIsNotEnabled()
        compose.onNodeWithText("Saldo antes dos movimentos de (dd/mm/aaaa)").performTextReplacement("01/09/2026")
        compose.onNodeWithText("Salvar conta").performClick()
        compose.runOnIdle { assertEquals(-5025L, saved?.openingCents); assertEquals("Minha conta", saved?.name) }
    }

    @Test fun transferRejectsSameAccountBeforeSubmitting() {
        val accounts = listOf(Account("a", "Conta A", "manual", 0, 0), Account("b", "Conta B", "manual", 0, 0))
        var saved: Triple<String, String, Long>? = null
        compose.setContent { MaterialTheme { TransferEditor(accounts, false, null, {}, { from, to, amount, _ -> saved = Triple(from, to, amount) }) } }
        compose.onNodeWithText("Valor da transferência").performScrollTo().performTextInput("25,50")
        compose.onNodeWithText("Destino: Conta B").performScrollTo().performClick()
        compose.onNode(hasText("Conta A") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Registrar transferência").assertIsNotEnabled()
        compose.onNodeWithText("Destino: Conta A").performScrollTo().performClick()
        compose.onNode(hasText("Conta B") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Registrar transferência").performClick()
        compose.runOnIdle { assertEquals(Triple("a", "b", 2550L), saved) }
    }

    @Test fun addAccountAtFreeLimitExplainsProAndProCreatesThird() {
        val debug = (androidx.test.core.app.ApplicationProvider.getApplicationContext<FluxoApplication>().entitlements as DebugEntitlements)
        try {
            debug.forced.value = false
            var added = 0
            compose.setContent { MaterialTheme { AddAccountButton(2, debug.isPro.collectAsState().value) { added++ } } }
            compose.onNodeWithTag("accounts.add").performClick()
            compose.onNodeWithTag("accounts.limit").assertIsDisplayed()
            compose.onNodeWithText("Ver o Fluxo Pro").performClick()
            compose.onNodeWithTag("paywall.price").assertIsDisplayed()
            compose.onNodeWithTag("paywall.close").performClick()
            compose.runOnIdle { assertEquals(0, added) }
            debug.forced.value = true
            compose.waitForIdle()
            compose.onNodeWithTag("accounts.add").performClick()
            compose.runOnIdle { assertEquals(1, added) }
        } finally { debug.forced.value = null }
    }
}
