/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.data

import java.io.File

data class Update(
    val isAvailableOnline: Boolean = false,
    val downloadId: String = LOCAL_ID,
    val downloadUrl: String? = null,
    val eta: Long = 0,
    val file: File? = null,
    val fileSize: Long = 0,
    val isFinalizing: Boolean = false,
    val installProgress: Int = 0,
    val name: String? = null,
    val progress: Int = 0,
    val speed: Long = 0,
    val status: UpdateStatus = UpdateStatus.UNKNOWN,
    val timestamp: Long = 0,
    val type: String? = null,
    val version: String? = null,
) {
    fun withAvailableOnline(v: Boolean) = copy(isAvailableOnline = v)
    fun withDownloadId(v: String) = copy(downloadId = v)
    fun withDownloadUrl(v: String?) = copy(downloadUrl = v)
    fun withEta(v: Long) = copy(eta = v)
    fun withFile(v: File?) = copy(file = v)
    fun withFileSize(v: Long) = copy(fileSize = v)
    fun withFinalizing(v: Boolean) = copy(isFinalizing = v)
    fun withInstallProgress(v: Int) = copy(installProgress = v)
    fun withName(v: String?) = copy(name = v)
    fun withProgress(v: Int) = copy(progress = v)
    fun withSpeed(v: Long) = copy(speed = v)
    fun withStatus(v: UpdateStatus) = copy(status = v)
    fun withTimestamp(v: Long) = copy(timestamp = v)
    fun withType(v: String?) = copy(type = v)
    fun withVersion(v: String?) = copy(version = v)

    fun toBuilder() = Builder(this)

    class Builder(
        private var isAvailableOnline: Boolean = false,
        private var downloadId: String = LOCAL_ID,
        private var downloadUrl: String? = null,
        private var eta: Long = 0,
        private var file: File? = null,
        private var fileSize: Long = 0,
        private var isFinalizing: Boolean = false,
        private var installProgress: Int = 0,
        private var name: String? = null,
        private var progress: Int = 0,
        private var speed: Long = 0,
        private var status: UpdateStatus = UpdateStatus.UNKNOWN,
        private var timestamp: Long = 0,
        private var type: String? = null,
        private var version: String? = null,
    ) {
        constructor(update: Update) : this(
            update.isAvailableOnline, update.downloadId, update.downloadUrl,
            update.eta, update.file, update.fileSize, update.isFinalizing,
            update.installProgress, update.name, update.progress, update.speed,
            update.status, update.timestamp, update.type, update.version,
        )

        fun setAvailableOnline(v: Boolean) = apply { isAvailableOnline = v }
        fun setDownloadId(v: String) = apply { downloadId = v }
        fun setDownloadUrl(v: String?) = apply { downloadUrl = v }
        fun setEta(v: Long) = apply { eta = v }
        fun setFile(v: File?) = apply { file = v }
        fun setFileSize(v: Long) = apply { fileSize = v }
        fun setFinalizing(v: Boolean) = apply { isFinalizing = v }
        fun setInstallProgress(v: Int) = apply { installProgress = v }
        fun setName(v: String?) = apply { name = v }
        fun setProgress(v: Int) = apply { progress = v }
        fun setSpeed(v: Long) = apply { speed = v }
        fun setStatus(v: UpdateStatus) = apply { status = v }
        fun setTimestamp(v: Long) = apply { timestamp = v }
        fun setType(v: String?) = apply { type = v }
        fun setVersion(v: String?) = apply { version = v }
        fun build() = Update(
            isAvailableOnline, downloadId, downloadUrl, eta, file, fileSize,
            isFinalizing, installProgress, name, progress, speed, status,
            timestamp, type, version,
        )
    }

    companion object {
        const val LOCAL_ID = "local"
    }
}
