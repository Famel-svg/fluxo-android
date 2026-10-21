package br.com.fluxo.app

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fluxo.core.IgnoreReason
import br.com.fluxo.core.ListenerState
import br.com.fluxo.core.ListenerStatus
import br.com.fluxo.core.NotificationSource
import br.com.fluxo.core.NotificationSourceClassifier
import br.com.fluxo.core.OemHints
import br.com.fluxo.core.ReconnectResult
import br.com.fluxo.core.SourceCounters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session-only diagnostics. Contains no amounts, descriptions or raw notification text. */
data class CaptureSnapshot(
    val connected: Boolean? = null,
    val lastObserved: Map<String, Long> = emptyMap(),
    val reconnecting: Boolean = false,
    val reconnectAttempted: Boolean = false,
    val lastResult: ReconnectResult? = null,
    val counters: Map<String, SourceCounters> = emptyMap(),
    /** Notifications dropped by the capture policy (capture paused or Fluxo's own); count only, no package kept. */
    val blockedByPolicy: Int = 0,
) {
    fun listenerState(access: Boolean): ListenerState =
        ListenerStatus.resolve(access, connected, reconnecting, reconnectAttempted)
}

object CaptureHealth {
    private val mutable = MutableStateFlow(CaptureSnapshot())
    val state = mutable.asStateFlow()

    @Synchronized fun connected(value: Boolean) {
        val current = mutable.value
        mutable.value = if (value) current.copy(connected = true, reconnectAttempted = false, lastResult = null)
        else current.copy(connected = false)
    }

    @Synchronized fun observed(packageName: String, at: Long) {
        if (packageName.isBlank() || at <= 0) return
        val previous = mutable.value.lastObserved[packageName] ?: 0
        val counters = mutable.value.counters
        mutable.value = mutable.value.copy(
            lastObserved = mutable.value.lastObserved + (packageName to maxOf(at, previous)),
            counters = counters + (packageName to (counters[packageName] ?: SourceCounters())
                .let { it.copy(observed = it.observed + 1) }),
        )
    }

    @Synchronized fun blocked() {
        mutable.value = mutable.value.copy(blockedByPolicy = mutable.value.blockedByPolicy + 1)
    }

    /** A notification of [packageName] became (or updated) a ledger entry. */
    @Synchronized fun stored(packageName: String) = updateCounters(packageName) { it.copy(stored = it.stored + 1) }

    /** A notification was discarded; only the fixed category is kept. */
    @Synchronized fun ignored(packageName: String, reason: IgnoreReason) =
        updateCounters(packageName) { it.copy(ignored = it.ignored + 1, lastIgnore = reason) }

    private fun updateCounters(packageName: String, change: (SourceCounters) -> SourceCounters) {
        if (packageName.isBlank()) return
        val counters = mutable.value.counters
        mutable.value = mutable.value.copy(
            counters = counters + (packageName to change(counters[packageName] ?: SourceCounters())))
    }

    @Synchronized fun reconnectStarted() {
        mutable.value = mutable.value.copy(reconnecting = true, lastResult = null)
    }

    @Synchronized fun reconnectFinished(result: ReconnectResult) {
        mutable.value = mutable.value.copy(
            reconnecting = false,
            reconnectAttempted = result !is ReconnectResult.Connected && result !is ReconnectResult.NoAccess,
            lastResult = result,
        )
    }

    @Synchronized internal fun resetForTest() {
        mutable.value = CaptureSnapshot()
    }
}

/** User-facing service line of the Estado block. */
internal fun serviceStateText(state: ListenerState): String = when (state) {
    ListenerState.ACCESS_MISSING -> "Serviço indisponível sem permissão"
    ListenerState.WAITING -> "Serviço aguardando conexão com o Android"
    ListenerState.DISCONNECTED -> "Serviço desconectado"
    ListenerState.CONNECTING -> "Tentando conectar…"
    ListenerState.CONNECTED -> "Serviço conectado"
    ListenerState.STUCK -> "O Android não está iniciando o serviço"
}

@Composable
fun CaptureHealthCard(access: Boolean, capture: Boolean) {
    val snapshot by CaptureHealth.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CaptureHealthContent(snapshot, access, capture) {
        CaptureConnection.launchReconnect(context)
    }
}

/** Ways to stop the battery manager from closing the capture service in the background. */
@Composable
private fun KeepCaptureRunning(manufacturer: String = Build.MANUFACTURER) {
    val context = LocalContext.current
    var openFailed by remember { mutableStateOf(false) }
    fun open(target: SettingsTarget) { openFailed = !CaptureConnection.open(context, target) }
    val xiaomi = manufacturer.trim().lowercase(java.util.Locale.ROOT) in setOf("xiaomi", "redmi", "poco")
    Text("Manter a captura ativa", style = MaterialTheme.typography.titleSmall)
    Text("O Fluxo não precisa ficar aberto, mas o Android pode fechar o serviço para economizar bateria. " +
        "Deixe o Fluxo sem restrição de bateria" + if (xiaomi) " e com início automático ligado." else ".",
        style = MaterialTheme.typography.bodySmall)
    FluxoWrapButton("Abrir otimização de bateria", onClick = { open(SettingsTarget.BATTERY) })
    if (xiaomi) FluxoWrapButton("Abrir início automático", onClick = { open(SettingsTarget.AUTOSTART) })
    OemHints.forManufacturer(manufacturer)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (openFailed) Text("Não foi possível abrir essa tela. Abra as Configurações do Android manualmente.",
        style = MaterialTheme.typography.bodySmall)
}

