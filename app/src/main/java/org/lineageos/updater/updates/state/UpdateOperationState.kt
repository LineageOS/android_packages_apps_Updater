/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates.state

import androidx.annotation.StringRes
import org.lineageos.updater.R
import org.lineageos.updater.controller.UpdaterController
import org.lineageos.updater.data.Update
import org.lineageos.updater.data.UpdateStatus
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import org.lineageos.updater.util.InstallUtils

enum class UpdateOperationPhase(
    @param:StringRes val titleRes: Int? = null,
) {
    IDLE,
    DOWNLOADING(R.string.downloading_notification),
    DOWNLOAD_PAUSED(R.string.download_paused_notification),
    DOWNLOAD_ERROR(R.string.download_paused_error_notification),
    VERIFYING(R.string.list_verifying_update),
    VERIFICATION_FAILED(R.string.verification_failed_notification),
    VERIFIED(R.string.download_completed_notification),
    INSTALLING_RECOVERY(R.string.dialog_prepare_zip_message),
    INSTALLING(R.string.preparing_ota_first_boot),
    FINALIZING(R.string.finalizing_package),
    INSTALLATION_SUSPENDED(R.string.installation_suspended_notification),
    INSTALLATION_FAILED(R.string.update_failed_notification),
    WAITING_FOR_REBOOT(R.string.installing_update_finished),
}

data class UpdateOperationState(
    val phase: UpdateOperationPhase,
    val isBusy: Boolean,
    val isFullyDownloaded: Boolean,
    val canInstall: Boolean,
    val canExport: Boolean,
    val canDelete: Boolean,
) {
    val isDownloading: Boolean
        get() = phase == UpdateOperationPhase.DOWNLOADING

    val isDownloadPaused: Boolean
        get() = phase == UpdateOperationPhase.DOWNLOAD_PAUSED ||
                phase == UpdateOperationPhase.DOWNLOAD_ERROR

    val isVerifying: Boolean
        get() = phase == UpdateOperationPhase.VERIFYING

    val isInstalling: Boolean
        get() = when (phase) {
            UpdateOperationPhase.INSTALLING_RECOVERY,
            UpdateOperationPhase.INSTALLING,
            UpdateOperationPhase.FINALIZING,
            UpdateOperationPhase.INSTALLATION_SUSPENDED -> true

            else -> false
        }

    val isFinalizing: Boolean
        get() = phase == UpdateOperationPhase.FINALIZING

    @get:StringRes
    val titleRes: Int?
        get() = phase.titleRes

    companion object {
        fun from(controller: UpdaterController, update: Update): UpdateOperationState {
            val downloadId = update.downloadId
            val status = update.status
            val phase = when {
                controller.isDownloading(downloadId) -> UpdateOperationPhase.DOWNLOADING
                status == UpdateStatus.PAUSED -> UpdateOperationPhase.DOWNLOAD_PAUSED
                status == UpdateStatus.PAUSED_ERROR -> UpdateOperationPhase.DOWNLOAD_ERROR

                controller.isVerifyingUpdate(downloadId) -> UpdateOperationPhase.VERIFYING
                status == UpdateStatus.VERIFICATION_FAILED -> UpdateOperationPhase.VERIFICATION_FAILED
                status == UpdateStatus.VERIFIED -> UpdateOperationPhase.VERIFIED

                controller.isWaitingForReboot(downloadId) -> UpdateOperationPhase.WAITING_FOR_REBOOT

                controller.isInstallingUpdate(downloadId) ->
                    when {
                        !DeviceInfoUtils.isABDevice -> UpdateOperationPhase.INSTALLING_RECOVERY
                        status == UpdateStatus.INSTALLATION_SUSPENDED -> UpdateOperationPhase.INSTALLATION_SUSPENDED
                        update.isFinalizing -> UpdateOperationPhase.FINALIZING
                        else -> UpdateOperationPhase.INSTALLING
                    }

                status == UpdateStatus.INSTALLATION_SUSPENDED -> UpdateOperationPhase.INSTALLATION_SUSPENDED
                status == UpdateStatus.INSTALLATION_FAILED -> UpdateOperationPhase.INSTALLATION_FAILED

                else -> UpdateOperationPhase.IDLE
            }
            val canDelete = phase == UpdateOperationPhase.VERIFIED ||
                    phase == UpdateOperationPhase.VERIFICATION_FAILED
            val isLocal = downloadId == Update.LOCAL_ID
            val isFullyDownloaded = controller.isFullyDownloaded(update)

            return UpdateOperationState(
                phase = phase,
                isBusy = controller.isBusy,
                isFullyDownloaded = isLocal || isFullyDownloaded,
                canInstall = InstallUtils.canInstall(update) || isLocal,
                canExport = phase == UpdateOperationPhase.VERIFIED && !isLocal,
                canDelete = canDelete,

                )
        }
    }
}
