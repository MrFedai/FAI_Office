plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// PDFium headers and library: the same pinned prebuilt as the Flutter PoC (run.sh copies the .so into jniLibs).
val pdfiumDir: String = System.getenv("PDFIUM_DIR")
    ?: "${System.getProperty("user.home")}/dev/cache/fai-office/pdfium-8111/android-arm64"

android {
    namespace = "org.faioffice.poc.compose"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.faioffice.poc.compose"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DPDFIUM_DIR=$pdfiumDir" } }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug") // PoC only: debug key, like the Flutter template
        }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(bom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
}
