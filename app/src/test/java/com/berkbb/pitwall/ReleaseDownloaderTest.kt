package com.berkbb.pitwall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** The download's fallback from github.com to the api.github.com asset address, without the network. */
class ReleaseDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val bytes = "pitwall release bytes".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val pageUrl = "https://github.com/berkbb/pitwall-updater/releases/download/v1.0.12/pitwall-updater-v1.0.12-universal-release.apk"
    private val apiUrl = "https://api.github.com/repos/berkbb/pitwall-updater/releases/assets/123"
    private val userAgent = "PitwallUpdater/test"

    /** One canned answer: an HTTP status with a body, or an I/O failure when [code] is null. */
    private class Answer(val code: Int?, val body: ByteArray = ByteArray(0))

    private class FakeConnection(url: URL, private val answer: Answer) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = answer.code ?: throw IOException("connection reset")
        override fun getContentLengthLong(): Long = answer.body.size.toLong()
        override fun getInputStream(): InputStream = ByteArrayInputStream(answer.body)
    }

    private class Network(private val answers: Map<String, Answer>) {
        val opened = mutableListOf<FakeConnection>()
        val open: (URL) -> HttpURLConnection = { url -> FakeConnection(url, answers.getValue(url.toString())).also { opened += it } }
        val urls get() = opened.map { it.url.toString() }
        fun header(index: Int, name: String) = opened[index].getRequestProperty(name)
    }

    private fun release(apiUrl: String?) = ReleaseInfo("v1.0.12", pageUrl, bytes.size.toLong(), sha, apiUrl)

    private fun download(release: ReleaseInfo, network: Network, onChunk: () -> Unit = {}): Pair<Boolean, File> {
        val target = folder.newFolder().resolve("pitwall-updater.apk")
        val ok = ReleaseDownloader.download(release, target, userAgent, network.open, onChunk = onChunk) {}
        return ok to target
    }

    @Test
    fun aServerErrorOnGithubFallsBackToTheApiAssetAddress() {
        val network = Network(mapOf(pageUrl to Answer(504), apiUrl to Answer(200, bytes)))
        val (ok, target) = download(release(apiUrl), network)
        assertTrue(ok)
        assertEquals(listOf(pageUrl, apiUrl), network.urls)
        assertNull(network.header(0, "Accept"))
        assertEquals("application/octet-stream", network.header(1, "Accept"))
        assertEquals(userAgent, network.header(0, "User-Agent"))
        assertEquals(userAgent, network.header(1, "User-Agent"))
        assertTrue(bytes.contentEquals(target.readBytes()))
        assertFalse(ApkmCache.partialFileFor(target).exists())
    }

    @Test
    fun aBrokenConnectionAlsoFallsBack() {
        val network = Network(mapOf(pageUrl to Answer(null), apiUrl to Answer(200, bytes)))
        assertTrue(download(release(apiUrl), network).first)
        assertEquals(listOf(pageUrl, apiUrl), network.urls)
    }

    @Test
    fun aMissingFileIsNotRetriedAndNeitherIsAReleaseWithoutAnApiAddress() {
        val notFound = Network(mapOf(pageUrl to Answer(404), apiUrl to Answer(200, bytes)))
        assertFalse(download(release(apiUrl), notFound).first)
        assertEquals(listOf(pageUrl), notFound.urls)

        val noApi = Network(mapOf(pageUrl to Answer(503)))
        assertFalse(download(release(null), noApi).first)
        assertEquals(listOf(pageUrl), noApi.urls)
    }

    @Test
    fun theFallbackStillHasToMatchTheDigest() {
        val network = Network(mapOf(pageUrl to Answer(502), apiUrl to Answer(200, "something else".toByteArray())))
        val (ok, target) = download(release(apiUrl), network)
        assertFalse(ok)
        assertFalse(target.exists())
        assertFalse(ApkmCache.partialFileFor(target).exists())
    }

    @Test
    fun theFirstAddressIsEnoughWhenItWorks() {
        val network = Network(mapOf(pageUrl to Answer(200, bytes)))
        val (ok, target) = download(release(apiUrl), network)
        assertTrue(ok)
        assertEquals(listOf(pageUrl), network.urls)
        assertEquals(sha, ApkmCache.sha256Of(target))
    }

    @Test
    fun aCancelledDownloadIsNotRetried() {
        // cancelDownload() cancels the job and then disconnects: the read fails with an I/O error while the
        // coroutine is already cancelled, so the activity check throws before the second address is tried.
        val network = Network(mapOf(pageUrl to Answer(null), apiUrl to Answer(200, bytes)))
        try {
            download(release(apiUrl), network) { throw CancellationException("cancelled") }
            fail("the cancellation must reach the caller")
        } catch (_: CancellationException) {
        }
        assertEquals(listOf(pageUrl), network.urls)
    }
}
