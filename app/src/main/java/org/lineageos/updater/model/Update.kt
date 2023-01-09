/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
