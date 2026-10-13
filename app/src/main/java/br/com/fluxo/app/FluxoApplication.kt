package br.com.fluxo.app

import android.app.Application

class FluxoApplication : Application() {
    val repository: LedgerRepository by lazy { LedgerRepository(this) }

    override fun onCreate() {
        super.onCreate()
        CaptureConnection.ensureComponentEnabled(this)
    }
}
