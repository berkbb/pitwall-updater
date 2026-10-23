# Pitwall Updater for Android TV & Google TV

**Pitwall Updater** is an unofficial, independent, open-source updater for the F1 TV app on Android TV and Google TV devices running **Android 8.0 (API 26) or higher** (including Philips, Sony, TCL, Xiaomi, Chromecast, etc.). It is built with **Kotlin** and **Jetpack Compose**.

> [!IMPORTANT]
> Pitwall Updater is not affiliated with, endorsed by, or sponsored by Formula One Licensing B.V. or Formula 1. "F1 TV" is used only to identify the app this tool works with. See [TRADEMARKS.md](TRADEMARKS.md).

---

## 🏎️ Background & Motivation

Many capable Android TV sets (for example Philips `49PUS7503/62` on the `TPM171E` chassis) are not offered the F1 TV app in the Google Play Store. Community builds of the app are published as multi-part App Bundles (`.apkm`: `base.apk` + architecture, language and density splits), which most sideloading tools cannot install directly on a TV.

Pitwall Updater handles this on the TV itself. It tracks the community 4K/UHD build published at [Alexvbp/f1tv-4k-patch](https://github.com/Alexvbp/f1tv-4k-patch), downloads and verifies it, and installs the required splits through Android's native `PackageInstaller` Session API. This project does not host, modify, or redistribute any F1 TV binaries.

---

## ✨ Features

- **Direct GitHub Release Tracking:** Queries the GitHub Releases API for the newest *stable* Alexvbp 4K/UHD release (pre-release / device-specific variants such as `-dev` or `-shield-…` are skipped) and compares it numerically against the installed package (`com.formulaone.production`).
- **Verified, Version-Bound Downloads:** Each `.apkm` is cached under its release tag, downloaded to a temporary `.part` file, and only accepted after its SHA-256 matches the digest published by GitHub (exact size check as fallback). A new release never reuses an older cached package.
- **Signing-Key Conflict Guidance:** If an official (Play Store) F1 TV build is installed, Android rejects the community build (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The updater detects this and offers a one-click *Uninstall Current F1 TV* action before reinstalling.
- **Dynamic ABI & Architecture Detection:** Inspects TV processor architecture at runtime (`Build.SUPPORTED_ABIS`). Selects the best device ABI that the bundle actually ships — 64-bit (`arm64-v8a`) when available, 32-bit (`armeabi-v7a`) otherwise — and reports an incompatibility instead of installing a package without native libraries.
- **Native Split APK Streaming:** Extracts `.apkm` ZIP archives in cache and streams the exact required split APKs directly into Android's native `PackageInstaller.Session`. No root required; uses standard TV user-confirmation prompts.
- **Storage Pre-Check & Cancellable Downloads:** Verifies there is enough free space for the download plus the installer's staging copy before starting, and the download can be cancelled at any time from the remote (partial files are removed).
- **Fresh Install & Update Handling:**
  - If F1 TV is **not installed**: Prompts to download and perform an initial installation.
  - If an **update is available**: Prompts to download and apply the update.
  - If **up to date**: Allows verification or clean reinstallation.
- **Trilingual UI (English default, Turkish & Romanian):** Fully localized interface with an instantaneous in-app language switcher (`🌐 EN` / `🌐 TR` / `🌐 RO`) that switches text and status live without restarting the app.
- **Automated Localization & Architecture Verification:** Full unit test suite (`LocalizationParityTest`, `DynamicArchitectureAndLocaleTest`, `ReleaseResolverTest`, `ApkmCacheTest`, `ManifestConfigurationTest`) covering 1:1 key parity, ABI selection, version comparison, download verification, and manifest security invariants.
- **100% D-Pad & TV Remote Optimized:** Built from scratch for 10-foot television viewing with high-contrast focus rings, custom button hover states, and loading indicators.
- **Ultra-Lightweight Footprint:** Native Jetpack Compose UI without bloated webview or cross-platform runtime overhead.

---

## 📺 Compatibility Matrix

| Platform / Series                        | Operating System               | Architecture           | Supported? | Notes                                        |
| :--------------------------------------- | :----------------------------- | :--------------------- | :--------: | :------------------------------------------- |
| **All Android TV & Google TV Sets**      | Android TV 8.0+ (Oreo to 14+)  | 32-bit / 64-bit ARM    |  **Yes**   | Philips, Sony, TCL, Xiaomi, Chromecast, etc. |
| **Philips TPM171E** (e.g. 49PUS7503)     | Android TV 8.0 Oreo            | 32-bit (`armeabi-v7a`) |  **Yes**   | Tested & verified                            |
| **Philips TPM181E / TPM191E**            | Android TV 9 Pie               | 32-bit / 64-bit ARM    |  **Yes**   | Fully compatible                             |
| **Philips TPM211E / TPM221E / TPM231E**  | Android TV 11 / Google TV      | 64-bit (`arm64-v8a`)   |  **Yes**   | Philips The One & OLED                       |
| **Legacy Android TV (Android < 8.0)**    | Android TV 5.0 – 7.1.2         | Any                    |   **No**   | Minimum SDK requires Android 8.0 (API 26+)   |
| **Philips Saphi OS / Titan OS**          | Proprietary Linux              | N/A                    |   **No**   | Linux-based; cannot execute Android APKs     |
| **Samsung (Tizen OS)**                   | Tizen OS                       | N/A                    |   **No**   | Not Android-based; cannot execute APKs       |
| **LG (webOS)**                           | webOS                          | N/A                    |   **No**   | Not Android-based; cannot execute APKs       |

> [!WARNING]
> **Unsupported Systems:**
> - **Philips Saphi OS / Titan OS** (Linux-based, cannot run Android APKs)
> - **Samsung (Tizen OS)** (Not Android-based, cannot run Android APKs)
> - **LG (webOS)** (Not Android-based, cannot run Android APKs)
> - **Legacy Android TV (< 8.0)** (Requires minimum API 26 / Android 8.0 Oreo)

---

## 🛠️ Tech Stack & Requirements

- **Language:** Kotlin 2.2.10 (AGP built-in Kotlin)
- **UI Framework:** Jetpack Compose (BOM 2026.09.00) + Material 3
- **Build System:** Gradle 9.7.1 + Android Gradle Plugin 9.4.1 (R8 shrinking enabled, ~1 MB release APK)
- **Compile SDK:** API 37.2 (Android 17) — requires the *Android SDK Platform 37.2* package and Android Studio 2026.1.3 or newer
- **Java Compatibility:** JDK 21 (LTS)
- **Minimum SDK:** API 26 (Android 8.0 Oreo or newer)
- **Target SDK:** API 35 (Android 15)

---

## 🚀 Building & Compilation

### 💻 Method 1: Command Line Interface (CLI)

Ensure a JDK (17–21) and the Android SDK are installed, then run the following commands:

```bash
# 1. Java environment
# Point JAVA_HOME at a JDK 17–21 (macOS example: export JAVA_HOME=$(/usr/libexec/java_home -v 21))

# 2. Run automated unit test suite
./gradlew test

# 3. Assemble Release APK
./gradlew assemble
```

### 🛠️ Method 2: Android Studio

1. Open Android Studio and select **Open**, targeting the repository root.
2. Wait for Gradle synchronization to finish.
3. From the top menu bar, select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

---

### 🔐 Release Signing

Release builds are signed with a dedicated keystore when one is configured, so APKs built on different machines can update each other. Create a git-ignored `keystore.properties` in the repository root:

```properties
storeFile=/path/to/pitwall-release.jks
storePassword=…
keyAlias=pitwall
keyPassword=…
```

Alternatively set the `PITWALL_KEYSTORE_FILE`, `PITWALL_KEYSTORE_PASSWORD`, `PITWALL_KEY_ALIAS`, and `PITWALL_KEY_PASSWORD` environment variables. Without either, the build falls back to the local debug key and prints a warning.

Installed copies only accept an update signed with the same key, and the in-app update (About > Check for Updates) depends on it. `release-cert.sha256` holds the expected signing certificate; the packaging script compares every release against it and prints a **SIGNING KEY MISMATCH** warning when they differ. A build with a different key cannot update existing installations: on those devices the app must be uninstalled first and the new version installed again, which removes its data. To keep the key, package on the original machine or copy its `~/.android/debug.keystore`; after switching to a dedicated keystore on purpose, update `release-cert.sha256` (once, since every existing installation then needs a reinstall).

---

### 📍 Compiled APK Location

Upon build completion, the generated release APK file is located at:
- **Release APK:** `app/build/outputs/apk/release/pitwall-updater-v1.0.11-universal-release.apk`

### 📦 Release Packaging Script

`./package.sh` (or double-click `package.command` in macOS Finder) runs the unit tests and writes the release APK to `dist/` together with a `SHA256SUMS` file:

| File | Target |
| :--- | :--- |
| `pitwall-updater-v<version>-universal-release.apk` | All architectures |

Pitwall Updater is pure Kotlin; its only native code is Compose's small `androidx.graphics.path` library (about 10 KB per architecture), so one universal APK serves every architecture. (The F1 TV bundle it installs is still resolved per TV processor at install time.)

---

## ⚙️ Permissions & TV Installation Guide

### 📺 Daily Usage & In-App Updates
* **No Developer Options Required:** Once Pitwall Updater is installed, regular F1 TV updates do not require TV Developer Options to remain enabled.
* **Install Unknown Apps Permission:** Only the standard Android *"Install unknown apps"* (`REQUEST_INSTALL_PACKAGES`) permission is needed. The application automatically redirects you to this system setting upon initial run.
* **Uninstall Prompt:** `REQUEST_DELETE_PACKAGES` is declared solely to open the system uninstall confirmation for a conflicting official F1 TV build; nothing is removed without your confirmation.

### 📦 Initial TV Installation Methods

#### 🔹 Method 1: USB Flash Drive or File Manager (No Developer Mode Required - Easy)
1. Copy `pitwall-updater-v1.0.11-universal-release.apk` to a USB flash drive or transfer via **Send Files to TV** / **X-plore**.
2. Open the file manager on your TV and install the APK.

#### 🔹 Method 2: Network ADB Sideloading (Requires TV Developer Options)
1. Go to TV Settings > **Device Preferences** > **About** > Click **Build Number** 7 times to enable **Developer Options**.
2. Under **Developer Options**, enable **Network / USB Debugging**.
3. Connect and install from your computer:

```bash
adb connect [TV_IP_ADDRESS]:5555
adb install -r dist/pitwall-updater-v1.0.11-universal-release.apk
```

---

## 👤 Author & Credits

- **Developer:** **berkbb** ([https://berkbb.github.io](https://berkbb.github.io))
- **F1 TV 4K/UHD Patch:** [Alexvbp/f1tv-4k-patch](https://github.com/Alexvbp/f1tv-4k-patch)

---

## 📄 License & Legal Disclaimers

This project is open-source software licensed under the **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)**.

### ⚖️ Trademark & Non-Affiliation Notice
- **Formula 1, F1, FORMULA ONE, F1 TV**, and related marks, logos, and emblems are registered trademarks of **Formula One Licensing B.V.**, a Formula 1 company.
- This project is an **independent, unofficial open-source utility** developed strictly for personal, educational, and interoperability purposes on Android TV and Google TV platforms.
- This software is **not affiliated with, endorsed by, sponsored by, authorized by, or in any way officially connected** with Formula One Licensing B.V., Formula One Group, Liberty Media, or any of their subsidiaries or affiliates.
- This repository does not host, distribute, or stream any copyrighted audio, video, or proprietary application binaries. All installation operations interact with user-authorized, publicly accessible packages using standard Android APIs.
