/**
 * The version being built, from the release tag, or `null` for an ordinary local build.
 *
 * `android-release.yml` strips the `android-v` prefix and exports the rest, so a tag of
 * `android-v0.2.0` builds an APK that reports `0.2.0`. Without this the version was pinned at
 * `1.0` in source and every release identified itself identically — which defeats the point of
 * tagging at all, since the workflow exists so that "which build is on my phone" has an answer
 * and App info is where you go to read it.
 */
val releaseVersionName: String? = providers.environmentVariable("ANDROID_VERSION_NAME").orNull
    ?.trim()?.takeIf { it.isNotEmpty() }

/**
 * `0.2.0` becomes `200`, `1.12.3` becomes `11203`.
 *
 * Android compares versionCode as an integer and refuses a downgrade, so this has to rise with the
 * version rather than merely differ. Padding each component to two digits keeps that true across a
 * minor or patch number reaching double figures, which a naive `major.minor.patch` concatenation
 * would break at `0.10.0`.
 */
fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    return parts.getOrElse(0) { 0 } * 10000 + parts.getOrElse(1) { 0 } * 100 + parts.getOrElse(2) { 0 }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.flambeauriver.dailydriver"
    // compileSdk is a build-time setting (which API surface javac/kotlinc can see) and is
    // independent of minSdk/targetSdk (which control actual runtime behavior on-device) — it's
    // kept at the latest AndroidX-required level, currently 36, rather than pinned to the target
    // device. minSdk/targetSdk below ARE pinned to the target device; see android/CLAUDE.md.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flambeauriver.dailydriver"
        minSdk = 33
        targetSdk = 33
        // Local builds are "dev" rather than a number, so an APK sideloaded off a workstation is
        // never mistaken for a release when you are working out what is on the phone.
        versionCode = releaseVersionName?.let(::versionCodeOf) ?: 1
        versionName = releaseVersionName ?: "dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The repo-root fixtures/ corpus is put on the unit-test classpath so tests load it as a
    // resource rather than by walking relative paths out of the module directory. It lives
    // outside android/ on purpose: design doc §7.3 has the C# suite executing the same files, so
    // neither language owns them (T019, and T070's sync corpus later).
    sourceSets {
        getByName("test") {
            resources.srcDir("$rootDir/../fixtures")
        }
        // Puts the exported schemas (T151) where MigrationTestHelper looks for them — it reads
        // them from the *test app's* assets at runtime, not from the build directory.
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    // Room schema export (T151). The generated JSON is committed, so a schema change shows up as
    // a reviewable diff and `MigrationTestHelper` has something to migrate against. Requires
    // `ksp.useKSP2=true` in gradle.properties — see the note there before touching either.
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }

    // Release signing, supplied by CI through the environment (T159).
    //
    // **A stable signing key is the point, not ceremony.** Android refuses to install an APK over
    // one signed by a different key, and the debug keystore is auto-generated per machine — a
    // fresh one on every CI runner. Without this, every build would be signed differently and
    // updating the launcher on the phone would mean uninstalling it first, taking its database
    // (and every unsynced note) with it. Sync does not exist yet, so that is real data loss.
    //
    // Configured only when the environment provides a keystore, so a local `assembleRelease` and
    // any build without the secrets still work — they just produce an unsigned APK, exactly as
    // before. Absent secrets must not turn into a build failure for someone who only wants to
    // compile the project.
    val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")

    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    // ABI filtering, required by MediaPipe rather than chosen for tidiness (T029).
    //
    // `tasks-genai` ships `libllm_inference_engine_jni.so` for four ABIs, and native libraries are
    // stored *uncompressed* in the APK (`useLegacyPackaging` has defaulted to false since AGP 4.2),
    // so each one costs its full size: arm64-v8a 26.6 MB, armeabi-v7a 19.2 MB, x86 32.7 MB,
    // x86_64 30.2 MB. Shipping all four takes a 22 MB release APK past 130 MB to carry three
    // copies the target device (SM-G781V, arm64-v8a) can never execute.
    //
    // **Debug keeps x86_64, and that is not symmetry for its own sake.** CI runs the instrumented
    // suite on an API 33 x86_64 emulator (`.github/workflows/android.yml`), and an APK containing
    // native libraries with none matching the device ABI is rejected at install with
    // `INSTALL_FAILED_NO_MATCHING_ABIS`. Filtering debug down to arm64 alone would break
    // `connectedDebugAndroidTest` — which android/CLAUDE.md calls the only thing that has ever
    // caught a bug in the Room layer — with an error that reads like a broken emulator rather than
    // like a build setting. The emulator still cannot run *inference*: it is
    // `-gpu swiftshader_indirect`, and Google's guide says the API "does not reliably support
    // device emulators". It only has to install and let the Room tests run.
    buildTypes {
        debug {
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

            // **A separate package, so the daily driver is never the thing under test** (T030).
            //
            // The phone runs a signed release build and *is* the author's home screen, holding a
            // Room database of notes and the T014 command log, neither of which syncs anywhere.
            // Installing a debug build over it means either a version-code fight or, if the keys
            // ever diverge, an uninstall — and an uninstall there is real data loss.
            //
            // With a suffix the two coexist: `…dailydriver` stays the launcher you use, and
            // `…dailydriver.debug` is a throwaway that instrumented tests and model spikes can
            // install, fill and delete freely. Remove it with:
            //   adb uninstall com.flambeauriver.dailydriver.debug
            //
            // Safe for the Room schema directory, which is named from the *namespace* (the
            // database class's FQN) rather than from applicationId — see the rename warning in
            // android/CLAUDE.md, which is about `namespace` and does not apply here.
            applicationIdSuffix = ".debug"
        }
        release {
            // ChromeOsAbiSupport wants x86_64 kept so the APK runs on a Chromebook. It is
            // correct about the consequence and wrong about the goal: this is a HOME launcher
            // pinned to one phone (android/CLAUDE.md, T001), and 30 MB of native library for a
            // platform that is not a target is a worse answer than the warning.
            //noinspection ChromeOsAbiSupport
            ndk { abiFilters += listOf("arm64-v8a") }
            // Null when unconfigured, which leaves the APK unsigned rather than failing.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

// Lifts kotlinx-serialization-core for the *test APK*. Same error as T151's build-time one
// (`AbstractMethodError: ... typeParametersSerializers()`) but an entirely separate classpath:
// MigrationTestHelper reads the exported schema at runtime through room-migration, whose
// serializers are compiled against 1.8.1, while a transitive BOM here constrains core to 1.7.3
// where that method is still abstract rather than defaulted.
//
// `force`, not a plain dependency: the 1.7.3 pin comes from a BOM *constraint*, which an ordinary
// declaration doesn't outrank, and the bare `-core` module doesn't resolve standalone. Scoped to
// the test configurations because that's the only place room-migration runs on-device.
//
// Caught by SchemaExportTest the first time it was actually executed on a device — the build,
// unit tests and lint were all green with this broken.
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:${libs.versions.kotlinxSerialization.get()}")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:${libs.versions.kotlinxSerialization.get()}")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // For LocalLifecycleOwner — the compose-ui one is deprecated, and T146's status-line tick
    // needs it to stop ticking while the launcher isn't resumed.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // T2's inference runtime (T029). Nothing outside `slm/` may import this — the whole point of
    // `InferenceEngine` is that the command pipeline never names MediaPipe.
    implementation(libs.mediapipe.tasks.genai)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}