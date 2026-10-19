package br.com.fluxo.core

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class FeaturePolicyTest {
    @Test fun freeCreatesAccountsOnlyBelowLimit() {
        assertTrue(FeaturePolicy.canCreateAccount(FeaturePolicy.MAX_CONTAS_FREE - 1, false))
        assertFalse(FeaturePolicy.canCreateAccount(FeaturePolicy.MAX_CONTAS_FREE, false))
        assertFalse(FeaturePolicy.canCreateAccount(FeaturePolicy.MAX_CONTAS_FREE + 3, false)) // already over after losing Pro
    }

    @Test fun proCreatesAccountsWithoutLimit() { assertTrue(FeaturePolicy.canCreateAccount(50, true)) }

    @Test fun featuresFollowPro() { ProFeature.entries.forEach {
        assertTrue(FeaturePolicy.isAvailable(it, true))
        assertFalse(FeaturePolicy.isAvailable(it, false))
    } }
}
