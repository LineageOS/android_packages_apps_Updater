/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.app.Application
import org.lineageos.updater.notifications.NotificationHelper

class UpdaterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).setUpNotificationChannels()
    }
}
