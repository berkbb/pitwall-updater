# Changelog

All notable changes to the **Pitwall Updater for Android TV & Google TV** (formerly F1 TV Manager) project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

---

## [1.0.9] - 2026-10-19

### Added
- **Self-update:** when a newer Pitwall Updater is out, "Update Now" in the update window downloads the release APK for the TV's processor (arm32 / arm64 / x64, else universal), checks it against the SHA-256 digest GitHub publishes and installs it over the app after the TV's confirmation prompt; the app reopens by itself after the update. Download progress, the confirmation step and any failure are shown in the same window.

---

## [1.0.8] - 2026-10-19

### Added
- **Pitwall Updater update check:** "Check for Updates" in the About window checks this app's own GitHub releases and always opens a result window: up to date (with the installed version), a newer version and where to download it, no published release / repository not public, or GitHub unreachable. A quiet check at start-up shows "Pitwall Updater vX available" under the header when a newer version exists. The F1 TV check on the main screen is unchanged.

---

## [1.0.7] - 2026-10-19

### Added
- **App version in the About window:** A Version card next to Device & Platform shows the installed Pitwall Updater version.

---

## [1.0.6] - 2026-10-19

### Fixed
- **About window fits on one TV screen:** Slimmer info cards and tighter vertical spacing keep the Device & Platform card fully visible on 1080p (960×540 dp) TVs instead of cutting it off at the bottom.
- **License viewer scrolls with the D-pad:** Up/Down now scroll the license text; focus moves on to the buttons only once the text reaches its start or end.

---

## [1.0.5] - 2026-10-18

### Changed
- **License Migration to GPL-3.0:** Migrated repository license from MIT to GNU General Public License v3.0 (GPL-3.0) to ensure derivative works remain open-source and protect against closed-source commercial forks, aligning with the licensing of AynaCast Player.
- **Default Multi-Architecture APK Packaging:** Configured release builds to generate `universal`, `arm64` (`arm64-v8a`), `arm32` (`armeabi-v7a`), and `x64` (`x86_64`) APKs by default, removing the Android App Bundle (`.aab`) generation step from the release packager.
- **In-App Read-Only License Viewer:** Added a dedicated full-screen license viewer modal in the About dialog, allowing users to view the GNU GPL-3.0 license text with a single click and D-pad scrolling support.
- **Privacy & Path Hygiene:** Removed and untracked `local.properties` to ensure local SDK paths and usernames are never tracked in Git.

---

## [1.0.4] - 2026-10-17

### Changed
- **Full-screen About window:** the Info button opens a full-screen window like AynaCast's, with the accent-bar title, a close button in the top-right corner and Close at the bottom; developer, website, patch and device details are shown as cards.

---

## [1.0.3] - 2026-10-14

### Changed
- **Renamed to Pitwall Updater:** The app is now called *Pitwall Updater* in all locales, so the Formula 1 / F1 TV trademarks are used only to describe compatibility. The application ID and Kotlin package moved from `com.berkbb.f1tv.manager` to `com.berkbb.pitwall` (it installs as a new app next to the old one), the Gradle root project is `PitwallUpdater`, the APK is `pitwall-updater-v<version>-release.apk`, and the signing environment variables are `PITWALL_*`.
- **Neutral Visual Identity:** Replaced the F1-style banner and red/dark palette with a generic update icon and a violet accent (`#7C4DFF`).
- **Documentation:** Reworded the README to describe the project as an unofficial updater and added a prominent non-affiliation notice.

### Added
- **Opt-In Per-ABI Splits:** Introduced `-Ppitwall.abiSplits=true` build flag generating architecture-specific release APKs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) alongside the universal release APK.
- **Automated Release Packager:** Created `package.sh` and macOS Finder launcher `package.command` to execute tests, build both split APKs and the Android App Bundle (`.aab`), copy them to `dist/`, and generate a `SHA256SUMS` verification manifest.

---

## [1.0.2] - 2026-10-10

