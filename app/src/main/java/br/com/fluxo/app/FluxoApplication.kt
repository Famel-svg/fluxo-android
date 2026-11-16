package br.com.fluxo.app

import android.app.Application
import br.com.fluxo.core.DebugEntitlementPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class FluxoApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: LedgerRepository by lazy { LedgerRepository(this) }

    val entitlements: EntitlementsSource by lazy {
        createEntitlements(this).let { if (DebugEntitlementPolicy.switchVisible(published = !BuildConfig.DEBUG)) DebugEntitlements(it) else it }
    }

    override fun onCreate() {
        super.onCreate()
        CaptureConnection.ensureComponentEnabled(this)
        CaptureKeeper.schedule(this)
        SmartAlertScheduler.schedule(this)
        observeLedgerForWidget()
    }

    @OptIn(FlowPreview::class)
    private fun observeLedgerForWidget() {
        appScope.launch {
            repository.transactions.drop(1).debounce(1000).collect { FluxoWidget.refresh(this@FluxoApplication) }
        }
    }
}
