package br.com.fluxo.app

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        assumeTrue("UI smoke tests require an emulator with disposable app data", Build.HARDWARE in setOf("ranchu", "goldfish"))
        fixtureStarted = true
        runBlocking { repository.clear() }
        compose.waitForIdle()
    }

    @After
    fun clearSyntheticLedger() {
        if (fixtureStarted) runBlocking { repository.clear() }
    }

    @Test
    fun manualExpenseCanBeCreatedEditedAndDeleted() {
        addExpense("125,90", "Mercado sintético")
        compose.onNodeWithText("Lançamentos").performClick()
        compose.onNodeWithText("Mercado sintético").assertIsDisplayed()
        compose.onNodeWithText(currency("125.90")).assertIsDisplayed().performClick()

        compose.onNodeWithText("Valor em reais").performTextReplacement("130,45")
        compose.onNodeWithText("Salvar e confirmar").performClick()
        compose.waitUntil(10_000) { repository.transactions.value.singleOrNull()?.amountCents == 13045L }
        compose.onNodeWithText(currency("130.45")).assertIsDisplayed()
        compose.onNodeWithText(currency("125.90")).assertDoesNotExist()

        compose.onNodeWithText("Mercado sintético").performClick()
        compose.onNodeWithText("Excluir lançamento").performScrollTo().performClick()
        compose.onNodeWithText("Excluir lançamento?").assertIsDisplayed()
        compose.onNodeWithText("Excluir", substring = false).performClick()
        compose.waitUntil(10_000) { repository.transactions.value.isEmpty() }
        compose.onNodeWithText("Nenhum lançamento").assertIsDisplayed()
        compose.onNodeWithText("Mercado sintético").assertDoesNotExist()
    }

    @Test
    fun dashboardSeparatesCreditCommitmentsFromCashExpenses() {
        addExpense("20,00", "Débito sintético", "Cartão de débito")
        addExpense("80,00", "Crédito sintético", "Cartão de crédito")

        assertMetric("Saídas de caixa", "20.00")
        assertMetric("Compras no crédito", "80.00")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("RESULTADO OBSERVADO"))
        compose.onNodeWithText(currency("-20.00")).assertIsDisplayed()
    }

    private fun addExpense(amount: String, description: String, method: String? = null) {
        val previousCount = repository.transactions.value.size
        compose.onNodeWithContentDescription("Adicionar lançamento").performClick()
        compose.onNodeWithText("Valor em reais").performTextInput(amount)
        compose.onNodeWithText("Descrição").performTextInput(description)
        if (method != null) {
            compose.onNodeWithText("Meio: Outro / não informado").performScrollTo().performClick()
            compose.onNodeWithText(method, substring = false).performClick()
        }
        compose.onNodeWithText("Salvar e confirmar").performClick()
        compose.waitUntil(10_000) { repository.transactions.value.size == previousCount + 1 }
        compose.waitForIdle()
    }

    private fun assertMetric(label: String, value: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        compose.onNode(hasText(currency(value)) and hasAnySibling(hasText(label)), useUnmergedTree = true).assertIsDisplayed()
    }

    private fun currency(value: String): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(value))
}
