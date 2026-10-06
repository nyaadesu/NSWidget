plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.nswidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.nswidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

dependencies {
    // Widget (Jetpack Glance = Compose-style API for app widgets)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1") // dynamic (wallpaper) colours in the widget

    implementation("androidx.core:core-ktx:1.13.1")

    // Background refresh
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Settings screen
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is a stub on the JVM, so tests need the real thing.
    testImplementation("org.json:json:20240303")
}
