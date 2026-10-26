# AGENTS.md - Master Constitution & Governance Protocol

## 🎯 Purpose & Scope
This repository implements the **Pitwall Updater for Android TV and Google TV** (an unofficial updater for the F1 TV app) (Android 8.0 Oreo and above).
All AI agents operating within this repository must adhere strictly to this constitution, the declarative domain rules under `.agents/rules/`, and the structured procedural memory under `.agents/skills/`.

---

## 🏛️ Core Constitutional Invariants

### 1. Mandatory 1:1 Translation & Localization Rule
- **Trilingual Parity (EN, TR, RO):** Every string resource defined in the primary English locale (`values/strings.xml`) must possess an exact, 1:1 corresponding key in Turkish (`values-tr/strings.xml`) and Romanian (`values-ro/strings.xml`).
- **Default Locale:** The default application language is **English (`en`)**.
- **Zero Missing / Orphan Keys:** No key may exist in a localized file without existing in the base English resource file, and no key may be omitted.
- **Placeholder Parity:** All dynamic format specifiers (e.g. `%1$s`, `%1$d`, `%%%1$d`) must match in type, index, and quantity across all translation files.
- **No Hardcoded Display Strings:** UI components must never contain hardcoded display strings; all text must be accessed through Compose `stringResource(R.string.key)` or resource IDs.

### 2. Mandatory Verification & Automated Testing Invariant ("Test is a MUST")
- **Non-Negotiable Execution Gate:** Every single modification affecting source code, string resources, architecture handling, download/cache verification, manifest configuration, or packaging **MUST** be verified by running the automated unit test suite (`./gradlew test`) before completing any task.
- **Zero Test Failure Policy:** No task is considered complete with failing or skipped assertions in `LocalizationParityTest.kt`, `DynamicArchitectureAndLocaleTest.kt`, `ReleaseResolverTest.kt`, `ApkmCacheTest.kt`, or `ManifestConfigurationTest.kt`.
- **Release Verification:** Build and assembly tasks must confirm successful execution of `./gradlew assemble`.

### 3. Dynamic Hardware & Platform Rule
- **Universal Android TV / Google TV Support:** The codebase must dynamically inspect hardware parameters (`Build.MANUFACTURER`, `Build.MODEL`, `Build.PRODUCT`, `Build.SUPPORTED_ABIS`) at runtime.
- **Zero Static Vendor Assumptions:** No hardcoded TV brand names (e.g., Philips, Sony, TCL) in code or logic.
- **Supported Platforms:** All Android TV / Google TV devices on Android 8.0+ (API 26+).
- **Unsupported Redline:** Non-Android TV operating systems (Philips Saphi OS / Titan OS, Samsung Tizen OS, LG webOS) and legacy Android TV (< 8.0) must never be targeted with incompatible APK installation workflows.

### 4. Language Standard for All Markdown & Governance Files
- **Strict 100% English Requirement:** All `.md` files throughout the entire repository (`README.md`, `DEVELOPMENT.md`, `CHANGELOG.md`, `AGENTS.md`, and all `.agents/**` context, rules, and skill files) must be written exclusively in English.

### 5. Strict Privacy, Sanitization & Path Hygiene Invariant
- **Zero Absolute Local Paths:** Agents must never write or commit absolute local system paths (such as `/Users/`, `C:\Users\`, or `file:///Users/`) into any source file, Markdown documentation, or build script. All internal Markdown references must use clean relative paths.
- **Zero Sensitive / Personal Metadata:** Any generated certificate must use generic open-source distinguished names (`CN=PitwallUpdater, O=OpenSource, C=US`). Release keystores and their passwords live only in git-ignored `keystore.properties`, `PITWALL_*` environment variables, or the shared keystore outside the repository (`~/.signing/babadogan-tv-release.p12`, password in the macOS Keychain); machine-specific files (`local.properties`) and JDK paths (`org.gradle.java.home`) must never be committed.
- **Install Pipeline Security:** `InstallResultReceiver` must stay non-exported, downloaded bundles must be verified (SHA-256 digest or exact size) before installation, and `<queries>` must declare `com.formulaone.production`.
- **Zero Telemetry & Secret Leakage:** The application must strictly contain zero tracking SDKs, zero telemetry, zero analytics, and zero credentials.

---

## 🧠 Memory Structure & Navigation
- **Domain Rules:** Consult `.agents/rules/` before modifying UI, translations, build scripts, or privacy configurations.
- **Procedural Skills:** Use `.agents/skills/` for translation auditing, testing, and verification routines.
- **Context & Knowledge Assets:** Consult `.agents/context/` for code graphs, domain models, and roadmaps.
