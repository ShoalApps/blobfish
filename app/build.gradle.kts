plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "io.github.shoalapps.blobfish"
    compileSdk = 37

    defaultConfig {
        // Shoal\'s Sentry address, given to release builds by CI; blank means reports are off.
        buildConfigField("String", "SENTRY_DSN", "\"${System.getenv("SENTRY_DSN") ?: ""}\"")
        applicationId = "io.github.shoalapps.blobfish"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        resValue("string", "app_name", "Blobfish")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key lives only in GitHub Actions secrets, never in the repo.
    val releaseKey = System.getenv("SHOAL_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey
                storePassword = System.getenv("SHOAL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SHOAL_KEY_ALIAS")
                keyPassword = System.getenv("SHOAL_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Blobfish Dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Bluesky's sign-in page in a browser tab.
    implementation("androidx.browser:browser:1.8.0")
    // Error reports, only with consent (MIT). See Telemetry.kt.
    implementation("io.sentry:sentry-android:8.59.0")
    // Home-screen widgets (Material You colours via Glance).
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
