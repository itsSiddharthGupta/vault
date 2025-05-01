// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.library) apply false

    // Code style and analysis tools
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

subprojects {
    pluginManager.withPlugin("kotlin-android") {
        apply("$rootDir/static-analysis/code-analysis.gradle")
    }

}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true) // Generate HTML report
        xml.required.set(true) // Generate XML report for CI integration
        txt.required.set(true) // Generate text report
        sarif.required.set(true) // Generate SARIF report for GitHub
    }
}

// Spotless configuration
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "1.8"
}
