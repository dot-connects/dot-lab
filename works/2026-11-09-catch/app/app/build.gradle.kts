plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.dot.catchpet"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.dot.catchpet"
        minSdk = 34 // specialUse foreground service
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Android ships org.json; plain JVM unit tests need the real one
    testImplementation("org.json:json:20240303")
}
