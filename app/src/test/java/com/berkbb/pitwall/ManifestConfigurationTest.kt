package com.berkbb.pitwall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestConfigurationTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val manifest: Element by lazy {
        val file = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .firstOrNull { it.isFile } ?: File("src/main/AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        factory.newDocumentBuilder().parse(file).documentElement
    }

    private fun elements(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun testF1TvPackageIsDeclaredForPackageVisibility() {
        // Without <queries>, Android 11+ hides F1 TV unless this app installed it
        val queried = elements("package").map { it.getAttributeNS(androidNs, "name") }
        assertTrue("F1 TV must be declared in <queries>", F1TV_PACKAGE_NAME in queried)
    }

    @Test
    fun testInstallResultReceiverIsNotExported() {
        val receiver = elements("receiver").first { it.getAttributeNS(androidNs, "name") == ".InstallResultReceiver" }
        assertEquals("InstallResultReceiver must not be exported", "false", receiver.getAttributeNS(androidNs, "exported"))
    }

    @Test
    fun testSelfUpdateRestartReceiverListensForOwnUpdatesOnly() {
        val receiver = elements("receiver").first { it.getAttributeNS(androidNs, "name") == ".SelfUpdateRestartReceiver" }
        assertEquals("SelfUpdateRestartReceiver must not be exported", "false", receiver.getAttributeNS(androidNs, "exported"))
        val actions = receiver.getElementsByTagName("action").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }
        }
        assertEquals(listOf("android.intent.action.MY_PACKAGE_REPLACED"), actions)
    }
}
