package br.com.fluxo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureConnectionLogicTest {
    private val now = 10_000_000L

    private fun auto(
        access: Boolean = true, capture: Boolean = true, connected: Boolean? = null, reconnecting: Boolean = false,
        attempted: Boolean = false, last: Long = 0L,
    ) = ReconnectPolicy.shouldAutoReconnect(access, capture, connected, reconnecting, attempted, last, now)

    @Test fun `auto reconnect runs when granted enabled and not connected`() {
        assertTrue(auto())
        assertTrue(auto(connected = false))
    }

    @Test fun `auto reconnect never runs without access`() {
        assertFalse(auto(access = false))
    }

    @Test fun `auto reconnect respects capture pause, connection, in flight and once per process`() {
        assertFalse(auto(capture = false))
        assertFalse(auto(connected = true))
        assertFalse(auto(reconnecting = true))
        assertFalse(auto(attempted = true))
    }

    @Test fun `auto reconnect honors cooldown across process starts`() {
        assertFalse(auto(last = now - 60_000))
        assertTrue(auto(last = now - ReconnectPolicy.COOLDOWN_MS))
        // A clock that moved backwards must not block forever.
        assertTrue(auto(last = now + 5_000))
    }

    @Test fun `status state machine`() {
        fun s(access: Boolean, connected: Boolean?, reconnecting: Boolean = false, attempted: Boolean = false) =
            ListenerStatus.resolve(access, connected, reconnecting, attempted)
        assertEquals(ListenerState.ACCESS_MISSING, s(false, null))
        assertEquals(ListenerState.ACCESS_MISSING, s(false, true, reconnecting = true))
        assertEquals(ListenerState.WAITING, s(true, null))
        assertEquals(ListenerState.DISCONNECTED, s(true, false))
        assertEquals(ListenerState.CONNECTING, s(true, null, reconnecting = true))
        assertEquals(ListenerState.CONNECTING, s(true, false, reconnecting = true, attempted = true))
        assertEquals(ListenerState.CONNECTED, s(true, true))
        assertEquals(ListenerState.CONNECTED, s(true, true, attempted = true))
        assertEquals(ListenerState.STUCK, s(true, null, attempted = true))
        assertEquals(ListenerState.STUCK, s(true, false, attempted = true))
    }

    @Test fun `component is re-enabled even when disable fails or rebind fails`() {
        val calls = mutableListOf<String>()
        val failures = ComponentRecycle.run(
            disable = { calls += "disable"; error("boom") },
            enable = { calls += "enable" },
            rebind = { calls += "rebind"; error("boom") },
        )
        assertEquals(listOf("disable", "enable", "rebind"), calls)
        assertEquals(listOf("desligar componente", "pedir reconexão"), failures)
    }

    @Test fun `enable failure is reported and rebind still attempted`() {
        val calls = mutableListOf<String>()
        val failures = ComponentRecycle.run({ calls += "d" }, { calls += "e"; error("x") }, { calls += "r" })
        assertEquals(listOf("d", "e", "r"), calls)
        assertEquals(listOf("religar componente"), failures)
    }

    @Test fun `clean recycle reports no failures`() {
        assertTrue(ComponentRecycle.run({}, {}, {}).isEmpty())
    }

    @Test fun `access is matched by exact component`() {
        val cls = "br.com.fluxo.app.BankNotificationListener"
        assertTrue(NotificationAccess.isGranted("br.com.fluxo.app/$cls", "br.com.fluxo.app", cls))
        assertTrue(NotificationAccess.isGranted("a.b/c.D:br.com.fluxo.app/.BankNotificationListener", "br.com.fluxo.app", cls))
        assertFalse(NotificationAccess.isGranted("br.com.fluxo.app/br.com.fluxo.app.Other", "br.com.fluxo.app", cls))
        assertFalse(NotificationAccess.isGranted("other.app/$cls", "br.com.fluxo.app", cls))
        assertFalse(NotificationAccess.isGranted(null, "br.com.fluxo.app", cls))
        assertFalse(NotificationAccess.isGranted("", "br.com.fluxo.app", cls))
    }

    @Test fun `oem hints are accurate or absent`() {
        assertTrue(OemHints.forManufacturer("Xiaomi")!!.contains("Início automático"))
        assertTrue(OemHints.forManufacturer("POCO")!!.contains("Sem restrições"))
        assertTrue(OemHints.forManufacturer("samsung")!!.contains("suspensão"))
        assertNull(OemHints.forManufacturer("Google"))
        assertNull(OemHints.forManufacturer(null))
    }

    @Test fun `counter summary matches the diagnostics wording`() {
        val c = SourceCounters(observed = 3, stored = 1, ignored = 2, lastIgnore = IgnoreReason.NO_FINANCIAL_WORD)
        assertEquals("3 observadas · 1 lançada · 2 ignoradas (sem palavra financeira)", c.summary())
        assertEquals("1 observada · 1 lançada · 0 ignoradas", SourceCounters(1, 1, 0).summary())
    }

    @Test fun `screening gives a fixed category for each discard and accepts real events`() {
        fun reason(text: String) = (NotificationParser.screen(text) as? NotificationParser.Screening.Ignored)?.reason
        assertEquals(IgnoreReason.EMPTY_TEXT, reason("   "))
        assertEquals(IgnoreReason.NO_FINANCIAL_WORD, reason("Seu extrato do mês está pronto"))
        assertEquals(IgnoreReason.PROMOTION, reason("Aproveite a oferta de pagamento parcelado"))
        assertEquals(IgnoreReason.NO_AMOUNT, reason("Pix recebido com sucesso"))
        assertEquals(IgnoreReason.UNCLEAR_DIRECTION, reason("Pix de R\$ 10,00"))
        assertTrue(NotificationParser.screen("Pix recebido R\$ 12,34") is NotificationParser.Screening.Accepted)
    }

    @Test fun `screening agrees with parse on what is accepted`() {
        listOf("Pix recebido R\$ 12,34", "Compra aprovada no crédito R\$ 45,67", "Oferta: ganhe cashback", "Olá").forEach {
            val accepted = NotificationParser.screen(it) is NotificationParser.Screening.Accepted
            val parsed = NotificationParser.parse(it)
            val expected = parsed != null && parsed.hasAmountCandidate &&
                !(parsed.direction == Direction.UNKNOWN && !parsed.hasEventStateSignal)
            assertEquals(it, expected, accepted)
        }
    }
}
