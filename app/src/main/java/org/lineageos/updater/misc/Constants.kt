/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.misc

import android.content.Context
import org.lineageos.updater.R
import java.util.concurrent.TimeUnit

object Constants {

    /**
     * User preferences
     */
    const val PREF_AB_PERF_MODE = "ab_perf_mode"
    const val PREF_AUTO_DELETE_UPDATES = "auto_delete_updates"
    const val PREF_METERED_NETWORK_WARNING = "pref_metered_network_warning"
    const val PREF_PERIODIC_CHECK_ENABLED = "periodic_check_enabled"
    const val PREF_UPDATE_RECOVERY = "update_recovery"

    enum class CheckInterval(val value: String, val milliseconds: Long) {
        DAILY("1", TimeUnit.DAYS.toMillis(1)),
        WEEKLY("2", TimeUnit.DAYS.toMillis(7)),
        MONTHLY("3", TimeUnit.DAYS.toMillis(30));

        companion object {
            const val PREF_KEY = "periodic_check_interval"

            fun fromValue(context: Context, value: String?): CheckInterval {
                val effectiveValue = value
                    ?: context.resources.getInteger(R.integer.def_periodic_check_interval).toString()
                return entries.find { it.value == effectiveValue } ?: WEEKLY
            }
        }
    }

    /**
     * Internal preferences
     */
    const val PREF_HAS_SEEN_INFO_DIALOG = "has_seen_info_dialog"
    const val PREF_HAS_SEEN_WELCOME_MESSAGE = "has_seen_welcome_message"
    const val PREF_INSTALL_AGAIN = "install_again"
    const val PREF_INSTALL_NEW_TIMESTAMP = "install_new_timestamp"
    const val PREF_INSTALL_NOTIFIED = "install_notified"
    const val PREF_INSTALL_OLD_TIMESTAMP = "install_old_timestamp"
    const val PREF_INSTALL_PACKAGE_PATH = "install_package_path"
    const val PREF_LAST_UPDATE_CHECK = "last_update_check"
    const val PREF_NEEDS_REBOOT_ID = "needs_reboot_id"
    const val PREF_NEXT_UPDATE_CHECK = "next_update_check"

    /**
     * Miscellaneous
     */
    const val AB_PAYLOAD_BIN_PATH = "payload.bin"
    const val AB_PAYLOAD_PROPERTIES_PATH = "payload_properties.txt"

    /**
     * Miscellaneous - Legacy
     */
    const val UNCRYPT_FILE_EXT = ".uncrypt"
    const val UPDATE_RECOVERY_EXEC = "/vendor/bin/install-recovery.sh"
}
