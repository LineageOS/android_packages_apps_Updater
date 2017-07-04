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
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.lineageos.updater.DownloadClient;
import org.lineageos.updater.UpdateDownload;
import org.lineageos.updater.UpdateStatus;
import org.lineageos.updater.UpdatesDbHelper;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UpdaterController implements UpdaterControllerInt {

    public static final String ACTION_DOWNLOAD_PROGRESS = "action_download_progress";
    public static final String ACTION_UPDATE_STATUS = "action_update_status_change";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";

    private final String TAG = "UpdaterController";

    private static UpdaterController sUpdaterController;

    private static final int MAX_REPORT_INTERVAL_MS = 1000;

    private final LocalBroadcastManager mBroadcastManager;
    private final UpdatesDbHelper mUpdatesDbHelper;

    private final PowerManager.WakeLock mWakeLock;

    private final File mDownloadRoot;

    private int mActiveDownloads = 0;
    private int mVerifyingUpdates = 0;

    public static synchronized UpdaterController getInstance() {
        return sUpdaterController;
    }

    protected static synchronized UpdaterController getInstance(Context context) {
        if (sUpdaterController == null) {
            sUpdaterController = new UpdaterController(context);
        }
        return sUpdaterController;
    }

    private UpdaterController(Context context) {
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mUpdatesDbHelper = new UpdatesDbHelper(context);
        mDownloadRoot = Utils.getDownloadPath(context);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Updater");
        mWakeLock.setReferenceCounted(false);

        for (UpdateDownload update : mUpdatesDbHelper.getUpdates()) {
            addUpdate(update, true);
        }
    }

    private class DownloadEntry {
        final UpdateDownload mUpdate;
        DownloadClient mDownloadClient;
        private DownloadEntry(UpdateDownload update) {
            mUpdate = update;
        }
    }

    private Map<String, DownloadEntry> mDownloads = new HashMap<>();

    private void notifyUpdateChange(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_STATUS);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mBroadcastManager.sendBroadcast(intent);
    }

    private void notifyDownloadProgress(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mBroadcastManager.sendBroadcast(intent);
    }

    private void tryReleaseWakelock() {
        if (!hasActiveDownloads()) {
            mWakeLock.release();
        }
    }

    private void addDownloadClient(DownloadEntry entry, DownloadClient downloadClient) {
        if (entry.mDownloadClient != null) {
            return;
        }
        entry.mDownloadClient = downloadClient;
        mActiveDownloads++;
    }

    private void removeDownloadClient(DownloadEntry entry) {
        if (entry.mDownloadClient == null) {
            return;
        }
        entry.mDownloadClient = null;
        mActiveDownloads--;
    }

    private DownloadClient.DownloadCallback getDownloadCallback(final String downloadId) {
        return new DownloadClient.DownloadCallback() {

            @Override
            public void onResponse(int statusCode, String url, DownloadClient.Headers headers) {
                final UpdateDownload update = mDownloads.get(downloadId).mUpdate;
                String contentLenght = headers.get("Content-Length");
                if (contentLenght != null) {
                    try {
                        long size = Long.parseLong(contentLenght);
                        update.setFileSize(size);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Could not get content-length");
                    }
                }
                update.setStatus(UpdateStatus.DOWNLOADING);
                update.setPersistentStatus(UpdateStatus.Persistent.INCOMPLETE);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mUpdatesDbHelper.addUpdateWithOnConflict(update);
                    }
                }).start();
                notifyUpdateChange(downloadId);
            }

            @Override
            public void onSuccess(String body) {
                Log.d(TAG, "Download complete");
                UpdateDownload update = mDownloads.get(downloadId).mUpdate;
                update.setStatus(UpdateStatus.VERIFYING);
                removeDownloadClient(mDownloads.get(downloadId));
                verifyUpdateAsync(downloadId);
                notifyUpdateChange(downloadId);
                tryReleaseWakelock();
            }

            @Override
            public void onFailure() {
                // The client is null if we intentionally stopped the download
                boolean cancelled = mDownloads.get(downloadId).mDownloadClient == null;
                UpdateDownload update = mDownloads.get(downloadId).mUpdate;
                if (cancelled) {
                    Log.d(TAG, "Download cancelled");
                    update.setStatus(UpdateStatus.PAUSED);
                    // Already notified
                } else {
                    Log.e(TAG, "Download failed");
                    removeDownloadClient(mDownloads.get(downloadId));
                    update.setStatus(UpdateStatus.PAUSED_ERROR);
                    notifyUpdateChange(downloadId);
                }
                tryReleaseWakelock();
            }
        };
    }

    private DownloadClient.ProgressListener getProgressListener(final String downloadId) {
        return new DownloadClient.ProgressListener() {
            private long mLastUpdate = 0;
            private int mProgress = 0;

            @Override
            public void update(long bytesRead, long contentLength, long speed, long eta,
                    boolean done) {
                final long now = SystemClock.elapsedRealtime();
                int progress = Math.round(bytesRead * 100 / contentLength);
                if (progress != mProgress || mLastUpdate - now > MAX_REPORT_INTERVAL_MS) {
                    mProgress = progress;
                    mLastUpdate = now;
                    getUpdate(downloadId).setProgress(progress);
                    getUpdate(downloadId).setEta(eta);
                    getUpdate(downloadId).setSpeed(speed);
                    notifyDownloadProgress(downloadId);
                }
            }
        };
    }

    private void verifyUpdateAsync(final String downloadId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mVerifyingUpdates++;
                UpdateDownload update = mDownloads.get(downloadId).mUpdate;
                File file = update.getFile();
                if (file.exists() && verifyPackage(file)) {
                    update.setPersistentStatus(UpdateStatus.Persistent.VERIFIED);
                    mUpdatesDbHelper.changeUpdateStatus(update);
                    update.setStatus(UpdateStatus.VERIFIED);
                } else {
                    update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
                    mUpdatesDbHelper.removeUpdate(downloadId);
                    update.setProgress(0);
                    update.setStatus(UpdateStatus.VERIFICATION_FAILED);
                }
                mVerifyingUpdates--;
                notifyUpdateChange(downloadId);
            }
        }).start();
    }

    private boolean verifyPackage(File file) {
        try {
            android.os.RecoverySystem.verifyPackage(file, null, null);
            Log.e(TAG, "Verification successful");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Verification failed", e);
            if (file.exists()) {
                file.delete();
            } else {
                // The download was probably stopped. Exit silently
                Log.e(TAG, "Error while verifying the file", e);
            }
            return false;
        }
    }

    private boolean fixUpdateStatus(UpdateDownload update) {
        switch (update.getPersistentStatus()) {
            case UpdateStatus.Persistent.VERIFIED:
            case UpdateStatus.Persistent.INCOMPLETE:
                if (update.getFile() == null || !update.getFile().exists()) {
                    update.setStatus(UpdateStatus.UNKNOWN);
                    return false;
                } else {
                    int progress = Math.round(
                            update.getFile().length() * 100 / update.getFileSize());
                    update.setProgress(progress);
                }
                break;
        }
        return true;
    }

    @Override
    public boolean addUpdate(UpdateDownload update) {
        return addUpdate(update, false);
    }

    private boolean addUpdate(final UpdateDownload update, boolean local) {
        Log.d(TAG, "Adding download: " + update.getDownloadId());
        if (mDownloads.containsKey(update.getDownloadId())) {
            Log.e(TAG, "Download (" + update.getDownloadId() + ") already added");
            return false;
        }
        if (!fixUpdateStatus(update) && local) {
            update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mUpdatesDbHelper.removeUpdate(update.getDownloadId());
                }
            }).start();
            Log.d(TAG, update.getDownloadId() + " had an invalid status and is local");
            return false;
        }
        mDownloads.put(update.getDownloadId(), new DownloadEntry(update));
        return true;
    }

    @Override
    public boolean startDownload(String downloadId) {
        Log.d(TAG, "Starting " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return false;
        }
        UpdateDownload update = mDownloads.get(downloadId).mUpdate;
        File destination = new File(mDownloadRoot, update.getName());
        update.setFile(destination);
        DownloadClient downloadClient =
                DownloadClient.downloadFile(update.getDownloadUrl(),
                        update.getFile(),
                        getDownloadCallback(downloadId),
                        getProgressListener(downloadId));
        addDownloadClient(mDownloads.get(downloadId), downloadClient);
        update.setStatus(UpdateStatus.STARTING);
        notifyUpdateChange(downloadId);
        mWakeLock.acquire();
        return true;
    }

    @Override
    public boolean resumeDownload(String downloadId) {
        Log.d(TAG, "Resuming " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return false;
        }
        UpdateDownload update = mDownloads.get(downloadId).mUpdate;
        File file = update.getFile();
        if (file.exists() && file.length() == update.getFileSize()) {
            Log.d(TAG, "File already downloaded, starting verification");
            update.setStatus(UpdateStatus.VERIFYING);
            verifyUpdateAsync(downloadId);
            notifyUpdateChange(downloadId);
        } else {
            DownloadClient downloadClient =
                    DownloadClient.downloadFileResume(update.getDownloadUrl(),
                            update.getFile(),
                            getDownloadCallback(downloadId),
                            getProgressListener(downloadId));
            addDownloadClient(mDownloads.get(downloadId), downloadClient);
            update.setStatus(UpdateStatus.STARTING);
            notifyUpdateChange(downloadId);
            mWakeLock.acquire();
        }
        return true;
    }

    @Override
    public boolean pauseDownload(String downloadId) {
        Log.d(TAG, "Pausing " + downloadId);
        if (!isDownloading(downloadId)) {
            return false;
        }

        // First remove the client and then cancel the download so that when the download
        // fails, we know it was intentional
        DownloadClient downloadClient = mDownloads.get(downloadId).mDownloadClient;
        removeDownloadClient(mDownloads.get(downloadId));
        downloadClient.cancel();
        UpdateDownload update = mDownloads.get(downloadId).mUpdate;
        update.setStatus(UpdateStatus.PAUSED);
        notifyUpdateChange(downloadId);
        return true;
    }

    private void deleteUpdateAsync(final String downloadId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                UpdateDownload update = mDownloads.get(downloadId).mUpdate;
                File file = update.getFile();
                if (file.exists() && !file.delete()) {
                    Log.e(TAG, "Could not delete " + file.getAbsolutePath());
                }
                mUpdatesDbHelper.removeUpdate(downloadId);
            }
        }).start();
    }

    @Override
    public boolean cancelDownload(String downloadId) {
        Log.d(TAG, "Cancelling " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return false;
        }
        UpdateDownload update = mDownloads.get(downloadId).mUpdate;
        update.setStatus(UpdateStatus.DELETED);
        update.setProgress(0);
        update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
        deleteUpdateAsync(downloadId);
        notifyUpdateChange(downloadId);
        return true;
    }

    @Override
    public Set<String> getIds() {
        return mDownloads.keySet();
    }

    @Override
    public List<UpdateDownload> getUpdates() {
        List<UpdateDownload> updates = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            updates.add(entry.mUpdate);
        }
        return updates;
    }

    @Override
    public UpdateDownload getUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? entry.mUpdate : null;
    }

    @Override
    public boolean isDownloading(String downloadId) {
        return mDownloads.containsKey(downloadId) &&
                mDownloads.get(downloadId).mDownloadClient != null;
    }

    @Override
    public boolean hasActiveDownloads() {
        return mActiveDownloads > 0;
    }

    @Override
    public boolean isVerifyingUpdate() {
        return mVerifyingUpdates > 0;
    }
}
