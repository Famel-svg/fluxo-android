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
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real first-run wizard (name, salary, then VR and VT, one page each) on a disposable emulator. The stored
 * profile is saved before each test and put back afterwards, so a personal installation is never left in a test state.
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
            repository.setSalaryMonthlyCents(0)
            repository.setOnboardingDone(false)
        }
        baseline = repository.benefits.value
        compose.waitForIdle()
    }

    @After fun restoreStoredProfile() { original?.let { OnboardingTestSupport.restore(repository, it) } }

    private fun cents(value: Long) = money(BigDecimal.valueOf(value, 2))
    private fun step(n: Int) { compose.onNodeWithTag("onboarding.progress").assertTextContains("$n de 3") }
    private fun next() { compose.onNodeWithTag("onboarding.next").assertIsEnabled().performClick(); compose.waitForIdle() }
    private fun back() { compose.onNodeWithTag("onboarding.back").performClick(); compose.waitForIdle() }
    private fun field(tag: String, text: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text) }
    private fun finished() { compose.waitUntil(10_000) { repository.onboardingDone.value }; compose.waitForIdle() }

    @Test fun threeStepsShowCurrentMonthCountsAndStartPersistsEverythingThenShowsTheTabs() {
        step(1)
        compose.onNodeWithTag("onboarding.back").assertDoesNotExist()
        field("onboarding.name", "  Rafael  ")
        next()
        step(2)
        field("onboarding.salary", "3.500,00")
        next()
        step(3)
        field("onboarding.vrRate", "39,00")
        field("onboarding.vtRate", "10,80")
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

        compose.onNodeWithTag("onboarding.start").assertIsEnabled().performClick()
        finished()
        compose.onNodeWithTag("navigation.0").assertIsDisplayed()
        compose.onNodeWithTag("onboarding.start").assertDoesNotExist()
        assertEquals(BenefitSettings(3900, 1080, baseline.overrides, BenefitCalculator.WORKDAYS, monThu), repository.benefits.value)
        assertEquals("Rafael", repository.profileName.value)
        assertEquals(350000L, repository.salaryMonthlyCents.value)
        compose.onNodeWithTag("dashboard.greeting").assertTextContains("Olá, Rafael")
    }

    @Test fun eachStepStaysLockedUntilItsOwnInputIsValid() {
        step(1)
        compose.onNodeWithTag("onboarding.next").assertIsNotEnabled()
        field("onboarding.name", "   ")
        compose.onNodeWithText("Informe um nome.").assertIsDisplayed()
        compose.onNodeWithTag("onboarding.next").assertIsNotEnabled()
        field("onboarding.name", "Ana")
        next()

        step(2)
        compose.onNodeWithTag("onboarding.next").assertIsNotEnabled()
        for (invalid in listOf("abc", "3.50", "35.00", "1,234")) {
            field("onboarding.salary", invalid)
            compose.onNodeWithText("Valor inválido", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("onboarding.next").assertIsNotEnabled()
        }
        field("onboarding.salary", "0,00")
        compose.onNodeWithText("Informe um valor maior que zero.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding.next").assertIsNotEnabled()
        field("onboarding.salary", "3500")
        compose.onNodeWithTag("onboarding.next").assertIsEnabled()
        next()

        step(3)
        compose.onNodeWithTag("onboarding.start").assertIsNotEnabled()
        field("onboarding.vrRate", "0")
        compose.onNodeWithTag("onboarding.start").assertIsNotEnabled()
        field("onboarding.vrRate", "1.50")
        compose.onNodeWithText("Valor inválido", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding.start").assertIsNotEnabled()
        field("onboarding.vrRate", "39,00")
        compose.onNodeWithTag("onboarding.start").assertIsEnabled()
        // A rate without any weekday is contradictory input, not a filled benefit.
        for (day in 1..5) compose.onNodeWithTag("onboarding.vrDay.$day").performScrollTo().performClick()
        compose.onNodeWithText("Escolha ao menos um dia.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding.start").assertIsNotEnabled()
        compose.onNodeWithTag("onboarding.vrDay.1").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding.start").assertIsEnabled()
        // A benefit left blank means "não recebo" as long as the other one is filled.
        field("onboarding.vrRate", "")
        compose.onNodeWithTag("onboarding.start").assertIsNotEnabled()
        field("onboarding.vtRate", "abc")
        compose.onNodeWithTag("onboarding.start").assertIsNotEnabled()
        field("onboarding.vtRate", "10,80")
        compose.onNodeWithTag("onboarding.start").assertIsEnabled().performClick()
        finished()
        val saved = repository.benefits.value
        assertEquals(0L, saved.vrDailyCents)
        assertEquals(1080L, saved.vtDailyCents)
        assertEquals(setOf(DayOfWeek.MONDAY), saved.vrWeekdays)
        assertEquals(BenefitCalculator.WORKDAYS, saved.vtWeekdays)
        assertEquals(350000L, repository.salaryMonthlyCents.value)
    }

    /**
     * Sends the system Back through the activity's dispatcher. Espresso.pressBack() waits for window
     * focus, which the wizard's keyboard can hold on slow emulators (it timed out on CI).
     */
    private fun systemBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    @Test fun backKeepsEverythingTypedAndSystemBackGoesToThePreviousStep() {
        field("onboarding.name", "Ana")
        next()
        field("onboarding.salary", "1.234,56")
        next()
        field("onboarding.vrRate", "25,50")
        compose.onNodeWithTag("onboarding.vtDay.1").performScrollTo().performClick()
        back()
        step(2)
        compose.onNodeWithTag("onboarding.salary").assertTextContains("1.234,56")
        next()
        step(3)
        compose.onNodeWithTag("onboarding.vrRate").performScrollTo().assertTextContains("25,50")
        compose.onNodeWithTag("onboarding.vtDay.1").performScrollTo().assertIsOff()
        systemBack()
        compose.waitForIdle()
        step(2)
        compose.onNodeWithTag("onboarding.salary").assertTextContains("1.234,56")
        systemBack()
        compose.waitForIdle()
        step(1)
        compose.onNodeWithTag("onboarding.name").assertTextContains("Ana")
        assertFalse(repository.onboardingDone.value)
    }

    @Test fun skippingEveryStepPersistsNothingButTheFlag() {
        field("onboarding.name", "Nome descartado")
        compose.onNodeWithTag("onboarding.skipStep").assertIsEnabled().performClick()
        step(2)
        field("onboarding.salary", "1.000,00")
        compose.onNodeWithTag("onboarding.skipStep").performClick()
        step(3)
        field("onboarding.vrRate", "39,00")
        compose.onNodeWithTag("onboarding.vtDay.1").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding.skipStep").assertIsEnabled().performClick()
        finished()
        compose.onNodeWithTag("navigation.0").assertIsDisplayed()
        assertEquals(baseline, repository.benefits.value)
        assertEquals("", repository.profileName.value)
        assertEquals(0L, repository.salaryMonthlyCents.value)
    }

    @Test fun skippingOnlyTheBenefitsStepKeepsTheNameAndSalaryFilledBefore() {
        field("onboarding.name", "Caio")
        next()
        field("onboarding.salary", "2.750,00")
        next()
        field("onboarding.vrRate", "39,00")
        compose.onNodeWithTag("onboarding.skipStep").performClick()
        finished()
        assertEquals("Caio", repository.profileName.value)
        assertEquals(275000L, repository.salaryMonthlyCents.value)
        assertEquals(baseline, repository.benefits.value)
    }

    @Test fun imeActionTriggersThePrimaryButtonOnlyWhenTheStepIsValid() {
        compose.onNodeWithTag("onboarding.name").performClick().performImeAction()
        compose.waitForIdle()
        step(1)
        field("onboarding.name", "Ana")
        compose.onNodeWithTag("onboarding.name").performClick().performImeAction()
        compose.waitForIdle()
        step(2)
        field("onboarding.salary", "abc")
        compose.onNodeWithTag("onboarding.salary").performClick().performImeAction()
        compose.waitForIdle()
        step(2)
        field("onboarding.salary", "3.500,00")
        compose.onNodeWithTag("onboarding.salary").performClick().performImeAction()
        compose.waitForIdle()
        step(3)
    }

    @Test fun stepAndTypedStateSurviveActivityRecreation() {
        field("onboarding.name", "Ana")
        next()
        field("onboarding.salary", "2.000,00")
        next()
        field("onboarding.vtRate", "10,80")
        compose.onNodeWithTag("onboarding.vtDay.5").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        step(3)
        compose.onNodeWithTag("onboarding.vtRate").performScrollTo().assertTextContains("10,80")
        compose.onNodeWithTag("onboarding.vtDay.5").performScrollTo().assertIsOff()
        back()
        step(2)
        compose.onNodeWithTag("onboarding.salary").assertTextContains("2.000,00")
        back()
        step(1)
        compose.onNodeWithTag("onboarding.name").assertTextContains("Ana")
    }

    @Test fun settingsReopensTheWizardPrefilledWithStoredValuesOnEveryStep() {
        runBlocking {
            repository.saveBenefits(month, 2550, 1050, BenefitDays(), BenefitCalculator.WORKDAYS, monThu)
            repository.setProfileName("Bia")
            repository.setSalaryMonthlyCents(420000)
            repository.setOnboardingDone(true)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("navigation.3").performClick()
        compose.onNodeWithText("Rever configuração inicial").performScrollTo().performClick()
        compose.waitForIdle()
        step(1)
        compose.onNodeWithTag("onboarding.name").assertTextContains("Bia")
        next()
        step(2)
        compose.onNodeWithTag("onboarding.salary").assertTextContains("4200,00")
        next()
        step(3)
        compose.onNodeWithTag("onboarding.vrRate").performScrollTo().assertTextContains("25,50")
        compose.onNodeWithTag("onboarding.vtRate").performScrollTo().assertTextContains("10,50")
        compose.onNodeWithTag("onboarding.vtDay.4").performScrollTo().assertIsOn()
        compose.onNodeWithTag("onboarding.vtDay.5").performScrollTo().assertIsOff()
        compose.onNodeWithTag("onboarding.start").assertIsEnabled()
    }

    @Test fun settingsProfileDialogEditsNameAndSalaryWithTheSameValidationAndBlankClearsIt() {
        runBlocking { repository.setProfileName("Bia"); repository.setOnboardingDone(true) }
        compose.waitForIdle()
        compose.onNodeWithTag("navigation.3").performClick()
        compose.onNodeWithTag("settings.salary").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Salário mensal").performScrollTo().performClick()
        field("settings.salary.input", "abc")
        compose.onNodeWithText("Valor inválido", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("settings.profile.save").assertIsNotEnabled()
        field("settings.salary.input", "0")
        compose.onNodeWithText("Informe um valor maior que zero.").assertIsDisplayed()
        compose.onNodeWithTag("settings.profile.save").assertIsNotEnabled()
        field("settings.salary.input", "5.000,00")
        compose.onNodeWithTag("settings.profile.save").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { repository.salaryMonthlyCents.value == 500000L }
        compose.waitForIdle()
        compose.onNodeWithText(cents(500000)).performScrollTo().assertIsDisplayed()
        assertEquals("Bia", repository.profileName.value)

        compose.onNodeWithText("Salário mensal").performScrollTo().performClick()
        field("settings.salary.input", "")
        compose.onNodeWithTag("settings.profile.save").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { repository.salaryMonthlyCents.value == 0L }
    }
}
