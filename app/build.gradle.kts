plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.siya.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.siya.ai"
        minSdk = 26
        targetSdk = 34
        // Har GitHub build ka number badhta hai, isliye naya APK purani app ke upar update ho jata hai.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.2.$versionCode"
    }

    // Hamesha ek hi signing key, taaki update bina uninstall ke ho (key alag hui to Android update rok deta hai).
    signingConfigs {
        create("siya") {
            storeFile = file("siya.keystore")
            storePassword = "siya-ai-2026"
            keyAlias = "siya"
            keyPassword = "siya-ai-2026"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("siya") }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
