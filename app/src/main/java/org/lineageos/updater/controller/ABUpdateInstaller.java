/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ServiceSpecificException;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.updater.UpdaterApplication;
import org.lineageos.updater.data.Update;
import org.lineageos.updater.data.UpdateStatus;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.Utils;

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
    private static final String PREF_INSTALLING_SUSPENDED_AB_ID = "installing_suspended_ab_id";

    private static ABUpdateInstaller sInstance = null;

    private final UpdaterController mUpdaterController;
    private final Context mContext;
    private String mDownloadId;

    private final UpdateEngine mUpdateEngine;
    private boolean mBound;

    private boolean mFinalizing;
    private int mProgress;

    private final UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback() {

        @Override
        public void onStatusUpdate(int status, float percent) {
            Update update = mUpdaterController.getUpdate(mDownloadId);
            if (update == null) {
                // We read the id from a preference, the update could no longer exist
                installationDone(status == UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT);
                return;
            }

            switch (status) {
                case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
                case UpdateEngine.UpdateStatusConstants.FINALIZING: {
                    if (update.getStatus() != UpdateStatus.INSTALLING) {
                        update = update.withStatus(UpdateStatus.INSTALLING);
                        mUpdaterController.setUpdate(mDownloadId, update);
                        mUpdaterController.notifyUpdateChange(mDownloadId);
                    }
                    mProgress = Math.round(percent * 100);
                    mFinalizing = status == UpdateEngine.UpdateStatusConstants.FINALIZING;
                    update = update.toBuilder()
                            .setInstallProgress(mProgress)
                            .setFinalizing(mFinalizing)
                            .build();
                    mUpdaterController.setUpdate(mDownloadId, update);
                    mUpdaterController.notifyInstallProgress(mDownloadId);
                }
                break;

                case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT: {
                    installationDone(true);
                    update = update.toBuilder()
                            .setInstallProgress(0)
                            .setStatus(UpdateStatus.UPDATED_NEED_REBOOT)
                            .build();
                    mUpdaterController.setUpdate(mDownloadId, update);
                    mUpdaterController.notifyUpdateChange(mDownloadId);
                }
                break;

                case UpdateEngine.UpdateStatusConstants.IDLE: {
                    // The service was restarted because we thought we were installing an
                    // update, but we aren't, so clear everything.
                    installationDone(false);
                }
                break;
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            if (errorCode != UpdateEngine.ErrorCodeConstants.SUCCESS) {
                installationDone(false);
                Update update = mUpdaterController.getUpdate(mDownloadId);
                mUpdaterController.setUpdate(mDownloadId, update.toBuilder()
                        .setInstallProgress(0)
                        .setStatus(UpdateStatus.INSTALLATION_FAILED)
                        .build());
                mUpdaterController.notifyUpdateChange(mDownloadId);
            }
        }
    };

    static synchronized boolean isInstallingUpdate(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(ABUpdateInstaller.PREF_INSTALLING_AB_ID, null) != null ||
                pref.getString(Constants.PREF_NEEDS_REBOOT_ID, null) != null;
    }

    static synchronized boolean isInstallingUpdate(Context context, String downloadId) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return downloadId.equals(pref.getString(ABUpdateInstaller.PREF_INSTALLING_AB_ID, null)) ||
                TextUtils.equals(pref.getString(Constants.PREF_NEEDS_REBOOT_ID, null), downloadId);
    }

    static synchronized boolean isInstallingUpdateSuspended(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(ABUpdateInstaller.PREF_INSTALLING_SUSPENDED_AB_ID, null) != null;
    }

    static synchronized boolean isWaitingForReboot(Context context, String downloadId) {
        String waitingId = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(Constants.PREF_NEEDS_REBOOT_ID, null);
        return TextUtils.equals(waitingId, downloadId);
    }

    private boolean shouldEnablePerformanceMode(boolean userPreferenceEnabled) {
        return ((UpdaterApplication) mContext).getBatteryMonitor()
                .getCurrentBatteryState().isAcCharging()
                || userPreferenceEnabled;
    }

    private void applyPerformanceMode(boolean userPreferenceEnabled) {
        try {
            mUpdateEngine.setPerformanceMode(shouldEnablePerformanceMode(userPreferenceEnabled));
        } catch (Throwable e) {
            Log.w(TAG, "Could not set performance mode", e);
        }
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

    public void install(String downloadId) {
        if (isInstallingUpdate(mContext)) {
            Log.e(TAG, "Already installing an update");
            return;
        }

        mDownloadId = downloadId;

        File file = mUpdaterController.getUpdate(mDownloadId).getFile();
        install(file, downloadId);
    }

    public void install(File file, String downloadId) {
        if (!file.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            Update update = mUpdaterController.getUpdate(downloadId);
            mUpdaterController.setUpdate(downloadId,
                    update.withStatus(UpdateStatus.INSTALLATION_FAILED));
            mUpdaterController.notifyUpdateChange(downloadId);
            return;
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
            Update update = mUpdaterController.getUpdate(downloadId);
            mUpdaterController.setUpdate(downloadId,
                    update.withStatus(UpdateStatus.INSTALLATION_FAILED));
            mUpdaterController.notifyUpdateChange(mDownloadId);
            return;
        }

        if (!mBound) {
            mBound = mUpdateEngine.bind(mUpdateEngineCallback);
            if (!mBound) {
                Log.e(TAG, "Could not bind");
                Update update = mUpdaterController.getUpdate(downloadId);
                mUpdaterController.setUpdate(downloadId,
                        update.withStatus(UpdateStatus.INSTALLATION_FAILED));
                mUpdaterController.notifyUpdateChange(downloadId);
                return;
            }
        }

        applyPerformanceMode(PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean(Constants.PREF_AB_PERF_MODE, false));

        String zipFileUri = "file://" + file.getAbsolutePath();
        try {
            mUpdateEngine.applyPayload(zipFileUri, offset, 0, headerKeyValuePairs);
        } catch (ServiceSpecificException e) {
            if (e.errorCode == 66 /* kUpdateAlreadyInstalled */) {
                installationDone(true);
                Update update = mUpdaterController.getUpdate(mDownloadId);
                mUpdaterController.setUpdate(mDownloadId,
                        update.withStatus(UpdateStatus.UPDATED_NEED_REBOOT));
                mUpdaterController.notifyUpdateChange(mDownloadId);
                return;
            }
            throw e;
        }

        Update update = mUpdaterController.getUpdate(mDownloadId);
        mUpdaterController.setUpdate(mDownloadId,
                update.withStatus(UpdateStatus.INSTALLING));
        mUpdaterController.notifyUpdateChange(mDownloadId);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(PREF_INSTALLING_AB_ID, mDownloadId)
                .apply();

    }

    public void reconnect() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "reconnect: Not installing any update");
            return;
        }

        if (mBound) {
            return;
        }

        mDownloadId = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(PREF_INSTALLING_AB_ID, null);

        // We will get a status notification as soon as we are connected
        mBound = mUpdateEngine.bind(mUpdateEngineCallback);
        if (!mBound) {
            Log.e(TAG, "Could not bind");
            return;
        }

        applyPerformanceMode(PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean(Constants.PREF_AB_PERF_MODE, false));
    }

    private void installationDone(boolean needsReboot) {
        String id = needsReboot ? mDownloadId : null;
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(Constants.PREF_NEEDS_REBOOT_ID, id)
                .remove(PREF_INSTALLING_AB_ID)
                .apply();
    }

    public void cancel() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "cancel: Not installing any update");
            return;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return;
        }

        mUpdateEngine.cancel();
        installationDone(false);

        Update update = mUpdaterController.getUpdate(mDownloadId);
        mUpdaterController.setUpdate(mDownloadId,
                update.withStatus(UpdateStatus.INSTALLATION_CANCELLED));
        mUpdaterController.notifyUpdateChange(mDownloadId);

    }

    public void setPerformanceMode(boolean enable) {
        applyPerformanceMode(enable);
    }

    public void suspend() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "cancel: Not installing any update");
            return;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return;
        }

        mUpdateEngine.suspend();

        Update update = mUpdaterController.getUpdate(mDownloadId);
        mUpdaterController.setUpdate(mDownloadId,
                update.withStatus(UpdateStatus.INSTALLATION_SUSPENDED));
        mUpdaterController.notifyUpdateChange(mDownloadId);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(PREF_INSTALLING_SUSPENDED_AB_ID, mDownloadId)
                .apply();

    }

    public void resume() {
        if (!isInstallingUpdateSuspended(mContext)) {
            Log.e(TAG, "cancel: No update is suspended");
            return;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return;
        }

        mUpdateEngine.resume();

        Update update = mUpdaterController.getUpdate(mDownloadId);
        mUpdaterController.setUpdate(mDownloadId, update.toBuilder()
                .setStatus(UpdateStatus.INSTALLING)
                .setInstallProgress(mProgress)
                .setFinalizing(mFinalizing)
                .build());
        mUpdaterController.notifyUpdateChange(mDownloadId);
        mUpdaterController.notifyInstallProgress(mDownloadId);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .remove(PREF_INSTALLING_SUSPENDED_AB_ID)
                .apply();

    }
}
