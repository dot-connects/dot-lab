import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase client values from google-services.json, written by scripts/wizard-parking-firebase.bat into the
// gitignored firebase.properties. Missing file = no push; family sync still works by pulling.
val firebase = Properties().apply {
    val f = rootProject.file("firebase.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun fb(key: String) = "\"" + firebase.getProperty(key, "") + "\""

android {
    namespace = "kr.dot.parkingfloor"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.dot.parkingfloor"
        minSdk = 34 // the specialUse service type starts at API 34
        targetSdk = 35
        versionCode = 6
        versionName = "0.4.0"
        buildConfigField("String", "SYNC_URL", "\"https://dot-lab-parking.dotconnects.workers.dev\"")
        buildConfigField("String", "FB_API_KEY", fb("apiKey"))
        buildConfigField("String", "FB_APP_ID", fb("appId"))
        buildConfigField("String", "FB_PROJECT_ID", fb("projectId"))
        buildConfigField("String", "FB_SENDER_ID", fb("senderId"))
    }
    buildFeatures { buildConfig = true }
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
    // family push; initialised by hand in PushSetup (no google-services plugin)
    implementation("com.google.firebase:firebase-messaging:25.1.3")
    testImplementation("junit:junit:4.13.2")
}
