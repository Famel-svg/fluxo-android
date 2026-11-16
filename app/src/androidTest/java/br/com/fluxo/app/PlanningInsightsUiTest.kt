package br.com.fluxo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.fluxo.core.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class PlanningInsightsUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.now()

    private fun gym(offset: Long) = Transaction(
        "gym-$offset", null, "manual", 9990, Direction.EXPENSE, PaymentMethod.PIX, ReviewStatus.CONFIRMED,
        month.minusMonths(offset).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        "Academia", category = Category.HEALTH
    )

    private fun show(rows: List<Transaction>) = compose.setContent {
        MaterialTheme { PlanningScreen(rows, emptyList(), month, false, null, {}, { _, _ -> }, { _, _, _ -> }, BenefitSettings(), { _, _, _, _, _, _, _ -> }) }
    }

    @Test fun recurringSectionListsRepeatedCharge() {
        show(listOf(gym(0), gym(1), gym(2)))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Recorrentes"))
        compose.onNodeWithText("Recorrentes").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Academia"))
        compose.onNodeWithText("Academia").assertExists()
        compose.onNodeWithTag("planning.recurringTotal").assertExists()
    }

    @Test fun cashflowSectionAppears() {
        show(listOf(gym(0), gym(1), gym(2)))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Fluxo de caixa", substring = true))
        compose.onNode(hasText("Fluxo de caixa", substring = true)).assertExists()
    }

    @Test fun recurringEmptyStateWithFewData() {
        show(listOf(gym(0)))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Recorrentes"))
        assertTrue(compose.onAllNodesWithText("Recorrentes aparecem depois de 2 meses de lançamentos.").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("planning.recurringTotal").assertDoesNotExist()
    }
}
