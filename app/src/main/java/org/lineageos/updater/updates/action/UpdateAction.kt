/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates.action

import android.content.Context
import androidx.annotation.StringRes
import org.lineageos.updater.R

enum class UpdateActionType(@param:StringRes val titleRes: Int) {
    START_DOWNLOAD(R.string.action_download),
    PAUSE_DOWNLOAD(R.string.action_pause),
    RESUME_DOWNLOAD(R.string.action_resume),
    CANCEL_DOWNLOAD(android.R.string.cancel),

    START_INSTALL(R.string.action_install),
    PAUSE_INSTALL(R.string.action_pause),
    RESUME_INSTALL(R.string.action_resume),
    CANCEL_INSTALL(android.R.string.cancel),

    REBOOT(R.string.reboot),
    SHOW_INFO(R.string.action_info),

    DELETE(R.string.menu_delete_update),
    EXPORT(R.string.menu_export_update),
    VIEW_DOWNLOADS(R.string.menu_view_downloads) {
        override fun title(context: Context) =
            context.getString(titleRes, context.getString(R.string.brand_name))
    };

    open fun title(context: Context) = context.getString(titleRes)
}

data class UpdateAction(
    val type: UpdateActionType,
    val enabled: Boolean = true,
)

data class UpdateActions(
    val primary: UpdateAction,
    val secondary: UpdateAction? = null,
    val overflow: List<UpdateAction> = emptyList(),
)
