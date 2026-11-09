plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.dot.standby"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.dot.standby.probe"
        minSdk = 34 // specialUse foreground service; measurement runs on the operator's Fold8 only
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // FoldingFeature (FLAT / HALF_OPENED) from Jetpack WindowManager
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
