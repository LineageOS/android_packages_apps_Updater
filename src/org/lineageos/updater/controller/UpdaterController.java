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
import android.database.sqlite.SQLiteDatabase;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.lineageos.updater.UpdateDownload;
import org.lineageos.updater.UpdateStatus;
import org.lineageos.updater.UpdatesDbHelper;
import org.lineageos.updater.download.DownloadClient;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UpdaterController implements UpdaterControllerInt {

    public static final String ACTION_DOWNLOAD_PROGRESS = "action_download_progress";
    public static final String ACTION_INSTALL_PROGRESS = "action_install_progress";
    public static final String ACTION_UPDATE_REMOVED = "action_update_removed";
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
    private Set<String> mVerifyingUpdates = new HashSet<>();

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

        Utils.cleanupDownloadsDir(context);

        for (UpdateDownload update : mUpdatesDbHelper.getUpdates()) {
            addUpdate(update, false);
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

    void notifyUpdateChange(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_STATUS);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mBroadcastManager.sendBroadcast(intent);
    }

    void notifyUpdateDelete(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_REMOVED);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mBroadcastManager.sendBroadcast(intent);
    }

    void notifyDownloadProgress(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mBroadcastManager.sendBroadcast(intent);
    }

    void notifyInstallProgress(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_INSTALL_PROGRESS);
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
                if (update.getFileSize() <= 0) {
                    String contentLenght = headers.get("Content-Length");
                    if (contentLenght != null) {
                        try {
                            long size = Long.parseLong(contentLenght);
                            if (size > 0) {
                                update.setFileSize(size);
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Could not get content-length");
                        }
                    }
                }
                update.setStatus(UpdateStatus.DOWNLOADING);
                update.setPersistentStatus(UpdateStatus.Persistent.INCOMPLETE);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mUpdatesDbHelper.addUpdateWithOnConflict(update,
                                SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }).start();
                notifyUpdateChange(downloadId);
            }

            @Override
            public void onSuccess(File destination) {
                Log.d(TAG, "Download complete");
                UpdateDownload update = mDownloads.get(downloadId).mUpdate;
                update.setStatus(UpdateStatus.VERIFYING);
                removeDownloadClient(mDownloads.get(downloadId));
                verifyUpdateAsync(downloadId);
                notifyUpdateChange(downloadId);
                tryReleaseWakelock();
            }

            @Override
            public void onFailure(boolean cancelled) {
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
                UpdateDownload update = mDownloads.get(downloadId).mUpdate;
                if (contentLength <= 0) {
                    if (update.getFileSize() <= 0) {
                        return;
                    } else {
                        contentLength = update.getFileSize();
                    }
                }
                if (contentLength <= 0) {
                    return;
                }
                final long now = SystemClock.elapsedRealtime();
                int progress = Math.round(bytesRead * 100 / contentLength);
                if (progress != mProgress || mLastUpdate - now > MAX_REPORT_INTERVAL_MS) {
                    mProgress = progress;
                    mLastUpdate = now;
                    update.setProgress(progress);
                    update.setEta(eta);
                    update.setSpeed(speed);
                    notifyDownloadProgress(downloadId);
                }
            }
        };
    }

    private void verifyUpdateAsync(final String downloadId) {
        mVerifyingUpdates.add(downloadId);
        new Thread(new Runnable() {
            @Override
            public void run() {
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
                mVerifyingUpdates.remove(downloadId);
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
                } else if (update.getFileSize() > 0) {
                    update.setStatus(UpdateStatus.PAUSED);
                    int progress = Math.round(
                            update.getFile().length() * 100 / update.getFileSize());
                    update.setProgress(progress);
                }
                break;
        }
        return true;
    }

    @Override
    public void setUpdatesNotAvailableOnline(List<String> downloadIds) {
        for (String downloadId : downloadIds) {
            DownloadEntry update = mDownloads.get(downloadId);
            if (update != null) {
                update.mUpdate.setAvailableOnline(false);
            }
        }
    }

    @Override
    public void setUpdatesAvailableOnline(List<String> downloadIds, boolean purgeList) {
        List<String> toRemove = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            boolean online = downloadIds.contains(entry.mUpdate.getDownloadId());
            entry.mUpdate.setAvailableOnline(online);
            if (!online && purgeList &&
                    entry.mUpdate.getPersistentStatus() == UpdateStatus.Persistent.UNKNOWN) {
                toRemove.add(entry.mUpdate.getDownloadId());
            }
        }
        for (String downloadId : toRemove) {
            Log.d(TAG, downloadId + " no longer available online, removing");
            mDownloads.remove(downloadId);
            notifyUpdateDelete(downloadId);
        }
    }

    @Override
    public boolean addUpdate(UpdateDownload update) {
        return addUpdate(update, true);
    }

    private boolean addUpdate(final UpdateDownload update, boolean availableOnline) {
        Log.d(TAG, "Adding download: " + update.getDownloadId());
        if (mDownloads.containsKey(update.getDownloadId())) {
            Log.d(TAG, "Download (" + update.getDownloadId() + ") already added");
            UpdateDownload updateAdded = mDownloads.get(update.getDownloadId()).mUpdate;
            updateAdded.setAvailableOnline(availableOnline && updateAdded.getAvailableOnline());
            updateAdded.setDownloadUrl(update.getDownloadUrl());
            return false;
        }
        if (!fixUpdateStatus(update) && !availableOnline) {
            update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
            deleteUpdateAsync(update);
            Log.d(TAG, update.getDownloadId() + " had an invalid status and is not online");
            return false;
        }
        update.setAvailableOnline(availableOnline);
        UpdateDownload updateCopy = new UpdateDownload(update);
        mDownloads.put(update.getDownloadId(), new DownloadEntry(updateCopy));
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
        if (destination.exists()) {
            destination = Utils.appendSequentialNumber(destination);
            Log.d(TAG, "Changing name with " + destination.getName());
        }
        update.setFile(destination);
        DownloadClient downloadClient = new DownloadClient.Builder()
                .setUrl(update.getDownloadUrl())
                .setDestination(update.getFile())
                .setDownloadCallback(getDownloadCallback(downloadId))
                .setProgressListener(getProgressListener(downloadId))
                .build();
        addDownloadClient(mDownloads.get(downloadId), downloadClient);
        update.setStatus(UpdateStatus.STARTING);
        notifyUpdateChange(downloadId);
        downloadClient.start();
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
        if (file == null || !file.exists()) {
            Log.e(TAG, "The destination file of " + downloadId + " doesn't exist, can't resume");
            update.setStatus(UpdateStatus.PAUSED_ERROR);
            notifyUpdateChange(downloadId);
            return false;
        }
        if (file.exists() && update.getFileSize() > 0 && file.length() >= update.getFileSize()) {
            Log.d(TAG, "File already downloaded, starting verification");
            update.setStatus(UpdateStatus.VERIFYING);
            verifyUpdateAsync(downloadId);
            notifyUpdateChange(downloadId);
        } else {
            DownloadClient downloadClient = new DownloadClient.Builder()
                    .setUrl(update.getDownloadUrl())
                    .setDestination(update.getFile())
                    .setDownloadCallback(getDownloadCallback(downloadId))
                    .setProgressListener(getProgressListener(downloadId))
                    .build();
            addDownloadClient(mDownloads.get(downloadId), downloadClient);
            update.setStatus(UpdateStatus.STARTING);
            notifyUpdateChange(downloadId);
            downloadClient.resume();
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

        DownloadEntry entry = mDownloads.get(downloadId);
        entry.mDownloadClient.cancel();
        removeDownloadClient(entry);
        entry.mUpdate.setStatus(UpdateStatus.PAUSED);
        entry.mUpdate.setEta(0);
        entry.mUpdate.setSpeed(0);
        notifyUpdateChange(downloadId);
        return true;
    }

    private void deleteUpdateAsync(final UpdateDownload update) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File file = update.getFile();
                if (file.exists() && !file.delete()) {
                    Log.e(TAG, "Could not delete " + file.getAbsolutePath());
                }
                mUpdatesDbHelper.removeUpdate(update.getDownloadId());
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
        deleteUpdateAsync(update);

        if (!update.getAvailableOnline()) {
            Log.d(TAG, "Download no longer available online, removing");
            mDownloads.remove(downloadId);
            notifyUpdateDelete(downloadId);
        } else {
            notifyUpdateChange(downloadId);
        }

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
            updates.add(new UpdateDownload(entry.mUpdate));
        }
        return updates;
    }

    @Override
    public UpdateDownload getUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? new UpdateDownload(entry.mUpdate) : null;
    }

    UpdateDownload getActualUpdate(String downloadId) {
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
        return mVerifyingUpdates.size() > 0;
    }

    @Override
    public boolean isVerifyingUpdate(String downloadId) {
        return mVerifyingUpdates.contains(downloadId);
    }

    @Override
    public boolean isInstallingUpdate() {
        return ABUpdateInstaller.isInstallingUpdate();
    }

    @Override
    public boolean isInstallingUpdate(String downloadId) {
        return ABUpdateInstaller.isInstallingUpdate(downloadId);
    }
}
