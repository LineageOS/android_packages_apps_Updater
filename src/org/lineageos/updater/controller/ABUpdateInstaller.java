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
package org.lineageos.updater.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.Update;
import org.lineageos.updater.model.UpdateStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ABUpdateInstaller {

    private static final String TAG = "ABUpdateInstaller";

    private static final String PREF_INSTALLING_AB_ID = "installing_ab_id";
    private static final String PREF_NEEDS_REBOOT = "needs_reboot";

    private static ABUpdateInstaller sInstance = null;

    private final UpdaterController mUpdaterController;
    private final Context mContext;
    private String mDownloadId;
    private boolean mReconnecting;

    private UpdateEngine mUpdateEngine;
    private boolean mBound;

    private final UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback() {

        @Override
        public void onStatusUpdate(int status, float percent) {
            Update update = mUpdaterController.getActualUpdate(mDownloadId);
            if (update == null) {
                // We read the id from a preference, the update could no longer exist
                installationDone(status == UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT);
                return;
            }

            switch (status) {
                case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
                case UpdateEngine.UpdateStatusConstants.FINALIZING: {
                    int progress = Math.round(percent * 100);
                    mUpdaterController.getActualUpdate(mDownloadId).setInstallProgress(progress);
                    boolean finalizing = status == UpdateEngine.UpdateStatusConstants.FINALIZING;
                    mUpdaterController.getActualUpdate(mDownloadId).setFinalizing(finalizing);
                    mUpdaterController.notifyInstallProgress(mDownloadId);
                }
                break;

                case UpdateEngine.UpdateStatusConstants.REPORTING_ERROR_EVENT: {
                    installationDone(false);
                    update.setInstallProgress(0);
                    update.setStatus(UpdateStatus.INSTALLATION_FAILED);
                    mUpdaterController.notifyUpdateChange(mDownloadId);
                }
                break;

                case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT: {
                    installationDone(true);
                    update.setInstallProgress(0);
                    update.setStatus(UpdateStatus.INSTALLED);
                    mUpdaterController.notifyUpdateChange(mDownloadId);
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(
                            mContext);
                    boolean deleteUpdate = pref.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES,
                            false);
                    if (deleteUpdate) {
                        mUpdaterController.deleteUpdate(mDownloadId);
                    }
                }
                break;

                case UpdateEngine.UpdateStatusConstants.IDLE: {
                    if (mReconnecting) {
                        // The service was restarted because we thought we were installing an
                        // update, but we aren't, so clear everything.
                        installationDone(false);
                        mReconnecting = false;
                    }
                }
                break;
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
        }
    };

    static synchronized boolean isInstallingUpdate(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(ABUpdateInstaller.PREF_INSTALLING_AB_ID, null) != null ||
                pref.getBoolean(ABUpdateInstaller.PREF_NEEDS_REBOOT, false);
    }

    static synchronized boolean isInstallingUpdate(Context context, String downloadId) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return downloadId.equals(pref.getString(ABUpdateInstaller.PREF_INSTALLING_AB_ID, null)) ||
                pref.getBoolean(ABUpdateInstaller.PREF_NEEDS_REBOOT, false);
    }

    private ABUpdateInstaller(Context context, UpdaterController updaterController) {
        mUpdaterController = updaterController;
        mContext = context.getApplicationContext();
        mUpdateEngine = new UpdateEngine();
    }

    static synchronized ABUpdateInstaller getInstance(Context context,
            UpdaterController updaterController) {
        if (sInstance == null) {
            sInstance = new ABUpdateInstaller(context, updaterController);
        }
        return sInstance;
    }

    public boolean install(String downloadId) {
        if (isInstallingUpdate(mContext)) {
            Log.e(TAG, "Already installing an update");
            return false;
        }

        mDownloadId = downloadId;

        File file = mUpdaterController.getActualUpdate(mDownloadId).getFile();
        if (!file.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            mUpdaterController.getActualUpdate(downloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED);
            mUpdaterController.notifyUpdateChange(downloadId);
            return false;
        }

        long offset;
        String[] headerKeyValuePairs;
        try {
            ZipFile zipFile = new ZipFile(file);
            offset = Utils.getZipEntryOffset(zipFile, Constants.AB_PAYLOAD_BIN_PATH);
            ZipEntry payloadPropEntry = zipFile.getEntry(Constants.AB_PAYLOAD_PROPERTIES_PATH);
            try (InputStream is = zipFile.getInputStream(payloadPropEntry);
                 InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader br = new BufferedReader(isr)) {
                List<String> lines = new ArrayList<>();
                for (String line; (line = br.readLine()) != null;) {
                    lines.add(line);
                }
                headerKeyValuePairs = new String[lines.size()];
                headerKeyValuePairs = lines.toArray(headerKeyValuePairs);
            }
            zipFile.close();
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "Could not prepare " + file, e);
            mUpdaterController.getActualUpdate(mDownloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED);
            mUpdaterController.notifyUpdateChange(mDownloadId);
            return false;
        }

        if (!mBound) {
            mBound = mUpdateEngine.bind(mUpdateEngineCallback);
            if (!mBound) {
                Log.e(TAG, "Could not bind");
                mUpdaterController.getActualUpdate(downloadId)
                        .setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(downloadId);
                return false;
            }
        }

        String zipFileUri = "file://" + file.getAbsolutePath();
        mUpdateEngine.applyPayload(zipFileUri, offset, 0, headerKeyValuePairs);

        mUpdaterController.getActualUpdate(mDownloadId).setStatus(UpdateStatus.INSTALLING);
        mUpdaterController.notifyUpdateChange(mDownloadId);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(PREF_INSTALLING_AB_ID, mDownloadId)
                .apply();

        return true;
    }

    public boolean reconnect() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "reconnect: Not installing any update");
            return false;
        }

        if (mBound) {
            return true;
        }

        mReconnecting = true;
        mDownloadId = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(PREF_INSTALLING_AB_ID, null);

        // We will get a status notification as soon as we are connected
        mBound = mUpdateEngine.bind(mUpdateEngineCallback);
        if (!mBound) {
            Log.e(TAG, "Could not bind");
            return false;
        }

        return true;
    }

    private void installationDone(boolean needsReboot) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putBoolean(PREF_NEEDS_REBOOT, needsReboot)
                .remove(PREF_INSTALLING_AB_ID)
                .apply();
    }

    public boolean cancel() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "cancel: Not installing any update");
            return false;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return false;
        }

        mUpdateEngine.cancel();
        installationDone(false);

        mUpdaterController.getActualUpdate(mDownloadId)
                .setStatus(UpdateStatus.INSTALLATION_CANCELLED);
        mUpdaterController.notifyUpdateChange(mDownloadId);

        return true;
    }
}
