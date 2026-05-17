/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates.action

import android.app.Activity
import android.content.Intent
import android.os.PowerManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.net.toUri
import org.lineageos.updater.R
import org.lineageos.updater.UpdaterApplication
import org.lineageos.updater.controller.UpdaterController
import org.lineageos.updater.controller.UpdaterService
import org.lineageos.updater.data.Update
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import org.lineageos.updater.misc.Utils
import org.lineageos.updater.util.BatteryMonitor.BatteryState
import org.lineageos.updater.util.InstallUtils
import org.lineageos.updater.util.StringUtil
import java.time.format.FormatStyle

class UpdateActionHandler(
    private val activity: Activity,
    private val updaterController: UpdaterController,
    private val exportUpdate: (Update) -> Unit,
    private val showDialog: (AlertDialogState) -> Unit,
) {

    private val application = activity.application as UpdaterApplication
    private val batteryMonitor = application.batteryMonitor
    private val brandName = activity.getString(R.string.brand_name)
    private val networkMonitor = application.networkMonitor
    private val userPreferencesRepository = application.userPreferencesRepository

    fun perform(action: UpdateAction, update: Update) {
        val downloadId = update.downloadId
        when (action.type) {
            UpdateActionType.START_DOWNLOAD -> runWithActiveDownloadWarning(update) {
                runDownloadWithMeteredWarning {
                    updaterController.startDownload(downloadId)
                }
            }

            UpdateActionType.PAUSE_DOWNLOAD -> updaterController.pauseDownload(downloadId)
            UpdateActionType.RESUME_DOWNLOAD -> runWithActiveDownloadWarning(update) {
                if (updaterController.isFullyDownloaded(update)) {
                    updaterController.resumeDownload(downloadId)
                } else {
                    runDownloadWithMeteredWarning {
                        updaterController.resumeDownload(downloadId)
                    }
                }
            }

            UpdateActionType.CANCEL_DOWNLOAD -> showConfirmDialog(
                title = activity.getString(R.string.confirm_cancel_dialog_title),
                message = activity.getString(R.string.confirm_cancel_dialog_message),
                onConfirm = { updaterController.cancelDownload(downloadId) },
            )

            UpdateActionType.START_INSTALL -> {
                if (update.downloadId != Update.LOCAL_ID && !InstallUtils.canInstall(update)) {
                    return
                }

                if (!batteryMonitor.currentBatteryState.isLevelOk) {
                    showDialog(
                        AlertDialogState(
                            title = activity.getString(R.string.dialog_battery_low_title),
                            text = AnnotatedString(
                                activity.getString(
                                    R.string.dialog_battery_low_message_pct,
                                    BatteryState.MIN_BATT_PCT_DISCHARGING,
                                    BatteryState.MIN_BATT_PCT_CHARGING,
                                )
                            ),
                        )
                    )
                    return
                }

                if (InstallUtils.isScratchMounted()) {
                    showDialog(
                        AlertDialogState(
                            title = activity.getString(R.string.dialog_scratch_mounted_title),
                            text = AnnotatedString(
                                activity.getString(
                                    R.string.dialog_scratch_mounted_message,
                                )
                            ),
                        )
                    )
                    return
                }

                val messageRes = if (DeviceInfoUtils.isABDevice) {
                    R.string.apply_update_dialog_message_ab
                } else {
                    R.string.apply_update_dialog_message
                }
                val buildDate = StringUtil.getDateLocalizedUTC(
                    activity,
                    FormatStyle.MEDIUM,
                    update.timestamp,
                )
                val buildInfoText = activity.getString(
                    R.string.list_build_version_date,
                    brandName,
                    update.version,
                    buildDate,
                )
                showDialog(
                    AlertDialogState(
                        title = activity.getString(R.string.apply_update_dialog_title),
                        text = AnnotatedString(
                            activity.getString(
                                messageRes,
                                buildInfoText,
                                activity.getString(android.R.string.ok),
                            )
                        ),
                        onConfirm = {
                            Utils.triggerUpdate(activity, update.downloadId)
                        },
                        showDismiss = true,
                    )
                )
            }

            UpdateActionType.PAUSE_INSTALL -> startInstallService(
                UpdaterService.ACTION_INSTALL_SUSPEND
            )

            UpdateActionType.RESUME_INSTALL -> startInstallService(
                UpdaterService.ACTION_INSTALL_RESUME
            )

            UpdateActionType.CANCEL_INSTALL -> showConfirmDialog(
                title = activity.getString(R.string.cancel_installation_dialog_title),
                message = activity.getString(R.string.cancel_installation_dialog_message),
                onConfirm = { startInstallService(UpdaterService.ACTION_INSTALL_STOP) },
            )

            UpdateActionType.SHOW_INFO -> {
                val reason = InstallUtils.getBlockedReason(update)
                val title: String
                val message: AnnotatedString

                when (reason) {
                    InstallUtils.BlockedReason.DOWNGRADE -> {
                        title = activity.getString(R.string.blocked_update_dialog_title_downgrade)
                        message =
                            AnnotatedString(activity.getString(R.string.blocked_update_dialog_message_downgrade))
                    }

                    InstallUtils.BlockedReason.VERSION_UNSUPPORTED -> {
                        title = activity.getString(R.string.blocked_update_dialog_title)
                        val url = activity.getString(
                            R.string.blocked_update_info_url,
                            DeviceInfoUtils.device
                        )
                        val messageString = String.format(
                            StringUtil.getCurrentLocale(activity),
                            activity.getString(R.string.blocked_update_dialog_message),
                            url
                        )
                        message = buildAnnotatedString {
                            append(messageString)
                            val urlStart = messageString.indexOf(url)
                            if (urlStart != -1) {
                                val urlEnd = urlStart + url.length
                                addStyle(
                                    style = SpanStyle(
                                        textDecoration = TextDecoration.Underline
                                    ),
                                    start = urlStart,
                                    end = urlEnd
                                )
                                addLink(LinkAnnotation.Url(url), urlStart, urlEnd)
                            }
                        }
                    }

                    InstallUtils.BlockedReason.NONE -> return
                }

                showDialog(
                    AlertDialogState(
                        title = title,
                        text = message,
                    )
                )
            }

            UpdateActionType.DELETE -> showConfirmDialog(
                title = activity.getString(R.string.confirm_delete_dialog_title),
                message = activity.getString(R.string.confirm_delete_dialog_message),
                onConfirm = { updaterController.deleteUpdate(downloadId) },
            )

            UpdateActionType.EXPORT -> exportUpdate(update)
            UpdateActionType.VIEW_DOWNLOADS -> activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    activity.getString(
                        R.string.menu_downloads_url,
                        DeviceInfoUtils.device,
                    ).toUri(),
                )
            )

            UpdateActionType.REBOOT ->
                activity.getSystemService(PowerManager::class.java).reboot(null)
        }
    }

    private fun runWithActiveDownloadWarning(update: Update, downloadAction: () -> Unit) {
        if (!updaterController.hasActiveDownloads() ||
            updaterController.isDownloading(update.downloadId)
        ) {
            downloadAction()
            return
        }

        showConfirmDialog(
            title = activity.getString(R.string.download_switch_confirm_title),
            message = activity.getString(R.string.download_switch_confirm_message),
            onConfirm = downloadAction,
        )
    }

    private fun runDownloadWithMeteredWarning(downloadAction: () -> Unit) {
        val warn = userPreferencesRepository.getMeteredNetworkWarningBlocking()
        if (!(networkMonitor.currentNetworkState.isMetered && warn)) {
            downloadAction()
            return
        }

        showConfirmDialog(
            title = activity.getString(R.string.update_over_metered_network_title),
            message = activity.getString(R.string.update_over_metered_network_message),
            onConfirm = downloadAction,
        )
    }

    private fun startInstallService(intentAction: String) {
        activity.startService(
            Intent(activity, UpdaterService::class.java).setAction(intentAction)
        )
    }

    private fun showConfirmDialog(
        title: String,
        message: CharSequence,
        onConfirm: () -> Unit = {},
    ) {
        showDialog(
            AlertDialogState(
                title = title,
                text = AnnotatedString(message.toString()),
                onConfirm = onConfirm,
                showDismiss = true,
            )
        )
    }
}
