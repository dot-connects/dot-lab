import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

// Same feed and secrets as 눈금 (status-widget); the file stays outside git.
val nungeum = Properties().apply {
    val f = rootProject.file("../../../../status-widget/secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String) = "\"" + nungeum.getProperty(key, "") + "\""

android {
    namespace = "kr.dot.sb"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.dot.standby"
        minSdk = 34
        targetSdk = 35
        versionCode = 13
        versionName = "0.4.0"
    }
    // personal = the operator's phone (usage card + PC feed + secrets); public = release builds, no secret anywhere
    // (tools/check_secrets.py scans both APKs). Different ids so both can sit on one phone.
    flavorDimensions += "dist"
    productFlavors {
        create("personal") {
            dimension = "dist"
            buildConfigField("String", "STATUS_BOT_TOKEN", secret("statusBotToken"))
            buildConfigField("String", "STATUS_CHAT_ID", secret("statusChatId"))
            buildConfigField("String", "LAN_TOKEN", secret("lanToken"))
        }
        create("public") {
            dimension = "dist"
            applicationIdSuffix = ".pub"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
            all { t -> listOf("shot.themes", "shot.group").forEach { k -> project.findProperty(k)?.let { t.systemProperty(k, it) } }; t.systemProperty("shot.second", "10") }
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
