/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.data.source.network

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import org.lineageos.updater.R
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import java.io.Closeable
import java.io.IOException

class UpdatesNetworkDataSource(private val context: Context) : Closeable {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val SOCKET_TIMEOUT_MS = 15_000L

        // At ~332 bytes per entry (measured against the live API), 4 KB comfortably fits
        // ~12 entries. The server currently returns 4. A response this large arriving from
        // a metadata-only endpoint is already suspicious and warrants a hard stop.
        private const val MAX_BODY_SIZE_BYTES = 4 * 1024
    }

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

    private val client = HttpClient(Android) {
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    suspend fun fetchUpdates(): List<NetworkUpdate> {
        val response = client.get(serverUrl)

        if (!response.status.isSuccess()) {
            throw IOException("Unexpected HTTP status: ${response.status}")
        }

        val contentType = response.contentType()
        if (contentType == null || !contentType.match(ContentType.Application.Json)) {
            throw IOException("Unexpected content type: $contentType")
        }

        val bytes = response.bodyAsChannel()
            .readRemaining(MAX_BODY_SIZE_BYTES.toLong() + 1)
            .readByteArray()
        if (bytes.size > MAX_BODY_SIZE_BYTES) {
            throw IOException("Response body exceeds $MAX_BODY_SIZE_BYTES bytes")
        }

        val updates = Json.decodeFromString<NetworkUpdateResponse>(
            bytes.decodeToString()
        ).updates

        return updates
    }

    override fun close() = client.close()
}
