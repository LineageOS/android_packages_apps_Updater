/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.misc

object Constants {
    const val AB_PAYLOAD_BIN_PATH = "payload.bin"
    const val AB_PAYLOAD_PROPERTIES_PATH = "payload_properties.txt"

    const val AUTO_UPDATES_CHECK_INTERVAL_NEVER = 0
    const val AUTO_UPDATES_CHECK_INTERVAL_DAILY = 1
    const val AUTO_UPDATES_CHECK_INTERVAL_WEEKLY = 2
    const val AUTO_UPDATES_CHECK_INTERVAL_MONTHLY = 3

    const val PREF_LAST_UPDATE_CHECK = "last_update_check"
    const val PREF_AUTO_UPDATES_CHECK_INTERVAL = "auto_updates_check_interval"
    const val PREF_AUTO_DELETE_UPDATES = "auto_delete_updates"
    const val PREF_AB_PERF_MODE = "ab_perf_mode"
    const val PREF_MOBILE_DATA_WARNING = "pref_mobile_data_warning"
    const val PREF_NEEDS_REBOOT_ID = "needs_reboot_id"

    const val UNCRYPT_FILE_EXT = ".uncrypt"

    const val PROP_AB_DEVICE = "ro.build.ab_update"
    const val PROP_BUILD_DATE = "ro.build.date.utc"
    const val PROP_BUILD_VERSION = "ro.lineage.build.version"
    const val PROP_BUILD_VERSION_INCREMENTAL = "ro.build.version.incremental"
    const val PROP_DEVICE = "ro.lineage.device"
    const val PROP_NEXT_DEVICE = "ro.updater.next_device"
    const val PROP_RELEASE_TYPE = "ro.lineage.releasetype"
    const val PROP_UPDATER_ALLOW_DOWNGRADING = "lineage.updater.allow_downgrading"
    const val PROP_UPDATER_URI = "lineage.updater.uri"

    const val PREF_INSTALL_OLD_TIMESTAMP = "install_old_timestamp"
    const val PREF_INSTALL_NEW_TIMESTAMP = "install_new_timestamp"
    const val PREF_INSTALL_PACKAGE_PATH = "install_package_path"
    const val PREF_INSTALL_AGAIN = "install_again"
    const val PREF_INSTALL_NOTIFIED = "install_notified"

    const val UPDATE_RECOVERY_EXEC = "/vendor/bin/install-recovery.sh"
    const val UPDATE_RECOVERY_PROPERTY = "persist.vendor.recovery_update"

    const val HAS_SEEN_INFO_DIALOG = "has_seen_info_dialog"
}
