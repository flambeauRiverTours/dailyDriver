plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.dailydriver"
    // compileSdk is a build-time setting (which API surface javac/kotlinc can see) and is
    // independent of minSdk/targetSdk (which control actual runtime behavior on-device) — it's
    // kept at the latest AndroidX-required level, currently 36, rather than pinned to the target
    // device. minSdk/targetSdk below ARE pinned to the target device; see android/CLAUDE.md.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.dailydriver"
        minSdk = 33
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

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

    buildTypes {
        release {
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