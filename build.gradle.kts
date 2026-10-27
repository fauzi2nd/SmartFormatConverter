
// Root project Gradle configuration.
// Plugins are declared here but applied only in the app module.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
