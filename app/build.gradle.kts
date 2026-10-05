import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing.
 *
 * Credentials never live in the repository. They come from the first of:
 *
 * 1. `keystore.properties` next to `settings.gradle.kts` (git-ignored);
 * 2. `~/Documents/Golda-secrets/keystore.properties`, a folder outside the
 *    checkout;
 * 3. environment variables (CI, via repository secrets): `GOLDA_STORE_FILE`,
 *    `GOLDA_STORE_PASSWORD`, `GOLDA_KEY_ALIAS`, `GOLDA_KEY_PASSWORD`.
 *
 * A relative `storeFile` is resolved against the folder its properties file is
 * in. With a key, debug and release are both signed with it, so either one
 * installs over the other and the local database survives. Without one, release
 * is signed with the debug key: installable for trying things out, but the
 * release workflow refuses to publish it.
 *
 * See docs/RELEASING.md.
 */
val secretsDir = File(System.getProperty("user.home"), "Documents/Golda-secrets")
val keystoreDir = listOf(rootProject.projectDir, secretsDir).firstOrNull { File(it, "keystore.properties").exists() }
val keystoreProperties = Properties().apply {
    keystoreDir?.let { dir -> File(dir, "keystore.properties").inputStream().use { load(it) } }
}

fun secret(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = secret("storeFile", "GOLDA_STORE_FILE")?.let { path ->
    File(path).takeIf { it.isAbsolute } ?: File(keystoreDir ?: rootProject.projectDir, path)
}
val hasReleaseSigning = releaseStoreFile?.exists() == true

android {
    namespace = "sh.aminov.golda"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "sh.aminov.golda"
        minSdk = 26
        targetSdk = 37
        versionCode = 19
        versionName = "0.16.0"

        // Scenario tests run in an application with in-memory data and refuse to start anywhere but
        // an emulator; see CONTRIBUTING.md before running them.
        testInstrumentationRunner = "sh.aminov.golda.GoldaTestRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = secret("storePassword", "GOLDA_STORE_PASSWORD")
                keyAlias = secret("keyAlias", "GOLDA_KEY_ALIAS")
                keyPassword = secret("keyPassword", "GOLDA_KEY_PASSWORD")
            }
        }
    }

    // The language is picked inside the app (on Android 8–12 without the system's per-app setting),
    // so an app bundle must keep every language in the base APK.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        debug {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    constraints {
        // The test APK must use the app's versions, and Compose's test libraries need 1.2 of these
        // (the app's own dependencies settle for 1.1).
        implementation(libs.androidx.concurrent.futures)
        implementation(libs.androidx.concurrent.futures.ktx)
    }
}
