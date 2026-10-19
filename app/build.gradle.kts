plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "br.com.fluxo.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "br.com.fluxo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PRIVACY_URL", "\"https://famel-svg.github.io/fluxo-privacidade/\"")
    }
    flavorDimensions += "distribuicao"
    productFlavors {
        create("github") { buildConfigField("boolean", "STORE_BUILD", "false") }
        create("play") {
            applicationId = "io.github.famelsvg.fluxo"
            buildConfigField("boolean", "STORE_BUILD", "true")
        }
    }
    signingConfigs {
        // Upload key for Play: only wired when PLAY_UPLOAD_KEYSTORE points to an existing file.
        create("upload") {
            val path = System.getenv("PLAY_UPLOAD_KEYSTORE")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("PLAY_UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("PLAY_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("PLAY_UPLOAD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val uploadPath = System.getenv("PLAY_UPLOAD_KEYSTORE")
            signingConfig = when {
                System.getenv("FLUXO_RELEASE_SIGN_WITH_DEBUG") == "1" -> signingConfigs.getByName("debug")
                uploadPath != null && file(uploadPath).exists() -> signingConfigs.getByName("upload")
                else -> null
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
