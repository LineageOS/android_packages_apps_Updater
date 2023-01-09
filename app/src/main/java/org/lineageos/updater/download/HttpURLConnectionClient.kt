/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.download

import android.os.SystemClock
import android.util.Log
import org.lineageos.updater.download.DownloadClient.DownloadCallback
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.regex.Pattern

class HttpURLConnectionClient internal constructor(
    url: String?,
    private val destination: File,
    private val progressListener: DownloadClient.ProgressListener?,
    private val callback: DownloadCallback?,
    private val useDuplicateLinks: Boolean
) : DownloadClient {
    private var client = URL(url).openConnection() as HttpURLConnection

    private var downloadThread: DownloadThread? = null

    inner class Headers : DownloadClient.Headers {
        override fun get(name: String?): String? {
            return client.getHeaderField(name)
        }
    }

    override fun start() {
        if (downloadThread != null) {
            Log.e(TAG, "Already downloading")
            return
        }

        downloadFileInternalCommon(false)
    }

    override fun resume() {
        if (downloadThread != null) {
            Log.e(TAG, "Already downloading")
            return
        }

        downloadFileResumeInternal()
    }

    override fun cancel() {
        if (downloadThread == null) {
            Log.e(TAG, "Not downloading")
        }

        downloadThread?.interrupt()
        downloadThread = null
    }

    private fun downloadFileResumeInternal() {
        if (!destination.exists()) {
            callback?.onFailure(false)
            return
        }

        val offset = destination.length()
        client.setRequestProperty("Range", "bytes=$offset-")

        downloadFileInternalCommon(true)
    }

    private fun downloadFileInternalCommon(resume: Boolean) {
        downloadThread?.let {
            Log.wtf(TAG, "Already downloading")
            return
        }

        downloadThread = DownloadThread(resume).also {
            it.start()
        }
    }

    private inner class DownloadThread(private val mResume: Boolean) : Thread() {
        private var totalBytes: Long = 0
        private var totalBytesRead: Long = 0
        private var curSampleBytes: Long = 0
        private var lastMillis: Long = 0
        private var speed: Long = -1
        private var eta: Long = -1

        private fun calculateSpeed(justResumed: Boolean) {
            val millis = SystemClock.elapsedRealtime()
            if (justResumed) {
                // If we don't start over with these after resumption, we get huge numbers for
                // ETA since the delta will grow, resulting in a very low speed
                lastMillis = millis
                speed = -1 // we don't want the moving avg with values from who knows when

                // need to do this as well, otherwise the second time we call calculateSpeed(),
                // the difference (mTotalBytesRead - mCurSampleBytes) will be larger than expected,
                // resulting in a higher speed calculation
                curSampleBytes = totalBytesRead
                return
            }

            val delta = millis - lastMillis
            if (delta > 500) {
                val curSpeed = (totalBytesRead - curSampleBytes) * 1000 / delta
                speed = if (speed == -1L) {
                    curSpeed
                } else {
                    (speed * 3 + curSpeed) / 4
                }
                lastMillis = millis
                curSampleBytes = totalBytesRead
            }
        }

        private fun calculateEta() {
            if (speed > 0) {
                eta = (totalBytes - totalBytesRead) / speed
            }
        }

        @Throws(IOException::class)
        private fun changeClientUrl(newUrl: URL) {
            val range = client.getRequestProperty("Range")

            client.disconnect()

            client = newUrl.openConnection() as HttpURLConnection
            if (range != null) {
                client.setRequestProperty("Range", range)
            }
        }

        @Throws(IOException::class)
        private fun handleDuplicateLinks() {
            val protocol = client.url.protocol

            class DuplicateLink(val url: String, val priority: Int)

            var duplicates: PriorityQueue<DuplicateLink>? = null
            for ((key, value) in client.headerFields) {
                if ("Link".equals(key, ignoreCase = true)) {
                    duplicates = PriorityQueue(value.size,
                        Comparator.comparingInt { d: DuplicateLink -> d.priority })

                    // https://tools.ietf.org/html/rfc6249
                    // https://tools.ietf.org/html/rfc5988#section-5
                    val regex = "(?i)<(.+)>\\s*;\\s*rel=duplicate(?:.*pri=([0-9]+).*|.*)?"
                    val pattern = Pattern.compile(regex)
                    for (field in value) {
                        val matcher = pattern.matcher(field)
                        if (matcher.matches()) {
                            val url = matcher.group(1)!!
                            val pri = matcher.group(2)
                            val priority = pri?.toInt() ?: 999999
                            duplicates.add(DuplicateLink(url, priority))
                            Log.d(TAG, "Adding duplicate link $url")
                        } else {
                            Log.d(TAG, "Ignoring link $field")
                        }
                    }
                }
            }

            var newUrl = client.getHeaderField("Location")
            while (true) {
                try {
                    val url = URL(newUrl)
                    if (url.protocol != protocol) {
                        // If we hadn't handled duplicate links, we wouldn't have
                        // used this url.
                        throw IOException("Protocol changes are not allowed")
                    }
                    Log.d(TAG, "Downloading from $newUrl")
                    changeClientUrl(url)
                    client.connectTimeout = 5000
                    client.connect()
                    if (!isSuccessCode(client.responseCode)) {
                        throw IOException("Server replied with " + client.responseCode)
                    }
                    return
                } catch (e: IOException) {
                    if (duplicates != null && !duplicates.isEmpty()) {
                        val link = duplicates.poll()
                        if (link != null) {
                            duplicates.remove(link)
                            newUrl = link.url
                            Log.e(TAG, "Using duplicate link " + link.url, e)
                        }
                    } else {
                        throw e
                    }
                }
            }
        }

        override fun run() {
            var justResumed = false

            try {
                client.instanceFollowRedirects = !useDuplicateLinks
                client.connect()
                var responseCode = client.responseCode
                if (useDuplicateLinks && isRedirectCode(responseCode)) {
                    handleDuplicateLinks()
                    responseCode = client.responseCode
                }
                callback?.onResponse(Headers())
                if (mResume && isPartialContentCode(responseCode)) {
                    justResumed = true
                    totalBytesRead = destination.length()
                    Log.d(TAG, "The server fulfilled the partial content request")
                } else if (mResume || !isSuccessCode(responseCode)) {
                    Log.e(TAG, "The server replied with code $responseCode")
                    callback?.onFailure(isInterrupted)
                    return
                }
                client.inputStream.use { inputStream ->
                    FileOutputStream(destination, mResume).use { outputStream ->
                        totalBytes = client.contentLength + totalBytesRead
                        val b = ByteArray(8192)
                        var count = 0
                        while (!isInterrupted && inputStream.read(b).also { count = it } > 0) {
                            outputStream.write(b, 0, count)
                            totalBytesRead += count.toLong()
                            calculateSpeed(justResumed)
                            calculateEta()
                            justResumed = false // otherwise we will never get speed and ETA again
                            progressListener?.update(totalBytesRead, totalBytes, speed, eta)
                        }
                        progressListener?.update(totalBytesRead, totalBytes, speed, eta)
                        outputStream.flush()
                        if (isInterrupted) {
                            callback?.onFailure(true)
                        } else {
                            callback?.onSuccess()
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error downloading file", e)
                callback?.onFailure(isInterrupted)
            } finally {
                client.disconnect()
            }
        }
    }

    companion object {
        private const val TAG = "HttpURLConnectionClient"
        private fun isSuccessCode(statusCode: Int): Boolean {
            return statusCode / 100 == 2
        }

        private fun isRedirectCode(statusCode: Int): Boolean {
            return statusCode / 100 == 3
        }

        private fun isPartialContentCode(statusCode: Int): Boolean {
            return statusCode == 206
        }
    }
}
