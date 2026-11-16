package br.com.fluxo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.fluxo.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordsBatchAndTagsUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = System.currentTimeMillis()

    private fun pending(id: String) = Transaction(
        id, "key-$id", "com.itau", 1000, Direction.EXPENSE, PaymentMethod.UNKNOWN, ReviewStatus.REVIEW,
        now, "Pendente $id", reason = BatchConfirm.METHOD_ONLY_REASON
    )

    private fun confirmed(id: String, description: String, sourceKey: String?, category: Category = Category.FOOD) = Transaction(
        id, sourceKey, "com.itau", 2500, Direction.EXPENSE, PaymentMethod.PIX, ReviewStatus.CONFIRMED,
        now, description, category = category
    )

    private fun show(
        rows: List<Transaction>, tags: Map<String, Set<String>> = emptyMap(),
        initialCategory: Category? = null, confirmBatch: (List<String>, PaymentMethod?, (Int) -> Unit) -> Unit = { _, _, _ -> }
    ) = compose.setContent {
        MaterialTheme {
            RecordsScreen(
                rows, 0, {}, false, {}, {}, listOf(null), { it }, { "R$ ${it ?: 0}" }, { it.name }, { it.name },
                tags = tags, confirmBatch = confirmBatch, initialCategory = initialCategory
            )
        }
    }

    @Test fun batchConfirmAsCreditCallsConfirmBatchWithAllThreeIds() {
        var ids: List<String>? = null
        var method: PaymentMethod? = null
        show(listOf(pending("a"), pending("b"), pending("c")), confirmBatch = { i, m, _ -> ids = i; method = m })
        compose.onNodeWithText("Confirmar 3 como Crédito").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar em lote?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, ids) }
        compose.onNodeWithText("Confirmar 3").performClick()
        compose.runOnIdle {
            assertEquals(setOf("a", "b", "c"), ids?.toSet())
            assertEquals(PaymentMethod.CREDIT_CARD, method)
        }
    }

    @Test fun tagChipsShowOnRow() {
        show(listOf(confirmed("t1", "Padaria Sintetica", "k1")), tags = mapOf("t1" to setOf("etiquetaxyz")))
        assertTrue(compose.onAllNodesWithText("etiquetaxyz").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun originSealDistinguishesManualFromCaptured() {
        show(listOf(confirmed("m", "Lançamento A", null), confirmed("n", "Lançamento B", "notif-1")))
        compose.onNodeWithText("Lançado manualmente").assertExists()
        compose.onNode(hasText("Capturado da notificação", substring = true)).assertExists()
    }

    @Test fun initialCategoryFiltersList() {
        show(
            listOf(confirmed("f", "Padaria Sintetica", "k1", Category.FOOD), confirmed("t", "Oficina Sintetica", "k2", Category.TRANSPORT)),
            initialCategory = Category.FOOD
        )
        compose.onNodeWithText("Padaria Sintetica").assertExists()
        compose.onNodeWithText("Oficina Sintetica").assertDoesNotExist()
    }
}
