/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.download

import java.io.File
import java.io.IOException

interface DownloadClient {
    interface DownloadCallback {
        fun onResponse(headers: Headers?)
        fun onSuccess()
        fun onFailure(cancelled: Boolean)
    }

    interface ProgressListener {
        fun update(bytesRead: Long, contentLength: Long, speed: Long, eta: Long)
    }

    interface Headers {
        operator fun get(name: String?): String?
    }

    /**
     * Start the download. This method has no effect if the download already started.
     */
    fun start()

    /**
     * Resume the download. The download will fail if the server can't fulfil the
     * partial content request and DownloadCallback.onFailure() will be called.
     * This method has no effect if the download already started or the destination
     * file doesn't exist.
     */
    fun resume()

    /**
     * Cancel the download. This method has no effect if the download isn't ongoing.
     */
    fun cancel()

    class Builder {
        private var url: String? = null
        private var destination: File? = null
        private var callback: DownloadCallback? = null
        private var progressListener: ProgressListener? = null
        private var useDuplicateLinks = false

        @Throws(IOException::class)
        fun build(): DownloadClient {
            checkNotNull(url) { "No download URL defined" }
            checkNotNull(destination) { "No download destination defined" }
            return HttpURLConnectionClient(
                url, destination!!, progressListener, callback,
                useDuplicateLinks
            )
        }

        fun setUrl(url: String?): Builder {
            this.url = url
            return this
        }

        fun setDestination(destination: File?): Builder {
            this.destination = destination
            return this
        }

        fun setDownloadCallback(downloadCallback: DownloadCallback?): Builder {
            callback = downloadCallback
            return this
        }

        fun setProgressListener(progressListener: ProgressListener?): Builder {
            this.progressListener = progressListener
            return this
        }

        fun setUseDuplicateLinks(useDuplicateLinks: Boolean): Builder {
            this.useDuplicateLinks = useDuplicateLinks
            return this
        }
    }
}
