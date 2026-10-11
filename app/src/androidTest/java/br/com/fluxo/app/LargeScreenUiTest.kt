package br.com.fluxo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.fluxo.core.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.ZoneId

/**
 * Renders Início at fixed window sizes through [DeviceConfigurationOverride] (compose ui-test 1.8+, no extra
 * dependency), so a tall 444 x 986 dp phone (about 1220 x 2712 px at 440 dpi) is covered without an emulator that
 * can emulate it. The disposable AVD used by the project cannot go above 1920 px of height.
 */
@RunWith(AndroidJUnit4::class)
class LargeScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2026, 10)

    private fun at(day: Int, hour: Int) = month.atDay(day).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun expense(id: String, cents: Long, day: Int, method: PaymentMethod, category: Category) =
        Transaction(id, null, "manual", cents, Direction.EXPENSE, method, ReviewStatus.CONFIRMED, at(day, 12), "Compra $id", "", category)

    private val rows = listOf(
        expense("a", 7000, 5, PaymentMethod.MEAL_VOUCHER, Category.FOOD),
        expense("b", 15000, 6, PaymentMethod.DEBIT_CARD, Category.FOOD),
        expense("c", 4200, 7, PaymentMethod.PIX, Category.TRANSPORT),
        expense("d", 31000, 8, PaymentMethod.CREDIT_CARD, Category.LEISURE),
    )
    private val benefits = BenefitSettings(vrDailyCents = 3900, vtDailyCents = 1080, vrWeekdays = BenefitCalculator.WORKDAYS)

    private fun show(size: DpSize) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                MaterialTheme { Dashboard(rows, month, false, {}, {}, {}, {}, {}, {}, {}, benefits = benefits) }
            }
        }
    }

    @Test fun tallPhoneKeepsBenefitBlocksAndChartsInsideTheWindow() {
        val size = DpSize(444.dp, 986.dp)
        show(size)
        compose.onNodeWithTag("dashboard.vr").assertIsDisplayed()
        compose.onNodeWithTag("dashboard.vt").assertIsDisplayed()
        compose.onNodeWithText("Gastos por dia").assertIsDisplayed()
        for (tag in listOf("dashboard.vr", "dashboard.vt")) {
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag must start inside the window", bounds.left >= 0.dp)
            assertTrue("$tag is ${bounds.right} wide but the window is ${size.width}", bounds.right <= size.width)
        }
    }

    @Test fun narrowPhoneStillShowsTheSameBlocks() {
        show(DpSize(320.dp, 640.dp))
        compose.onNodeWithTag("dashboard.vr").assertIsDisplayed()
        compose.onNodeWithTag("dashboard.vt").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("dashboard.vr").getUnclippedBoundsInRoot()
        assertTrue("dashboard.vr overflows 320dp: ${bounds.right}", bounds.right <= 320.dp)
    }
}
