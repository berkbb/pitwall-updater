import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// AGP 9 compiles Kotlin natively (built-in Kotlin); only the Compose compiler plugin is applied
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = "1.0.11"

// Release signing: keystore.properties (git-ignored) or PITWALL_* environment variables.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "PITWALL_KEYSTORE_FILE")

android {
    namespace = "com.berkbb.pitwall"
    // Android 17 platform (API 37.2); targetSdk stays at 35 until on-device behavior review
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.berkbb.pitwall"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "PITWALL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "PITWALL_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "PITWALL_KEY_PASSWORD")
            }
        }
    }

    testBuildType = "release"

    buildTypes {
        release {
            // R8 code shrinking + resource shrinking (AndroidX/Compose ship their own consumer rules)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                // Fallback keeps local builds working, but debug-signed APKs cannot update each other across machines
                logger.warn("Pitwall: no release keystore configured (keystore.properties / PITWALL_KEYSTORE_FILE); signing release with the debug key.")
                signingConfigs.getByName("debug")
            }
        }
    }

    // Keep all language resources in the base APK of the AAB (TVs sideload, no Play language splits)
    bundle {
        language {
            enableSplit = false
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Pure Kotlin, no native libraries: one universal APK serves every ABI.
// APK output name: pitwall-updater-v<version>-universal-release.apk (replaces the removed applicationVariants API)
base {
    archivesName.set("pitwall-updater-v$appVersionName-universal")
}

androidComponents {
    // Release-only packaging: the debug variant is disabled
    beforeVariants(selector().withBuildType("debug")) { variantBuilder ->
        variantBuilder.enable = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
}
