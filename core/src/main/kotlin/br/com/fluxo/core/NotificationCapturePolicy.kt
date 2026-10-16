package br.com.fluxo.core

/** Pure gate shared by listener callbacks and persistence: any app except Fluxo itself, while capture is on. */
object NotificationCapturePolicy {
    fun allows(packageName: String, ownPackageName: String, captureEnabled: Boolean): Boolean =
        captureEnabled && packageName.isNotBlank() && packageName != ownPackageName
}
