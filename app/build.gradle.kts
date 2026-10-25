import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.roruku.smartformatconverter"

    compileSdk = 37

    defaultConfig {
        applicationId = "org.roruku.smartformatconverter"

        minSdk = 26
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val localProps = Properties().apply {
                val propFile = rootProject.file("local.properties")
                if (propFile.exists()) {
                    propFile.inputStream().use { stream ->
                        load(stream)
                    }
                }
            }
            val keystorePath = System.getenv("KEYSTORE_FILE")
                ?: (project.findProperty("KEYSTORE_FILE") as? String)
                ?: localProps.getProperty("KEYSTORE_FILE")
            if (keystorePath != null && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: (project.findProperty("KEYSTORE_PASSWORD") as? String)
                    ?: localProps.getProperty("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: (project.findProperty("KEY_ALIAS") as? String)
                    ?: localProps.getProperty("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: (project.findProperty("KEY_PASSWORD") as? String)
                    ?: localProps.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }

    lint {
        abortOnError = true
        // Treat project lint warnings as regressions instead of silently
        // green-lighting a debug build with outstanding issues.
        warningsAsErrors = true
        checkReleaseBuilds = true
    }
}

dependencies {

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Android Core
    implementation(libs.androidx.core.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Media and Storage
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.heifwriter)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Media3: ExoPlayer for preview and Transformer for hardware-accelerated conversion
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.muxer)
    implementation(libs.androidx.media3.effect)

    // Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.test.json)

    // Android Instrumentation Tests
    // The Compose BOM is required separately for the androidTest classpath.
    // This is not a duplicate: implementation and androidTestImplementation
    // are independent dependency configurations.
    androidTestImplementation(platform(libs.androidx.compose.bom))

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )
    androidTestImplementation(
        libs.androidx.espresso.core
    )
    androidTestImplementation(
        libs.androidx.junit
    )

    // Debug Tools
    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}

// Persist current Room schema for future migration tests.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
