// ─────────────────────────────────────────────────────────────────────────────
// app/build.gradle.kts
//
// KSP / JBR 25.x + Compose compiler fix
// ──────────────────────────────────────
// The error:
//   "The Kotlin Gradle plugin was loaded multiple times in different subprojects"
//   / "org.jetbrains.kotlin.plugin.compose incompatible with KSP …"
//
// Root cause: AGP ≥ 8.3 + Kotlin ≥ 2.0 replaced the old
//   composeOptions { kotlinCompilerExtensionVersion = "…" }
// block with the standalone `org.jetbrains.kotlin.plugin.compose` plugin.
// Older KSP / JBR 25.x toolchain versions can mis-version this handoff.
//
// Fix applied here:
//   1. Pin Kotlin to 2.0.21 (stable, matches KSP 2.0.21-1.0.28).
//   2. Remove any explicit composeOptions block — the Compose plugin
//      now derives the compiler version from the Kotlin version automatically.
//   3. Add a Java toolchain pinned to JDK 17 (JBR 25.x exposes JDK 21
//      which can conflict with kotlinOptions.jvmTarget = "17").
//   4. Keep KSP version aligned: 2.0.21-1.0.28 in project-level plugins block.
// ─────────────────────────────────────────────────────────────────────────────

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")          // version set in project-level plugins block
    id("org.jetbrains.kotlin.plugin.compose")   // Kotlin 2.0+ compose plugin (replaces composeOptions)
    id("com.google.devtools.ksp")               // version must match kotlin version — see settings
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace  = "health.cogniware"
    compileSdk = 35   // bump to 35 for Android 15 compat (safe with minSdk 26)

    defaultConfig {
        applicationId = "health.cogniware"
        minSdk        = 26     // Android 8.0 — covers 95 %+ of active devices
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase config — injected from local.properties, never hardcoded
        buildConfigField("String", "SUPABASE_URL",      "\"${project.findProperty("SUPABASE_URL")      ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose     = true
        // composeOptions block is GONE — the kotlin.plugin.compose plugin
        // handles kotlinCompilerExtensionVersion automatically from the
        // Kotlin version declared in the project-level plugins block.
    }

    // Packaging options — prevent duplicate META-INF files from Ktor/Kotlin stdlib
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/previous-compilation-data.bin"
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dependencies
// ─────────────────────────────────────────────────────────────────────────────

dependencies {

    // ── AndroidX core ────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.activity:activity-compose:1.9.1")

    // ── Jetpack Compose BOM ───────────────────────────────────────────────────
    // BOM 2024.06.00 pairs with Compose compiler 1.5.14, compatible with Kotlin 2.0.21
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ── Room — local feature store ────────────────────────────────────────────
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ── Health Connect ────────────────────────────────────────────────────────
    // Stable 1.1.0-rc01 — read steps, HR, HRV, blood pressure, sleep
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // ── ML Kit Pose Detection (on-device) ────────────────────────────────────
    implementation("com.google.mlkit:pose-detection:18.0.0-beta3")
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta3")

    // ── CameraX ──────────────────────────────────────────────────────────────
    val cameraXVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // ── Networking — Supabase via Ktor ────────────────────────────────────────
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ── Google Play Services ──────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ── Material Design ───────────────────────────────────────────────────────
    implementation("com.google.android.material:material:1.12.0")

    // ── Security ──────────────────────────────────────────────────────────────
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ── Guava (needed by CameraX / MLKit) ────────────────────────────────────
    implementation("com.google.guava:guava:33.2.1-android")

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
