/*
 * Copyright (C) 2017-2025 The LineageOS Project
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

package org.lineageos.updater.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.RecoverySystem;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.IOException;

class UpdateInstaller {

    private static final String TAG = "UpdateInstaller";

    private static UpdateInstaller sInstance = null;
    private static String sInstallingUpdate = null;

    private final Context mContext;
    private final UpdaterController mUpdaterController;

    private UpdateInstaller(Context context, UpdaterController controller) {
        mContext = context.getApplicationContext();
        mUpdaterController = controller;
    }

    static synchronized UpdateInstaller getInstance(Context context,
                                                    UpdaterController updaterController) {
        if (sInstance == null) {
            sInstance = new UpdateInstaller(context, updaterController);
        }
        return sInstance;
    }

    static synchronized boolean isInstalling() {
        return sInstallingUpdate != null;
    }

    static synchronized boolean isInstalling(String downloadId) {
        return sInstallingUpdate != null && sInstallingUpdate.equals(downloadId);
    }

    void install(String downloadId) {
        if (isInstalling()) {
            Log.e(TAG, "Already installing an update");
            return;
        }

        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        long buildTimestamp = SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0);
        long lastBuildTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP,
                buildTimestamp);
        boolean isReinstalling = buildTimestamp == lastBuildTimestamp;

        preferences.edit()
                .putLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, buildTimestamp)
                .putLong(Constants.PREF_INSTALL_NEW_TIMESTAMP, update.getTimestamp())
                .putString(Constants.PREF_INSTALL_PACKAGE_PATH, update.getFile().getAbsolutePath())
                .putBoolean(Constants.PREF_INSTALL_AGAIN, isReinstalling)
                .putBoolean(Constants.PREF_INSTALL_NOTIFIED, false)
                .apply();

        new Thread(() -> {
            try {
                sInstallingUpdate = downloadId;
                mUpdaterController.getActualUpdate(downloadId).setStatus(UpdateStatus.INSTALLING);
                mUpdaterController.notifyUpdateChange(downloadId);

                RecoverySystem.processPackage(mContext, update.getFile(), progress -> {
                    mUpdaterController.getActualUpdate(downloadId).setInstallProgress(progress);
                    mUpdaterController.notifyInstallProgress(downloadId);
                });

                RecoverySystem.installPackage(mContext, update.getFile(), true /*processed*/);
            } catch (IOException e) {
                Log.e(TAG, "Could not install update", e);
                mUpdaterController.getActualUpdate(downloadId).setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(downloadId);
            } finally {
                synchronized (UpdateInstaller.class) {
                    sInstallingUpdate = null;
                }
            }
        }).start();
    }

    public synchronized void cancel() {}
}