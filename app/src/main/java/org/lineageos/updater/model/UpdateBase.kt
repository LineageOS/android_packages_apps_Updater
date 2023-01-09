/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.model

open class UpdateBase : UpdateBaseInfo {
    override lateinit var name: String
    override lateinit var downloadId: String
    override var timestamp: Long = 0
    override lateinit var type: String
    override lateinit var version: String
    override lateinit var downloadUrl: String
    override var fileSize: Long = 0

    constructor()
    constructor(update: UpdateBaseInfo) {
        name = update.name
        downloadUrl = update.downloadUrl
        downloadId = update.downloadId
        timestamp = update.timestamp
        type = update.type
        version = update.version
        fileSize = update.fileSize
    }
}
