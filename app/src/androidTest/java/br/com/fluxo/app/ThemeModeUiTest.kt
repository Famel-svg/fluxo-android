package br.com.fluxo.app

import android.os.Build
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Appearance setting: persistence, the live palette switch and tab state. Restores the stored choice and profile. */
@RunWith(AndroidJUnit4::class)
class ThemeModeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val repository get() = (compose.activity.application as FluxoApplication).repository
    private var profile: ProfileSnapshot? = null
    private var originalMode: ThemeMode? = null

    @Before fun skipOnboarding() {
        assumeTrue("Pass disposableEmulator=true only from the guarded ApkPreview runner",
            InstrumentationRegistry.getArguments().getString("disposableEmulator") == "true")
        assumeTrue("UI tests require an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        originalMode = repository.themeMode.value
        profile = OnboardingTestSupport.skip(repository)
        runBlocking { repository.setThemeMode(ThemeMode.LIGHT) }
        compose.waitForIdle()
    }

    @After fun restore() {
        originalMode?.let { runBlocking { repository.setThemeMode(it) } }
        profile?.let { OnboardingTestSupport.restore(repository, it) }
        compose.waitForIdle()
    }

    @Test fun choiceIsStoredSurvivesANewRepositoryAndIsKeptByClear() = runBlocking {
        repository.setThemeMode(ThemeMode.DARK)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(ThemeMode.DARK, LedgerRepository(context).themeMode.value)
        repository.clear()
        assertEquals(ThemeMode.DARK, repository.themeMode.value)
        assertEquals(ThemeMode.DARK, LedgerRepository(context).themeMode.value)
        repository.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, LedgerRepository(context).themeMode.value)
    }

    @Test fun choosingEscuroSwitchesThePaletteAndLeavingItRestoresLight() {
        compose.onNodeWithTag("navigation.3").performClick()
        assertEquals(FluxoPalette.Light.paper, FluxoColors.Paper)
        compose.onNodeWithTag("settings.theme.dark").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings.theme.dark").assertIsSelected()
        assertEquals(ThemeMode.DARK, repository.themeMode.value)
        assertEquals(FluxoPalette.Dark.paper, FluxoColors.Paper)
        assertEquals(FluxoPalette.Dark.ink, FluxoColors.Ink)
        assertNotEquals(FluxoPalette.Light.paper, FluxoColors.Paper)
        compose.onNodeWithTag("settings.theme.light").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(FluxoPalette.Light.paper, FluxoColors.Paper)
        compose.onNodeWithTag("settings.theme.system").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings.theme.system").assertIsSelected()
    }

    @Test fun switchingTabsShowsTheRightContentAndKeepsTheRecordsSearchText() {
        compose.onNodeWithTag("navigation.1").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("mercado sintetico")
        compose.waitForIdle()
        compose.onNodeWithTag("navigation.0").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("dashboard.neon").assertExists()
        compose.onNode(hasText("mercado sintetico") and hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithTag("navigation.2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Orçamentos").assertExists()
        compose.onNodeWithTag("navigation.1").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("mercado sintetico") and hasSetTextAction()).assertExists()
    }
}
