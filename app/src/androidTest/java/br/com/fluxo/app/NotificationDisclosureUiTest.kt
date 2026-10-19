package br.com.fluxo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDisclosureUiTest {
    @get:Rule val compose = createComposeRule()
    private var consented by mutableStateOf(false)
    private var opened = 0

    private fun setContent() = compose.setContent {
        MaterialTheme {
            val connect = rememberNotificationAccessOpener(consented, { consented = true }, {}, { opened++; true })
            FluxoWrapButton("Conectar", onClick = connect)
        }
    }

    @Test fun connectShowsDisclosureAndDeclineKeepsEverythingClosed() {
        setContent()
        compose.onNodeWithText("Conectar").performClick()
        compose.onNodeWithText("Acesso às notificações").assertIsDisplayed()
        compose.onNodeWithText("Agora não").performClick()
        compose.onNodeWithText("Acesso às notificações").assertDoesNotExist()
        compose.runOnIdle { assertFalse(consented); assertEquals(0, opened) }
    }

    @Test fun agreeRecordsConsentAndOpensSettingsOnce() {
        setContent()
        compose.onNodeWithText("Conectar").performClick()
        compose.onNodeWithText("Concordo").performClick()
        compose.runOnIdle { assertTrue(consented); assertEquals(1, opened) }
        // With consent on record the disclosure is not shown again.
        compose.onNodeWithText("Conectar").performClick()
        compose.onNodeWithText("Acesso às notificações").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, opened) }
    }
}
