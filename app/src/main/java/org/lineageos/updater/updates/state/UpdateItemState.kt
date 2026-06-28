/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates.state

import androidx.annotation.StringRes
import org.lineageos.updater.updates.action.UpdateActions

data class UpdateItemState(
    val downloadId: String,
    val isLocal: Boolean,

    val buildDate: String,
    val buildVersion: String,
    val status: String,

    val fileSize: String,
    val androidUpdateInfo: String,
    val securityUpdate: String,
    @param:StringRes val installNote: Int,

    val progress: ProgressState?,
    val actions: UpdateActions,
)

sealed interface ProgressState {
    data class Determinate(
        val percent: Float,
        val downloadedSize: String,
        val eta: String,
    ) : ProgressState

    data object Indeterminate : ProgressState
}
