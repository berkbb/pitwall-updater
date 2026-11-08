# Code Graph Wiki & Symbol Index

## 🧬 Component Map

### 1. `MainActivity.kt`
- **Role:** Entry point, Jetpack Compose root, reactive `ConfigurationContext` locale manager, remote D-Pad focus handlers.
- **Key Symbols:** `MainActivity`, `PitwallScreen`, `InfoCard`, `TvActionButton` (single focus target, optional `modifier`), About `Dialog`.

### 2. `UpdaterViewModel.kt`
- **Role:** MVVM StateFlow manager (atomic `update {}`), GitHub release list fetch, verified `.part` download engine, install permission dispatcher, uninstall prompt for signing-key conflicts. Uses the application context only.
- **Key Symbols:** `UpdaterViewModel`, `UpdaterUiState` (`statusArg: Any?`), `downloadAndInstall`, `cancelDownload`, `uninstallF1TV`.

### 3. `PackageInstallerHelper.kt`
- **Role:** Bundle-aware ABI resolution, `.apkm` ZIP streaming into native `PackageInstaller.Session`.
- **Key Symbols:** `PackageInstallerHelper.installApkm` → `InstallStartResult`, `resolveBestAbi(supportedAbis, bundleAbis)`, `shouldIncludeSplit`, `splitName`.

### 3a. `ReleaseResolver.kt`
- **Role:** Pure (Android-free) release selection, numeric version comparison, update status evaluation, tag-bound cache naming.
- **Key Symbols:** `ReleaseResolver.selectLatestStable`, `parseVersion`, `compareVersions`, `evaluate`, `parseAssets`, `parseReleases`, `apiAssetUrl`, `F1TV_PACKAGE_NAME`, `UPSTREAM_REPO`, `UPSTREAM_RELEASES_URL`.

### 3a-bis. `ReleaseDownloader.kt`
- **Role:** Android-free download of a release file into its `.part` file with the api.github.com asset-address fallback after an I/O error or 5xx, verified through `ApkmCache.isValid`; the connection opener is injectable for tests (`ReleaseDownloaderTest`).
- **Key Symbols:** `ReleaseDownloader.download`.

### 3b. `ApkmCache.kt`
- **Role:** Tag-bound `.apkm` cache with SHA-256 / exact-size verification and pruning of stale bundles.
- **Key Symbols:** `ApkmCache.fileFor`, `isValid`, `pruneExcept`, `requiredFreeBytes`, `toMegabytesCeil`.

### 4. `LocaleHelper.kt`
- **Role:** Pure dynamic locale resolution, system fallback, and language cycling engine.
- **Key Symbols:** `LocaleHelper.resolveInitialLanguage`, `LocaleHelper.getNextLanguage`.

### 5. `InstallResultReceiver.kt`
- **Role:** Non-exported BroadcastReceiver for system package installation status intents; flags `STATUS_FAILURE_CONFLICT` as a signing-key conflict.
- **Key Symbols:** `InstallResultReceiver`, `InstallEvent`, `InstallEvents`.

### 6. Unit Tests
- **Role:** 1:1 string key/placeholder parity (EN, TR, RO), ABI / split selection, version comparison & release selection, cache verification, manifest security invariants.
- **Key Symbols:** `LocalizationParityTest`, `DynamicArchitectureAndLocaleTest`, `ReleaseResolverTest`, `ApkmCacheTest`, `ManifestConfigurationTest`.
