plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Lint and format (spec section 10): ./gradlew detekt ktlintCheck   (fix: ./gradlew ktlintFormat)
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
        source.setFrom(files("src")) // Kotlin Multiplatform keeps code in commonMain, androidMain, iosMain, ...
    }

    // Skip generated code (SQLDelight, Compose resources). The filter sees absolute paths; compare with / separators.
    // Gradle does not treat the filter as a task input: after changing it, run ktlint once with --rerun-tasks.
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            exclude { it.file.invariantSeparatorsPath.contains("/build/") }
        }
    }
}
