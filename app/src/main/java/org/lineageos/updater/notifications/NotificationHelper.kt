/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import org.lineageos.updater.R
import org.lineageos.updater.UpdatesActivity
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import org.lineageos.updater.misc.StringGenerator
import java.text.DateFormat

class NotificationHelper(context: Context) {

    companion object {
        const val CHANNEL_EXPORT = "export_notification_channel"
        private const val CHANNEL_INSTALL_ERROR = "install_error_notification_channel"
        private const val CHANNEL_NEW_UPDATES = "new_updates_notification_channel"
        const val CHANNEL_ONGOING = "ongoing_notification_channel"

        private const val ID_EXPORT = 16
        private const val ID_INSTALL_ERROR = 0
        private const val ID_NEW_UPDATES = 1
        private const val ID_ONGOING = 10
    }

    private val appContext = context.applicationContext

    private val notificationManager: NotificationManager =
        context.getSystemService() ?: throw IllegalStateException()

    fun setUpNotificationChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_ONGOING,
                    appContext.getString(R.string.ongoing_channel_title),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_EXPORT,
                    appContext.getString(R.string.export_channel_title),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_NEW_UPDATES,
                    appContext.getString(R.string.new_updates_channel_title),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_INSTALL_ERROR,
                    appContext.getString(R.string.update_failed_channel_title),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        )
    }

    fun showNewUpdatesNotification() {
        val intent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, UpdatesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(appContext, CHANNEL_NEW_UPDATES)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_SYSTEM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(intent)
            .setContentTitle(appContext.getString(R.string.new_updates_found_title))
            .setContentText(appContext.getString(R.string.new_updates_found_summary))
            .setSmallIcon(R.drawable.ic_system_update)
            .build()
        notificationManager.notify(ID_NEW_UPDATES, notification)
    }

    fun showUpdateFailedNotification(installTimestamp: Long) {
        val buildDate = StringGenerator.getDateLocalizedUTC(
            appContext,
            DateFormat.MEDIUM,
            installTimestamp,
        )
        val buildInfo = appContext.getString(
            R.string.list_build_version_date, DeviceInfoUtils.buildVersion, buildDate,
        )
        val intent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, UpdatesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification =
            Notification.Builder(appContext, CHANNEL_INSTALL_ERROR)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ERROR)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(intent)
                .setSmallIcon(R.drawable.ic_system_update)
                .setContentTitle(appContext.getString(R.string.update_failed_notification))
                .setStyle(Notification.BigTextStyle().bigText(buildInfo))
                .setContentText(buildInfo)
                .build()
        notificationManager.notify(ID_INSTALL_ERROR, notification)
    }
}
