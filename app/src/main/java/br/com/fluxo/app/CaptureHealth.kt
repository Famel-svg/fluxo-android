package br.com.fluxo.app

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session-only diagnostics. Contains no amounts, descriptions or raw notification text. */
data class CaptureSnapshot(
    val connected: Boolean? = null,
    val lastObserved: Map<String, Long> = emptyMap(),
)

object CaptureHealth {
    private val mutable = MutableStateFlow(CaptureSnapshot())
    val state = mutable.asStateFlow()

    @Synchronized fun connected(value: Boolean) {
        mutable.value = mutable.value.copy(connected = value)
    }

    @Synchronized fun observed(packageName: String, at: Long) {
        if (supportedBanks.none { it.packageName == packageName } || at <= 0) return
        val previous = mutable.value.lastObserved[packageName] ?: 0
        mutable.value = mutable.value.copy(lastObserved = mutable.value.lastObserved +
            (packageName to maxOf(at, previous)))
    }
}

@Composable
fun CaptureHealthCard(access: Boolean, capture: Boolean, banks: Set<String>) {
    val snapshot by CaptureHealth.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    CaptureHealthContent(snapshot, access, capture, banks, message) {
        message = try {
            NotificationListenerService.requestRebind(ComponentName(context, BankNotificationListener::class.java))
            "Reconexão solicitada. Aguarde a confirmação do Android."
        } catch (_: Exception) {
            "Não foi possível solicitar conexão. Confira o acesso nas configurações Android."
        }
    }
}

@Composable
internal fun CaptureHealthContent(
    snapshot: CaptureSnapshot, access: Boolean, capture: Boolean, banks: Set<String>,
    message: String? = null, reconnect: () -> Unit = {},
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Diagnóstico da captura", style = MaterialTheme.typography.titleMedium)
            Text(if (access) "Permissão: concedida" else "Permissão: não concedida")
            Text(if (capture) "Captura: habilitada" else "Captura: pausada")
            Text(when (snapshot.connected) {
                true -> "Serviço: conectado ao Android"
                false -> "Serviço: desconectado do Android"
                null -> "Serviço: conexão ainda não confirmada nesta sessão"
            })
            if (banks.isEmpty()) Text("Selecione pelo menos um banco para capturar.")
            supportedBanks.filter { it.packageName in banks }.forEach { bank ->
                val time = snapshot.lastObserved[bank.packageName]?.let {
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
                }
                Text("${bank.name}: ${time?.let { "última notificação observada em $it" }
                    ?: "nenhuma notificação observada nesta sessão"}")
            }
            Text("Observada não significa lançamento confirmado. Mensagens sem valor ou promocionais podem ser descartadas. Ausência de notificações não comprova falha.",
                style = MaterialTheme.typography.bodySmall)
            if (access && capture && banks.isNotEmpty() && snapshot.connected != true) {
                OutlinedButton(onClick = reconnect) { Text("Solicitar reconexão") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Mantenha notificações do banco ativas. O Android pode ocultar conteúdo sensível. Confira pendências e complete manualmente o que faltar.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
