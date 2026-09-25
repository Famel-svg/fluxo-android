pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Fluxo"
include(":core", ":app")
// Synthetic notification emitter is opt-in and never a production dependency.
if (providers.gradleProperty("notificationE2e").orNull == "true") {
    include(":notification-fixture")
}
