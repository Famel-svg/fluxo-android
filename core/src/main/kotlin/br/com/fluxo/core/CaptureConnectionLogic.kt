package br.com.fluxo.core

import java.util.Locale

/** Pure decision logic for the notification listener connection. No Android types, no personal data. */

/** Honest service status, derived from real signals only (access list + listener callbacks). */
enum class ListenerState {
    /** Notification access is not granted for our listener component. */
    ACCESS_MISSING,
    /** Granted; the system has not connected the listener yet and no recovery was tried. */
    WAITING,
    /** Granted; the listener was connected earlier in this session and the system disconnected it. */
    DISCONNECTED,
    /** Granted; a reconnect attempt is running right now. */
    CONNECTING,
    /** The listener's own onListenerConnected fired. */
    CONNECTED,
    /** Granted, a reconnect was tried and the system still did not connect the listener. */
    STUCK,
}

object ListenerStatus {
    fun resolve(access: Boolean, connected: Boolean?, reconnecting: Boolean, reconnectAttempted: Boolean): ListenerState = when {
        !access -> ListenerState.ACCESS_MISSING
        connected == true -> ListenerState.CONNECTED
        reconnecting -> ListenerState.CONNECTING
        reconnectAttempted -> ListenerState.STUCK
        connected == false -> ListenerState.DISCONNECTED
        else -> ListenerState.WAITING
    }
}

/** Outcome of one reconnect attempt, with a fixed user-facing message. */
sealed interface ReconnectResult {
    val message: String
    data object Connected : ReconnectResult { override val message = "Serviço conectado" }
    data object NotConnected : ReconnectResult {
        override val message = "O Android não iniciou o serviço. Veja as opções abaixo."
    }
    data object NoAccess : ReconnectResult {
        override val message = "Conceda o acesso às notificações antes de reconectar."
    }
    data object AlreadyRunning : ReconnectResult { override val message = "Já existe uma tentativa em andamento." }
    data class Failed(val step: String) : ReconnectResult {
        override val message get() = "Não foi possível reiniciar o serviço ($step). Use as opções abaixo."
    }
}

object ReconnectPolicy {
    const val SETTLE_MS = 3_000L
    const val COOLDOWN_MS = 10 * 60_000L

    /**
     * Automatic recovery on app foreground. Never without access or while capture is paused, never
     * when already connected, once per process, and not again within the cooldown since the last
     * automatic attempt (which survives process restarts).
     */
    fun shouldAutoReconnect(
        access: Boolean, captureEnabled: Boolean, connected: Boolean?, reconnecting: Boolean,
        attemptedThisProcess: Boolean, lastAutoAttemptAt: Long, now: Long,
        cooldownMs: Long = COOLDOWN_MS,
    ): Boolean = access && captureEnabled && connected != true && !reconnecting && !attemptedThisProcess &&
        (lastAutoAttemptAt <= 0L || now < lastAutoAttemptAt || now - lastAutoAttemptAt >= cooldownMs)
}

/** Disable -> enable toggle that can never leave the component disabled. */
object ComponentRecycle {
    /** Runs disable, always enable, then rebind. Returns the failed step names (empty = all ok). */
    fun run(disable: () -> Unit, enable: () -> Unit, rebind: () -> Unit): List<String> {
        val failures = mutableListOf<String>()
        try {
            disable()
        } catch (_: Exception) {
            failures += "desligar componente"
        } finally {
            try { enable() } catch (_: Exception) { failures += "religar componente" }
        }
        try { rebind() } catch (_: Exception) { failures += "pedir reconexão" }
        return failures
    }
}

object NotificationAccess {
    /** True when the flattened enabled_notification_listeners setting lists exactly this component. */
    fun isGranted(flattened: String?, packageName: String, className: String): Boolean {
        if (flattened.isNullOrBlank()) return false
        return flattened.split(':').any { entry ->
            val parts = entry.trim().split('/')
            if (parts.size != 2 || parts[0] != packageName) false else {
                val cls = parts[1]
                (if (cls.startsWith(".")) packageName + cls else cls) == className
            }
        }
    }
}

object OemHints {
    /** One-line advice for restrictive battery managers, or null when no accurate line exists. */
    fun forManufacturer(manufacturer: String?): String? = when (manufacturer.orEmpty().trim().lowercase(Locale.ROOT)) {
        "xiaomi", "redmi", "poco" -> "Em aparelhos Xiaomi, Redmi e POCO: ative 'Início automático' e, em Bateria, 'Sem restrições'."
        "samsung" -> "Em aparelhos Samsung: em Bateria, tire o Fluxo da lista de apps em suspensão."
        "huawei", "honor" -> "Em aparelhos Huawei e Honor: permita que o Fluxo rode em segundo plano nas configurações de bateria e de início de apps."
        "oppo", "realme", "oneplus" -> "Em aparelhos OPPO, realme e OnePlus: permita início automático e atividade em segundo plano para o Fluxo."
        "vivo", "iqoo" -> "Em aparelhos vivo: permita que o Fluxo rode em segundo plano nas configurações de bateria."
        else -> null
    }
}

/** Fixed categories only: never the text, amount or sender content. */
enum class IgnoreReason(val label: String) {
    GROUP_SUMMARY("resumo de grupo"),
    EMPTY_TEXT("texto vazio"),
    NO_AMOUNT("sem valor em reais"),
    PROMOTION("promoção"),
    NO_FINANCIAL_WORD("sem palavra financeira"),
    UNCLEAR_DIRECTION("sem entrada ou saída clara"),
    WALLET_DUPLICATE("repetida da notificação do banco"),
}

/** Session counters of one source package. */
data class SourceCounters(
    val observed: Int = 0,
    val stored: Int = 0,
    val ignored: Int = 0,
    val lastIgnore: IgnoreReason? = null,
) {
    fun summary(): String {
        val base = "$observed observada${if (observed == 1) "" else "s"} · " +
            "$stored lançada${if (stored == 1) "" else "s"} · $ignored ignorada${if (ignored == 1) "" else "s"}"
        return if (ignored > 0 && lastIgnore != null) "$base (${lastIgnore.label})" else base
    }
}
