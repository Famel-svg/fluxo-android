package br.com.fluxo.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProPaywallUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paywallShowsBenefitsPriceAndBuy() {
        var bought = false
        compose.setContent { MaterialTheme { ProPaywallScreen(false, "R$ 19,90", false, { bought = true }, {}) } }
        compose.onNodeWithText("Fluxo Pro").assertIsDisplayed()
        compose.onNodeWithTag("paywall.free").assertIsDisplayed()
        compose.onNodeWithText("•  Tudo do grátis, com contas ativas ilimitadas").assertIsDisplayed()
        compose.onNodeWithTag("paywall.retry").assertDoesNotExist()
        compose.onNodeWithTag("paywall.price").assertTextEquals("R$ 19,90")
        compose.onNodeWithTag("paywall.buy").performClick()
        assertEquals(true, bought)
    }

    @Test fun paywallWithoutPriceOffersRetry() {
        var retried = 0
        compose.setContent { MaterialTheme { ProPaywallScreen(false, null, false, {}, {}, { retried++ }) } }
        compose.onNodeWithTag("paywall.price").assertTextEquals("Preço indisponível")
        compose.onNodeWithTag("paywall.buy").assertIsNotEnabled()
        compose.onNodeWithTag("paywall.retry").performClick()
        assertEquals(1, retried)
    }

    @Test fun paywallFreeForNowHidesBuy() {
        compose.setContent { MaterialTheme { ProPaywallScreen(true, null, true, {}, {}) } }
        compose.onNodeWithTag("paywall.price").assertTextEquals("Grátis por enquanto")
        compose.onNodeWithTag("paywall.owned").assertIsDisplayed()
        compose.onNodeWithTag("paywall.buy").assertDoesNotExist()
    }

    @Test fun debugToggleSwitchesIsPro() {
        val base = object : EntitlementsSource { override val isPro: StateFlow<Boolean> = MutableStateFlow(false) }
        val debug = DebugEntitlements(base)
        compose.setContent { MaterialTheme { Column {
            DebugProRow(debug)
            Text(if (debug.isPro.collectAsState().value) "estado:pro" else "estado:free")
        } } }
        compose.onNodeWithText("estado:free").assertIsDisplayed()
        compose.onNodeWithTag("settings.debugPro").performClick()
        compose.onNodeWithText("estado:pro").assertIsDisplayed()
        compose.onNodeWithTag("settings.debugPro").performClick()
        compose.onNodeWithText("estado:free").assertIsDisplayed()
    }
}
