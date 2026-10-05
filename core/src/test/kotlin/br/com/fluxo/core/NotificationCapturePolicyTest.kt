package br.com.fluxo.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCapturePolicyTest {
    private val ownPackage = "br.com.fluxo.app"
    private val legacyBank = "com.nu.production"
    private val arbitraryApp = "com.example.wallet"

    @Test
    fun `arbitrary app requires global capture and explicit multiapp consent`() {
        assertFalse(policy(captureEnabled = false, allowOtherApps = true, packageName = arbitraryApp))
        assertFalse(policy(captureEnabled = true, allowOtherApps = false, packageName = arbitraryApp))
        assertTrue(policy(captureEnabled = true, allowOtherApps = true, packageName = arbitraryApp))
    }

    @Test
    fun `selected legacy bank remains compatible without granting universal access`() {
        assertTrue(policy(captureEnabled = true, allowOtherApps = false, packageName = legacyBank))
        assertFalse(policy(captureEnabled = true, allowOtherApps = false, packageName = arbitraryApp))
    }

    @Test
    fun `own app is always rejected`() {
        assertFalse(policy(captureEnabled = true, allowOtherApps = true, packageName = ownPackage))
    }

    private fun policy(captureEnabled: Boolean, allowOtherApps: Boolean, packageName: String) =
        NotificationCapturePolicy.allows(packageName, ownPackage, captureEnabled, allowOtherApps, setOf(legacyBank))
}
