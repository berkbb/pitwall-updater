package com.berkbb.pitwall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DynamicArchitectureAndLocaleTest {

    // ==========================================
    // 1. Dynamic Locale & Persistence Tests
    // ==========================================

    @Test
    fun testSavedLanguageTakesPrecedenceOverSystemLocale() {
        assertEquals("tr", LocaleHelper.resolveInitialLanguage(savedLang = "tr", systemLang = "en"))
        assertEquals("ro", LocaleHelper.resolveInitialLanguage(savedLang = "ro", systemLang = "tr"))
        assertEquals("en", LocaleHelper.resolveInitialLanguage(savedLang = "en", systemLang = "ro"))
    }

    @Test
    fun testSystemLanguageFallbackWhenNoSavedPreference() {
        assertEquals("tr", LocaleHelper.resolveInitialLanguage(savedLang = null, systemLang = "tr"))
        assertEquals("ro", LocaleHelper.resolveInitialLanguage(savedLang = null, systemLang = "ro"))
        assertEquals("en", LocaleHelper.resolveInitialLanguage(savedLang = null, systemLang = "en"))
    }

    @Test
    fun testUnsupportedSystemLanguagesFallbackToEnglish() {
        assertEquals("en", LocaleHelper.resolveInitialLanguage(savedLang = null, systemLang = "de"))
        assertEquals("en", LocaleHelper.resolveInitialLanguage(savedLang = null, systemLang = "fr"))
        assertEquals("en", LocaleHelper.resolveInitialLanguage(savedLang = null, systemLang = "es"))
        assertEquals("en", LocaleHelper.resolveInitialLanguage(savedLang = null, systemLang = "it"))
        assertEquals("en", LocaleHelper.resolveInitialLanguage(savedLang = null, systemLang = null))
    }

    @Test
    fun testLanguageCycleTransitions() {
        assertEquals("tr", LocaleHelper.getNextLanguage("en"))
        assertEquals("ro", LocaleHelper.getNextLanguage("tr"))
        assertEquals("en", LocaleHelper.getNextLanguage("ro"))
        assertEquals("en", LocaleHelper.getNextLanguage("unknown"))
    }

    @Test
    fun testLocaleResolutionIsIndependentOfTurkishDefaultLocale() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            assertEquals("en", LocaleHelper.resolveInitialLanguage(null, "INTEL"))
            assertEquals("tr", LocaleHelper.resolveInitialLanguage(null, "TR"))
            assertEquals("ro", LocaleHelper.resolveInitialLanguage(null, "RO"))
        } finally {
            Locale.setDefault(original)
        }
    }

    // ==========================================
    // 2. Dynamic ABI & Split Selection Tests
    // ==========================================

    @Test
    fun testResolveBestAbi() {
        // Modern 64-bit Google TV
        val abis64 = arrayOf("arm64-v8a", "armeabi-v7a", "armeabi")
        assertEquals("arm64_v8a", PackageInstallerHelper.resolveBestAbi(abis64))

        // 32-bit legacy TV (e.g. MediaTek MT5596)
        val abis32 = arrayOf("armeabi-v7a", "armeabi")
        assertEquals("armeabi_v7a", PackageInstallerHelper.resolveBestAbi(abis32))

        // x86_64 Emulator
        val abisX86 = arrayOf("x86_64", "x86")
        assertEquals("x86_64", PackageInstallerHelper.resolveBestAbi(abisX86))
    }

    @Test
    fun testResolveBestAbiUsesAbisActuallyShippedInBundle() {
        val abis64 = arrayOf("arm64-v8a", "armeabi-v7a", "armeabi")

        // 64-bit device, bundle ships both: prefer arm64
        assertEquals("arm64_v8a", PackageInstallerHelper.resolveBestAbi(abis64, setOf("arm64_v8a", "armeabi_v7a")))

        // 64-bit device, bundle ships only 32-bit split: fall back to armeabi_v7a instead of installing no native libs
        assertEquals("armeabi_v7a", PackageInstallerHelper.resolveBestAbi(abis64, setOf("armeabi_v7a")))

        // 32-bit-only device, bundle ships only arm64: no compatible split
        assertNull(PackageInstallerHelper.resolveBestAbi(arrayOf("armeabi-v7a", "armeabi"), setOf("arm64_v8a")))
    }

    @Test
    fun testNoAbiSplitsAreIncludedWithoutACompatibleAbi() {
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("base.apk", null))
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("config.en.apk", null))
        assertFalse(PackageInstallerHelper.shouldIncludeSplit("config.arm64_v8a.apk", null))
    }

    @Test
    fun testSplitNameStripsDirectories() {
        assertEquals("base.apk", PackageInstallerHelper.splitName("splits/base.apk"))
        assertEquals("config.arm64_v8a.apk", PackageInstallerHelper.splitName("config.arm64_v8a.apk"))
    }

    @Test
    fun testShouldIncludeSplitForArm64Device() {
        val targetAbi = "arm64_v8a"

        // Must include base, matching ABI, and all density/language splits
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("base.apk", targetAbi))
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("config.arm64_v8a.apk", targetAbi))
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("config.en.apk", targetAbi))
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("config.tr.apk", targetAbi))
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("config.xxhdpi.apk", targetAbi))

        // Must reject conflicting 32-bit ARM or x86 splits
        assertFalse(PackageInstallerHelper.shouldIncludeSplit("config.armeabi_v7a.apk", targetAbi))
        assertFalse(PackageInstallerHelper.shouldIncludeSplit("config.x86.apk", targetAbi))
        assertFalse(PackageInstallerHelper.shouldIncludeSplit("config.x86_64.apk", targetAbi))
        assertFalse(PackageInstallerHelper.shouldIncludeSplit("metadata.json", targetAbi))
    }

    @Test
    fun testShouldIncludeSplitForArmeabiV7aDevice() {
        val targetAbi = "armeabi_v7a"

        // Must include base, matching 32-bit ABI, and config splits
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("base.apk", targetAbi))
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("config.armeabi_v7a.apk", targetAbi))
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("config.en.apk", targetAbi))
        assertTrue(PackageInstallerHelper.shouldIncludeSplit("config.hdpi.apk", targetAbi))

        // Must reject 64-bit ARM split
        assertFalse(PackageInstallerHelper.shouldIncludeSplit("config.arm64_v8a.apk", targetAbi))
    }
}
