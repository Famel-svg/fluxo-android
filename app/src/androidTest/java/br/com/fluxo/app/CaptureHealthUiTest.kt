package br.com.fluxo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureHealthUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun permissionAndToggleDoNotClaimServiceIsConnected() {
        var requested = 0
        compose.setContent { MaterialTheme {
            CaptureHealthContent(CaptureSnapshot(), true, true, setOf("com.c6bank.app")) { requested++ }
        } }
        compose.onNodeWithText("Permissão: concedida").assertIsDisplayed()
        compose.onNodeWithText("Serviço: conexão ainda não confirmada nesta sessão").assertIsDisplayed()
        compose.onNodeWithText("Solicitar reconexão").performClick()
        compose.runOnIdle { assertEquals(1, requested) }
    }

    @Test fun pausedCaptureDoesNotOfferReconnectEvenIfPermissionIsGranted() {
        compose.setContent { MaterialTheme {
            CaptureHealthContent(CaptureSnapshot(connected = false), true, false, setOf("com.itau"))
        } }
        compose.onNodeWithText("Captura: pausada").assertIsDisplayed()
        compose.onNodeWithText("Solicitar reconexão").assertDoesNotExist()
    }

    @Test fun diagnosticsDoNotExposeUnselectedBankAndExplainMissingEvents() {
        compose.setContent { MaterialTheme {
            CaptureHealthContent(CaptureSnapshot(true, mapOf("com.itau" to 1700000000000)), true, true, setOf("com.c6bank.app"))
        } }
        compose.onNodeWithText("C6 Bank: nenhuma notificação observada nesta sessão").assertIsDisplayed()
        compose.onAllNodes(hasText("Itaú", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Ausência de notificações não comprova falha.", substring = true).assertExists()
    }
}
