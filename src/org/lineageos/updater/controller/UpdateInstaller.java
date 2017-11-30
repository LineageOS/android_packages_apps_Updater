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
import android.os.SystemClock;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.FileUtils;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.File;
import java.io.IOException;

class UpdateInstaller {

    private static final String TAG = "UpdateInstaller";

    private static Thread sPrepareUpdateThread;

    private final Context mContext;
    private final UpdaterController mUpdaterController;

    UpdateInstaller(Context context, UpdaterController controller) {
        mContext = context;
        mUpdaterController = controller;
    }

    static boolean isInstalling() {
        return sPrepareUpdateThread != null;
    }

    static boolean isInstalling(String downloadId) {
        return sPrepareUpdateThread != null && downloadId.equals(sPrepareUpdateThread.getName());
    }

    void install(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        boolean deleteUpdate = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean(Constants.PREF_AUTO_UPDATES_CHECK, false);
        if (deleteUpdate) {
            // Renaming the file is enough to have it deleted automatically
            File uncrytpFile = new File(
                    update.getFile().getAbsolutePath() + Constants.UNCRYPT_FILE_EXT);
            update.getFile().renameTo(uncrytpFile);
            installPackage(uncrytpFile, downloadId);
        } else if (Utils.isEncrypted(mContext, update.getFile())) {
            // uncrypt rewrites the file so that it can be read without mounting
            // the filesystem, so create a copy of it.
            prepareForUncryptAndInstall(update);
        } else {
            installPackage(update.getFile(), downloadId);
        }
    }

    private void installPackage(File update, String downloadId) {
        try {
            android.os.RecoverySystem.installPackage(mContext, update);
        } catch (IOException e) {
            // TODO: show error message
            Log.e(TAG, "Could not install update", e);
        }
    }

    private void prepareForUncryptAndInstall(UpdateInfo update) {
        if (sPrepareUpdateThread != null) {
            Log.e(TAG, "Already preparing an update");
            return;
        }

        String uncryptFilePath = update.getFile().getAbsolutePath() + Constants.UNCRYPT_FILE_EXT;
        File uncryptFile = new File(uncryptFilePath);

        Runnable copyUpdateRunnable = new Runnable() {
            private long mLastUpdate = -1;

            FileUtils.ProgressCallBack mProgressCallBack = new FileUtils.ProgressCallBack() {
                @Override
                public void update(int progress) {
                    long now = SystemClock.elapsedRealtime();
                    if (mLastUpdate < 0 || now - mLastUpdate > 500) {
                        mUpdaterController.getActualUpdate(update.getDownloadId())
                                .setInstallProgress(progress);
                        mUpdaterController.notifyInstallProgress(update.getDownloadId());
                        mLastUpdate = now;
                    }
                }
            };

            @Override
            public void run() {
                try {
                    FileUtils.copyFile(update.getFile(), uncryptFile, mProgressCallBack);

                    // Use INSTALLATION_CANCELLED to clear everything.
                    // This shouldn't really matter in case of success.
                    mUpdaterController.getActualUpdate(update.getDownloadId())
                            .setStatus(UpdateStatus.INSTALLATION_CANCELLED);
                    mUpdaterController.getActualUpdate(update.getDownloadId())
                            .setInstallProgress(0);
                    mUpdaterController.notifyUpdateChange(update.getDownloadId());

                    if (!sPrepareUpdateThread.isInterrupted()) {
                        installPackage(uncryptFile, update.getDownloadId());
                    } else {
                        uncryptFile.delete();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not copy update", e);
                    uncryptFile.delete();
                    mUpdaterController.getActualUpdate(update.getDownloadId())
                            .setStatus(UpdateStatus.INSTALLATION_FAILED);
                    mUpdaterController.notifyUpdateChange(update.getDownloadId());
                } finally {
                    sPrepareUpdateThread = null;
                }
            }
        };

        sPrepareUpdateThread = new Thread(copyUpdateRunnable);
        sPrepareUpdateThread.setName(update.getDownloadId());
        sPrepareUpdateThread.start();

        mUpdaterController.getActualUpdate(update.getDownloadId())
                .setStatus(UpdateStatus.INSTALLING);
        mUpdaterController.notifyUpdateChange(update.getDownloadId());
    }

    public void cancel() {
        if (sPrepareUpdateThread == null) {
            Log.d(TAG, "Nothing is being copied");
            return;
        }
        sPrepareUpdateThread.interrupt();
    }
}
