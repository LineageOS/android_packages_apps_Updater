/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.download

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

class SingleRangeHttpFetcher(private val url: String) {

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun download(offset: Long, size: Long): ByteArray = try {
        val endOffset = offset + size - 1
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$offset-$endOffset")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException(
                    "Server does not support range requests: expected HTTP 206, " +
                            "got ${response.code} for $url",
                )
            }

            val contentRange = response.header("Content-Range")
                ?: throw IOException("Missing Content-Range header for $url")
            val expectedContentRangePrefix = "bytes $offset-$endOffset/"
            if (!contentRange.startsWith(expectedContentRangePrefix)) {
                throw IOException(
                    "Unexpected Content-Range for $url: expected " +
                            "$expectedContentRangePrefix*, got $contentRange",
                )
            }

            response.body!!.source().readByteArray(size)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to download range offset=$offset size=$size", e)
        throw e
    }

    companion object {
        private const val TAG = "SingleRangeHttpFetcher"
    }
}
