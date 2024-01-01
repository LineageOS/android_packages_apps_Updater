/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemProperties;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import org.lineageos.updater.misc.BuildInfoUtils;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;

import java.text.DateFormat;

public class UpdaterReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_REBOOT =
            "org.lineageos.updater.action.INSTALL_REBOOT";

    private static final String INSTALL_ERROR_NOTIFICATION_CHANNEL =
            "install_error_notification_channel";

    private static boolean shouldShowUpdateFailedNotification(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // We can't easily detect failed re-installations
        if (preferences.getBoolean(Constants.PREF_INSTALL_AGAIN, false) ||
                preferences.getBoolean(Constants.PREF_INSTALL_NOTIFIED, false)) {
            return false;
        }

        long buildTimestamp = SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0);
        long lastBuildTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, -1);
        return buildTimestamp == lastBuildTimestamp;
    }

    private static void showUpdateFailedNotification(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String buildDate = StringGenerator.getDateLocalizedUTC(context,
                DateFormat.MEDIUM, preferences.getLong(Constants.PREF_INSTALL_NEW_TIMESTAMP, 0));
        String buildInfo = context.getString(R.string.list_build_version_date,
                Utils.getDisplayVersion(BuildInfoUtils.getBuildVersion()), buildDate);

        Intent notificationIntent = new Intent(context, UpdatesActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationChannel notificationChannel = new NotificationChannel(
                INSTALL_ERROR_NOTIFICATION_CHANNEL,
                context.getString(R.string.update_failed_channel_title),
                NotificationManager.IMPORTANCE_LOW);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                INSTALL_ERROR_NOTIFICATION_CHANNEL)
                .setContentIntent(intent)
                .setSmallIcon(R.drawable.ic_system_update)
                .setContentTitle(context.getString(R.string.update_failed_notification))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(buildInfo))
                .setContentText(buildInfo);

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(notificationChannel);
        nm.notify(0, builder.build());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_INSTALL_REBOOT.equals(intent.getAction())) {
            PowerManager pm = context.getSystemService(PowerManager.class);
            pm.reboot(null);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            pref.edit().remove(Constants.PREF_NEEDS_REBOOT_ID).apply();

            if (shouldShowUpdateFailedNotification(context)) {
                pref.edit().putBoolean(Constants.PREF_INSTALL_NOTIFIED, true).apply();
                showUpdateFailedNotification(context);
            }
        }
    }
}
