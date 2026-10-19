package br.com.fluxo.app

import android.app.Application

internal fun createEntitlements(app: Application): EntitlementsSource =
    if (BuildConfig.PRO_FOR_ALL) AlwaysPro else BillingRepository(app)
