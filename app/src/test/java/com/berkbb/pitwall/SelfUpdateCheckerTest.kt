package com.berkbb.pitwall

import org.junit.Assert.assertEquals
import org.junit.Test

class SelfUpdateCheckerTest {

    @Test
    fun newerTagIsAvailable() {
        assertEquals(SelfUpdateStatus.Available("1.0.8", "1.0.7", null), SelfUpdateChecker.statusFor("v1.0.8", "1.0.7"))
        assertEquals(SelfUpdateStatus.Available("1.1.0", "1.0.10", null), SelfUpdateChecker.statusFor("v1.1.0", "1.0.10"))
    }

    @Test
    fun sameOrOlderTagIsUpToDate() {
        assertEquals(SelfUpdateStatus.UpToDate("1.0.7"), SelfUpdateChecker.statusFor("v1.0.7", "1.0.7"))
        assertEquals(SelfUpdateStatus.UpToDate("1.0.8"), SelfUpdateChecker.statusFor("v1.0.7", "1.0.8"))
    }

    private val assets = listOf("arm32", "arm64", "universal", "x64").map {
        ReleaseAsset("pitwall-updater-v1.0.9-$it-release.apk", "https://example.com/$it.apk", 900_000, null)
    } + ReleaseAsset("SHA256SUMS", "https://example.com/sums", 400, null)

    @Test
    fun selectsTheApkForTheDeviceAbi() {
        assertEquals("https://example.com/arm32.apk", SelfUpdateChecker.selectApk("v1.0.9", assets, arrayOf("armeabi-v7a", "armeabi"))?.downloadUrl)
        assertEquals("https://example.com/arm64.apk", SelfUpdateChecker.selectApk("v1.0.9", assets, arrayOf("arm64-v8a", "armeabi-v7a"))?.downloadUrl)
        assertEquals("https://example.com/x64.apk", SelfUpdateChecker.selectApk("v1.0.9", assets, arrayOf("x86_64"))?.downloadUrl)
    }

    @Test
    fun fallsBackToUniversalOrNothing() {
        assertEquals("https://example.com/universal.apk", SelfUpdateChecker.selectApk("v1.0.9", assets, arrayOf("x86"))?.downloadUrl)
        assertEquals(null, SelfUpdateChecker.selectApk("v1.0.9", assets.takeLast(1), arrayOf("armeabi-v7a")))
    }

    @Test
    fun universalOnlyReleaseServesEveryAbi() {
        // Current releases ship only the universal APK (see package.sh)
        val universalOnly = assets.filter { "-arm" !in it.name && "-x64" !in it.name }
        for (abis in listOf(arrayOf("armeabi-v7a", "armeabi"), arrayOf("arm64-v8a", "armeabi-v7a"), arrayOf("x86_64"))) {
            assertEquals("https://example.com/universal.apk", SelfUpdateChecker.selectApk("v1.0.9", universalOnly, abis)?.downloadUrl)
        }
    }

    @Test
    fun missingTagIsUnavailable() {
        assertEquals(SelfUpdateStatus.Unavailable, SelfUpdateChecker.statusFor("", "1.0.7"))
    }
}
