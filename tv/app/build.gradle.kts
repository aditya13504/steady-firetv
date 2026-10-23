plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.steady.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.steady.tv"
        // Fire OS 6 (API 25) and newer: every Fire TV Stick still getting updates.
        minSdk = 25
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
        // Steady Cloud (the hosted companion) when configured; otherwise the emulator's host machine (10.0.2.2).
        val cloud = (project.findProperty("steadyCloud") as String?) ?: ""
        buildConfigField("String", "CLOUD_SERVER", "\"$cloud\"")
        buildConfigField("String", "DEFAULT_SERVER", "\"${project.findProperty("steadyServer") ?: cloud.ifBlank { "http://10.0.2.2:8787" }}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.tv:tv-material:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
