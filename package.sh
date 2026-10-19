#!/usr/bin/env bash
# Pitwall Updater release packager: runs the unit tests, assembles the release APK
# and copies it (with a SHA-256 checksum) into dist/.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# JDK 17–21: keep an existing JAVA_HOME, otherwise resolve one dynamically on macOS
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
    for v in 21 17; do
        if JAVA_HOME="$(/usr/libexec/java_home -v "$v" 2>/dev/null)"; then
            export JAVA_HOME
            break
        fi
        unset JAVA_HOME
    done
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in \
        /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
        /opt/homebrew/opt/openjdk@21 \
        /opt/homebrew/opt/openjdk@17 \
        /opt/homebrew/opt/openjdk; do
        if [[ -d "$candidate" && -x "$candidate/bin/java" ]]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "❌ JAVA_HOME is not set and no JDK 17–21 was found." >&2
    exit 1
fi
echo "☕ JAVA_HOME: $JAVA_HOME"

VERSION="$(sed -n 's/^val appVersionName = "\(.*\)"/\1/p' app/build.gradle.kts)"
if [[ -z "$VERSION" ]]; then
    echo "❌ Could not read appVersionName from app/build.gradle.kts." >&2
    exit 1
fi
APK_DIR="app/build/outputs/apk/release"

# Signing check: installed apps (and their in-app update) only accept a new version signed with the
# same key. release-cert.sha256 holds the expected certificate; a mismatch is reported, not fatal.
check_release_signing() {
    local apk="$1"
    local expected actual apksigner sdk
    expected="$(tr -d '[:space:]' < release-cert.sha256 2>/dev/null || true)"
    sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/SDK/Android}}"
    apksigner="$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)"
    if [[ -z "$apksigner" || -z "$expected" ]]; then
        echo "⚠️  Signing not verified (apksigner or release-cert.sha256 missing)."
        return 0
    fi
    actual="$("$apksigner" verify --print-certs "$apk" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | head -n 1)"
    if [[ "$actual" == "$expected" ]]; then
        echo "🔏 Signing key matches release-cert.sha256 (${actual:0:16}…)."
        return 0
    fi
    cat <<WARN

⚠️⚠️⚠️  SIGNING KEY MISMATCH / İMZA ANAHTARI UYUŞMUYOR  ⚠️⚠️⚠️
   Expected / Beklenen : $expected
   This APK / Bu APK   : ${actual:-unknown}
   Devices that have the app signed with the expected key CANNOT update to this build, and the
   in-app update fails too. On those devices uninstall the app first, then install this version
   (the app's data and settings are removed).
   Beklenen anahtarla kurulu cihazlar bu sürüme GÜNCELLENEMEZ, uygulama içi güncelleme de çalışmaz.
   O cihazlarda uygulamayı önce kaldırın, sonra bu sürümü yeniden kurun (uygulama verileri silinir).
   To keep the key, build on the original machine or copy its ~/.android/debug.keystore here.

WARN
}

if [[ ! -f keystore.properties && -z "${PITWALL_KEYSTORE_FILE:-}" ]]; then
    echo "⚠️  No release keystore configured; the packages will be signed with the debug key."
fi

echo "🧪 Running unit tests and building release v$VERSION (universal, x64, arm64, arm32 APKs)..."
./gradlew clean test assembleRelease

# Expected outputs: universal, x64, arm64, arm32 APKs
TARGETS=(
    "universal:universal"
    "x86_64:x64"
    "arm64-v8a:arm64"
    "armeabi-v7a:arm32"
)

rm -rf dist
mkdir -p dist

for pair in "${TARGETS[@]}"; do
    abi="${pair%%:*}"
    target="${pair##*:}"
    src="$APK_DIR/pitwall-updater-v${VERSION}-${target}-release.apk"
    if [[ ! -f "$src" ]]; then
        src="$APK_DIR/pitwall-updater-v${VERSION}-${abi}-release.apk"
    fi
    if [[ ! -f "$src" ]]; then
        echo "❌ Expected output not found: $src" >&2
        exit 1
    fi
    cp "$src" "dist/pitwall-updater-v${VERSION}-${target}-release.apk"
done

(cd dist && shasum -a 256 *.apk > SHA256SUMS)

check_release_signing "dist/pitwall-updater-v${VERSION}-universal-release.apk"

echo "✅ Release packages ready in dist/:"
(cd dist && ls -lh *.apk | awk '{print "   " $NF " (" $5 ")"}')
echo "   SHA256SUMS"
