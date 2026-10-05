package br.com.fluxo.core

/** Pure gate shared by listener callbacks and persistence. */
object NotificationCapturePolicy {
    fun allows(
        packageName: String,
        ownPackageName: String,
        captureEnabled: Boolean,
        allowOtherApps: Boolean,
        enabledLegacyPackages: Set<String>,
    ): Boolean = captureEnabled &&
        packageName != ownPackageName &&
        (packageName in enabledLegacyPackages || allowOtherApps)
}
