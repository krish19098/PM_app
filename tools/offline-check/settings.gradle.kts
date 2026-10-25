// Offline verification build for environments that cannot reach Google's Maven repository.
// It compiles and tests :core, and type-checks the Android app's Kotlin sources (:uicheck)
// against Compose Multiplatform desktop artifacts plus androidx signature stubs.
// It does NOT produce an APK; use the root Gradle build for that.
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "pmbible-offline-check"
include(":core")
project(":core").projectDir = file("../../core")
include(":uicheck")
