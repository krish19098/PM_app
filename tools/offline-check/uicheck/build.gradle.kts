plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(21) }
sourceSets {
    main {
        kotlin.srcDir("../../../app/src/main/java")
        kotlin.srcDir("stubs")
    }
}
configurations.all {
    exclude(group = "androidx.collection")
    exclude(group = "androidx.lifecycle")
    exclude(group = "androidx.annotation")
    exclude(group = "androidx.arch.core")
    exclude(group = "org.jetbrains.androidx.lifecycle")
    exclude(group = "org.jetbrains.androidx.core")
    exclude(group = "org.jetbrains.androidx.savedstate")
}
dependencies {
    implementation(project(":core"))
    val cmp = "1.7.3"
    implementation("org.jetbrains.compose.runtime:runtime-desktop:$cmp")
    implementation("org.jetbrains.compose.runtime:runtime-saveable-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-desktop:$cmp")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$cmp")
    implementation("org.jetbrains.compose.animation:animation-desktop:$cmp")
    implementation("org.jetbrains.compose.material3:material3-desktop:1.7.3")
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation(libs.kotlinx.serialization.json)
    // Platform classes (android.*) for signatures only; point -PandroidJar at your SDK if needed.
    compileOnly(files(providers.gradleProperty("androidJar").getOrElse("/opt/android-sdk/platforms/android-35/android.jar")))
}
