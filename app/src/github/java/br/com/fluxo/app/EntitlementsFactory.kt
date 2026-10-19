package br.com.fluxo.app

import android.app.Application

internal fun createEntitlements(app: Application): EntitlementsSource = AlwaysPro
