/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.misc

object Constants {

    /**
     * User preferences
     */
    const val AUTO_UPDATES_CHECK_INTERVAL_NEVER = 0
    const val AUTO_UPDATES_CHECK_INTERVAL_DAILY = 1
    const val AUTO_UPDATES_CHECK_INTERVAL_WEEKLY = 2
    const val AUTO_UPDATES_CHECK_INTERVAL_MONTHLY = 3

    const val PREF_AB_PERF_MODE = "ab_perf_mode"
    const val PREF_AUTO_DELETE_UPDATES = "auto_delete_updates"
    const val PREF_AUTO_UPDATES_CHECK_INTERVAL = "auto_updates_check_interval"
    const val PREF_METERED_NETWORK_WARNING = "pref_metered_network_warning"

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
