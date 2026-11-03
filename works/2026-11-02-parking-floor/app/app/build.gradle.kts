plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.dot.parkingfloor"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.dot.parkingfloor"
        minSdk = 34 // the specialUse service type starts at API 34
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        // R8 stays off so crash.log is readable without a mapping file
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Korean text recognizer served by Play services (~260 KB in the APK, model downloaded on first use)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.2")
    testImplementation("junit:junit:4.13.2")
}
