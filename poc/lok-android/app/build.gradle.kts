plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Engine libraries, assets and LibreOfficeKit's Java bridge are copied in by run.sh from the LibreOffice
// Android build (lo-android/); none of them is committed.
android {
    namespace = "org.faioffice.poc.lok"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.faioffice.poc.lok"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes {
        release {
            isMinifyEnabled = false // the JNI bridge looks up Java names; keep them
            signingConfig = signingConfigs.getByName("debug") // PoC only
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }
