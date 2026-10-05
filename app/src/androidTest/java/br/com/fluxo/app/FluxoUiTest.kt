package br.com.fluxo.app

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Destructive fixture setup is restricted to an emulator, never a personal device. */
@RunWith(AndroidJUnit4::class)
class FluxoUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val repository get() = (compose.activity.application as FluxoApplication).repository
    private var fixtureStarted = false

    @Before
    fun prepareSyntheticLedger() {
        assumeTrue("Pass disposableEmulator=true only from the guarded ApkPreview runner",
            InstrumentationRegistry.getArguments().getString("disposableEmulator") == "true")
        assumeTrue("UI smoke tests require an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        fixtureStarted = true
        runBlocking { repository.clear() }
        compose.waitForIdle()
    }

    @After
    fun clearSyntheticLedger() {
        if (fixtureStarted) runBlocking { repository.clear() }
    }

    @Test
    fun summaryKeepsCapturePromptClearOfAddButton() {
    compose.onNodeWithText("Configure a captura").performScrollTo()
    compose.onNodeWithText("Configure a captura").assertIsDisplayed()
    compose.onNodeWithContentDescription("Adicionar lançamento").assertDoesNotExist()
    val promptBounds = compose.onNodeWithText("Configure a captura").fetchSemanticsNode().boundsInRoot
    assert(promptBounds.width > 0f && promptBounds.height > 0f)
    }

    @Test
    fun manualExpenseCanBeCreatedEditedAndDeleted() {
        addExpense("125,90", "Mercado sintético")
        compose.onNodeWithTag("navigation.1").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Mercado sintético"))
        compose.onNodeWithText("Mercado sintético").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(currency("125.90")).performScrollTo().assertIsDisplayed().performClick()

        compose.onNodeWithText("Valor em reais").performTextReplacement("130,45")
        compose.onNodeWithText("Salvar lançamento").performClick()
        compose.waitUntil(10_000) { repository.transactions.value.singleOrNull()?.amountCents == 13045L }
        compose.onNodeWithText(currency("130.45")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(currency("125.90")).assertDoesNotExist()

        compose.onNodeWithText("Mercado sintético").performScrollTo().performClick()
        compose.onNodeWithText("Excluir lançamento").performScrollTo().performClick()
        compose.onNodeWithText("Excluir lançamento?").assertIsDisplayed()
        compose.onNodeWithText("Excluir", substring = false).performClick()
        compose.waitUntil(10_000) { repository.transactions.value.isEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Nenhum lançamento"))
        compose.onNodeWithText("Nenhum lançamento").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mercado sintético").assertDoesNotExist()
    }

    @Test
    fun dashboardSeparatesCreditCommitmentsFromCashExpenses() {
        addExpense("20,00", "Débito sintético", "Cartão de débito")
        addExpense("80,00", "Crédito sintético", "Cartão de crédito")
        compose.onNodeWithTag("navigation.0").performClick()

        assertMetric("Saídas de caixa", "20.00")
        assertMetric("Compras no crédito", "80.00")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("RESULTADO OBSERVADO"))
        compose.onNodeWithText(currency("-20.00")).assertIsDisplayed()
    }

    private fun addExpense(amount: String, description: String, method: String? = null) {
        val previousCount = repository.transactions.value.size
        compose.onNodeWithTag("navigation.1").performClick()
        compose.onNodeWithContentDescription("Adicionar lançamento").performClick()
        compose.onNodeWithText("Valor em reais").performTextInput(amount)
        compose.onNodeWithText("Descrição").performTextInput(description)
        if (method != null) {
            compose.onNodeWithText("Meio: Outro / não informado").performScrollTo().performClick()
            compose.onNodeWithText(method, substring = false).performClick()
        }
        compose.onNodeWithText("Salvar lançamento").performClick()
        compose.waitUntil(10_000) { repository.transactions.value.size == previousCount + 1 }
        compose.waitForIdle()
    }

    private fun assertMetric(label: String, value: String) {
        compose.onNodeWithText(label, substring = true).performScrollTo()
        compose.onNode(hasText(currency(value)) and hasAnySibling(hasText(label)), useUnmergedTree = true).assertIsDisplayed()
    }

    private fun currency(value: String): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(value))
}
