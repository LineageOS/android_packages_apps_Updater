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
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
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

            val contentType = response.body?.contentType()
            if (contentType == null ||
                contentType.type != "application" ||
                contentType.subtype != "json"
            ) {
                throw IOException("Unexpected content type: $contentType")
            }

            val body = response.body ?: throw IOException("Empty response body")
            body.bytes()
        }

        return Json.decodeFromString<NetworkUpdateResponse>(
            bytes.decodeToString()
        ).updates
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val SOCKET_TIMEOUT_MS = 15_000L
    }
}
