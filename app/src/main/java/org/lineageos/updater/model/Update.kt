/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.model

import java.io.File

class Update : UpdateBase, UpdateInfo {
    override var status = UpdateStatus.UNKNOWN
    override var persistentStatus = UpdateStatus.Persistent.UNKNOWN
    override var file: File? = null
    override var progress = 0
    override var eta: Long = 0
    override var speed: Long = 0
    override var installProgress = 0
    override var availableOnline = false
    override var finalizing = false

    constructor()
    constructor(update: UpdateInfo) : super(update) {
        status = update.status
        persistentStatus = update.persistentStatus
        file = update.file
        progress = update.progress
        eta = update.eta
        speed = update.speed
        installProgress = update.installProgress
        availableOnline = update.availableOnline
        finalizing = update.finalizing
    }
}
