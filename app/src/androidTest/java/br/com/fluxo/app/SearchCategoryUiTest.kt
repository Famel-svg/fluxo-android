package br.com.fluxo.app

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.ZoneId

/** Uses disposable synthetic data only; never clears a personal device's ledger. */
@RunWith(AndroidJUnit4::class)
class SearchCategoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val repository get() = (compose.activity.application as FluxoApplication).repository
    private var fixtureStarted = false

    @Before fun seedPreviousMonth() {
        assumeTrue("Pass disposableEmulator=true only from the guarded ApkPreview runner",
            InstrumentationRegistry.getArguments().getString("disposableEmulator") == "true")
        assumeTrue("Integration tests require an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        fixtureStarted = true
        runBlocking {
            repository.clear()
            repository.save(Transaction(
                "search-category-fixture", null, "com.itau", 12345, Direction.EXPENSE,
                PaymentMethod.CREDIT_CARD, ReviewStatus.CONFIRMED,
                YearMonth.now().minusMonths(1).atDay(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                "Mercado histórico sintético"
            ))
        }
        compose.waitForIdle()
    }

    @After fun clearFixtures() { if (fixtureStarted) runBlocking { repository.clear() } }

    @Test fun accentInsensitiveSearchFindsOldTransactionAndEditedCategoryCombinesWithFilters() {
        compose.onNodeWithTag("navigation.1").performClick()
        compose.onNodeWithText("Buscar descrição, banco ou categoria").performTextInput("itau")
        scrollTo("Nenhum lançamento")
        compose.onNodeWithText("Nenhum lançamento").assertIsDisplayed()
        scrollTo("Todos os meses")
        // The chip sits in the horizontally scrolling filter row.
        compose.onNodeWithText("Todos os meses").performScrollTo().performClick()
        scrollTo("Mercado histórico sintético")
        compose.onNodeWithText("Mercado histórico sintético").assertIsDisplayed().performClick()
        compose.onNodeWithText("Categoria: Sem categoria").performScrollTo().performClick()
        compose.onNode(hasText("Alimentação") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Salvar lançamento").performClick()
        compose.waitUntil(10_000) { repository.transactions.value.singleOrNull()?.category == Category.FOOD }
        runBlocking { repository.refresh() }
        assertEquals(Category.FOOD, repository.transactions.value.single().category)
        compose.waitForIdle()

        // Compact "Filtros" action; its accessibility label keeps the former wording.
        scrollTo("Filtros")
        compose.onNodeWithContentDescription("Banco, meio e categoria").performClick()
        scrollTo("Meio: Todos")
        compose.onNodeWithText("Meio: Todos").performClick()
        compose.onNodeWithText("Cartão de crédito", substring = false).performClick()
        scrollTo("Categoria: Todas")
        compose.onNodeWithText("Categoria: Todas").performClick()
        compose.onNode(hasText("Alimentação") and hasAnyAncestor(isPopup())).performClick()
        scrollTo("Mercado histórico sintético")
        compose.onNodeWithText("Mercado histórico sintético").assertIsDisplayed()

        scrollTo("Categoria: Alimentação")
        compose.onNodeWithText("Categoria: Alimentação").performClick()
        compose.onNodeWithText("Transporte", substring = false).performClick()
        scrollTo("Nenhum lançamento")
        compose.onNodeWithText("Nenhum lançamento").assertIsDisplayed()
        compose.onNodeWithText("Mercado histórico sintético").assertDoesNotExist()
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }
}
