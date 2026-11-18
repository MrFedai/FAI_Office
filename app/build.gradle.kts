// Compose Multiplatform shell: screens, navigation, design system (commonMain) and the desktop and Web entry
// points. The Android entry point lives in :androidApp (AGP 9 keeps com.android.application out of KMP modules).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "org.faioffice.app"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    jvm("desktop")
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "fai-office.js" } }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        named("desktopMain") {
            dependencies { implementation(compose.desktop.currentOs) }
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.faioffice.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            packageName = "fai-office"
            packageVersion = "0.2.0"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.instrument", "jdk.unsupported")
        }
        buildTypes.release.proguard { isEnabled.set(true) }
    }
}
