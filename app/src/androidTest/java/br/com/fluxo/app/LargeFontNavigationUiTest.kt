package br.com.fluxo.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeFontNavigationUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val repository get() = (compose.activity.application as FluxoApplication).repository
    private var profile: ProfileSnapshot? = null

    @Before fun skipOnboarding() { profile = OnboardingTestSupport.skip(repository) }

    @After fun restoreProfile() { profile?.let { OnboardingTestSupport.restore(repository, it) } }

    @Test
    fun allDestinationsRemainReachableWithDoubleFontScale() {
        compose.runOnUiThread {
            val density = compose.activity.resources.displayMetrics.density
            compose.activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    MaterialTheme { FluxoScreen() }
                }
            }
        }

        compose.onNodeWithText("Registros").assertIsDisplayed()
        compose.onNodeWithText("Planejar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lançamentos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Planejamento").assertIsDisplayed()
        for (index in listOf(1, 2, 3, 0)) {
            compose.onNodeWithTag("navigation.$index")
                .assertIsDisplayed().performClick().assertIsSelected()
        }
        compose.onNodeWithContentDescription("Próximo mês").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Mês anterior").assertIsDisplayed().performClick()
    }
}
