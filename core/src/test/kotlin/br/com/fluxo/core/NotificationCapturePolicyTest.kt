package br.com.fluxo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCapturePolicyTest {
    private val ownPackage = "br.com.fluxo.app"

    @Test
    fun capturesAnyAppWhileCaptureIsOn() {
        assertTrue(NotificationCapturePolicy.allows("com.example.vr", ownPackage, true))
        assertTrue(NotificationCapturePolicy.allows("com.nu.production", ownPackage, true))
    }

    @Test
    fun pausedCaptureBlocksEverything() {
        assertFalse(NotificationCapturePolicy.allows("com.nu.production", ownPackage, false))
    }

    @Test
    fun neverCapturesItsOwnNotifications() {
        assertFalse(NotificationCapturePolicy.allows(ownPackage, ownPackage, true))
        assertFalse(NotificationCapturePolicy.allows("", ownPackage, true))
    }

    @Test
    fun classifierSeparatesBanksFromOthers() {
        val banks = listOf("com.nu.production", "com.itau")
        assertEquals(NotificationSource.BANK, NotificationSourceClassifier.classify("com.itau", banks))
        assertEquals(NotificationSource.OTHER, NotificationSourceClassifier.classify("com.example.vr", banks))
    }
}
