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
        private var mUrl: String? = null
        private var mDestination: File? = null
        private var mCallback: DownloadCallback? = null
        private var mProgressListener: ProgressListener? = null
        private var mUseDuplicateLinks = false

        @Throws(IOException::class)
        fun build(): DownloadClient {
            checkNotNull(mUrl) { "No download URL defined" }
            checkNotNull(mDestination) { "No download destination defined" }
            return HttpURLConnectionClient(
                mUrl, mDestination!!, mProgressListener, mCallback,
                mUseDuplicateLinks
            )
        }

        fun setUrl(url: String?): Builder {
            mUrl = url
            return this
        }

        fun setDestination(destination: File?): Builder {
            mDestination = destination
            return this
        }

        fun setDownloadCallback(downloadCallback: DownloadCallback?): Builder {
            mCallback = downloadCallback
            return this
        }

        fun setProgressListener(progressListener: ProgressListener?): Builder {
            mProgressListener = progressListener
            return this
        }

        fun setUseDuplicateLinks(useDuplicateLinks: Boolean): Builder {
            mUseDuplicateLinks = useDuplicateLinks
            return this
        }
    }
}
