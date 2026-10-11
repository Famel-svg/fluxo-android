package br.com.fluxo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.fluxo.core.*
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
class BenefitBalanceUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 10)
    private val monday = month.atDay(5).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun currency(value: String) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(value))
    private fun voucher(id: String, cents: Long, method: PaymentMethod = PaymentMethod.MEAL_VOUCHER) =
        Transaction(id, null, "manual", cents, Direction.EXPENSE, method, ReviewStatus.CONFIRMED, monday, "Almoço")
    private fun settings(vr: Long = 3900, vt: Long = 0) = BenefitSettings(vrDailyCents = vr, vtDailyCents = vt, vrWeekdays = BenefitCalculator.WORKDAYS)

    private fun show(rows: List<Transaction>, benefits: BenefitSettings) {
        compose.setContent {
            MaterialTheme { Dashboard(rows, month, false, {}, {}, {}, {}, {}, {}, {}, benefits = benefits) }
        }
    }

    @Test fun showsRemainingVrOfMonthlyEstimate() {
        show(listOf(voucher("a", 7000), voucher("b", 5000)), settings())
        compose.onNodeWithTag("dashboard.vr").assertIsDisplayed()
        compose.onNodeWithTag("dashboard.vrRemaining").assertTextEquals(currency("738.00"))
        compose.onNodeWithText("de ${currency("858.00")} no mês").assertIsDisplayed()
        compose.onNodeWithTag("dashboard.vt").assertDoesNotExist()
    }

    @Test fun overspendIsShownAboveTheEstimate() {
        show(listOf(voucher("a", 90000)), settings())
        compose.onNodeWithTag("dashboard.vrRemaining").assertTextContains("acima da estimativa", substring = true)
        compose.onNodeWithTag("dashboard.vrRemaining").assertTextContains(currency("42.00"), substring = true)
    }

    @Test fun hiddenWhenVrRateIsZero() {
        show(listOf(voucher("a", 7000)), settings(vr = 0, vt = 1000))
        compose.onNodeWithTag("dashboard.vr").assertDoesNotExist()
        compose.onNodeWithTag("dashboard.vt").assertDoesNotExist()
    }

    @Test fun transportRowAppearsUnderVrAndVoucherSpendingStaysOutOfCash() {
        show(listOf(voucher("a", 3000), voucher("t", 1000, PaymentMethod.TRANSPORT_VOUCHER)), settings(vt = 500))
        compose.onNodeWithTag("dashboard.vt").assertIsDisplayed()
        compose.onNodeWithText("Entradas ${currency("0.00")} − saídas de caixa ${currency("0.00")}").assertIsDisplayed()
    }

    @Test fun hideValuesToggleMasksTheBalance() {
        show(listOf(voucher("a", 7000)), settings())
        compose.onNodeWithTag("dashboard.visibility").performClick()
        compose.onNodeWithTag("dashboard.vrRemaining").assertTextEquals("••••")
    }
}
