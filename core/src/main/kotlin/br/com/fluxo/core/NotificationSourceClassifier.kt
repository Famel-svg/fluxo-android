package br.com.fluxo.core

enum class NotificationSource { BANK, OTHER }

/** Pure grouping of a notification's app; it never decides whether the notification is captured. */
object NotificationSourceClassifier {
    fun classify(packageName: String, bankPackages: Collection<String>): NotificationSource =
        if (packageName in bankPackages) NotificationSource.BANK else NotificationSource.OTHER
}
