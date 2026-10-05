package br.com.fluxo.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.Direction
import br.com.fluxo.core.ReviewStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPrivacyDeduplicationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val repository get() = (instrumentation.targetContext.applicationContext as FluxoApplication).repository
    private var prepared = false
    
    private val bankApp = "com.nu.production"
    private val walletApp = "com.example.wallet"
    private val deliveryApp = "com.example.delivery"
    private val emailApp = "com.google.android.gm"
    private val messagingApp = "com.whatsapp"
    private val socialApp = "com.instagram.android"
    private val systemApp = "android"
    
    @Before fun prepare() = runBlocking {
        assumeTrue("Requires explicit disposable-emulator notificationE2e opt-in",
            InstrumentationRegistry.getArguments().getString("notificationE2e") == "true")
        assumeTrue("Physical devices forbidden", Build.HARDWARE in setOf("ranchu", "goldfish"))
        prepared = true
        repository.clear()
        repository.setCaptureEnabled(true)
        repository.setAllowOtherApps(true)
        listOf(bankApp).forEach {
        repository.setBankEnabled(it, true)
         }
    }
    
    @After fun clean() = runBlocking {
        if (!prepared) return@runBlocking
        repository.clear()
        repository.setCaptureEnabled(false)
        repository.setAllowOtherApps(false)
        listOf(bankApp).forEach {
        repository.setBankEnabled(it, false)
         }
    }

    @Test fun verifyPrivacyNoRawBodyPersisted() = runBlocking {
        val payload = "Pix recebido Faker Silva CPF 123.456.789-00 valor de R$ 100,00"
        
        repository.ingest("tx_bank", bankApp, 1000L, payload)
        
        val transactions = repository.transactions.value
        assertEquals(1, transactions.size)
        
        val tx = transactions.first()
        assertEquals(10000L, tx.amountCents)
        assertEquals(Direction.INCOME, tx.direction)
        assertFalse("O banco de dados não deve conter o nome sensível", tx.description.contains("Faker"))
        assertFalse("O banco de dados não deve conter CPF", tx.description.contains("123"))
        val reopened = LedgerRepository(instrumentation.targetContext)
        reopened.refresh()
        assertEquals(transactions, reopened.transactions.value)
        instrumentation.targetContext.openOrCreateDatabase("fluxo.db", 0, null).use { db ->
            db.rawQuery("SELECT * FROM transactions", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                for (column in 0 until cursor.columnCount) {
                    val stored = cursor.getString(column).orEmpty()
                    assertFalse("Raw name must not be persisted", stored.contains("Faker"))
                    assertFalse("Raw identifier must not be persisted", stored.contains("123.456.789-00"))
                    assertFalse("Raw body must not be persisted", stored.contains(payload))
                }
            }
        }
    }
    
    @Test fun verifyDeduplicationWithoutNewTransactions() = runBlocking {
        repository.ingest("tx_wallet_1", walletApp, 1000L, "Compra aprovada R$ 15,20 no débito")
        assertEquals(1, repository.transactions.value.size)
        
        // Exact duplicate notification resend (simulating same ID/content or updated timestamp)
        repository.ingest("tx_wallet_1", walletApp, 2000L, "Compra aprovada R$ 15,20 no débito")
        assertEquals(1, repository.transactions.value.size) // Should not increase
    }

    @Test fun testBloqueioFontesNaoAutorizadas() = runBlocking {
        repository.setAllowOtherApps(false)
        repository.setBankEnabled(bankApp, false)
        val payload = "Pix recebido R$ 500,00"
        assertFalse(repository.allowsNotification(bankApp))
        repository.ingest("tx_unauth", bankApp, 1000L, payload)
        assertEquals(0, repository.transactions.value.size)
        repository.setBankEnabled(bankApp, true)
        repository.ingest("tx_authorized", bankApp, 1001L, payload)
        assertEquals(50000L, repository.transactions.value.single().amountCents)
    }

    @Test fun multiappConsentAllowsUnselectedBankButCapturePauseStillBlocks() = runBlocking {
        repository.setBankEnabled(bankApp, false)
        assertTrue(repository.allowOtherApps.value)
        assertTrue(repository.allowsNotification(bankApp))
        repository.ingest("tx_multiapp", bankApp, 1000L, "Pix recebido R$ 500,00")
        assertEquals(1, repository.transactions.value.size)
        repository.setCaptureEnabled(false)
        assertFalse(repository.allowsNotification(bankApp))
        repository.ingest("tx_paused", bankApp, 1001L, "Pix recebido R$ 600,00")
        assertEquals(1, repository.transactions.value.size)
    }
    
    @Test fun testDescarteConteudoSensivelENaoFinanceiro() = runBlocking {
        // Promotion message - should be completely discarded
        repository.ingest("tx_nontx", socialApp, 1000L, "Promoção imperdível Compre 2 leve 3")
        assertEquals(0, repository.transactions.value.size)
    }
    
    @Test fun verifyMultiCategoryAndReviewLane() = runBlocking {
        // Partially matched parsing might mark it as review lane
                repository.ingest("tx_email", emailApp, 1000L, "Comprovante 123 Pagamento aprovado 89,90 processado")
 
                val transactions = repository.transactions.value
                assertEquals(1, transactions.size)
                //Usually, parser identifies 89,90
                val tx = transactions.first()
                assertEquals(null, tx.amountCents)
                assertEquals(ReviewStatus.REVIEW, tx.status)
    }
}
