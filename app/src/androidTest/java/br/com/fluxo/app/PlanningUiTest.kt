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
import java.time.DayOfWeek
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
        compose.setContent { MaterialTheme { PlanningScreen(rows, listOf(Budget(month.toString(), Category.FOOD, 10000)), month, false, null, {}, { _, _ -> }, { _, _, _ -> }, BenefitSettings(), { _, _, _, _, _, _, _ -> }) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Excesso: ${currency("20.00")}"))
        compose.onNodeWithText("Excesso: ${currency("20.00")}").assertIsDisplayed()
        compose.onNodeWithText("2 lançamento(s) · 100,0% das despesas").assertIsDisplayed()
    }

    @Test fun failedSaveKeepsInputAndCapturesOriginalMonth() {
        val selectedMonth = mutableStateOf(month)
        val error = mutableStateOf<String?>(null)
        var saved: Budget? = null
        compose.setContent { MaterialTheme { PlanningScreen(emptyList(), emptyList(), selectedMonth.value, false, error.value, { error.value = null }, { budget, _ -> saved = budget; error.value = "Falha de gravação" }, { _, _, _ -> }, BenefitSettings(), { _, _, _, _, _, _, _ -> }) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Definir limite mensal"))
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
        compose.setContent { MaterialTheme { PlanningScreen(emptyList(), listOf(Budget(month.toString(), Category.FOOD, 10000)), month, false, error.value, { error.value = null }, { _, _ -> }, { targetMonth, category, _ -> requested = targetMonth to category; error.value = "Falha ao remover" }, BenefitSettings(), { _, _, _, _, _, _, _ -> }) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Editar limite de Alimentação"))
        compose.onNodeWithText("Editar limite de Alimentação").performClick()
        compose.onNodeWithText("Remover limite").performScrollTo().performClick()
        compose.onNodeWithText("Remover este limite?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, requested) }
        compose.onNodeWithText("Remover", substring = false).performClick()
        compose.onNodeWithText("Remover este limite?").assertIsDisplayed()
        compose.runOnIdle { assertEquals("2026-09" to Category.FOOD, requested) }
    }

    @Test fun benefitEditorSavesExactRatesAndSeparateMonthlyDaysThenRecalculates() {
        val selected = mutableStateOf(month)
        val settings = mutableStateOf(BenefitSettings())
        compose.setContent { MaterialTheme {
            PlanningScreen(emptyList(), emptyList(), selected.value, false, null, {},
                { _, _ -> }, { _, _, _ -> }, settings.value,
                { target, vr, vt, days, vrWeekdays, vtWeekdays, done ->
                    settings.value = BenefitSettings(vr, vt, settings.value.overrides + (target to days), vrWeekdays, vtWeekdays)
                    done()
                })
        } }
        compose.onNodeWithTag("benefits.configure").performScrollTo().performClick()
        compose.onNodeWithTag("benefits.vrRate").performScrollTo().performTextReplacement("25,50")
        compose.onNodeWithTag("benefits.vtRate").performScrollTo().performTextReplacement("10,50")
        compose.onNodeWithTag("benefits.vrDays").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("benefits.vtDays").performScrollTo().performTextReplacement("12")
        compose.onNodeWithTag("benefits.save").performClick()
        compose.runOnIdle {
            assertEquals(2550L, settings.value.vrDailyCents)
            assertEquals(1050L, settings.value.vtDailyCents)
            assertEquals(BenefitDays(0, 12), settings.value.overrides[month])
        }
        compose.onNodeWithTag("benefits.total").assertTextEquals("Total estimado: ${currency("126.00")}")
        compose.runOnIdle { selected.value = YearMonth.of(2026, 12) }
        compose.onNodeWithTag("benefits.total").assertTextEquals("Total estimado: ${currency("828.00")}")
        compose.runOnIdle { selected.value = month }
        compose.onNodeWithTag("benefits.configure").performScrollTo().performClick()
        compose.onNodeWithTag("benefits.vrDays").performScrollTo().performTextClearance()
        compose.onNodeWithTag("benefits.save").performClick()
        compose.runOnIdle { assertEquals(BenefitDays(null, 12), settings.value.overrides[month]) }
        compose.onNodeWithTag("benefits.total").assertTextEquals("Total estimado: ${currency("687.00")}")
        compose.onNodeWithTag("benefits.configure").performScrollTo().performClick()
        compose.onNodeWithTag("benefits.vtDays").performScrollTo().performTextClearance()
        compose.onNodeWithTag("benefits.save").performClick()
        compose.runOnIdle { assertEquals(BenefitDays(), settings.value.overrides[month]) }
        compose.onNodeWithTag("benefits.total").assertTextEquals("Total estimado: ${currency("792.00")}")
    }

    @Test fun benefitEditorRejectsInvalidInputAndKeepsFieldsAndOriginalMonthOnFailure() {
        val selected = mutableStateOf(month)
        val error = mutableStateOf<String?>(null)
        var requestedMonth: YearMonth? = null
        var requestedRate: Long? = null
        compose.setContent { MaterialTheme {
            PlanningScreen(emptyList(), emptyList(), selected.value, false, error.value,
                { error.value = null }, { _, _ -> }, { _, _, _ -> }, BenefitSettings(),
                { target, vr, _, _, _, _, _ ->
                    requestedMonth = target
                    requestedRate = vr
                    error.value = "Falha de gravação"
                })
        } }
        compose.onNodeWithTag("benefits.configure").performScrollTo().performClick()
        compose.onNodeWithTag("benefits.vrRate").performScrollTo().performTextReplacement("1.50")
        compose.onNodeWithTag("benefits.save").assertIsNotEnabled()
        compose.onNodeWithTag("benefits.vrRate").performScrollTo().performTextReplacement("1.234,56")
        compose.onNodeWithTag("benefits.vrDays").performScrollTo().performTextReplacement("31")
        compose.onNodeWithTag("benefits.save").assertIsNotEnabled()
        compose.onNodeWithTag("benefits.vrDays").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("benefits.save").assertIsEnabled()
        compose.runOnIdle { selected.value = month.plusMonths(1) }
        compose.onNodeWithTag("benefits.save").performClick()
        compose.onNodeWithText("Falha de gravação").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("benefits.vrRate").performScrollTo().assertTextContains("1.234,56")
        compose.runOnIdle {
            assertEquals(month, requestedMonth)
            assertEquals(123456L, requestedRate)
        }
    }

    @Test fun benefitEditorWeekdayChipsSaveSeparateSchedulesAndSummaryFollowsEachOne() {
        val monThu = BenefitCalculator.WORKDAYS - DayOfWeek.FRIDAY
        val settings = mutableStateOf(BenefitSettings(3900, 1080))
        compose.setContent { MaterialTheme {
            PlanningScreen(emptyList(), emptyList(), month, false, null, {}, { _, _ -> }, { _, _, _ -> }, settings.value,
                { target, vr, vt, days, vrWeekdays, vtWeekdays, done ->
                    settings.value = BenefitSettings(vr, vt, settings.value.overrides + (target to days), vrWeekdays, vtWeekdays)
                    done()
                })
        } }
        val vrCount = BenefitCalculator.scheduledDays(month, BenefitCalculator.WORKDAYS)
        val vtCount = BenefitCalculator.scheduledDays(month, monThu)
        compose.onNodeWithTag("benefits.configure").performScrollTo().performClick()
        for (day in 1..5) compose.onNodeWithTag("benefits.vrDay.$day").performScrollTo().assertIsOn()
        compose.onNodeWithTag("benefits.vrDay.6").performScrollTo().assertIsOff()
        compose.onNodeWithTag("benefits.vtDay.5").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("benefits.save").performClick()
        compose.runOnIdle {
            assertEquals(BenefitCalculator.WORKDAYS, settings.value.vrWeekdays)
            assertEquals(monThu, settings.value.vtWeekdays)
        }
        compose.onNodeWithText("VR: $vrCount dias (seg–sex) · VT: $vtCount dias (seg–qui)", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("benefits.total").assertTextEquals("Total estimado: ${currency("%d.%02d".format((3900L * vrCount + 1080L * vtCount) / 100, (3900L * vrCount + 1080L * vtCount) % 100))}")
    }

    private fun showWithSalary(rows: List<Transaction>, salaryCents: Long, settings: BenefitSettings = BenefitSettings(3900, 1080)) {
        compose.setContent { MaterialTheme {
            PlanningScreen(rows, emptyList(), month, false, null, {}, { _, _ -> }, { _, _, _ -> }, settings,
                { _, _, _, _, _, _, _ -> }, salaryMonthlyCents = salaryCents)
        } }
    }

    @Test fun incomeBlockShowsSalaryPlusBenefitsAndHalfOfTheSalarySpent() {
        showWithSalary(listOf(expense("half", 175000, PaymentMethod.DEBIT_CARD)), 350000)
        val vr = 3900L * BenefitCalculator.scheduledDays(month, BenefitCalculator.WORKDAYS)
        val vt = 1080L * BenefitCalculator.scheduledDays(month, BenefitCalculator.WORKDAYS)
        fun cents(value: Long) = currency("%d.%02d".format(value / 100, value % 100))
        compose.onNodeWithTag("planning.income").assertIsDisplayed()
        compose.onNodeWithText("Renda prevista do mês").assertIsDisplayed()
        compose.onNodeWithText("Salário ${cents(350000)} + VR ${cents(vr)} + VT ${cents(vt)}").assertIsDisplayed()
        compose.onNodeWithText("= ${cents(350000 + vr + vt)}").assertIsDisplayed()
        compose.onNodeWithTag("planning.salaryShare").assertTextEquals("Gastos do mês: 50% do salário")
        compose.onNodeWithText("Estimativa para planejamento", substring = true).assertIsDisplayed()
    }

    @Test fun shareOfSalaryPastOneHundredPercentIsStillShownAsAnInteger() {
        showWithSalary(listOf(expense("big", 400000, PaymentMethod.CREDIT_CARD)), 350000)
        compose.onNodeWithTag("planning.salaryShare").assertTextEquals("Gastos do mês: 114% do salário")
    }

    @Test fun noSalaryMeansNoIncomeBlockAndASalaryDoesNotChangeTheMonthSpending() {
        val rows = listOf(expense("a", 175000, PaymentMethod.DEBIT_CARD))
        showWithSalary(rows, 0)
        compose.onNodeWithTag("planning.income").assertDoesNotExist()
        compose.onNodeWithTag("planning.salaryShare").assertDoesNotExist()
        compose.onNodeWithText("Renda prevista do mês").assertDoesNotExist()
    }

    @Test fun incomeBlockDoesNotChangeTheSpendingHeroOrCategoryTotals() {
        showWithSalary(listOf(expense("a", 175000, PaymentMethod.DEBIT_CARD)), 350000)
        compose.onNodeWithText("Gastos do mês").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("1 lançamento(s) · 100,0% das despesas"))
        compose.onNodeWithText("1 lançamento(s) · 100,0% das despesas").assertIsDisplayed()
    }

    private fun expense(id: String, amount: Long, method: PaymentMethod) = Transaction(
        id, null, "manual", amount, Direction.EXPENSE, method, ReviewStatus.CONFIRMED,
        month.atDay(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), id, category = Category.FOOD
    )
    private fun currency(value: String) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(value))
}
