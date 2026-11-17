// PoC WP8: Jetpack Compose + PDFium (JNI), same scenario as ../flutter-pdf. Throwaway (MASTER_SPEC section 15).
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "compose-pdf"
include(":app")
