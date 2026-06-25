/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalSerializationApi::class)

package org.lineageos.updater.data.source.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.lineageos.updater.data.Update
import org.lineageos.updater.misc.Constants

// Commented out fields below are available in production, but not needed in runtime.
// If you wish to uncomment any of them, please update the README.md to indicate that.

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdate(
    // @SerialName("date") val date: String,
    @SerialName("datetime") val datetime: Long,
    @SerialName("files") val files: List<NetworkUpdateFile>,
    @SerialName("type") val type: String,
    @SerialName("version") val version: String,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdateFile(
    // @SerialName("date") val date: String? = null,
    // @SerialName("datetime") val datetime: Long? = null,
    @SerialName("filename") val filename: String,
    // @SerialName("filepath") val filepath: String,
    @SerialName("os_patch_level") val osPatchLevel: String? = null,
    @SerialName("os_sdk_level") val osSdkLevel: Int? = null,
    @SerialName("ota_property_files") val otaPropertyFiles: String? = null,
    // @SerialName("sha1") val sha1: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("size") val size: Long,
    // @SerialName("type") val type: String? = null,
    @SerialName("url") val url: String,
)

private data class PackageFileRange(
    val offset: Long,
    val size: Long,
)

private fun String.parsePackageFileRanges() =
    split(",").associate { token ->
        val parts = token.trim().split(":")
        parts[0].trim() to PackageFileRange(
            offset = parts[1].trim().toLong(),
            size = parts[2].trim().toLong(),
        )
    }

fun NetworkUpdate.toUpdate(): Update {
    val file = files[0]
    val packageFileRanges = file.otaPropertyFiles?.parsePackageFileRanges().orEmpty()
    val payloadMetadataRange = packageFileRanges[Constants.AB_PAYLOAD_METADATA_PATH]
    val payloadRange = packageFileRanges[Constants.AB_PAYLOAD_BIN_PATH]
    val payloadPropertiesRange = packageFileRanges[Constants.AB_PAYLOAD_PROPERTIES_PATH]

    return Update(
        downloadId = file.sha256,
        name = file.filename,
        timestamp = datetime,
        type = type,
        fileSize = file.size,
        downloadUrl = file.url,
        version = version,
        osPatchLevel = file.osPatchLevel,
        osSdkLevel = file.osSdkLevel,
        payloadMetadataOffset = payloadMetadataRange?.offset,
        payloadMetadataSize = payloadMetadataRange?.size,
        payloadOffset = payloadRange?.offset,
        payloadSize = payloadRange?.size,
        payloadPropertiesOffset = payloadPropertiesRange?.offset,
        payloadPropertiesSize = payloadPropertiesRange?.size,
        isAvailableOnline = true,
    )
}
