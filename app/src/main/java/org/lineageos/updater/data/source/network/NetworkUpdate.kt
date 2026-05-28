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

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdate(
    // @SerialName("date") val date: String,
    @SerialName("datetime") val datetime: Long,
    @SerialName("files") val files: List<NetworkUpdateFile>,
    @SerialName("os_patch_level") val osPatchLevel: String? = null,
    @SerialName("os_sdk_level") val osSdkLevel: Int? = null,
    // @SerialName("ota_property_files") val otaPropertyFiles: String? = null,
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
    // @SerialName("ota_property_files") val otaPropertyFiles: String? = null,
    // @SerialName("sha1") val sha1: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("size") val size: Long,
    // @SerialName("type") val type: String? = null,
    @SerialName("url") val url: String,
)

fun NetworkUpdate.toUpdate(): Update {
    return Update(
        downloadId = files[0].sha256,
        name = files[0].filename,
        timestamp = datetime,
        type = type,
        fileSize = files[0].size,
        downloadUrl = files[0].url,
        version = version,
        osPatchLevel = osPatchLevel ?: files[0].osPatchLevel,
        osSdkLevel = osSdkLevel ?: files[0].osSdkLevel,
        isAvailableOnline = true,
    )
}
