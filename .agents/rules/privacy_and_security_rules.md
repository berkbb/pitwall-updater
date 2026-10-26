# Privacy, Sanitization & Security Rules

## 📌 Standard Overview
This rule document establishes strict privacy, metadata sanitization, and security invariants across all agent workflows and codebase operations.

---

## 📋 Mandatory Rules & Guidelines

### 1. Absolute Local Path Prohibition
- **Zero Local User System Paths:** Agents must never write, embed, or commit local system paths (e.g. `/Users/username/...`, `C:\Users\username\...`, `file:///Users/...`) into any source code, documentation file (`.md`), or build configuration.
- **Relative Markdown Links:** All documentation cross-references must use relative Markdown links (e.g. `[LICENSE](LICENSE)`).

### 2. Identity & Certificate Sanitization
- **Generated Certificates:** Any programmatic or build-time X.509 certificate generation must use standard open-source distinguished names:
  `CN=PitwallUpdater, O=OpenSource, C=US`
- **Release Signing Secrets:** Keystores and passwords are supplied only through git-ignored `keystore.properties`, `PITWALL_*` environment variables, or the TV apps' shared keystore outside the repository (`~/.signing/babadogan-tv-release.p12`) with its password in the macOS Keychain item `babadogan-tv-release-keystore`. Never write the password into any file.
- **Zero Sensitive Data:** No private email addresses, personal phone numbers, physical addresses, API secrets, or passwords may be stored anywhere in the codebase.

### 3. Zero Telemetry & Privacy Preservation
- **No Analytics / Tracking SDKs:** The application does not include any analytics libraries, telemetry trackers, or advertising SDKs.
- **Minimal Network Surface:** The only permitted outbound HTTP calls are public GitHub release queries and `.apkm` asset downloads.

### 4. Install Pipeline Security
- `InstallResultReceiver` must remain `android:exported="false"`.
- Downloaded bundles must pass SHA-256 digest (or exact size) verification before being handed to `PackageInstaller`.
- Never extract archive entries to the filesystem using unvalidated entry names (Zip Slip).
