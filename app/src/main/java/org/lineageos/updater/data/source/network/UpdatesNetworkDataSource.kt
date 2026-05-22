/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.data.source.network

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lineageos.updater.R
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdatesNetworkDataSource(private val context: Context) {
    private val serverUrl: String
        get() {
            val base = DeviceInfoUtils.updaterUri.trim().ifEmpty {
                context.getString(R.string.updater_server_url)
            }
            require(base.startsWith("https://")) {
                "Update server URL must use HTTPS: $base"
            }
            return base
                .replace("{device}", DeviceInfoUtils.device)
                .replace("{type}", DeviceInfoUtils.releaseType.lowercase())
                .replace("{incr}", DeviceInfoUtils.buildVersionIncremental)
        }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun fetchUpdates(): List<NetworkUpdate> {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP status: ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response body")

            val contentType = body.contentType()
            if (contentType == null ||
                !((contentType.type == "application" && contentType.subtype == "json") ||
                        (contentType.type == "text" && contentType.subtype == "plain"))
            ) {
                throw IOException("Unexpected content type: $contentType")
            }

            val source = body.source()
            if (source.request(MAX_BODY_SIZE_BYTES + 1)) {
                throw IOException("Response body exceeds $MAX_BODY_SIZE_BYTES bytes")
            }
            source.buffer.readByteArray()
        }

        return Json.decodeFromString<List<NetworkUpdate>>(
            bytes.decodeToString()
        )
    }

    companion object {
        // At ~2661 bytes per entry (measured against the live API), 32 KB comfortably fits
        // ~12 entries. The server currently returns 4. A response this large arriving from
        // a metadata-only endpoint is already suspicious and warrants a hard stop.
        private const val MAX_BODY_SIZE_BYTES = 32 * 1024L
    }
}
