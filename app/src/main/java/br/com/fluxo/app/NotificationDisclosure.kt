package br.com.fluxo.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Full-screen prominent disclosure shown before the notification-access settings are opened. */
@Composable
internal fun NotificationDisclosureDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    Dialog(onDismissRequest = onDecline, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("disclosure"), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Acesso às notificações", style = MaterialTheme.typography.headlineSmall)
                    Text("O que o Fluxo lê: as notificações de aplicativos de bancos e de benefícios (como VR e VT), " +
                        "só para reconhecer compras, Pix e depósitos.")
                    Text("O que o Fluxo guarda: apenas o lançamento extraído (valor, data, estabelecimento e categoria). " +
                        "O texto da notificação não é guardado.")
                    Text("Onde fica: somente neste aparelho.")
                    Text("Nada é enviado: o aplicativo não tem permissão de internet.")
                    Text("Você pode revogar a qualquer momento nas configurações de acesso às notificações do Android " +
                        "ou pausando a captura no Fluxo.")
                }
                FluxoWrapButton("Concordo", onClick = onAgree, modifier = Modifier.testTag("disclosure.agree"))
                FluxoWrapButton("Agora não", onClick = onDecline, quiet = true, modifier = Modifier.testTag("disclosure.decline"))
            }
        }
    }
}

/**
 * Returns the action behind "open notification access": the system screen opens only after consent,
 * which is requested through the disclosure first.
 */
@Composable
internal fun rememberNotificationAccessOpener(
    consented: Boolean, onConsent: () -> Unit, onOpened: (Boolean) -> Unit, open: () -> Boolean,
): () -> Unit {
    var showing by rememberSaveable { mutableStateOf(false) }
    if (showing) NotificationDisclosureDialog(
        onAgree = { showing = false; onConsent(); onOpened(open()) },
        onDecline = { showing = false },
    )
    return { if (consented) onOpened(open()) else showing = true }
}
