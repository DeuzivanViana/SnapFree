import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.snapfree.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.snapfree.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        // Version of app/src/main/res/raw/ytdlp, which overrides the older yt-dlp bundled by youtubedl-android.
        buildConfigField("String", "BUNDLED_YTDLP_VERSION", "\"2026.10.26\"")

        // yt-dlp, Python and FFmpeg ship as native binaries. Restricting ABIs keeps the APK smaller.
        // Real phones use ARM; x86_64 is kept so the app also runs on the Android emulator.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Produces one APK per ABI plus a universal APK. Install the arm64-v8a one on modern phones.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // Release signing data is read from `keystore.properties` in the project root (never commit it)
    // or from environment variables, so no key or password lives in the source code.
    val keystoreProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun signingValue(key: String, env: String): String? =
        keystoreProps.getProperty(key) ?: System.getenv(env)
    val releaseStoreFile = signingValue("storeFile", "SNAPFREE_STORE_FILE")

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "SNAPFREE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SNAPFREE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SNAPFREE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Minification is disabled because youtubedl-android relies on Jackson reflection.
            isMinifyEnabled = false
            // Without a release key, release builds fall back to the local debug key so they still install.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // youtubedl-android executes binaries from the native library directory,
        // so native libraries must be extracted on install.
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)

    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    // Shared preferences helper used to keep the library's yt-dlp version record in sync.
    implementation(libs.youtubedl.common)

    testImplementation(libs.junit)
}
