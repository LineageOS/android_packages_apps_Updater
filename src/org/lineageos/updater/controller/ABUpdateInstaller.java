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

import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Log;

import org.lineageos.updater.UpdateDownload;
import org.lineageos.updater.UpdateStatus;
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

    private static boolean sIsInstallingUpdate;

    private static final String PAYLOAD_BIN_PATH = "payload.bin";
    private static final String PAYLOAD_PROPERTIES_PATH = "payload_properties.txt";

    private final UpdaterController mUpdaterController;
    private final String mDownloadId;

    private final UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback() {

        @Override
        public void onStatusUpdate(int status, float percent) {
            switch (status) {
                case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
                case UpdateEngine.UpdateStatusConstants.FINALIZING: {
                    int progress = Math.round(percent * 100);
                    mUpdaterController.getActualUpdate(mDownloadId).setInstallProgress(progress);
                    mUpdaterController.notifyInstallProgress(mDownloadId);
                }
                break;

                case UpdateEngine.UpdateStatusConstants.REPORTING_ERROR_EVENT: {
                    UpdateDownload update = mUpdaterController.getActualUpdate(mDownloadId);
                    update.setInstallProgress(0);
                    update.setStatus(UpdateStatus.INSTALLATION_FAILED);
                    mUpdaterController.notifyUpdateChange(mDownloadId);;
                }
                break;
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            sIsInstallingUpdate = false;
            switch (errorCode) {
                case UpdateEngine.ErrorCodeConstants.SUCCESS: {
                    UpdateDownload update = mUpdaterController.getActualUpdate(mDownloadId);
                    update.setInstallProgress(0);
                    update.setStatus(UpdateStatus.INSTALLED);
                    mUpdaterController.notifyUpdateChange(mDownloadId);
                }
                break;

                default: {
                    UpdateDownload update = mUpdaterController.getActualUpdate(mDownloadId);
                    update.setInstallProgress(0);
                    update.setStatus(UpdateStatus.INSTALLATION_FAILED);
                    mUpdaterController.notifyUpdateChange(mDownloadId);
                }
                break;
            }
        }
    };

    static synchronized boolean start(UpdaterController updaterController,
            String downloadId) {
        if (sIsInstallingUpdate) {
            return false;
        }
        ABUpdateInstaller installer = new ABUpdateInstaller(updaterController, downloadId);
        sIsInstallingUpdate = installer.startUpdate();
        return sIsInstallingUpdate;
    }

    static synchronized boolean isInstallingUpdate() {
        return sIsInstallingUpdate;
    }

    private ABUpdateInstaller(UpdaterController updaterController, String downloadId) {
        mUpdaterController = updaterController;
        mDownloadId = downloadId;
    }

    private boolean startUpdate() {
        File file = mUpdaterController.getActualUpdate(mDownloadId).getFile();
        if (!file.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            return false;
        }

        mUpdaterController.getActualUpdate(mDownloadId).setStatus(UpdateStatus.INSTALLING);
        mUpdaterController.notifyUpdateChange(mDownloadId);

        long offset;
        String[] headerKeyValuePairs;
        try {
            ZipFile zipFile = new ZipFile(file);
            offset = Utils.getZipEntryOffset(zipFile, PAYLOAD_BIN_PATH);
            ZipEntry payloadPropEntry = zipFile.getEntry(PAYLOAD_PROPERTIES_PATH);
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

        UpdateEngine updateEngine = new UpdateEngine();
        updateEngine.bind(mUpdateEngineCallback);
        String zipFileUri = "file://" + file.getAbsolutePath();
        updateEngine.applyPayload(zipFileUri, offset, 0, headerKeyValuePairs);

        return true;
    }

    static boolean isABUpdate(ZipFile zipFile) {
        return zipFile.getEntry(PAYLOAD_BIN_PATH) != null &&
                zipFile.getEntry(PAYLOAD_PROPERTIES_PATH) != null;
    }
}
