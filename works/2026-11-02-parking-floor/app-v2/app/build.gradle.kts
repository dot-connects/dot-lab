import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

// Firebase client values, kept in the gitignored app-v2/firebase.properties. Missing file = no push; family sync still pulls.
val firebase = Properties().apply {
    val f = rootProject.file("firebase.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun fb(key: String) = "\"" + firebase.getProperty(key, "") + "\""

// Release upload key (created in Task 11). Missing -> debug key.
val upload = Properties().apply {
    val f = file("${System.getProperty("user.home")}/.wherepark/upload.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "kr.dot.wherepark"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.dot.wherepark"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SYNC_URL", "\"https://dot-lab-parking.dotconnects.workers.dev\"")
        buildConfigField("String", "FB_API_KEY", fb("apiKey"))
        buildConfigField("String", "FB_APP_ID", fb("appId"))
        buildConfigField("String", "FB_PROJECT_ID", fb("projectId"))
        buildConfigField("String", "FB_SENDER_ID", fb("senderId"))
    }
    signingConfigs {
        if (upload.getProperty("storeFile") != null) create("upload") {
            storeFile = file(upload.getProperty("storeFile"))
            storePassword = upload.getProperty("storePassword")
            keyAlias = upload.getProperty("keyAlias")
            keyPassword = upload.getProperty("keyPassword")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // r9 shot filters: -Pshot.themes=IPHONE,DAYPAPER -Pshot.group=board
            all { t ->
                listOf("shot.themes", "shot.group").forEach { k -> project.findProperty(k)?.let { t.systemProperty(k, it) } }; t.systemProperty("shot.second", "10")
                // test classes over parallel JVMs; 2 by default (all-session heavy-job rule), -Ptest.forks=1 next to a render
                t.maxParallelForks = (project.findProperty("test.forks") as String?)?.toInt() ?: 2; t.maxHeapSize = "1536m"
            }
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.firebase:firebase-messaging:25.1.3")
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.fragment:fragment:1.8.5") // lintVital: transitive fragment 1.1.0 trips InvalidFragmentVersionForActivityResult
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(bom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.29.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.29.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
