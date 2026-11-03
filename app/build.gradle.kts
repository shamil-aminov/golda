import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
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
    // Screenshot tests draw the screens with the app's own resources (fonts, icons).
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reaches into the JDK's file descriptors, which JDK 17+ keeps closed.
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED") }
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
    testImplementation(libs.kotest.property)
    // Screenshot tests: screens drawn on the JVM by Robolectric and compared with stored images.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
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

// Mutation testing of the domain (SPEC §10): Pitest changes the compiled code one small way at a time
// (a + for a −, a < for a <=, a branch dropped) and runs the unit tests against each change. A change
// no test notices is a gap in the tests. ./gradlew pitest; the report is in build/reports/pitest.
val pitest: Configuration by configurations.creating
dependencies { pitest(libs.pitest.command.line) }

tasks.register<JavaExec>("pitest") {
    group = "verification"
    description = "Mutation-tests the domain logic against the unit tests."
    val unitTest = tasks.named<Test>("testDebugUnitTest")
    dependsOn(unitTest)
    mainClass.set("org.pitest.mutationtest.commandline.MutationCoverageReport")
    classpath = pitest
    val testClasspath = files(unitTest.map { it.classpath })
    val sources = file("src/main/java")
    val report = layout.buildDirectory.dir("reports/pitest")
    val classPathFile = layout.buildDirectory.file("tmp/pitest/classpath.txt")
    // The code and the tests are its inputs: a change to either runs it again.
    inputs.files(testClasspath)
    outputs.dir(report)
    argumentProviders.add(
        CommandLineArgumentProvider {
            // One entry per line in a file: the test classpath is too long for a Windows command line.
            val list = classPathFile.get().asFile.apply { parentFile.mkdirs() }
            list.writeText(testClasspath.files.joinToString("\n") { it.path })
            listOf(
                "--reportDir", report.get().asFile.path,
                "--targetClasses", "sh.aminov.golda.domain.*",
                // Wording and the prompt are text, not logic.
                "--excludedClasses", "sh.aminov.golda.domain.I18n*,sh.aminov.golda.domain.VoicePrompt*,sh.aminov.golda.domain.CategoriesKt*,sh.aminov.golda.domain.*\$\$serializer",
                // What Kotlin generates for data classes.
                "--excludedMethods", "equals,hashCode,toString,component*,copy*,copy\$default",
                "--targetTests", "sh.aminov.golda.*Test",
                "--sourceDirs", sources.path,
                "--classPathFile", list.path,
                // The app's own classes reach the unit tests as a jar, which Pitest would not mutate by itself.
                "--mutableCodePaths", testClasspath.files.filter { "runtime_app_classes_jar" in it.path }.joinToString(",") { it.path },
                "--outputFormats", "HTML,CSV",
                "--timestampedReports", "false",
                "--threads", "4",
                // The floor: tests that notice fewer breaks than this fail the build. Raise it, never lower it.
                "--mutationThreshold", "82",
            )
        },
    )
}

