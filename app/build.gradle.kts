plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.florsent"
    // Current AndroidX releases require compiling against API 37.1. This only controls which APIs
    // are visible at compile time; runtime behavior is opted into via targetSdk (36).
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.florsent"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.11.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // android.util.Log and friends return defaults in JVM unit tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    bundle {
        // In-app language switching needs every language on the device. With splits on, Play would
        // only install the strings for the system language and 繁體中文 / English could not load.
        language { enableSplit = false }
    }

    androidResources {
        // Ship only the languages Florsent is written in, so library strings (system dialogs,
        // time picker) never show a third language. Keep in sync with res/xml/locales_config.xml.
        localeFilters += listOf("en", "zh-rTW")
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true // Shizuku UserService interface
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    // Optional per-app desaturation (advanced). Adds no permissions besides Shizuku's own API_V23.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
