plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 따로 같이 네컷 for Google Play (T-0188): a WebView shell around site/ (assembled into assets/www by ../assemble.mjs).
android {
    namespace = "kr.dot.ttarogachi"
    compileSdk = 36
    defaultConfig {
        applicationId = "kr.dot.ttarogachi"
        minSdk = 29 // MediaStore saves without a storage permission
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    // Play upload key: kept outside the repo, password in the user env var, never committed (same scheme as the catch app)
    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.ttarogachi-upload.jks")
            storePassword = System.getenv("TTAROGACHI_KS_PASS")
            keyAlias = "upload"
            keyPassword = System.getenv("TTAROGACHI_KS_PASS")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
}
