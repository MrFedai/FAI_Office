plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Lint (ci/lint.sh): ktlint for formatting, detekt for code smells, in every product module.
val ktlintVersion: String = libs.versions.ktlint.cli.get()
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "dev.detekt")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintVersion)
        // Generated sources (Compose resources Res, collectors) follow the generator's style, not ours.
        filter { exclude { it.file.path.contains("/build/generated/") } }
    }
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("ci/detekt.yml"))
    }
}

// License gate (ci/licenses.sh, MASTER_SPEC section 11).
apply(from = "ci/license-check.gradle.kts")
