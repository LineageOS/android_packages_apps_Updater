/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.updater;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import org.json.JSONException;
import org.lineageos.updater.download.DownloadClient;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.UpdateInfo;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class UpdatesCheckReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdatesCheckReceiver";

    private static final String DAILY_CHECK_ACTION = "daily_check_action";
    private static final String ONESHOT_CHECK_ACTION = "oneshot_check_action";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Utils.cleanupDownloadsDir(context);
        }

        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (!preferences.getBoolean(Constants.PREF_AUTO_UPDATES_CHECK, true)) {
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
        final File jsonNew = new File(json.getAbsolutePath() + ".tmp");
        String url = Utils.getServerURL(context);
        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(boolean cancelled) {
                Log.e(TAG, "Could not download updates list, scheduling new check");
                scheduleUpdatesCheck(context);
            }

            @Override
            public void onResponse(int statusCode, String url,
                    DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                try {
                    if (json.exists() && Utils.checkForNewUpdates(json, jsonNew)) {
                        showNotification(context);
                        updateRepeatingUpdatesCheck(context);
                    }
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

        DownloadClient downloadClient = new DownloadClient.Builder()
                .setUrl(url)
                .setDestination(jsonNew)
                .setDownloadCallback(callback)
                .build();
        downloadClient.start();
    }

    private static void showNotification(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
        notificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        Intent notificationIntent = new Intent(context, UpdatesActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(intent);
        notificationBuilder.setContentTitle(context.getString(R.string.new_updates_found_title));
        notificationBuilder.setAutoCancel(true);
        notificationManager.notify(0, notificationBuilder.build());
    }

    private static PendingIntent getRepeatingUpdatesCheckIntent(Context context) {
        Intent intent = new Intent(context, UpdatesCheckReceiver.class);
        intent.setAction(DAILY_CHECK_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    public static void updateRepeatingUpdatesCheck(Context context) {
        cancelRepeatingUpdatesCheck(context);
        scheduleRepeatingUpdatesCheck(context);
    }

    public static void scheduleRepeatingUpdatesCheck(Context context) {
        long millisToNextRelease = millisToNextRelease(context);
        PendingIntent updateCheckIntent = getRepeatingUpdatesCheckIntent(context);
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + millisToNextRelease,
                AlarmManager.INTERVAL_DAY, updateCheckIntent);

        Date nextCheckDate = new Date(System.currentTimeMillis() + millisToNextRelease);
        Log.d(TAG, "Setting daily updates check: " + nextCheckDate);
    }

    public static void cancelRepeatingUpdatesCheck(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getRepeatingUpdatesCheckIntent(context));
    }

    private static PendingIntent getUpdatesCheckIntent(Context context) {
        Intent intent = new Intent(context, UpdatesCheckReceiver.class);
        intent.setAction(ONESHOT_CHECK_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    public static void scheduleUpdatesCheck(Context context) {
        long millisToNextCheck = AlarmManager.INTERVAL_HOUR * 2;
        PendingIntent updateCheckIntent = getUpdatesCheckIntent(context);
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + millisToNextCheck,
                updateCheckIntent);

        Date nextCheckDate = new Date(System.currentTimeMillis() + millisToNextCheck);
        Log.d(TAG, "Setting one-shot updates check: " + nextCheckDate);
    }

    public static void cancelUpdatesCheck(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getUpdatesCheckIntent(context));
        Log.d(TAG, "Cancelling pending one-shot check");
    }

    private static long millisToNextRelease(Context context) {
        final long extraMillis = 3 * AlarmManager.INTERVAL_HOUR;
        List<UpdateInfo> updates = null;
        try {
            updates = Utils.parseJson(Utils.getCachedUpdateList(context), false);
        } catch (IOException | JSONException ignored) {
        }

        if (updates == null || updates.size() == 0) {
            return SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_DAY;
        }

        long buildTimestamp = 0;
        for (UpdateInfo update : updates) {
            if (update.getTimestamp() > buildTimestamp) {
                buildTimestamp = update.getTimestamp();
            }
        }
        buildTimestamp *= 1000;

        Calendar c = Calendar.getInstance();
        long now = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.setTimeInMillis(c.getTimeInMillis() + millisSinceMidnight(buildTimestamp));
        long millisToNextRelease = (c.getTimeInMillis() - now);
        millisToNextRelease += extraMillis;
        if (c.getTimeInMillis() < now) {
            millisToNextRelease += AlarmManager.INTERVAL_DAY;
        }

        return millisToNextRelease;
    }

    private static long millisSinceMidnight(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return millis - c.getTimeInMillis();
    }
}
