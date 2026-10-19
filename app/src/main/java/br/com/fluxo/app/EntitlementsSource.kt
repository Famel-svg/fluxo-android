package br.com.fluxo.app

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Estado do Fluxo Pro. Compra, preço e restauração só fazem algo no flavor play. */
interface EntitlementsSource {
    val isPro: StateFlow<Boolean>
    fun restore() {}
    suspend fun queryPrice(): String? = null
    fun launchPurchase(activity: Activity) {}
}

object AlwaysPro : EntitlementsSource {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}

/** Só em debug: força Pro ligado/desligado (null = segue [base]) para testes de interface. */
class DebugEntitlements(private val base: EntitlementsSource) : EntitlementsSource by base {
    val forced = MutableStateFlow<Boolean?>(null)
    override val isPro: StateFlow<Boolean> = combine(base.isPro, forced) { real, f -> f ?: real }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), SharingStarted.Eagerly, base.isPro.value)
}