/** Shown only after a reconnect attempt left the listener disconnected: plain explanation and ways out. */
@Composable
internal fun ListenerRecoveryGuide(manufacturer: String = Build.MANUFACTURER) {
    val context = LocalContext.current
    var openFailed by remember { mutableStateOf(false) }
    fun open(target: SettingsTarget) { openFailed = !CaptureConnection.open(context, target) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("O Android não está iniciando o serviço", style = MaterialTheme.typography.titleSmall)
        Text("A permissão está concedida, mas o Android não conectou o serviço de notificações. " +
            "Desligue e ligue de novo o acesso do Fluxo e confira as restrições de bateria.",
            style = MaterialTheme.typography.bodySmall)
        FluxoWrapButton("Abrir acesso às notificações", onClick = { open(SettingsTarget.NOTIFICATION_ACCESS) })
        FluxoWrapButton("Abrir informações do app", onClick = { open(SettingsTarget.APP_DETAILS) })
        FluxoWrapButton("Abrir otimização de bateria", onClick = { open(SettingsTarget.BATTERY) })
        OemHints.forManufacturer(manufacturer)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (openFailed) Text("Não foi possível abrir essa tela. Abra as Configurações do Android manualmente.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun CaptureHealthContent(
    snapshot: CaptureSnapshot, access: Boolean, capture: Boolean, reconnect: () -> Unit = {},
) {
    val context = LocalContext.current
    val note = MaterialTheme.typography.bodySmall.copy(color = FluxoColors.Mute)
    val state = snapshot.listenerState(access)
    FluxoSettingsCard {
        run {
            Text("Diagnóstico da captura", style = MaterialTheme.typography.titleMedium)
            Text(if (access) "Permissão: concedida" else "Permissão: não concedida")
            Text(if (capture) "Captura: habilitada" else "Captura: pausada")
            Text(when (state) {
                ListenerState.CONNECTED -> "Serviço: conectado ao Android"
                ListenerState.DISCONNECTED -> "Serviço: desconectado do Android"
                ListenerState.CONNECTING -> "Serviço: tentando conectar…"
                ListenerState.STUCK -> "Serviço: o Android não iniciou o serviço após a tentativa de reconexão"
                ListenerState.ACCESS_MISSING -> "Serviço: aguardando a permissão de acesso"
                ListenerState.WAITING -> "Serviço: conexão ainda não confirmada nesta sessão"
            })
            val apps = snapshot.lastObserved.keys + snapshot.counters.keys
            val (banks, others) = apps.sortedBy { appLabel(context, it).lowercase() }.partition {
                NotificationSourceClassifier.classify(it, supportedBanks.map { bank -> bank.packageName }) == NotificationSource.BANK
            }
            if (snapshot.blockedByPolicy > 0) Text("Descartadas pela política (captura pausada ou do próprio Fluxo): ${snapshot.blockedByPolicy}")
            if (!access) Text("Se o Android bloquear a permissão: em Informações do app, abra o menu e escolha Permitir configurações restritas.", style = note)
            if (apps.isEmpty()) Text("Nenhuma notificação observada nesta sessão.")
            listOf("Bancos" to banks, "Outros apps" to others).filter { it.second.isNotEmpty() }.forEach { (title, group) ->
                Text(title, style = MaterialTheme.typography.titleSmall)
                group.forEach { pkg ->
                    val time = snapshot.lastObserved[pkg]?.let {
                        DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
                    }
                    val counts = snapshot.counters[pkg]?.takeIf { it.observed > 0 || it.ignored > 0 || it.stored > 0 }?.summary()
                    Text("${appLabel(context, pkg)}: ${listOfNotNull(counts, time?.let { "última às $it" }).joinToString(" · ")}")
                }
            }
            Text("Observada não significa lançamento confirmado. Mensagens sem valor ou promocionais podem ser descartadas. Ausência de notificações não comprova falha.",
                style = note)
            if (access && capture && state != ListenerState.CONNECTED) {
                val busy = state == ListenerState.CONNECTING
                FluxoWrapButton(if (busy) "Conectando…" else "Solicitar reconexão", onClick = reconnect, enabled = !busy)
            }
            if (!snapshot.reconnecting) snapshot.lastResult?.let { Text(it.message, style = note) }
            // The recovery guide already shows these settings when the service is stuck.
            if (access && capture && state != ListenerState.STUCK) KeepCaptureRunning()
            Text("Mantenha notificações do banco ativas. O Android pode ocultar conteúdo sensível. Confira pendências e complete manualmente o que faltar.",
                style = note)
        }
    }
}

/** Known bank name, else the installed app label, else the package name (labels of other apps may be hidden by Android). */
private fun appLabel(context: android.content.Context, packageName: String): String =
    supportedBanks.firstOrNull { it.packageName == packageName }?.name ?: try {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    } catch (_: Exception) { packageName }
