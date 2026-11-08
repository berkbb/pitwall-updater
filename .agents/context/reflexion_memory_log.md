# Reflexion Memory Log & Self-Correction Trajectories

## 🧠 Episodes & Lessons Learned

### Episode 1: Hardcoded Device Strings
- **Issue:** Hardcoded "Philips" and model numbers in UI and resource strings prevented universal Android TV support.
- **Resolution:** Replaced with runtime `Build.MANUFACTURER`, `Build.MODEL`, `Build.PRODUCT`, and `Build.SUPPORTED_ABIS` combined with dynamic localized string formatting `%1$s %2$s • %3$s (%4$s)`.

### Episode 2: Multi-Language Parity & Testing
- **Issue:** Adding new languages risks missing keys or mismatched format parameters (`%1$d`, `%%%1$d`).
- **Resolution:** Established mandatory 1:1 translation rules under `.agents/rules/localization_and_translation_rules.md` and implemented an automated `LocalizationParityTest` that validates key sets equality, non-empty values, and placeholder matching.

### Episode 3: Full Review Findings (v1.0.2)
- **Issue:** Tests passed while core behavior was broken: the download cache ignored versions, package visibility hid F1 TV on Android 11+, version checks used substring matching, the install receiver was exported, and a "patcher/signer" was a no-op that produced unsigned APKs.
- **Resolution:** Tag-bound SHA-256-verified cache, `<queries>`, numeric version comparison, non-exported receiver, removal of the dead patcher/signer, and tests that assert behavior (`ReleaseResolverTest`, `ApkmCacheTest`, `ManifestConfigurationTest`).
- **Lesson:** Tests must assert outcomes (bytes changed, signatures valid, versions ordered), not merely that files exist. Verify claims in README/CHANGELOG against reachable code.

### Episode 4: One Download Address Is Not Enough (2026-11-08 GitHub Regional Outage)
- **Issue:** Both downloads (the upstream `.apkm` and the self-update APK) used only `browser_download_url`. On 2026-11-08 one GitHub edge region answered HTTP 504 for every `github.com/…/releases/download/…` address for hours while `api.github.com` kept working, so the release check succeeded and every download failed.
- **Resolution:** The release JSON already names the asset on the API (`url` field). `ReleaseResolver.parseAssets` keeps it only under `https://api.github.com/repos/<owner>/<repo>/releases/assets/` for the expected repository (`UPSTREAM_REPO`, `SELF_REPO`); the shared `ReleaseDownloader` retries once from there with `Accept: application/octet-stream` after an I/O error or a 5xx (never after 404, never after a cancellation), truncating the `.part` file first, and `ApkmCache.isValid` verifies the result as before. The connection opener is injected so `ReleaseDownloaderTest` covers the fallback and the digest rejection without the network.
- **Lesson:** A verified download needs a second, independently routed address for the same bytes; the digest check makes the second address as safe as the first. Keep network code Android-free and injectable so failure paths get JVM tests.