### Fixed
- **Stale Download Cache:** The cache reused any `.apkm` larger than 20 MB, so new releases (~48 MB) installed the previously cached package. Bundles are now cached per release tag, downloaded to a `.part` file, and verified against GitHub's published SHA-256 digest (exact size fallback) before use; old bundles are pruned.
- **Package Visibility (Android 11+):** Added a `<queries>` declaration for `com.formulaone.production`; F1 TV installed via Play Store or ADB was previously reported as "not installed".
- **Version Comparison:** Replaced substring matching (`"3.0.10".contains("3.0.1")`) with numeric segment comparison; release selection now skips drafts, pre-releases, and device-specific variant tags.
- **ABI Selection:** The installer picks the best device ABI that the bundle actually contains and reports an incompatibility instead of installing a bundle without native libraries.
- **Signing-Key Conflicts:** `INSTALL_FAILED_UPDATE_INCOMPATIBLE` is detected and the UI offers an *Uninstall Current F1 TV* action (`REQUEST_DELETE_PACKAGES`).
- **D-Pad Focus:** Removed the duplicate focus target on every button; the About dialog is now a real `Dialog` window that traps focus.
- **Robustness:** HTTP status checks, connection cleanup, `ZipFile` closing on errors, partial-download cleanup, duplicate-install guard, throttled and atomic progress updates, application context in the ViewModel, and no network call on language switch.

### Security
- **Exported Receiver / Intent Redirection:** `InstallResultReceiver` is no longer exported, so other apps cannot inject fake install results or arbitrary intents to launch.

### Removed
- **On-Device Patcher & Signer:** Removed `OnDevicePatcher.kt`, `ApkSignerHelper.kt`, and `OnDevicePatcherTest.kt`. The patcher wrote DEX files back unchanged, the signer produced unsigned APKs, the extractor was vulnerable to Zip Slip, and neither was reachable from the app.
- Unused `androidx.tv:tv-foundation` alpha dependency, unused resources, and inactive ProGuard keep rules.

### Added
- **Storage Pre-Check:** Downloads start only when enough free space exists for the bundle, the installer's staging copy, and a safety margin; otherwise the required size is shown.
- **Cancel Download:** A *Cancel Download* button (auto-focused for the remote) aborts the transfer and removes the partial file.

### Build & Repository
- Enabled R8 code and resource shrinking for release builds (APK ~10.7 MB → ~0.9 MB); removed unused `material-icons-extended` and `ui-tooling-preview`.
- Migrated to AGP 9.4.1 with built-in Kotlin (KGP 2.2.10), Gradle 9.7.1, and compileSdk 37.2 (Android 17); targetSdk raised to 35. The `org.jetbrains.kotlin.android` plugin and `kotlinOptions` were replaced by built-in Kotlin and `kotlin.compilerOptions`; APK naming moved from `applicationVariants` to `base.archivesName`.
- Dependencies: Compose BOM 2026.09.00, core-ktx 1.19.1, lifecycle 2.11.0, activity-compose 1.13.0, coroutines 1.11.0.
- Removed the fixed landscape orientation (ignored on large screens from Android 16; TVs are landscape already).
- Release signing via git-ignored `keystore.properties` or `F1TV_*` environment variables (debug-key fallback with warning).
- Stopped tracking `local.properties`; removed the machine-specific `org.gradle.java.home` from `gradle.properties`.
- Renamed the Gradle root project to `F1TVManager`; `BuildConfig.VERSION_NAME` is used for the HTTP User-Agent.
- Replaced the legacy `variantFilter` with `androidComponents.beforeVariants`.
- New unit tests: `ReleaseResolverTest`, `ApkmCacheTest`, `ManifestConfigurationTest`, and bundle-aware ABI cases.

---

## [1.0.1] - 2026-09-30

### Added & Architecture
- **On-Device DEX Patching Engine:** Created standalone in-memory DEX bytecode patcher (`OnDevicePatcher.kt`) and local APK self-signing engine (`ApkSignerHelper.kt`), enabling direct on-device 4K/UHD unlocking independently of external release pipelines.
- **Dynamic ABI Resolution & Extraction:** Refactored `PackageInstallerHelper.kt` with exact ABI token matching for 32-bit (`armeabi-v7a`), 64-bit (`arm64-v8a`), and x86 architectures.
- **Automated Unit Testing Suite:** Created comprehensive test suites (`LocalizationParityTest.kt`, `DynamicArchitectureAndLocaleTest.kt`, `OnDevicePatcherTest.kt`) covering 1:1 key parity, placeholder formatting, locale fallbacks, and ABI split filtering.

