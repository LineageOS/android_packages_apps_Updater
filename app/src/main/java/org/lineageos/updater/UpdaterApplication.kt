/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.app.Application
import org.lineageos.updater.util.BatteryMonitor
import org.lineageos.updater.util.NetworkMonitor

class UpdaterApplication : Application() {
    val batteryMonitor by lazy { BatteryMonitor(applicationContext) }
    val networkMonitor by lazy { NetworkMonitor(applicationContext) }
}
