package br.com.fluxo.app

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.BenefitCalculator
import br.com.fluxo.core.BenefitDays
import br.com.fluxo.core.BenefitSettings
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real first-run screen on a disposable emulator. The stored profile is saved before each test and put
 * back afterwards, so a personal installation is never left in a test state.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val repository get() = (compose.activity.application as FluxoApplication).repository
    private var original: ProfileSnapshot? = null
    private val month = YearMonth.now()
    private val monThu = BenefitCalculator.WORKDAYS - DayOfWeek.FRIDAY
    private lateinit var baseline: BenefitSettings

    @Before fun startFromFreshInstallState() {
        assumeTrue("Pass disposableEmulator=true only from the guarded ApkPreview runner",
            InstrumentationRegistry.getArguments().getString("disposableEmulator") == "true")
        assumeTrue("UI tests require an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        original = OnboardingTestSupport.snapshot(repository)
        runBlocking {
            repository.saveBenefits(month, 0, 0, BenefitDays(), BenefitCalculator.WORKDAYS, BenefitCalculator.WORKDAYS)
            repository.setProfileName("")
            repository.setOnboardingDone(false)
        }
        baseline = repository.benefits.value
        compose.waitForIdle()
    }

    @After fun restoreStoredProfile() { original?.let { OnboardingTestSupport.restore(repository, it) } }

    private fun cents(value: Long) = money(BigDecimal.valueOf(value, 2))

    @Test fun fillingValuesShowsCurrentMonthCountsAndStartPersistsEverythingThenShowsTheTabs() {
        compose.onNodeWithTag("onboarding.name").performTextInput("  Rafael  ")
        compose.onNodeWithTag("onboarding.vrRate").performScrollTo().performTextReplacement("39,00")
        compose.onNodeWithTag("onboarding.vtRate").performScrollTo().performTextReplacement("10,80")
        compose.onNodeWithTag("onboarding.vtDay.5").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("onboarding.vrDay.5").performScrollTo().assertIsOn()
        compose.onNodeWithTag("onboarding.vrDay.6").performScrollTo().assertIsOff()

        val vrDays = BenefitCalculator.scheduledDays(month, BenefitCalculator.WORKDAYS)
        val vtDays = BenefitCalculator.scheduledDays(month, monThu)
        val total = 3900L * vrDays + 1080L * vtDays
        compose.onNodeWithTag("onboarding.vrLine").performScrollTo()
            .assertTextContains("$vrDays dias × ${cents(3900)} = ${cents(3900L * vrDays)}", substring = true)
        compose.onNodeWithTag("onboarding.vtLine").performScrollTo()
            .assertTextContains("$vtDays dias × ${cents(1080)} = ${cents(1080L * vtDays)}", substring = true)
        compose.onNodeWithTag("onboarding.preview").performScrollTo()
            .assertTextContains("Estimativa do mês: ${cents(total)}")

        compose.onNodeWithTag("onboarding.start").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { repository.onboardingDone.value }
        compose.waitForIdle()
        compose.onNodeWithTag("navigation.0").assertIsDisplayed()
        compose.onNodeWithTag("onboarding.start").assertDoesNotExist()
        assertEquals(BenefitSettings(3900, 1080, baseline.overrides, BenefitCalculator.WORKDAYS, monThu), repository.benefits.value)
        assertEquals("Rafael", repository.profileName.value)
        compose.onNodeWithTag("dashboard.greeting").assertTextContains("Olá, Rafael")
    }

    @Test fun skipPersistsNothingButTheFlag() {
        compose.onNodeWithTag("onboarding.name").performTextInput("Nome descartado")
        compose.onNodeWithTag("onboarding.vrRate").performScrollTo().performTextReplacement("39,00")
        compose.onNodeWithTag("onboarding.vtDay.1").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding.skip").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { repository.onboardingDone.value }
        compose.waitForIdle()
        compose.onNodeWithTag("navigation.0").assertIsDisplayed()
        assertEquals(baseline, repository.benefits.value)
        assertEquals("", repository.profileName.value)
    }

    @Test fun invalidMoneyShowsAnInlineErrorAndDisablesStartButBlankMeansZero() {
        compose.onNodeWithTag("onboarding.vrRate").performScrollTo().performTextReplacement("1.50")
        compose.onNodeWithText("Valor inválido", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding.start").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("onboarding.skip").assertIsEnabled()
        compose.onNodeWithTag("onboarding.vrRate").performTextClearance()
        compose.onNodeWithTag("onboarding.vtRate").performScrollTo().performTextReplacement("abc")
        compose.onNodeWithTag("onboarding.start").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("onboarding.vtRate").performTextClearance()
        compose.onNodeWithTag("onboarding.start").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { repository.onboardingDone.value }
        assertEquals(baseline, repository.benefits.value)
    }

    @Test fun typedStateSurvivesActivityRecreation() {
        compose.onNodeWithTag("onboarding.name").performTextInput("Ana")
        compose.onNodeWithTag("onboarding.vtRate").performScrollTo().performTextReplacement("10,80")
        compose.onNodeWithTag("onboarding.vtDay.5").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("onboarding.name").assertTextContains("Ana")
        compose.onNodeWithTag("onboarding.vtRate").performScrollTo().assertTextContains("10,80")
        compose.onNodeWithTag("onboarding.vtDay.5").performScrollTo().assertIsOff()
    }

    @Test fun settingsReopensOnboardingPrefilledWithStoredValues() {
        runBlocking {
            repository.saveBenefits(month, 2550, 1050, BenefitDays(), BenefitCalculator.WORKDAYS, monThu)
            repository.setProfileName("Bia")
            repository.setOnboardingDone(true)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("navigation.3").performClick()
        compose.onNodeWithText("Rever configuração inicial").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("onboarding.name").assertTextContains("Bia")
        compose.onNodeWithTag("onboarding.vrRate").performScrollTo().assertTextContains("25,50")
        compose.onNodeWithTag("onboarding.vtRate").performScrollTo().assertTextContains("10,50")
        compose.onNodeWithTag("onboarding.vtDay.4").performScrollTo().assertIsOn()
        compose.onNodeWithTag("onboarding.vtDay.5").performScrollTo().assertIsOff()
    }
}
