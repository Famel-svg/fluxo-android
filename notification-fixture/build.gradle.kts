plugins { id("com.android.application") }

android {
    namespace = "br.com.fluxo.fixture"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "synthetic-test-only"
    }
    flavorDimensions += "emitter"
    productFlavors {
        create("nubank") { dimension = "emitter"; applicationId = "com.nu.production" }
        create("c6") { dimension = "emitter"; applicationId = "com.c6bank.app" }
        create("itau") { dimension = "emitter"; applicationId = "com.itau" }
        create("unsupported") { dimension = "emitter"; applicationId = "br.com.fluxo.fixture.unsupported" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
androidComponents { beforeVariants(selector().withBuildType("release")) { it.enable = false } }
