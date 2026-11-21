// Shared services without UI (Kotlin Multiplatform): settings and localisation in Phase 2; files, format
// detection, recent files and safe save from Phase 3.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "org.faioffice.core"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        withHostTest {}
    }
    jvm("desktop")
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // Tests run in Node; the browser test task would need a local Chrome for Karma.
        browser { testTask { enabled = false } }
        nodejs()
    }

    sourceSets {
        commonMain.dependencies { implementation(libs.kotlinx.coroutines.core) }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
