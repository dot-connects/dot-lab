plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "kr.dot.catchpet"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.dot.catchpet"
        minSdk = 34 // specialUse foreground service
        targetSdk = 35
        versionCode = 9
        versionName = "0.2.8"
        ndk { abiFilters += "arm64-v8a" }
        // Worker that draws the AI pictures (works/2026-11-09-catch/worker)
        buildConfigField("String", "WORKER", "\"https://dot-lab-catch.dotconnects.workers.dev\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        // R8 on the debug build too: unshrunk dex was 32 MB, and the APK goes to the phone through a 30 MB hand-off
        debug {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources {
        noCompress += "tflite"
        // AAPT's default pattern drops "_*" asset folders; keep critters/_placeholder
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
    packaging { jniLibs { pickFirsts += "**/libc++_shared.so" } }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("roborazzi.test.record", "true") }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    val camerax = "1.4.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    // AR critters behind the developer switch (manifest: AR optional, the app runs without ARCore)
    implementation("com.google.ar:core:1.48.0")

    testImplementation("junit:junit:4.13.2")
    // Android ships org.json; plain JVM unit tests need the real one
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
