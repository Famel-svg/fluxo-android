package br.com.fluxo.core

/**
 * Pure gate shared by listener callbacks and persistence: any app except Fluxo itself, while capture is on.
 * The store build passes [restrictToKnownFinancial] to accept only [KnownFinancialApps].
 */
object NotificationCapturePolicy {
    fun allows(
        packageName: String, ownPackageName: String, captureEnabled: Boolean, restrictToKnownFinancial: Boolean = false,
    ): Boolean = captureEnabled && packageName.isNotBlank() && packageName != ownPackageName &&
        (!restrictToKnownFinancial || KnownFinancialApps.contains(packageName))
}
