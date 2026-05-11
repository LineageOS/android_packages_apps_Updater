/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.app.Application
import kotlinx.coroutines.MainScope
import org.lineageos.updater.util.BatteryMonitor
import org.lineageos.updater.util.NetworkMonitor

class UpdaterApplication : Application() {
    private val coroutineScope = MainScope()
    val batteryMonitor by lazy { BatteryMonitor(applicationContext, coroutineScope) }
    val networkMonitor by lazy { NetworkMonitor(applicationContext, coroutineScope) }
}
