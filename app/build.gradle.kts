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
        // Compose resources (fonts) must reach the Android assets of the KMP library.
        androidResources.enable = true
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
            implementation(libs.compose.components.resources)
        }
        named("desktopMain") {
            dependencies { implementation(compose.desktop.currentOs) }
        }
        // Contrast test and token preview screenshots (ImageComposeScene) run on the desktop JVM only.
        named("desktopTest") {
            dependencies { implementation(libs.kotlin.test) }
        }
    }
}

// Screenshots of the desktop tests: app/build/bakis/<theme>_<size>_<screen>.png.
val bakisDir = layout.buildDirectory.dir("bakis")
tasks.withType<Test>().configureEach {
    systemProperty("bakis.dir", bakisDir.get().asFile.absolutePath)
}

// Bundled fonts (composeResources/font, DESIGN.md section 4); Res stays internal to :app.
compose.resources {
    packageOfResClass = "org.faioffice.app.resources"
    publicResClass = false
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
