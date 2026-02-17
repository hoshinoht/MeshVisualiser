import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Load local.properties for API keys
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.meshvisualiser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.meshvisualiser"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ARCore Cloud Anchor API key — set in local.properties
        buildConfigField(
            "String",
            "ARCORE_CLOUD_ANCHOR_API_KEY",
            "\"${localProperties.getProperty("ARCORE_CLOUD_ANCHOR_API_KEY", "")}\""
        )
    }

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

    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }
}

dependencies {
    constraints {
        implementation("androidx.core:core") {
            version {
                strictly("1.15.0")
            }
            because("AGP 8.8.0 and compileSdk 35 are not compatible with androidx.core 1.17.x")
        }
        implementation("androidx.core:core-ktx") {
            version {
                strictly("1.15.0")
            }
            because("AGP 8.8.0 and compileSdk 35 are not compatible with androidx.core 1.17.x")
        }
    }

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha14")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Google Play Services - Nearby Connections
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ARCore and SceneView
    implementation("com.google.ar:core:1.52.0")
    implementation("io.github.sceneview:arsceneview:2.3.3")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("io.mockk:mockk-android:1.13.13")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
