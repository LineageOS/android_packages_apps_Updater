/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates.state

import org.lineageos.updater.updates.action.UpdateActions

data class UpdateItemState(
    val downloadId: String,
    val isLocal: Boolean,

    val buildDate: String,
    val buildVersion: String,
    val status: String,

    val fileSize: String,

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
