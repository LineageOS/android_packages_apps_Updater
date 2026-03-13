/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.data.source.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lineageos.updater.data.Update

@Serializable
data class NetworkUpdate(
    @SerialName("datetime") val timestamp: Long,
    @SerialName("filename") val name: String,
    @SerialName("id") val downloadId: String,
    @SerialName("romtype") val type: String,
    @SerialName("size") val fileSize: Long,
    @SerialName("url") val downloadUrl: String,
    @SerialName("version") val version: String,
)

@Serializable
data class NetworkUpdateResponse(
    @SerialName("response") val updates: List<NetworkUpdate>,
)

fun NetworkUpdate.toUpdate(): Update {
    return Update(
        downloadId = downloadId,
        name = name,
        timestamp = timestamp,
        type = type,
        fileSize = fileSize,
        downloadUrl = downloadUrl,
        version = version,
        isAvailableOnline = true,
    )
}
