
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()

        // RxFFmpeg legacy dependency.
        // Remove JitPack after completing migration.
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "Smart Format Converter"

include(":app")
