/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.data

/**
 * Status codes for updates.
 *
 * Some statuses are mapped from [android.os.UpdateEngine.UpdateStatusConstants],
 * while others are specific to this app.
 *
 * Each status has an associated [persistentStatus] value:
 * - 0: Temporary state. Not persisted to the database. On restart, updates in
 *      this state revert to [UNKNOWN].
 * - 1: Persistent (Download/Paused). On restart, restored as [PAUSED].
 * - 2: Persistent (Verified/Installing). On restart, restored as [VERIFIED].
 *
 * Unused UpdateEngine statuses:
 * - IDLE
 * - CHECKING_FOR_UPDATE
 * - FINALIZING
 * - REPORTING_ERROR_EVENT
 * - ATTEMPTING_ROLLBACK
 * - DISABLED
 */
enum class UpdateStatus(val persistentStatus: Int) {
    // Mapped from [android.os.UpdateEngine.UpdateStatusConstants]
    UPDATE_AVAILABLE(0),
    DOWNLOADING(1),
    VERIFYING(1),

    /**
     * Used for both A/B and legacy devices to align with the nomenclature from
     * [android.os.UpdateEngine.UpdateStatusConstants].
     *
     * Note the architectural difference:
     * - A/B devices: The update is applied *before* the reboot (in the background).
     * - Legacy devices: The update is applied *after* the reboot (in recovery).
     */
    UPDATED_NEED_REBOOT(2),

    // App specific
    UNKNOWN(0),
    STARTING(1),
    PAUSED(1),
    PAUSED_ERROR(1),
    DELETED(0),
    VERIFIED(2),
    VERIFICATION_FAILED(0),
    INSTALLING(2),
    INSTALLATION_FAILED(0),
    INSTALLATION_CANCELLED(0),
    INSTALLATION_SUSPENDED(2);

    /** True for statuses where an operation is actively in progress. */
    val isInProgress: Boolean
        get() = when (this) {
            DOWNLOADING,
            PAUSED,
            PAUSED_ERROR,
            VERIFYING,
            STARTING,
            INSTALLING,
            INSTALLATION_SUSPENDED -> true

            else -> false
        }

    companion object {
        @JvmStatic
        fun fromPersistentStatus(status: Int) = when (status) {
            1 -> PAUSED
            2 -> VERIFIED
            else -> UNKNOWN
        }
    }
}
