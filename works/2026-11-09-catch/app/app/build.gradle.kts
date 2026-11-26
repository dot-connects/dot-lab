plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Play Integrity: the Google Cloud project number linked in Play Console (wizard menu 7); 0 = not linked yet
val cloudProject = 447354767842L

android {
    namespace = "kr.dot.catchpet"
    compileSdk = 36
    defaultConfig {
        applicationId = "kr.dot.catchpet"
        minSdk = 34 // specialUse foreground service
        targetSdk = 36
        versionCode = 601
        versionName = "0.6.1"
        ndk { abiFilters += "arm64-v8a" }
        // Worker that draws the AI pictures (works/2026-11-09-catch/worker)
        buildConfigField("String", "DEBUG_PURCHASE", "\"\"")
        manifestPlaceholders["appLabel"] = "리얼뽑기"
        buildConfigField("String", "WORKER", "\"https://dot-lab-catch.dotconnects.workers.dev\"")
        buildConfigField("long", "CLOUD_PROJECT", "${cloudProject}L")
    }
    buildFeatures { compose = true; buildConfig = true }
    // Play upload key (plan-release Task 6): kept outside the repo, password in the user env var, never committed
    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.catchpet-upload.jks")
            storePassword = System.getenv("CATCHPET_KS_PASS")
            keyAlias = "upload"
            keyPassword = System.getenv("CATCHPET_KS_PASS")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // minified like the debug build the operator tests, so the Play build runs the same code
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // R8 on the debug build too: unshrunk dex was 32 MB, and the APK goes to the phone through a 30 MB hand-off
        debug {
            // the dev app (operator 11-16): installs beside the Play app, redraws AI pictures free on the operator's token
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "리얼뽑기 개발"
            // dev builds buy AI pet sheets free (operator 11-14): the Worker's debug token, read from this PC, never committed
            // -PstoreShots: no token, so StoreShotTest draws the screens the Play build shows
            val token = if (project.hasProperty("storeShots")) "" else
                File(System.getProperty("user.home"), ".catchpet-debug-purchase").takeIf { it.exists() }?.readText()?.trim().orEmpty()
            buildConfigField("String", "DEBUG_PURCHASE", "\"$token\"")
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
    implementation("androidx.compose.ui:ui-text-google-fonts")   // Jua + Gowun Dodum for the menus (mockup A)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    val camerax = "1.4.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mediapipe:tasks-vision:0.10.26")
    implementation("com.google.android.play:integrity:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
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

// an AAB without the project number draws nothing for any tester (every call fails integrity): refuse to build it
tasks.matching { it.name == "bundleRelease" }.configureEach {
    doFirst { check(cloudProject != 0L) { "cloudProject is 0: put the Play Console project number in app/build.gradle.kts" } }
}
