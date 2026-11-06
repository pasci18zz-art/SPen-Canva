plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.spenremote.presentation"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spenremote.presentation"
        minSdk = 29          // Android 10; Galaxy Tab S10+ läuft mit Android 14+
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Mit dem Debug-Schlüssel signiert, damit die APK ohne eigenen Keystore installierbar ist.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Bewusst keine Bibliotheken außer dem Android-Framework (kein AndroidX):
// weniger Fehlerquellen, kleine APK.
