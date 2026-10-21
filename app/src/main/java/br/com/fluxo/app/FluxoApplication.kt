package br.com.fluxo.app

import android.app.Application

class FluxoApplication : Application() {
    val repository: LedgerRepository by lazy { LedgerRepository(this) }

    val entitlements: EntitlementsSource by lazy {
        createEntitlements(this).let { if (BuildConfig.DEBUG) DebugEntitlements(it) else it }
    }

    override fun onCreate() {
        super.onCreate()
        CaptureConnection.ensureComponentEnabled(this)
        CaptureKeeper.schedule(this)
    }
}
