package br.com.fluxo.app

import android.app.Activity
import android.content.Context
import android.util.Base64
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.coroutines.resume

/** Compra única `fluxo_pro` pelo Google Play Billing. O estado Pro fica em cache para funcionar offline. */
class BillingRepository(context: Context) : EntitlementsSource {
    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean(KEY_PRO, false))
    override val isPro: StateFlow<Boolean> = state.asStateFlow()
    private var details: ProductDetails? = null
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) apply(purchases, authoritative = false)
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private fun connected(block: () -> Unit) {
        if (client.isReady) return block()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { if (result.responseCode == BillingClient.BillingResponseCode.OK) block() }
            override fun onBillingServiceDisconnected() {}
        })
    }

    override fun restore() = connected {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) apply(purchases, authoritative = true)
        }
    }

    /** Concede só compra PURCHASED, assinada e do produto certo. Consulta bem-sucedida sem compra revoga o cache. */
    private fun apply(purchases: List<Purchase>, authoritative: Boolean) {
        val owned = purchases.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED && verified(it) }
        owned.filter { !it.isAcknowledged }.forEach {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) {}
        }
        if (owned.isNotEmpty() || authoritative) {
            state.value = owned.isNotEmpty()
            prefs.edit().putBoolean(KEY_PRO, owned.isNotEmpty()).apply()
        }
    }

    override suspend fun queryPrice(): String? = suspendCancellableCoroutine { cont ->
        connected {
            val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, found ->
                details = if (result.responseCode == BillingClient.BillingResponseCode.OK) found.productDetailsList.firstOrNull() else null
                if (cont.isActive) cont.resume(details?.oneTimePurchaseOfferDetails?.formattedPrice)
            }
        }
    }

    override fun launchPurchase(activity: Activity) {
        val d = details ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())).build()
        client.launchBillingFlow(activity, params)
    }

    private fun verified(p: Purchase): Boolean {
        if (BuildConfig.PLAY_LICENSE_KEY.isEmpty()) return false // sem chave configurada, compra nunca concede Pro
        return runCatching {
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(BuildConfig.PLAY_LICENSE_KEY, Base64.DEFAULT)))
            Signature.getInstance("SHA1withRSA").run { initVerify(key); update(p.originalJson.toByteArray()); verify(Base64.decode(p.signature, Base64.DEFAULT)) }
        }.getOrDefault(false)
    }

    private companion object {
        const val PRODUCT_ID = "fluxo_pro"
        const val KEY_PRO = "is_pro"
    }
}
