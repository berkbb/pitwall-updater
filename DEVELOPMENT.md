# Development Guide

This guide details the architectural decisions, project structure, developer workflows, and debugging procedures for the **Pitwall Updater for Android TV & Google TV (Android 8.0+)**.

---

## 🏗️ Architecture & Component Overview

The application follows Modern Android Architecture principles:

- **Presentation Layer:** Jetpack Compose (BOM `2026.09.00`) + Material 3.
- **Build:** AGP 9.4.1 with built-in Kotlin (KGP 2.2.10; only the Compose compiler plugin is applied), Gradle 9.7.1, compileSdk 37.2 (`compileSdk { version = release(37) { minorApiLevel = 2 } }`), targetSdk 35, R8 minification + resource shrinking for release.
- **APK naming:** `base.archivesName = "pitwall-updater-v<version>"` produces `pitwall-updater-v<version>-release.apk` (AGP 9 removed the legacy `applicationVariants` API).
- **State Management:** MVVM pattern using Kotlin Coroutines and `StateFlow`.
- **System Integration:** Android `PackageInstaller` Session API via `PackageInstallerHelper`.

```
com.berkbb.pitwall/
├── MainActivity.kt               # Compose UI entry point, D-Pad focus & locale provider
├── LocaleHelper.kt               # Dynamic locale resolver & cycling helper (EN, TR, RO)
├── UpdaterViewModel.kt           # StateFlow UI state, GitHub API client, verified streaming download
├── ReleaseResolver.kt            # Pure release selection & numeric version comparison
├── ApkmCache.kt                  # Tag-bound .apkm cache with SHA-256 / size verification
├── PackageInstallerHelper.kt     # Bundle-aware ABI selection & PackageInstaller session pipeline
└── InstallResultReceiver.kt      # Non-exported receiver for package install status intents
```

### 1. `MainActivity.kt`

- Implements a reactive `CompositionLocalProvider` wrapping `LocalConfiguration` and `LocalContext` created with `createConfigurationContext()`. This enables instantaneous switching between English, Turkish, and Romanian without restarting the process.
- Implements `TvActionButton` with `MutableInteractionSource` and `collectIsFocusedAsState()` to render TV D-pad focus states:
  - Default Check Button: Platinum/White outline on focus (`#E0E0E0`).
  - Action / Update Button: Violet accent (`#7C4DFF`) default with Championship Gold outline on focus (`#FFD700`).
  - Language Toggle: Cyan focus ring (`#00ADB5`).
- `TvActionButton` relies on the `Button`'s own focus target (no extra `.focusable()`), so each button is a single D-pad stop.
- The About dialog is a Compose `Dialog` window: D-pad focus is trapped inside it and Back dismisses it.

### 2. `UpdaterViewModel.kt`

- Manages `UpdaterUiState`:
  ```kotlin
  data class UpdaterUiState(
      val installedVersion: String = "",
      val latestVersion: String = "",
      val downloadUrl: String = "",
      val isInstalled: Boolean = false,
      val isUpdateAvailable: Boolean = false,
      val isLoading: Boolean = false,
      val isDownloading: Boolean = false,
      val isSignatureConflict: Boolean = false,
      val downloadProgress: Int = 0,
      val statusResId: Int = R.string.status_detecting,
      val statusArg: Any? = null // Int for %1$d strings, String for %1$s strings
  )
  ```
- Uses string resource IDs (`statusResId`) instead of pre-rendered strings to allow Compose to re-evaluate localized text immediately upon language changes.
- Fetches the release list from the GitHub API:
  `https://api.github.com/repos/Alexvbp/f1tv-4k-patch/releases?per_page=20`
  and lets `ReleaseResolver.selectLatestStable` pick the newest non-draft, non-prerelease plain version tag (e.g. `v3.0.49.4`) that ships an `.apkm` asset.
- Versions are compared numerically on their leading dotted segments: tag `v3.0.49.4` vs installed `versionName` `3.0.49.4-SP166.4.1-release-R54.2-tv`.
- Downloads go to `f1tv_<tag>.apkm.part`, are verified against the asset's `digest` (`sha256:…`) or exact size, then renamed; older cached bundles are pruned.
- Uses the application context only; progress updates are atomic (`StateFlow.update`) and emitted only when the percentage changes.
- Before downloading, checks `StorageManager.getAllocatableBytes()` against `ApkmCache.requiredFreeBytes()` (download + installer staging copy + 50 MB margin).
- `cancelDownload()` cancels the job and disconnects the active connection; the `.part` file is deleted and the status becomes *Download cancelled*.

