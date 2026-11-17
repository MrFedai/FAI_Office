// PoC WP6: LibreOfficeKit on Android in its own process. Throwaway (MASTER_SPEC section 15).
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "lok-android"
include(":app")
