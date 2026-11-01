package com.kireibox.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal data class UpdateDownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
)

internal class UpdateDownloader(
    private val client: OkHttpClient,
    private val directory: File,
    private val maxBytes: Long = 200L * 1024L * 1024L,
) {
    suspend fun download(
        release: UpdateRelease,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            require(maxBytes > 0) { "Invalid update download limit" }
            val url = release.apkUrl.toHttpUrl()
            require(isTrustedDownloadUrl(url)) { "Update download address is not trusted" }
            check(directory.exists() || directory.mkdirs()) { "Unable to create update cache directory" }
            val assetName = release.apkAsset
            require(assetName.endsWith(".apk", ignoreCase = true) && '/' !in assetName && '\\' !in assetName) {
                "Update APK asset name is invalid"
            }
            val finalFile = File(directory, assetName)
            val partialFile = File(directory, "$assetName.part")
            partialFile.delete()
            finalFile.delete()
            try {
                val request =
                    Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.android.package-archive")
                        .header("User-Agent", "KireiBox-Android-Updater")
                        .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Update download failed (HTTP ${response.code})" }
                    val body = response.body
                    val totalBytes = body.contentLength()
                    check(totalBytes < 0 || totalBytes <= maxBytes) { "Update package exceeds size limit" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    val startedAt = System.nanoTime()
                    var bytesRead = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(partialFile).buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytesRead += count
                                check(bytesRead <= maxBytes) { "Update package exceeds size limit" }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                val elapsedSeconds =
                                    ((System.nanoTime() - startedAt) / 1_000_000_000.0)
                                        .coerceAtLeast(0.001)
                                onProgress(
                                    UpdateDownloadProgress(
                                        bytesRead = bytesRead,
                                        totalBytes = totalBytes.takeIf { it >= 0 } ?: release.apkSize,
                                        bytesPerSecond = (bytesRead / elapsedSeconds).toLong(),
                                    ),
                                )
                            }
                        }
                    }
                    check(totalBytes < 0 || bytesRead == totalBytes) { "Incomplete update download" }
                    val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    release.manifest?.let { manifest ->
                        check(actualSha256 == manifest.sha256) { "Update package SHA-256 verification failed" }
                    }
                }
                check(partialFile.renameTo(finalFile)) { "Unable to save update package" }
                finalFile
            } catch (error: Throwable) {
                partialFile.delete()
                finalFile.delete()
                throw error
            }
        }

    private fun isTrustedDownloadUrl(url: HttpUrl): Boolean =
        (url.host == "localhost" || url.host == "127.0.0.1") ||
            (url.isHttps && (url.host == "github.com" || url.host.endsWith(".githubusercontent.com")))
}
