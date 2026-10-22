buildscript {
    dependencies {
        // Keep AGP's built-in Kotlin compiler aligned with the Compose compiler.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
