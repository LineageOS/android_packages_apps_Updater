/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;

import androidx.preference.PreferenceManager;

import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.deviceinfo.DeviceInfoUtils;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.updatescheck.UpdatesCheckWorker;

public class UpdaterReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL_REBOOT =
            "org.lineageos.updater.action.INSTALL_REBOOT";

    private static boolean isUpdateSuccessful(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        long buildTimestamp = DeviceInfoUtils.getBuildDateTimestamp();
        long lastBuildTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, -1);
        // We can't easily detect failed re-installations.
        boolean isReinstall = preferences.getBoolean(Constants.PREF_INSTALL_AGAIN, false);

        return isReinstall || buildTimestamp != lastBuildTimestamp;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_INSTALL_REBOOT.equals(intent.getAction())) {
            PowerManager pm = context.getSystemService(PowerManager.class);
            pm.reboot(null);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Utils.removeUncryptFiles(Utils.getDownloadPath(context));
            UpdatesCheckWorker.schedulePeriodicCheck(context);

            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            String downloadId = pref.getString(Constants.PREF_NEEDS_REBOOT_ID, null);
            pref.edit().remove(Constants.PREF_NEEDS_REBOOT_ID).apply();

            if (downloadId != null && isUpdateSuccessful(context)) {
                Intent cleanupIntent = new Intent(context, UpdaterService.class);
                cleanupIntent.setAction(UpdaterService.ACTION_POST_REBOOT_CLEANUP);
                cleanupIntent.putExtra(UpdaterService.EXTRA_DOWNLOAD_ID, downloadId);
                context.startService(cleanupIntent);
            }

            if (!pref.getBoolean(Constants.PREF_INSTALL_NOTIFIED, false)
                    && !isUpdateSuccessful(context)) {
                pref.edit().putBoolean(Constants.PREF_INSTALL_NOTIFIED, true).apply();
                long installTimestamp = pref.getLong(Constants.PREF_INSTALL_NEW_TIMESTAMP, 0);
                ((UpdaterApplication) context.getApplicationContext()).getNotificationHelper()
                        .showUpdateFailedNotification(installTimestamp);
            }
        }
    }
}