### 3. `PackageInstallerHelper.kt`

- Collects the ABI splits present in the bundle and picks the first entry of `android.os.Build.SUPPORTED_ABIS` that the bundle actually ships; if none match, installation is aborted with a clear status (`NO_COMPATIBLE_ABI`).
- Reads the downloaded `.apkm` (ZIP archive) and extracts only:
  - `base.apk`
  - Architecture-specific split (`config.armeabi_v7a.apk` or `config.arm64_v8a.apk`)
  - Screen density split (matching device display density, or `config.xxhdpi.apk`)
  - Language splits (e.g. `config.en.apk`, `config.tr.apk` if present)
- Streams chunks directly to `PackageInstaller.openSession()` using `session.openWrite()`.
- Creates a `PendingIntent` targeted to the non-exported `InstallResultReceiver` to receive status feedback (`STATUS_PENDING_USER_ACTION` launches the TV confirmation dialog; `STATUS_FAILURE_CONFLICT` surfaces the uninstall action).
- Requires `<queries><package android:name="com.formulaone.production"/></queries>` in the manifest; without it Android 11+ hides F1 TV unless this app installed it.

---

## 💻 Development Environment Setup

### Prerequisites

- **macOS, Linux, or Windows**
- **Java Development Kit:** JDK 17–21 (e.g. Zulu OpenJDK 21 LTS), exposed via `JAVA_HOME`
- **Android SDK Tools:** `adb` (Android SDK Platform-Tools)
- **Gradle:** 8.11.1 (via `./gradlew` wrapper)

### Gradle Configuration (`gradle.properties`)

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

The JDK is intentionally not pinned in the repository. If you need a machine-specific JDK, set `org.gradle.java.home` in your user-level `~/.gradle/gradle.properties` instead.

### Release Signing

See the *Release Signing* section of the README. `keystore.properties`, `*.jks`, `*.keystore`, and `*.p12` are git-ignored.

---

## 🔍 Validation & Testing Workflows

### 1. Connecting to the Target TV

Ensure the TV is on the same local Wi-Fi / Ethernet subnet:

```bash
adb connect [IP_ADDRESS]:5555
adb devices
```

### 2. Running Automated Unit Tests

```bash
./gradlew test
```

### 3. Building & Deploying

```bash
# Assemble the universal release APK
./gradlew assemble
adb install -r app/build/outputs/apk/release/pitwall-updater-v1.0.11-universal-release.apk
```

### 4. Remote Inspection & Headless Testing

To inspect the TV interface remotely:

```bash
# Capture TV screenshot
adb exec-out screencap -p > /tmp/tv_screen.png

# Simulate remote D-Pad navigation
adb shell input keyevent 19  # DPAD_UP
adb shell input keyevent 20  # DPAD_DOWN
adb shell input keyevent 21  # DPAD_LEFT
adb shell input keyevent 22  # DPAD_RIGHT
adb shell input keyevent 23  # DPAD_CENTER (Select)
adb shell input keyevent 66  # ENTER
```

---

## 🛡️ Key Lessons Learned & Gotchas

1. **Signature Differences:**
   Official Google Play Store APKs and GitHub patched UHD builds use different signing keys. If replacing an existing official build via `PackageInstaller`, Android will reject with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Sideloading requires a one-time uninstall of the official package first; the manager detects `STATUS_FAILURE_CONFLICT` and offers an *Uninstall Current F1 TV* button.
2. **Java 27 Incompatibility:**
   Older toolchains (Kotlin Gradle Plugin `2.0.21`, Gradle `8.11`) failed on JDK 25+ / 27 with `IllegalArgumentException: 27`. Use a JDK 17–21 LTS build runtime.
3. **TV Deep Standby State:**
   Some Android TVs enter low-power deep standby after extended inactivity, which may temporarily terminate the ADB daemon. Waking the TV via remote or sending `adb shell input keyevent 224` (`KEYCODE_WAKEUP`) restores network connectivity.
4. **Unsupported Smart TV Platforms:**
   - **Philips Saphi OS / Titan OS:** Linux-based; cannot run APKs.
   - **Samsung (Tizen OS) & LG (webOS):** Proprietary operating systems; cannot run Android APKs.
   - **Android < 8.0:** Unsupported due to `minSdk = 26` requirement.
