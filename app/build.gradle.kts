plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pantrypulse.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pantrypulse.app"
        minSdk = 26
        targetSdk = 35
        // CI sets GITHUB_RUN_NUMBER so every published build has a higher versionCode (needed to update an installed app).
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2).coerceAtLeast(2)
        versionName = "1.1.0"
        // Community backend (backend/ folder, deployed on Vercel).
        buildConfigField("String", "COMMUNITY_API_URL", "\"https://pantry-pulse-backend.vercel.app\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        // Real phones are ARM; dropping emulator-only x86 builds of the ML Kit libraries
        // keeps the APK small.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Release signing comes from environment variables or ~/.gradle/gradle.properties so no
    // secret is ever committed. Without them the APK is signed with the standard debug key,
    // which installs fine for side-loading but must be replaced before a Play Store upload.
    val keystorePath = System.getenv("PANTRYPULSE_KEYSTORE") ?: project.findProperty("pantrypulse.keystore") as String?
    val hasReleaseKey = keystorePath != null && file(keystorePath).exists()
    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = file(keystorePath!!)
            storePassword = System.getenv("PANTRYPULSE_KEYSTORE_PASSWORD") ?: project.findProperty("pantrypulse.keystorePassword") as String?
            keyAlias = System.getenv("PANTRYPULSE_KEY_ALIAS") ?: project.findProperty("pantrypulse.keyAlias") as String?
            keyPassword = System.getenv("PANTRYPULSE_KEY_PASSWORD") ?: project.findProperty("pantrypulse.keyPassword") as String?
        }
    }

    buildTypes {
        release {
            // Shrink unused library code (mostly icons) without renaming anything.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text)

    implementation(libs.coil.compose)
    implementation(libs.osmdroid)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.work.testing)
}
