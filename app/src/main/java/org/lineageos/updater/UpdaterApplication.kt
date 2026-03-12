/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.app.Application
import com.android.settingslib.spa.framework.common.SettingsPageProviderRepository
import com.android.settingslib.spa.framework.common.SpaEnvironment
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import kotlinx.coroutines.MainScope
import org.lineageos.updater.data.UpdatesRepository
import org.lineageos.updater.data.source.local.UpdatesDatabase
import org.lineageos.updater.data.source.local.UpdatesLocalDataSource
import org.lineageos.updater.data.source.network.UpdatesNetworkDataSource
import org.lineageos.updater.notifications.NotificationHelper
import org.lineageos.updater.util.BatteryMonitor
import org.lineageos.updater.util.NetworkMonitor

class UpdaterApplication : Application() {
    private val coroutineScope = MainScope()
    private val database by lazy { UpdatesDatabase.getInstance(applicationContext) }
    private val networkDataSource by lazy { UpdatesNetworkDataSource(applicationContext) }
    private val localDataSource by lazy { UpdatesLocalDataSource(database.updateDao()) }

    val batteryMonitor by lazy { BatteryMonitor(applicationContext, coroutineScope) }
    val networkMonitor by lazy { NetworkMonitor(applicationContext, coroutineScope) }
    val notificationHelper by lazy { NotificationHelper(applicationContext) }
    val updatesRepository by lazy {
        UpdatesRepository(
            networkMonitor = networkMonitor,
            notificationHelper = notificationHelper,
            networkDataSource = networkDataSource,
            localDataSource = localDataSource,
        )
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.setUpNotificationChannels()
        SpaEnvironmentFactory.reset(object : SpaEnvironment(applicationContext) {
            override val pageProviderRepository = lazy {
                SettingsPageProviderRepository(emptyList())
            }

            override val isSpaExpressiveEnabled = true
        })
        NotificationHelper(this).setUpNotificationChannels()
    }
}
