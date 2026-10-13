package br.com.fluxo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun multiappDiagnosticsShowGenericSourceWithoutPackageName() {
        compose.setContent { MaterialTheme {
            CaptureHealthContent(
                CaptureSnapshot(connected = true, lastObserved = mapOf("com.example.wallet" to 1700000000000)),
                access = true,
                capture = true,
                banks = emptySet(),
                allowOtherApps = true,
            )
        } }
        compose.onNodeWithText("Outros apps: notificações observadas nesta sessão").assertIsDisplayed()
        compose.onNodeWithText("eventos financeiros reconhecidos", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Outro app: notificação observada às", substring = true).assertIsDisplayed()
        compose.onNodeWithText("com.example.wallet", substring = true).assertDoesNotExist()
    }

    @Test fun disablingOtherAppsHidesPreviouslyObservedEvents() {
        val allowOtherApps = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { MaterialTheme {
            CaptureHealthContent(
                CaptureSnapshot(connected = true, lastObserved = mapOf("com.example.wallet" to 1700000000000)),
                access = true,
                capture = true,
                banks = emptySet(),
                allowOtherApps = allowOtherApps.value,
            )
        } }
        compose.onNodeWithText("Outro app: notificação observada às", substring = true).assertExists()
        compose.runOnIdle { allowOtherApps.value = false }
        compose.onNodeWithText("Outro app: notificação observada às", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Outros apps:", substring = true).assertDoesNotExist()
    }

    @Test fun connectingShowsProgressAndDisablesTheButton() {
        compose.setContent { MaterialTheme {
            CaptureHealthContent(CaptureSnapshot(reconnecting = true), true, true, setOf("com.c6bank.app"))
        } }
        compose.onNodeWithText("Serviço: tentando conectar…").assertIsDisplayed()
        compose.onNodeWithText("Conectando…").assertIsNotEnabled()
        compose.onNodeWithText("Solicitar reconexão").assertDoesNotExist()
    }

    @Test fun stillDisconnectedAfterAttemptSaysAndroidDidNotStartTheService() {
        compose.setContent { MaterialTheme {
            CaptureHealthContent(
                CaptureSnapshot(reconnectAttempted = true, lastResult = br.com.fluxo.core.ReconnectResult.NotConnected),
                true, true, setOf("com.c6bank.app"))
        } }
        compose.onNodeWithText("Serviço: o Android não iniciou o serviço após a tentativa de reconexão").assertIsDisplayed()
        compose.onNodeWithText("O Android não iniciou o serviço. Veja as opções abaixo.").assertIsDisplayed()
        compose.onNodeWithText("Solicitar reconexão").assertIsDisplayed()
    }

    @Test fun connectedAfterReconnectShowsSuccessFeedback() {
        compose.setContent { MaterialTheme {
            CaptureHealthContent(
                CaptureSnapshot(connected = true, lastResult = br.com.fluxo.core.ReconnectResult.Connected),
                true, true, setOf("com.c6bank.app"))
        } }
        compose.onNodeWithText("Serviço: conectado ao Android").assertIsDisplayed()
        compose.onNodeWithText("Serviço conectado").assertIsDisplayed()
        compose.onNodeWithText("Solicitar reconexão").assertDoesNotExist()
    }

    @Test fun missingAccessIsReportedAsPermissionNotConnection() {
        compose.setContent { MaterialTheme {
            CaptureHealthContent(CaptureSnapshot(), false, true, setOf("com.c6bank.app"))
        } }
        compose.onNodeWithText("Serviço: aguardando a permissão de acesso").assertIsDisplayed()
        compose.onNodeWithText("Solicitar reconexão").assertDoesNotExist()
    }

    @Test fun countersAreShownPerSelectedBankOnly() {
        compose.setContent { MaterialTheme {
            CaptureHealthContent(
                CaptureSnapshot(connected = true, counters = mapOf(
                    "com.c6bank.app" to br.com.fluxo.core.SourceCounters(3, 1, 2, br.com.fluxo.core.IgnoreReason.NO_FINANCIAL_WORD),
                    "com.itau" to br.com.fluxo.core.SourceCounters(5, 5, 0))),
                true, true, setOf("com.c6bank.app"))
        } }
        compose.onNodeWithText("C6 Bank: 3 observadas · 1 lançada · 2 ignoradas (sem palavra financeira)").assertIsDisplayed()
        compose.onAllNodes(hasText("Itaú", substring = true)).assertCountEquals(0)
    }

    @Test fun recoveryGuideOffersThreeScreensAndTheXiaomiLine() {
        compose.setContent { MaterialTheme { ListenerRecoveryGuide("Xiaomi") } }
        compose.onNodeWithText("O Android não está iniciando o serviço").assertIsDisplayed()
        compose.onNodeWithText("Abrir acesso às notificações").assertIsDisplayed()
        compose.onNodeWithText("Abrir informações do app").assertIsDisplayed()
        compose.onNodeWithText("Abrir otimização de bateria").assertIsDisplayed()
        compose.onNodeWithText("Em aparelhos Xiaomi, Redmi e POCO: ative 'Início automático' e, em Bateria, 'Sem restrições'.").assertIsDisplayed()
    }

    @Test fun recoveryGuideHasNoVendorLineForOtherManufacturers() {
        compose.setContent { MaterialTheme { ListenerRecoveryGuide("Google") } }
        compose.onAllNodes(hasText("Em aparelhos", substring = true)).assertCountEquals(0)
    }

    @Test fun serviceLineOfTheEstadoBlockIsSpecificForEveryState() {
        val texts = br.com.fluxo.core.ListenerState.entries.map { serviceStateText(it) }
        assertEquals(texts.size, texts.toSet().size)
        assertFalse(texts.any { it.contains("ainda não confirmado") })
    }
}
