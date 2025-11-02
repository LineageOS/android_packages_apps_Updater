/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.download

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.PriorityQueue

/**
 * OkHttp-based download client with support for resumable downloads
 * and duplicate link handling using Kotlin coroutines.
 */
class DownloadClient private constructor(
    private val url: String,
    private val destination: File,
    private val progressListener: ProgressListener?,
    private val callback: DownloadCallback,
    private val useDuplicateLinks: Boolean
) {

    interface DownloadCallback {
        fun onResponse(headers: Headers)
        fun onSuccess()
        fun onFailure(cancelled: Boolean)
    }

    fun interface ProgressListener {
        fun update(bytesRead: Long, contentLength: Long, speed: Long, eta: Long)
    }

    private val client = OkHttpClient.Builder().followRedirects(!useDuplicateLinks)
        .followSslRedirects(!useDuplicateLinks).build()

    private var downloadJob: Job? = null

    /**
     * Start the download. Has no effect if download already started.
     */
    fun start() {
        if (downloadJob?.isActive == true) {
            Log.e(TAG, "Already downloading")
            return
        }
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            downloadInternal(resume = false)
        }
    }

    /**
     * Resume the download. Fails if server can't fulfill partial content request.
     * Has no effect if download already started or destination file doesn't exist.
     */
    fun resume() {
        if (downloadJob?.isActive == true) {
            Log.e(TAG, "Already downloading")
            return
        }
        if (!destination.exists()) {
            callback.onFailure(false)
            return
        }
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            downloadInternal(resume = true)
        }
    }

    /**
     * Cancel the download. Has no effect if download isn't ongoing.
     */
    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
    }

    private fun downloadInternal(resume: Boolean) {
        try {
            val offset = if (resume) destination.length() else 0L
            val requestBuilder = Request.Builder().url(url)

            if (resume && offset > 0) {
                requestBuilder.addHeader("Range", "bytes=$offset-")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val responseCode = response.code

            if (useDuplicateLinks && isRedirectCode(responseCode)) {
                handleDuplicateLinks(response)
                return
            }

            callback.onResponse(response.headers)

            val justResumed = when {
                resume && responseCode == 206 -> {
                    Log.d(TAG, "The server fulfilled the partial content request")
                    true
                }

                resume || !isSuccessCode(responseCode) -> {
                    Log.e(TAG, "The server replied with code $responseCode")
                    callback.onFailure(false)
                    return
                }

                else -> false
            }

            response.body?.use { body ->
                downloadToFile(body, resume, offset, justResumed)
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Download cancelled")
            callback.onFailure(true)
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Error downloading file", e)
            callback.onFailure(false)
        }
    }

    private fun downloadToFile(
        body: okhttp3.ResponseBody, resume: Boolean, offset: Long, justResumed: Boolean
    ) {
        var totalBytesRead = if (resume && justResumed) offset else 0L
        val totalBytes = body.contentLength() + totalBytesRead

        var speed = -1L
        var eta = -1L
        var curSampleBytes = totalBytesRead
        var lastMillis = 0L
        var skipSpeedCalc = justResumed

        body.byteStream().use { inputStream ->
            val append = resume && justResumed
            FileOutputStream(destination, append).buffered().use { outputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                while (true) {
                    downloadJob?.ensureActive()

                    val count = inputStream.read(buffer)
                    if (count <= 0) break

                    outputStream.write(buffer, 0, count)
                    totalBytesRead += count

                    if (skipSpeedCalc) {
                        lastMillis = SystemClock.elapsedRealtime()
                        curSampleBytes = totalBytesRead
                        skipSpeedCalc = false
                    } else {
                        val millis = SystemClock.elapsedRealtime()
                        val delta = millis - lastMillis
                        if (delta > SPEED_CALC_INTERVAL_MS) {
                            val curSpeed = ((totalBytesRead - curSampleBytes) * 1000) / delta
                            speed = if (speed == -1L) curSpeed else ((speed * 3) + curSpeed) / 4
                            lastMillis = millis
                            curSampleBytes = totalBytesRead
                        }
                    }

                    if (speed > 0) {
                        eta = (totalBytes - totalBytesRead) / speed
                    }

                    progressListener?.update(totalBytesRead, totalBytes, speed, eta)
                }

                outputStream.flush()
                progressListener?.update(totalBytesRead, totalBytes, speed, eta)
                callback.onSuccess()
            }
        }
    }

    private fun handleDuplicateLinks(response: Response) {
        val protocol = response.request.url.scheme

        data class DuplicateLink(val url: String, val priority: Int)

        val duplicates = response.headers.values("Link").mapNotNull { field ->
            // https://tools.ietf.org/html/rfc6249
            // https://tools.ietf.org/html/rfc5988#section-5
            DUPLICATE_LINK_REGEX.find(field)?.let { matchResult ->
                val url = matchResult.groupValues[1]
                val priority = matchResult.groupValues.getOrNull(2)?.toIntOrNull() ?: 999999
                Log.d(TAG, "Adding duplicate link $url")
                DuplicateLink(url, priority)
            } ?: run {
                Log.d(TAG, "Ignoring link $field")
                null
            }
        }.let { links ->
            PriorityQueue(compareBy<DuplicateLink> { it.priority }).apply {
                addAll(links)
            }
        }

        var newUrl = response.header("Location")

        while (true) {
            downloadJob?.ensureActive()

            try {
                requireNotNull(newUrl) { "No Location header" }

                val request = Request.Builder().url(newUrl).build()

                require(request.url.scheme == protocol) { "Protocol changes are not allowed" }

                Log.d(TAG, "Downloading from $newUrl")

                val newResponse = client.newCall(request).execute()

                require(isSuccessCode(newResponse.code)) { "Server replied with ${newResponse.code}" }

                callback.onResponse(newResponse.headers)
                newResponse.body?.use { body ->
                    downloadToFile(body, resume = false, offset = 0, justResumed = false)
                }
                return
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                val link = duplicates.poll()
                if (link != null) {
                    newUrl = link.url
                    Log.e(TAG, "Using duplicate link ${link.url}", e)
                } else {
                    Log.e(TAG, "All duplicate links exhausted", e)
                    callback.onFailure(false)
                    return
                }
            }
        }
    }

    class Builder {
        private var callback: DownloadCallback? = null
        private var destination: File? = null
        private var progressListener: ProgressListener? = null
        private var url: String? = null
        private var useDuplicateLinks: Boolean = false

        @Throws(IOException::class)
        fun build(): DownloadClient {
            val finalUrl = url ?: throw IllegalStateException("No download URL defined")
            val finalDestination =
                destination ?: throw IllegalStateException("No download destination defined")
            val finalCallback =
                callback ?: throw IllegalStateException("No download callback defined")

            return DownloadClient(
                finalUrl, finalDestination, progressListener, finalCallback, useDuplicateLinks
            )
        }

        fun setDestination(destination: File) = apply {
            this.destination = destination
        }

        fun setDownloadCallback(downloadCallback: DownloadCallback) = apply {
            this.callback = downloadCallback
        }

        fun setProgressListener(progressListener: ProgressListener) = apply {
            this.progressListener = progressListener
        }

        fun setUrl(url: String) = apply {
            this.url = url
        }

        fun setUseDuplicateLinks(useDuplicateLinks: Boolean) = apply {
            this.useDuplicateLinks = useDuplicateLinks
        }
    }

    companion object {
        private const val TAG = "DownloadClient"
        private const val DEFAULT_BUFFER_SIZE = 8192
        private const val SPEED_CALC_INTERVAL_MS = 500L

        private val DUPLICATE_LINK_REGEX =
            "(?i)<(.+)>\\s*;\\s*rel=duplicate(?:.*pri=([0-9]+).*|.*)?".toRegex()

        private fun isSuccessCode(statusCode: Int) = statusCode / 100 == 2

        private fun isRedirectCode(statusCode: Int) = statusCode / 100 == 3
    }
}
