package br.com.fluxo.core

import org.junit.Assert.*
import org.junit.Test

class DebugEntitlementPolicyTest {
    @Test fun publishedHidesSwitchAndIgnoresForcedState() {
        assertFalse(DebugEntitlementPolicy.switchVisible(published = true))
        assertFalse(DebugEntitlementPolicy.effectivePro(published = true, real = false, forced = true))
        assertTrue(DebugEntitlementPolicy.effectivePro(published = true, real = true, forced = false))
    }

    @Test fun unpublishedShowsSwitchAndAppliesForcedState() {
        assertTrue(DebugEntitlementPolicy.switchVisible(published = false))
        assertTrue(DebugEntitlementPolicy.effectivePro(published = false, real = false, forced = true))
        assertFalse(DebugEntitlementPolicy.effectivePro(published = false, real = true, forced = false))
        assertTrue(DebugEntitlementPolicy.effectivePro(published = false, real = true, forced = null))
    }
}
