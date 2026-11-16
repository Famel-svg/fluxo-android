package br.com.fluxo.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.AlertKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Toggles real preferences through the view model, so it only runs on the disposable emulator and restores them. */
@RunWith(AndroidJUnit4::class)
class SmartAlertsSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private var model: LedgerViewModel? = null
    private var original: Set<String>? = null

    @Before fun show() {
        assumeTrue("Pass disposableEmulator=true only from the guarded ApkPreview runner",
            InstrumentationRegistry.getArguments().getString("disposableEmulator") == "true")
        compose.setContent {
            MaterialTheme {
                val m = viewModel<LedgerViewModel>()
                model = m
                Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) { SmartAlertsCard(m) }
            }
        }
        compose.waitForIdle()
        original = model!!.alertsEnabled.value
    }

    @After fun restore() { original?.let { model?.setAlertsEnabled(it) } }

    private val labels = listOf(
        "Resumo de revisão", "Orçamento 80%", "Orçamento estourado", "Conta vencendo",
        "Gasto incomum", "Captura parada", "Resumo semanal"
    )

    @Test fun showsAllSevenKindsAndSwitchCallsSetter() {
        assertEquals(AlertKind.entries.size, labels.size)
        labels.forEach { compose.onNodeWithContentDescription("Aviso: $it").performScrollTo().assertExists() }
        val wasOn = AlertKind.REVIEW_DIGEST.name in model!!.alertsEnabled.value
        compose.onNodeWithContentDescription("Aviso: Resumo de revisão").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(!wasOn, AlertKind.REVIEW_DIGEST.name in model!!.alertsEnabled.value)
    }
}
