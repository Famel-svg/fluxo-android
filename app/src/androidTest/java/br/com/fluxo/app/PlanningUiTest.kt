package br.com.fluxo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.fluxo.core.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PlanningUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 9)

    @Test fun budgetCountsCreditAndDebitButExcludesReviewAndOtherMonths() {
        val rows = listOf(
            expense("credit", 8000, PaymentMethod.CREDIT_CARD),
            expense("debit", 4000, PaymentMethod.DEBIT_CARD),
            expense("pending", 9000, PaymentMethod.DEBIT_CARD).copy(status = ReviewStatus.REVIEW),
            expense("old", 5000, PaymentMethod.DEBIT_CARD).copy(occurredAt = month.minusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        )
        compose.setContent { MaterialTheme { PlanningScreen(rows, listOf(Budget(month.toString(), Category.FOOD, 10000)), month, false, null, {}, { _, _ -> }, { _, _, _ -> }) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Excesso: ${currency("20.00")}"))
        compose.onNodeWithText("Excesso: ${currency("20.00")}").assertIsDisplayed()
        compose.onNodeWithText("2 lançamento(s) · 100,0% das despesas").assertIsDisplayed()
    }

    @Test fun failedSaveKeepsInputAndCapturesOriginalMonth() {
        val selectedMonth = mutableStateOf(month)
        val error = mutableStateOf<String?>(null)
        var saved: Budget? = null
        compose.setContent { MaterialTheme { PlanningScreen(emptyList(), emptyList(), selectedMonth.value, false, error.value, { error.value = null }, { budget, _ -> saved = budget; error.value = "Falha de gravação" }, { _, _, _ -> }) } }
        compose.onNodeWithText("Definir limite mensal").performClick()
        compose.onNodeWithText("Limite em reais").performTextInput("150,25")
        compose.runOnIdle { selectedMonth.value = month.plusMonths(1) }
        compose.onNodeWithText("Salvar limite").performClick()
        compose.onNodeWithText("Falha de gravação").assertIsDisplayed()
        compose.onNodeWithText("150,25").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Budget("2026-09", Category.UNCATEGORIZED, 15025), saved) }
    }

    @Test fun removingBudgetRequiresConfirmationAndPreservesDialogOnFailure() {
        val error = mutableStateOf<String?>(null)
        var requested: Pair<String, Category>? = null
        compose.setContent { MaterialTheme { PlanningScreen(emptyList(), listOf(Budget(month.toString(), Category.FOOD, 10000)), month, false, error.value, { error.value = null }, { _, _ -> }, { targetMonth, category, _ -> requested = targetMonth to category; error.value = "Falha ao remover" }) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Editar limite de Alimentação"))
        compose.onNodeWithText("Editar limite de Alimentação").performClick()
        compose.onNodeWithText("Remover limite").performScrollTo().performClick()
        compose.onNodeWithText("Remover este limite?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, requested) }
        compose.onNodeWithText("Remover", substring = false).performClick()
        compose.onNodeWithText("Remover este limite?").assertIsDisplayed()
        compose.runOnIdle { assertEquals("2026-09" to Category.FOOD, requested) }
    }

    private fun expense(id: String, amount: Long, method: PaymentMethod) = Transaction(
        id, null, "manual", amount, Direction.EXPENSE, method, ReviewStatus.CONFIRMED,
        month.atDay(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), id, category = Category.FOOD
    )
    private fun currency(value: String) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(value))
}
