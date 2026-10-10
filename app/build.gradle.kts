import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

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
        versionCode = 3
        // Includes the build time so it's obvious which APK is installed (shown in the app).
        versionName = "1.2 (" + SimpleDateFormat("MMM d, HH:mm", Locale.ENGLISH).format(Date()) + ")"
    }

    // Release builds are signed with a private key that never enters the repo: the Gradle property
    // `nswidget.signing` (e.g. in ~/.gradle/gradle.properties) points to a properties file with
    // storeFile, storePassword, keyAlias and keyPassword. Without it, release builds are unsigned.
    val signing = (findProperty("nswidget.signing") as String?)?.let(::File)?.takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = File(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signing != null) signingConfig = signingConfigs.getByName("release")
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
