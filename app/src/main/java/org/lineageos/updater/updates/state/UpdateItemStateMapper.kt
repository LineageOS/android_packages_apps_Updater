/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates.state

import android.content.Context
import android.text.format.Formatter
import org.lineageos.updater.R
import org.lineageos.updater.controller.UpdaterController
import org.lineageos.updater.data.Update
import org.lineageos.updater.data.UpdateStatus
import org.lineageos.updater.updates.action.UpdateAction
import org.lineageos.updater.updates.action.UpdateActionType
import org.lineageos.updater.updates.action.UpdateActions
import org.lineageos.updater.util.NetworkMonitor.NetworkState
import org.lineageos.updater.util.StringUtil
import java.time.format.FormatStyle

class UpdateItemStateMapper(
    private val context: Context,
    private val updaterController: UpdaterController,
) {

    fun map(update: Update, networkState: NetworkState): UpdateItemState {
        val state = UpdateOperationState.from(updaterController, update)

        val progress = when {
            state.isDownloading -> {
                if (update.status == UpdateStatus.STARTING) {
                    ProgressState.Indeterminate
                } else {
                    ProgressState.Determinate(
                        percent = update.progress.toFloat(),
                        downloadedSize = downloadedSize(update),
                        eta = update.eta.takeIf { it > 0 }
                            ?.let { StringUtil.formatETA(context, it * 1000).toString() }
                            ?: "",
                    )
                }
            }

            state.isDownloadPaused -> ProgressState.Determinate(
                percent = update.progress.toFloat(),
                downloadedSize = downloadedSize(update),
                eta = "",
            )

            state.isVerifying || state.isFinalizing -> ProgressState.Indeterminate

            state.isInstalling -> ProgressState.Determinate(
                percent = update.installProgress.toFloat(),
                downloadedSize = "",
                eta = "",
            )

            else -> null
        }

        val buttonActions = when (state.phase) {
            UpdateOperationPhase.DOWNLOADING -> ActionButtons(
                primary = action(UpdateActionType.PAUSE_DOWNLOAD),
                secondary = action(UpdateActionType.CANCEL_DOWNLOAD),
            )

            UpdateOperationPhase.DOWNLOAD_PAUSED,
            UpdateOperationPhase.DOWNLOAD_ERROR -> ActionButtons(
                primary = action(
                    type = UpdateActionType.RESUME_DOWNLOAD,
                    enabled = networkState.isOnline,
                ),
                secondary = action(UpdateActionType.CANCEL_DOWNLOAD),
            )

            UpdateOperationPhase.VERIFYING -> ActionButtons(
                primary = action(
                    type = UpdateActionType.START_INSTALL,
                    enabled = false,
                ),
            )

            UpdateOperationPhase.VERIFIED -> ActionButtons(
                primary = when {
                    state.canInstall -> action(
                        type = UpdateActionType.START_INSTALL,
                        enabled = !state.isBusy,
                    )

                    state.canDelete -> action(
                        type = UpdateActionType.DELETE,
                        enabled = !state.isBusy,
                    )

                    else -> action(
                        type = UpdateActionType.SHOW_INFO,
                        enabled = !state.isBusy,
                    )
                },
            )

            UpdateOperationPhase.INSTALLING_RECOVERY -> ActionButtons(
                primary = action(UpdateActionType.CANCEL_INSTALL),
            )

            UpdateOperationPhase.INSTALLING,
            UpdateOperationPhase.FINALIZING -> ActionButtons(
                primary = action(UpdateActionType.PAUSE_INSTALL),
                secondary = action(UpdateActionType.CANCEL_INSTALL),
            )

            UpdateOperationPhase.INSTALLATION_SUSPENDED -> ActionButtons(
                primary = action(UpdateActionType.RESUME_INSTALL),
                secondary = action(UpdateActionType.CANCEL_INSTALL),
            )

            UpdateOperationPhase.WAITING_FOR_REBOOT -> ActionButtons(
                primary = action(UpdateActionType.REBOOT),
            )

            else -> ActionButtons(
                primary = if (!state.canInstall) {
                    action(
                        type = UpdateActionType.SHOW_INFO,
                        enabled = !state.isBusy,
                    )
                } else {
                    action(
                        type = UpdateActionType.START_DOWNLOAD,
                        enabled = networkState.isOnline && update.downloadUrl != null,
                    )
                },
            )
        }
        val actions = UpdateActions(
            primary = buttonActions.primary,
            secondary = buttonActions.secondary,
            overflow = buildList {
                if (state.canExport) {
                    add(action(UpdateActionType.EXPORT, enabled = !state.isBusy))
                }
                if (state.canDelete) {
                    add(action(type = UpdateActionType.DELETE, enabled = !state.isBusy))
                }
                if (update.downloadUrl != null) {
                    add(action(UpdateActionType.VIEW_DOWNLOADS))
                }
            },
        )

        return UpdateItemState(
            downloadId = update.downloadId,
            isLocal = state.isFullyDownloaded,
            buildDate = StringUtil.getDateLocalizedUTC(
                context,
                FormatStyle.LONG,
                update.timestamp,
            ),
            buildVersion = context.getString(
                R.string.list_build_version,
                update.version,
            ),
            status = state.titleRes?.let { context.getString(it) } ?: "",
            fileSize = Formatter.formatShortFileSize(context, update.fileSize),
            progress = progress,
            actions = actions,
        )
    }

    private fun downloadedSize(update: Update): String {
        val downloaded = Formatter.formatShortFileSize(context, update.file?.length() ?: 0L)
        val total = Formatter.formatShortFileSize(context, update.fileSize)

        return context.getString(R.string.list_download_progress_newer, downloaded, total)
    }

    private data class ActionButtons(
        val primary: UpdateAction,
        val secondary: UpdateAction? = null,
    )

    private fun action(
        type: UpdateActionType,
        enabled: Boolean = true,
    ) = UpdateAction(
        type = type,
        enabled = enabled,
    )
}
