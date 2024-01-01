/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.lineageos.updater.download.DownloadClient;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

public class UpdatesCheckReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdatesCheckReceiver";

    private static final String DAILY_CHECK_ACTION = "daily_check_action";
    private static final String ONESHOT_CHECK_ACTION = "oneshot_check_action";

    private static final String NEW_UPDATES_NOTIFICATION_CHANNEL =
            "new_updates_notification_channel";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Utils.cleanupDownloadsDir(context);
        }

        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        if (!Utils.isUpdateCheckEnabled(context)) {
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Set a repeating alarm on boot to check for new updates once per day
            scheduleRepeatingUpdatesCheck(context);
        }

        if (!Utils.isNetworkAvailable(context)) {
            Log.d(TAG, "Network not available, scheduling new check");
            scheduleUpdatesCheck(context);
            return;
        }

        final File json = Utils.getCachedUpdateList(context);
        final File jsonNew = new File(json.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL(context);
        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(boolean cancelled) {
                Log.e(TAG, "Could not download updates list, scheduling new check");
                scheduleUpdatesCheck(context);
            }

            @Override
            public void onResponse(DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess() {
                try {
                    if (json.exists() && Utils.checkForNewUpdates(json, jsonNew)) {
                        showNotification(context);
                        updateRepeatingUpdatesCheck(context);
                    }
                    //noinspection ResultOfMethodCallIgnored
                    jsonNew.renameTo(json);
                    long currentMillis = System.currentTimeMillis();
                    preferences.edit()
                            .putLong(Constants.PREF_LAST_UPDATE_CHECK, currentMillis)
                            .apply();
                    // In case we set a one-shot check because of a previous failure
                    cancelUpdatesCheck(context);
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Could not parse list, scheduling new check", e);
                    scheduleUpdatesCheck(context);
                }
            }
        };

        try {
            DownloadClient downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonNew)
                    .setDownloadCallback(callback)
                    .build();
            downloadClient.start();
        } catch (IOException e) {
            Log.e(TAG, "Could not fetch list, scheduling new check", e);
            scheduleUpdatesCheck(context);
        }
    }

    private static void showNotification(Context context) {
        NotificationManager notificationManager = context.getSystemService(
                NotificationManager.class);
        NotificationChannel notificationChannel = new NotificationChannel(
                NEW_UPDATES_NOTIFICATION_CHANNEL,
                context.getString(R.string.new_updates_channel_title),
                NotificationManager.IMPORTANCE_LOW);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context,
                NEW_UPDATES_NOTIFICATION_CHANNEL);
        notificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        Intent notificationIntent = new Intent(context, UpdatesActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notificationBuilder.setContentIntent(intent);
        notificationBuilder.setContentTitle(context.getString(R.string.new_updates_found_title));
        notificationBuilder.setAutoCancel(true);
        notificationManager.createNotificationChannel(notificationChannel);
        notificationManager.notify(0, notificationBuilder.build());
    }

    private static PendingIntent getRepeatingUpdatesCheckIntent(Context context) {
        Intent intent = new Intent(context, UpdatesCheckReceiver.class);
        intent.setAction(DAILY_CHECK_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static void updateRepeatingUpdatesCheck(Context context) {
        cancelRepeatingUpdatesCheck(context);
        scheduleRepeatingUpdatesCheck(context);
    }

    public static void scheduleRepeatingUpdatesCheck(Context context) {
        if (!Utils.isUpdateCheckEnabled(context)) {
            return;
        }

        PendingIntent updateCheckIntent = getRepeatingUpdatesCheckIntent(context);
        AlarmManager alarmMgr = context.getSystemService(AlarmManager.class);
        alarmMgr.setRepeating(AlarmManager.RTC, System.currentTimeMillis() +
                Utils.getUpdateCheckInterval(context), Utils.getUpdateCheckInterval(context),
                updateCheckIntent);

        Date nextCheckDate = new Date(System.currentTimeMillis() +
                Utils.getUpdateCheckInterval(context));
        Log.d(TAG, "Setting automatic updates check: " + nextCheckDate);
    }

    public static void cancelRepeatingUpdatesCheck(Context context) {
        AlarmManager alarmMgr = context.getSystemService(AlarmManager.class);
        alarmMgr.cancel(getRepeatingUpdatesCheckIntent(context));
    }

    private static PendingIntent getUpdatesCheckIntent(Context context) {
        Intent intent = new Intent(context, UpdatesCheckReceiver.class);
        intent.setAction(ONESHOT_CHECK_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static void scheduleUpdatesCheck(Context context) {
        long millisToNextCheck = AlarmManager.INTERVAL_HOUR * 2;
        PendingIntent updateCheckIntent = getUpdatesCheckIntent(context);
        AlarmManager alarmMgr = context.getSystemService(AlarmManager.class);
        alarmMgr.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + millisToNextCheck,
                updateCheckIntent);

        Date nextCheckDate = new Date(System.currentTimeMillis() + millisToNextCheck);
        Log.d(TAG, "Setting one-shot updates check: " + nextCheckDate);
    }

    public static void cancelUpdatesCheck(Context context) {
        AlarmManager alarmMgr = context.getSystemService(AlarmManager.class);
        alarmMgr.cancel(getUpdatesCheckIntent(context));
        Log.d(TAG, "Cancelling pending one-shot check");
    }
}
