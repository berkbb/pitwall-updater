package com.berkbb.pitwall

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release file into its .part file and publishes it as the target only when it matches the
 * release digest (or size, see [ApkmCache.isValid]). When the github.com address fails with an I/O error
 * or a 5xx answer and the release carries the asset's api.github.com address, the same asset is fetched
 * once more from there with `Accept: application/octet-stream`; a 404 is a plain failure and is not
 * retried. Android-free so the fallback can be unit-tested with a stand-in connection opener.
 */
object ReleaseDownloader {

    /**
     * @param open hands out the connection for a URL; tests replace the network here.
     * @param onConnection sees every opened connection, so a caller can disconnect it to cancel.
     * @param onChunk runs before every read and before the fallback attempt; throw from it to abort.
     * @param onProgress gets each new percentage while the total length is known.
     */
    fun download(
        release: ReleaseInfo,
        target: File,
        userAgent: String,
        open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
        onConnection: (HttpURLConnection) -> Unit = {},
        onChunk: () -> Unit = {},
        onProgress: (Int) -> Unit
    ): Boolean {
        val part = ApkmCache.partialFileFor(target)
        try {
            val first = runCatching { fetch(release.downloadUrl, null, userAgent, release.size, part, open, onConnection, onChunk, onProgress) }
            val fetched = when {
                first.getOrNull() == HttpURLConnection.HTTP_OK -> true
                release.apiUrl != null && first.isRetryable() -> {
                    onChunk() // a cancelled download is not retried
                    fetch(release.apiUrl, "application/octet-stream", userAgent, release.size, part, open, onConnection, onChunk, onProgress) == HttpURLConnection.HTTP_OK
                }
                else -> first.getOrThrow() == HttpURLConnection.HTTP_OK // rethrows anything that was not a plain failure
            }
            if (!fetched) return false
            if (!ApkmCache.isValid(part, release)) return false
            target.delete()
            return part.renameTo(target)
        } finally {
            if (part.exists()) part.delete()
        }
    }

    /** A broken connection or a server-side error (500 and above) is worth a second address; 404 is not. */
    private fun Result<Int>.isRetryable(): Boolean =
        exceptionOrNull() is IOException || (getOrNull() ?: 0) >= HttpURLConnection.HTTP_INTERNAL_ERROR

    /** Fetches [url] into [part] (truncated first) and returns the HTTP status; the body is read only on 200. */
    private fun fetch(
        url: String,
        accept: String?,
        userAgent: String,
        expectedSize: Long,
        part: File,
        open: (URL) -> HttpURLConnection,
        onConnection: (HttpURLConnection) -> Unit,
        onChunk: () -> Unit,
        onProgress: (Int) -> Unit
    ): Int {
        val connection = open(URL(url)).also(onConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            if (accept != null) setRequestProperty("Accept", accept)
            connectTimeout = 15000
            readTimeout = 30000
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) return code
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var done = 0L
            var lastProgress = -1
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        onChunk()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val progress = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            return code
        } finally {
            connection.disconnect()
        }
    }
}
