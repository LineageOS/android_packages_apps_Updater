/*
 * Copyright (C) 2017-2022 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    url: String?, destination: File,
    progressListener: DownloadClient.ProgressListener?,
    callback: DownloadCallback?,
    useDuplicateLinks: Boolean
) : DownloadClient {
    private var mClient: HttpURLConnection
    private val mDestination: File
    private val mProgressListener: DownloadClient.ProgressListener?
    private val mCallback: DownloadCallback?
    private val mUseDuplicateLinks: Boolean
    private var mDownloadThread: DownloadThread? = null

    inner class Headers : DownloadClient.Headers {
        override fun get(name: String?): String? {
            return mClient.getHeaderField(name)
        }
    }

    init {
        mClient = URL(url).openConnection() as HttpURLConnection
        mDestination = destination
        mProgressListener = progressListener
        mCallback = callback
        mUseDuplicateLinks = useDuplicateLinks
    }

    override fun start() {
        if (mDownloadThread != null) {
            Log.e(TAG, "Already downloading")
            return
        }
        downloadFileInternalCommon(false)
    }

    override fun resume() {
        if (mDownloadThread != null) {
            Log.e(TAG, "Already downloading")
            return
        }
        downloadFileResumeInternal()
    }

    override fun cancel() {
        if (mDownloadThread == null) {
            Log.e(TAG, "Not downloading")
            return
        }
        mDownloadThread!!.interrupt()
        mDownloadThread = null
    }

    private fun downloadFileResumeInternal() {
        if (!mDestination.exists()) {
            mCallback?.onFailure(false)
            return
        }
        val offset = mDestination.length()
        mClient.setRequestProperty("Range", "bytes=$offset-")
        downloadFileInternalCommon(true)
    }

    private fun downloadFileInternalCommon(resume: Boolean) {
        if (mDownloadThread != null) {
            Log.wtf(TAG, "Already downloading")
            return
        }
        mDownloadThread = DownloadThread(resume)
        mDownloadThread!!.start()
    }

    private inner class DownloadThread(private val mResume: Boolean) : Thread() {
        private var mTotalBytes: Long = 0
        private var mTotalBytesRead: Long = 0
        private var mCurSampleBytes: Long = 0
        private var mLastMillis: Long = 0
        private var mSpeed: Long = -1
        private var mEta: Long = -1
        private fun calculateSpeed(justResumed: Boolean) {
            val millis = SystemClock.elapsedRealtime()
            if (justResumed) {
                // If we don't start over with these after resumption, we get huge numbers for
                // ETA since the delta will grow, resulting in a very low speed
                mLastMillis = millis
                mSpeed = -1 // we don't want the moving avg with values from who knows when

                // need to do this as well, otherwise the second time we call calculateSpeed(),
                // the difference (mTotalBytesRead - mCurSampleBytes) will be larger than expected,
                // resulting in a higher speed calculation
                mCurSampleBytes = mTotalBytesRead
                return
            }
            val delta = millis - mLastMillis
            if (delta > 500) {
                val curSpeed = (mTotalBytesRead - mCurSampleBytes) * 1000 / delta
                mSpeed = if (mSpeed == -1L) {
                    curSpeed
                } else {
                    (mSpeed * 3 + curSpeed) / 4
                }
                mLastMillis = millis
                mCurSampleBytes = mTotalBytesRead
            }
        }

        private fun calculateEta() {
            if (mSpeed > 0) {
                mEta = (mTotalBytes - mTotalBytesRead) / mSpeed
            }
        }

        @Throws(IOException::class)
        private fun changeClientUrl(newUrl: URL) {
            val range = mClient.getRequestProperty("Range")
            mClient.disconnect()
            mClient = newUrl.openConnection() as HttpURLConnection
            if (range != null) {
                mClient.setRequestProperty("Range", range)
            }
        }

        @Throws(IOException::class)
        private fun handleDuplicateLinks() {
            val protocol = mClient.url.protocol

            class DuplicateLink(val mUrl: String, val mPriority: Int)

            var duplicates: PriorityQueue<DuplicateLink>? = null
            for ((key, value) in mClient.headerFields) {
                if ("Link".equals(key, ignoreCase = true)) {
                    duplicates = PriorityQueue(value.size,
                        Comparator.comparingInt { d: DuplicateLink -> d.mPriority })

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
            var newUrl = mClient.getHeaderField("Location")
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
                    mClient.connectTimeout = 5000
                    mClient.connect()
                    if (!isSuccessCode(mClient.responseCode)) {
                        throw IOException("Server replied with " + mClient.responseCode)
                    }
                    return
                } catch (e: IOException) {
                    if (duplicates != null && !duplicates.isEmpty()) {
                        val link = duplicates.poll()
                        if (link != null) {
                            duplicates.remove(link)
                            newUrl = link.mUrl
                            Log.e(TAG, "Using duplicate link " + link.mUrl, e)
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
                mClient.instanceFollowRedirects = !mUseDuplicateLinks
                mClient.connect()
                var responseCode = mClient.responseCode
                if (mUseDuplicateLinks && isRedirectCode(responseCode)) {
                    handleDuplicateLinks()
                    responseCode = mClient.responseCode
                }
                mCallback?.onResponse(Headers())
                if (mResume && isPartialContentCode(responseCode)) {
                    justResumed = true
                    mTotalBytesRead = mDestination.length()
                    Log.d(TAG, "The server fulfilled the partial content request")
                } else if (mResume || !isSuccessCode(responseCode)) {
                    Log.e(TAG, "The server replied with code $responseCode")
                    mCallback?.onFailure(isInterrupted)
                    return
                }
                mClient.inputStream.use { inputStream ->
                    FileOutputStream(mDestination, mResume).use { outputStream ->
                        mTotalBytes = mClient.contentLength + mTotalBytesRead
                        val b = ByteArray(8192)
                        var count = 0
                        while (!isInterrupted && inputStream.read(b).also { count = it } > 0) {
                            outputStream.write(b, 0, count)
                            mTotalBytesRead += count.toLong()
                            calculateSpeed(justResumed)
                            calculateEta()
                            justResumed = false // otherwise we will never get speed and ETA again
                            mProgressListener?.update(mTotalBytesRead, mTotalBytes, mSpeed, mEta)
                        }
                        mProgressListener?.update(mTotalBytesRead, mTotalBytes, mSpeed, mEta)
                        outputStream.flush()
                        if (isInterrupted) {
                            mCallback?.onFailure(true)
                        } else {
                            mCallback?.onSuccess()
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error downloading file", e)
                mCallback?.onFailure(isInterrupted)
            } finally {
                mClient.disconnect()
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
