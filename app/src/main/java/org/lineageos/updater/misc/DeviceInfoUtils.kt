/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.misc

import android.os.SystemProperties
import com.android.settingslib.DeviceInfoUtils as SettingsLibDeviceInfoUtils

object DeviceInfoUtils : SettingsLibDeviceInfoUtils() {

    private const val PROP_AB_DEVICE = "ro.build.ab_update"
    private const val PROP_ALLOW_MAJOR_UPGRADES = "lineage.updater.allow_major_upgrades"
    private const val PROP_BUILD_DATE = "ro.build.date.utc"
    private const val PROP_BUILD_VERSION = "ro.lineage.build.version"
    private const val PROP_BUILD_VERSION_INCREMENTAL = "ro.build.version.incremental"
    private const val PROP_DEVICE = "ro.lineage.device"
    private const val PROP_NEXT_DEVICE = "ro.updater.next_device"
    private const val PROP_RELEASE_TYPE = "ro.lineage.releasetype"
    private const val PROP_UPDATER_ALLOW_DOWNGRADING = "lineage.updater.allow_downgrading"
    private const val PROP_UPDATER_URI = "lineage.updater.uri"
    private const val PROP_UPDATE_RECOVERY = "persist.vendor.recovery_update"

    @JvmStatic
    val buildDateTimestamp: Long
        get() = SystemProperties.getLong(PROP_BUILD_DATE, 0)

    @JvmStatic
    val buildVersion: String
        get() = SystemProperties.get(PROP_BUILD_VERSION, "")

    @JvmStatic
    val buildVersionIncremental: String
        get() = SystemProperties.get(PROP_BUILD_VERSION_INCREMENTAL, "")

    @JvmStatic
    val device: String
        get() = SystemProperties.get(
            PROP_NEXT_DEVICE,
            SystemProperties.get(PROP_DEVICE)
        )

    @JvmStatic
    val releaseType: String
        get() = SystemProperties.get(PROP_RELEASE_TYPE)

    @JvmStatic
    val isABDevice: Boolean
        get() = SystemProperties.getBoolean(PROP_AB_DEVICE, false)

    @JvmStatic
    val isDowngradingAllowed: Boolean
        get() = SystemProperties.getBoolean(PROP_UPDATER_ALLOW_DOWNGRADING, false)

    @JvmStatic
    val isMajorUpdateAllowed: Boolean
        get() = SystemProperties.getBoolean(PROP_ALLOW_MAJOR_UPGRADES, false)

    @JvmStatic
    var isRecoveryUpdateEnabled: Boolean
        get() = SystemProperties.getBoolean(PROP_UPDATE_RECOVERY, false)
        set(value) = SystemProperties.set(PROP_UPDATE_RECOVERY, value.toString())

    @JvmStatic
    val updaterUri: String
        get() = SystemProperties.get(PROP_UPDATER_URI, "")
}