### Localization & Branding
- **Rebranding to F1 TV Manager:** Updated application title across all locales (`F1 TV Manager` in EN, `F1 TV Yöneticisi` in TR, `Manager F1 TV` in RO) and migrated package namespace to `com.berkbb.f1tv.manager`.
- **Romanian (RO) Language Support:** Added 1:1 Romanian localization (`values-ro/strings.xml`) alongside English and Turkish.
- **English Default:** Configured English (`EN`) as the application's default startup language with instantaneous live switcher (`🌐 EN` / `🌐 TR` / `🌐 RO`).

### Build & Packaging
- **Release-Only Streamlined Packaging:** Disabled debug variant packaging to produce a single, optimized, self-signed production artifact: `f1tv-manager-v1.0.1-release.apk`.
- **Custom Output Naming:** Configured Gradle `applicationVariants` rule to dynamically format APK filenames according to release versioning.

### Legal & Documentation
- **Trademark & Non-Affiliation Disclaimers:** Added explicit legal disclaimers to [`LICENSE`](LICENSE), [`README.md`](README.md), and the in-app `ℹ️ Info` dialog confirming independent open-source status with zero official Formula 1 affiliation.
- **100% English Documentation Standard:** Standardized all repository `.md` files (`README.md`, `DEVELOPMENT.md`, `CHANGELOG.md`, `AGENTS.md`) exclusively in English.
- **Universal Android TV / Google TV Support:** Clarified full compatibility with all Android TV & Google TV devices running **Android 8.0 (API 26) or higher** (Philips, Sony, TCL, Xiaomi, Chromecast with Google TV, etc.).
- **Unsupported Systems Matrix:** Explicitly documented unsupported platforms:
  - Philips Saphi OS / Titan OS (Linux-based, cannot run APKs)
  - Samsung Smart TVs (Tizen OS)
  - LG Smart TVs (webOS)
  - Legacy Android TV sets running Android < 8.0 (below `minSdk = 26`).

---

## [1.0.0] - 2026-09-18

### Added
- **Initial Release:** Lightweight native Android TV companion application written in Kotlin and Jetpack Compose.
- **Dynamic ABI Resolution:** Runtime CPU architecture detection supporting both 32-bit (`armeabi-v7a`) for legacy MediaTek platforms (e.g. TPM171E) and 64-bit (`arm64-v8a`) for modern Philips Google TV sets.
- **GitHub Release Integration:** Automated release checking and `.apkm` artifact resolution using the Alexvbp/f1tv-4k-patch repository.
- **Split APK Session Installer:** In-memory extraction and streaming of multi-part Android App Bundles via native `PackageInstaller.Session` API without third-party store dependencies.
- **Full Bilingual Localization:** Complete English and Turkish translations for all UI elements, status messages, card titles, and progress indicators.
- **Live Language Switcher:** Interactive top-bar toggle (`🌐 EN` / `🌐 TR`) for instantaneous runtime language changes without restarting the activity.
- **Fresh Install Capability:** Automatic detection when F1 TV is missing, enabling single-click download and initial setup.
- **Bilingual About & Info Modal:** Integrated `ℹ️ Info` dialog showcasing author credits (`berkbb`, https://berkbb.github.io), upstream patch credits, and live TV hardware metrics with full remote D-pad and Back-key handling.
- **Permissions & Lifecycle Sync:** Automatic `REQUEST_INSTALL_PACKAGES` permission checking with direct Settings intent dispatch, and reactive UI state synchronization via `InstallEvents` and `onResume()`.
- **Smart Local Caching:** Automatically skips redundant 70MB downloads on re-installation / verification if valid cached packages exist.
- **F1 Themed 10-Foot UI:** High-contrast focus borders, D-pad navigation styling, and dynamic loading spinners for TV remote usability.
- **Direct Launch Shortcut:** Quick action button to launch F1 TV directly from the updater when installed.

### Technical & Environment
- Migrated build environment to **Zulu OpenJDK 21 LTS** for full compatibility with Gradle 8.11 and Kotlin 2.0.21.
- Configured double-clickable macOS desktop utility scripts (`install_f1tv_philips_tpm171e.command`) for automated host-to-TV deployment.
