// FAI_Office: one application, modules per PHASE1_PROPOSAL.md section 3. poc/ is not part of this build.
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "fai-office"
include(":core", ":app", ":androidApp")
