package br.com.fluxo.app

import android.app.Notification
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Validates Android notification extraction, not listener access or live bank delivery. */
@RunWith(AndroidJUnit4::class)
class NotificationExtractionTest {
    @Test fun groupedSummaryIsIgnoredButIndividualGroupChildIsAccepted() {
        val summary = notification(title = "Pix recebido", body = "R$ 10,00")
        summary.notification.flags = summary.notification.flags or Notification.FLAG_GROUP_SUMMARY
        assertNull(extractNotification(summary))
        assertNotNull(extractNotification(notification(title = "Pix recebido", body = "R$ 10,00")))
    }

    @Test fun bigTextWinsWithoutAppendingShortTextAgain() {
        val candidate = requireNotNull(extractNotification(notification(
            title = "Pix recebido", body = "R$ 10,00", bigText = "Você recebeu R$ 10,00 por Pix",
        )))
        assertEquals("Pix recebido\nVocê recebeu R$ 10,00 por Pix", candidate.text)
        assertEquals(1, Regex("R\\$").findAll(candidate.text).count())
    }

    @Test fun missingOrBlankBigTextFallsBackToShortText() {
        listOf(null, "", " \n ").forEach { bigText ->
            val candidate = requireNotNull(extractNotification(notification(
                title = "Compra aprovada", body = "R$ 12,00 no débito", bigText = bigText,
            )))
            assertEquals("Compra aprovada\nR$ 12,00 no débito", candidate.text)
        }
    }

    @Test fun titleOnlyBodyOnlyAndRepeatedTitleHaveNoArtificialDuplicates() {
        assertEquals("Pix recebido R$ 10,00", extractNotification(notification(title = "Pix recebido R$ 10,00"))?.text)
        assertEquals("Pix recebido R$ 10,00", extractNotification(notification(body = "Pix recebido R$ 10,00"))?.text)
        assertEquals("Pix recebido R$ 10,00", extractNotification(notification(
            title = "Pix recebido R$ 10,00", body = "Pix recebido R$ 10,00",
        ))?.text)
    }

    @Test fun emptyOrWhitespaceTextIsIgnored() {
        assertNull(extractNotification(notification()))
        assertNull(extractNotification(notification(title = " \n ", body = "\t", bigText = " ")))
    }

    @Test fun preservedOriginalTimeAndKeyKeepIdentityAcrossUpdates() {
        val original = requireNotNull(extractNotification(notification(
            title = "Pix recebido", body = "R$ 10,00", originalTime = 100L, postTime = 200L,
        )))
        val updated = requireNotNull(extractNotification(notification(
            title = "Pix recebido", bigText = "Pix recebido R$ 10,00 de contato", originalTime = 100L, postTime = 500L,
        )))
        assertEquals(original.sourceKey, updated.sourceKey)
        assertEquals(100L, updated.timestamp)
        assertEquals("com.nu.production", updated.packageName)
        assertTrue(original.sourceKey.matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun missingOriginalTimeUsesPostTime() {
        listOf(0L, -1L).forEach { originalTime ->
            val candidate = requireNotNull(extractNotification(notification(
                title = "Pix recebido", originalTime = originalTime, postTime = 1234L,
            )))
            assertEquals(1234L, candidate.timestamp)
        }
    }

    @Test fun differentKeysOrOriginalTimesProduceDifferentIdentities() {
        val first = requireNotNull(extractNotification(notification(title = "Pix recebido", id = 1, originalTime = 100L)))
        val otherKey = requireNotNull(extractNotification(notification(title = "Pix recebido", id = 2, originalTime = 100L)))
        val otherTime = requireNotNull(extractNotification(notification(title = "Pix recebido", id = 1, originalTime = 101L)))
        assertNotEquals(first.sourceKey, otherKey.sourceKey)
        assertNotEquals(first.sourceKey, otherTime.sourceKey)
    }

    @Test fun missingOriginalTimeExposesStableLifecycleAliasAcrossUpdates() {
        val first = requireNotNull(extractNotification(notification(
            title = "Pix recebido", body = "R$ 10,00", originalTime = 0L, postTime = 100L,
        )))
        val update = requireNotNull(extractNotification(notification(
            title = "Pix recebido", body = "R$ 10,00", originalTime = 0L, postTime = 200L,
        )))
        val distinct = requireNotNull(extractNotification(notification(
            title = "Pix recebido", body = "R$ 10,00", originalTime = 0L, postTime = 200L, id = 2,
        )))
        assertNotNull(first.fallbackIdentity)
        assertEquals(first.fallbackIdentity, update.fallbackIdentity)
        assertNotEquals(first.fallbackIdentity, distinct.fallbackIdentity)
        assertNull(extractNotification(notification(title = "Pix recebido", originalTime = 100L))?.fallbackIdentity)
    }

    private fun notification(
        title: String? = null,
        body: String? = null,
        bigText: String? = null,
        originalTime: Long = 100L,
        postTime: Long = 200L,
        id: Int = 1,
    ): StatusBarNotification {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notification = Notification.Builder(context, "extraction-test")
            .setWhen(originalTime).setGroup("bank-events").build()
        notification.extras = Bundle().apply {
            title?.let { putCharSequence(Notification.EXTRA_TITLE, it) }
            body?.let { putCharSequence(Notification.EXTRA_TEXT, it) }
            bigText?.let { putCharSequence(Notification.EXTRA_BIG_TEXT, it) }
        }
        return StatusBarNotification("com.nu.production", "com.nu.production", id, "test",
            Process.myUid(), Process.myPid(), 0, notification, UserHandle.getUserHandleForUid(Process.myUid()), postTime)
    }
}
