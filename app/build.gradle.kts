import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/*
 * Release signing.
 * Locally: create keystore.properties in the project root (it is git-ignored):
 *     storeFile=/full/path/to/release.jks
 *     storePassword=...
 *     keyAlias=snaps
 *     keyPassword=...
 * On GitHub Actions the same values come from repository secrets (see README).
 * With neither, release builds are signed with the debug key so they still install.
 */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "SNAPS_STORE_FILE")

android {
    namespace = "com.snapsstudio.booth"
    compileSdk = 35

    defaultConfig {
        // Change applicationId before selling a rebranded copy; it must be unique per app.
        applicationId = "com.snapsstudio.booth"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "SNAPS_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SNAPS_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SNAPS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Keep CI producing an APK; warnings still show in the build report.
        abortOnError = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Serves the booth page from app assets over https://appassets.androidplatform.net,
    // a secure origin, so the camera, IndexedDB and localStorage all work.
    implementation("androidx.webkit:webkit:1.12.1")
}
