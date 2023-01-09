/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.model

import java.io.File

interface UpdateInfo : UpdateBaseInfo {
    val status: UpdateStatus
    val persistentStatus: Int
    val file: File?
    val progress: Int
    val eta: Long
    val speed: Long
    val installProgress: Int
    val availableOnline: Boolean
    val finalizing: Boolean
}
