plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "health.cogniware.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "health.cogniware.wear"
        minSdk        = 30   // Wear OS 3.0 minimum
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"
    }

    buildTypes {
        release { isMinifyEnabled = true }
        debug   { isDebuggable = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Wear OS UI (minimal — service only, no UI needed)
    implementation("androidx.wear:wear:1.3.0")
}
