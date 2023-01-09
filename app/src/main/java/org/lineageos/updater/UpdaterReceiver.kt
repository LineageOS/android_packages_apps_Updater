/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemProperties
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import org.lineageos.updater.misc.BuildInfoUtils.buildVersion
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.StringGenerator.getDateLocalizedUTC
import java.text.DateFormat

class UpdaterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_INSTALL_REBOOT == intent.action) {
            val powerManager = context.getSystemService(
                PowerManager::class.java
            )
            powerManager.reboot(null)
        } else if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            pref.edit().remove(Constants.PREF_NEEDS_REBOOT_ID).apply()
            if (shouldShowUpdateFailedNotification(context)) {
                pref.edit().putBoolean(Constants.PREF_INSTALL_NOTIFIED, true).apply()
                showUpdateFailedNotification(context)
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_REBOOT = "org.lineageos.updater.action.INSTALL_REBOOT"
        private const val INSTALL_ERROR_NOTIFICATION_CHANNEL = "install_error_notification_channel"

        private fun shouldShowUpdateFailedNotification(context: Context): Boolean {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)

            // We can't easily detect failed re-installations
            if (preferences.getBoolean(Constants.PREF_INSTALL_AGAIN, false) ||
                preferences.getBoolean(Constants.PREF_INSTALL_NOTIFIED, false)
            ) {
                return false
            }
            val buildTimestamp = SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0)
            val lastBuildTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, -1)
            return buildTimestamp == lastBuildTimestamp
        }

        private fun showUpdateFailedNotification(context: Context) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val buildDate = getDateLocalizedUTC(
                context,
                DateFormat.MEDIUM, preferences.getLong(Constants.PREF_INSTALL_NEW_TIMESTAMP, 0)
            )
            val buildInfo = context.getString(
                R.string.list_build_version_date,
                buildVersion, buildDate
            )
            val notificationIntent = Intent(context, UpdatesActivity::class.java)
            val intent = PendingIntent.getActivity(
                context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notificationChannel = NotificationChannel(
                INSTALL_ERROR_NOTIFICATION_CHANNEL,
                context.getString(R.string.update_failed_channel_title),
                NotificationManager.IMPORTANCE_LOW
            )
            val builder = NotificationCompat.Builder(
                context,
                INSTALL_ERROR_NOTIFICATION_CHANNEL
            )
                .setContentIntent(intent)
                .setSmallIcon(R.drawable.ic_system_update)
                .setContentTitle(context.getString(R.string.update_failed_notification))
                .setStyle(NotificationCompat.BigTextStyle().bigText(buildInfo))
                .setContentText(buildInfo)
            val notificationManager = context.getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(notificationChannel)
            notificationManager.notify(0, builder.build())
        }
    }
}
