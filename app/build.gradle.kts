import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// AGP 9 compiles Kotlin natively (built-in Kotlin); only the Compose compiler plugin is applied
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = "1.0.11"

// Release signing, first match wins: keystore.properties (git-ignored), PITWALL_* environment
// variables, or the TV apps' shared keystore ~/.signing/babadogan-tv-release.p12, whose password
// is the macOS Keychain item "babadogan-tv-release-keystore".
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(env)
val sharedKeystore = File(System.getProperty("user.home"), ".signing/babadogan-tv-release.p12")
val releaseStoreFile = signingValue("storeFile", "PITWALL_KEYSTORE_FILE") ?: sharedKeystore.takeIf { it.isFile }?.path
// macOS only; elsewhere the password comes from keystore.properties or the environment.
fun keychainPassword(): String? = runCatching {
    providers.exec {
        commandLine("security", "find-generic-password", "-s", "babadogan-tv-release-keystore", "-w")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()?.ifEmpty { null }
val releaseStorePassword = releaseStoreFile?.let { signingValue("storePassword", "PITWALL_KEYSTORE_PASSWORD") ?: keychainPassword() }

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
                storePassword = releaseStorePassword
                keyAlias = signingValue("keyAlias", "PITWALL_KEY_ALIAS") ?: "babadogan-tv"
                keyPassword = signingValue("keyPassword", "PITWALL_KEY_PASSWORD") ?: releaseStorePassword
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
                logger.warn("Pitwall: no release keystore (keystore.properties, PITWALL_KEYSTORE_FILE or ~/.signing/babadogan-tv-release.p12); signing release with the debug key.")
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

// Pure Kotlin; the only native code is Compose's ~10 KB androidx.graphics.path per ABI, so one universal APK serves every ABI.
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
