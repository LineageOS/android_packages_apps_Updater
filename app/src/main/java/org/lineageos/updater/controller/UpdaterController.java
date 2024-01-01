/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.lineageos.updater.UpdatesDbHelper;
import org.lineageos.updater.download.DownloadClient;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.Update;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UpdaterController {

    public static final String ACTION_DOWNLOAD_PROGRESS = "action_download_progress";
    public static final String ACTION_INSTALL_PROGRESS = "action_install_progress";
    public static final String ACTION_UPDATE_REMOVED = "action_update_removed";
    public static final String ACTION_UPDATE_STATUS = "action_update_status_change";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";

    private final String TAG = "UpdaterController";

    private static UpdaterController sUpdaterController;

    private static final int MAX_REPORT_INTERVAL_MS = 1000;

    private final Context mContext;
    private final LocalBroadcastManager mBroadcastManager;
    private final UpdatesDbHelper mUpdatesDbHelper;

    private final PowerManager.WakeLock mWakeLock;

    private final File mDownloadRoot;

    private int mActiveDownloads = 0;
    private final Set<String> mVerifyingUpdates = new HashSet<>();

    public static synchronized UpdaterController getInstance(Context context) {
        if (sUpdaterController == null) {
            sUpdaterController = new UpdaterController(context);
        }
        return sUpdaterController;
    }

    private UpdaterController(Context context) {
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mUpdatesDbHelper = new UpdatesDbHelper(context);
        mDownloadRoot = Utils.getDownloadPath(context);
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Updater:wakelock");
        mWakeLock.setReferenceCounted(false);
        mContext = context.getApplicationContext();

        Utils.cleanupDownloadsDir(context);

        for (Update update : mUpdatesDbHelper.getUpdates()) {
            addUpdate(update, false);
        }
    }

    private static class DownloadEntry {
        final Update mUpdate;
        DownloadClient mDownloadClient;
        private DownloadEntry(Update update) {
            mUpdate = update;
        }
    }

    private final Map<String, DownloadEntry> mDownloads = new HashMap<>();

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
            public void onResponse(DownloadClient.Headers headers) {
                final DownloadEntry entry = mDownloads.get(downloadId);
                if (entry == null) {
                    return;
                }
                final Update update = entry.mUpdate;
                String contentLength = headers.get("Content-Length");
                if (contentLength != null) {
                    try {
                        long size = Long.parseLong(contentLength);
                        if (update.getFileSize() < size) {
                            update.setFileSize(size);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Could not get content-length");
                    }
                }
                update.setStatus(UpdateStatus.DOWNLOADING);
                update.setPersistentStatus(UpdateStatus.Persistent.INCOMPLETE);
                new Thread(() -> mUpdatesDbHelper.addUpdateWithOnConflict(update,
                        SQLiteDatabase.CONFLICT_REPLACE)).start();
                notifyUpdateChange(downloadId);
            }

            @Override
            public void onSuccess() {
                Log.d(TAG, "Download complete");
                DownloadEntry entry = mDownloads.get(downloadId);
                if (entry != null) {
                    Update update = entry.mUpdate;
                    update.setStatus(UpdateStatus.VERIFYING);
                    removeDownloadClient(entry);
                    verifyUpdateAsync(downloadId);
                    notifyUpdateChange(downloadId);
                    tryReleaseWakelock();
                }
            }

            @Override
            public void onFailure(boolean cancelled) {
                if (cancelled) {
                    Log.d(TAG, "Download cancelled");
                    // Already notified
                } else {
                    DownloadEntry entry = mDownloads.get(downloadId);
                    if (entry != null) {
                        Update update = entry.mUpdate;
                        Log.e(TAG, "Download failed");
                        removeDownloadClient(entry);
                        update.setStatus(UpdateStatus.PAUSED_ERROR);
                        notifyUpdateChange(downloadId);
                    }
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
            public void update(long bytesRead, long contentLength, long speed, long eta) {
                DownloadEntry entry = mDownloads.get(downloadId);
                if (entry == null) {
                    return;
                }
                Update update = entry.mUpdate;
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
                int progress = Math.round(bytesRead * 100f / contentLength);
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

    @SuppressLint("SetWorldReadable")
    private void verifyUpdateAsync(final String downloadId) {
        mVerifyingUpdates.add(downloadId);
        new Thread(() -> {
            DownloadEntry entry = mDownloads.get(downloadId);
            if (entry != null) {
                Update update = entry.mUpdate;
                File file = update.getFile();
                if (file.exists() && verifyPackage(file)) {
                    //noinspection ResultOfMethodCallIgnored
                    file.setReadable(true, false);
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
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            } else {
                // The download was probably stopped. Exit silently
                Log.e(TAG, "Error while verifying the file", e);
            }
            return false;
        }
    }

    private boolean fixUpdateStatus(Update update) {
        switch (update.getPersistentStatus()) {
            case UpdateStatus.Persistent.VERIFIED:
            case UpdateStatus.Persistent.INCOMPLETE:
                if (update.getFile() == null || !update.getFile().exists()) {
                    update.setStatus(UpdateStatus.UNKNOWN);
                    return false;
                } else if (update.getFileSize() > 0) {
                    update.setStatus(UpdateStatus.PAUSED);
                    int progress = Math.round(
                            update.getFile().length() * 100f / update.getFileSize());
                    update.setProgress(progress);
                }
                break;
        }
        return true;
    }

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

    public boolean addUpdate(UpdateInfo update) {
        return addUpdate(update, true);
    }

    public boolean addUpdate(final UpdateInfo updateInfo, boolean availableOnline) {
        Log.d(TAG, "Adding download: " + updateInfo.getDownloadId());
        if (mDownloads.containsKey(updateInfo.getDownloadId())) {
            Log.d(TAG, "Download (" + updateInfo.getDownloadId() + ") already added");
            DownloadEntry entry = mDownloads.get(updateInfo.getDownloadId());
            if (entry != null) {
                Update updateAdded = entry.mUpdate;
                updateAdded.setAvailableOnline(availableOnline && updateAdded.getAvailableOnline());
                updateAdded.setDownloadUrl(updateInfo.getDownloadUrl());
            }
            return false;
        }
        Update update = new Update(updateInfo);
        if (!fixUpdateStatus(update) && !availableOnline) {
            update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
            deleteUpdateAsync(update);
            Log.d(TAG, update.getDownloadId() + " had an invalid status and is not online");
            return false;
        }
        update.setAvailableOnline(availableOnline);
        mDownloads.put(update.getDownloadId(), new DownloadEntry(update));
        return true;
    }

    @SuppressLint("WakelockTimeout")
    public void startDownload(String downloadId) {
        Log.d(TAG, "Starting " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry == null) {
            Log.e(TAG, "Could not get download entry");
            return;
        }
        Update update = entry.mUpdate;
        File destination = new File(mDownloadRoot, update.getName());
        if (destination.exists()) {
            destination = Utils.appendSequentialNumber(destination);
            Log.d(TAG, "Changing name with " + destination.getName());
        }
        update.setFile(destination);
        DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(update.getDownloadUrl())
                    .setDestination(update.getFile())
                    .setDownloadCallback(getDownloadCallback(downloadId))
                    .setProgressListener(getProgressListener(downloadId))
                    .setUseDuplicateLinks(true)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            update.setStatus(UpdateStatus.PAUSED_ERROR);
            notifyUpdateChange(downloadId);
            return;
        }
        addDownloadClient(entry, downloadClient);
        update.setStatus(UpdateStatus.STARTING);
        notifyUpdateChange(downloadId);
        downloadClient.start();
        mWakeLock.acquire();
    }

    @SuppressLint("WakelockTimeout")
    public void resumeDownload(String downloadId) {
        Log.d(TAG, "Resuming " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry == null) {
            Log.e(TAG, "Could not get download entry");
            return;
        }
        Update update = entry.mUpdate;
        File file = update.getFile();
        if (file == null || !file.exists()) {
            Log.e(TAG, "The destination file of " + downloadId + " doesn't exist, can't resume");
            update.setStatus(UpdateStatus.PAUSED_ERROR);
            notifyUpdateChange(downloadId);
            return;
        }
        if (file.exists() && update.getFileSize() > 0 && file.length() >= update.getFileSize()) {
            Log.d(TAG, "File already downloaded, starting verification");
            update.setStatus(UpdateStatus.VERIFYING);
            verifyUpdateAsync(downloadId);
            notifyUpdateChange(downloadId);
        } else {
            DownloadClient downloadClient;
            try {
                downloadClient = new DownloadClient.Builder()
                        .setUrl(update.getDownloadUrl())
                        .setDestination(update.getFile())
                        .setDownloadCallback(getDownloadCallback(downloadId))
                        .setProgressListener(getProgressListener(downloadId))
                        .setUseDuplicateLinks(true)
                        .build();
            } catch (IOException exception) {
                Log.e(TAG, "Could not build download client");
                update.setStatus(UpdateStatus.PAUSED_ERROR);
                notifyUpdateChange(downloadId);
                return;
            }
            addDownloadClient(entry, downloadClient);
            update.setStatus(UpdateStatus.STARTING);
            notifyUpdateChange(downloadId);
            downloadClient.resume();
            mWakeLock.acquire();
        }
    }

    public void pauseDownload(String downloadId) {
        Log.d(TAG, "Pausing " + downloadId);
        if (!isDownloading(downloadId)) {
            return;
        }

        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry != null) {
            entry.mDownloadClient.cancel();
            removeDownloadClient(entry);
            entry.mUpdate.setStatus(UpdateStatus.PAUSED);
            entry.mUpdate.setEta(0);
            entry.mUpdate.setSpeed(0);
            notifyUpdateChange(downloadId);
        }
    }

    private void deleteUpdateAsync(final Update update) {
        new Thread(() -> {
            File file = update.getFile();
            if (file.exists() && !file.delete()) {
                Log.e(TAG, "Could not delete " + file.getAbsolutePath());
            }
            mUpdatesDbHelper.removeUpdate(update.getDownloadId());
        }).start();
    }

    public void deleteUpdate(String downloadId) {
        Log.d(TAG, "Deleting update: " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry != null) {
            Update update = entry.mUpdate;
            update.setStatus(UpdateStatus.DELETED);
            update.setProgress(0);
            update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
            deleteUpdateAsync(update);

            final boolean isLocalUpdate = Update.LOCAL_ID.equals(downloadId);
            if (!isLocalUpdate && !update.getAvailableOnline()) {
                Log.d(TAG, "Download no longer available online, removing");
                mDownloads.remove(downloadId);
                notifyUpdateDelete(downloadId);
            } else {
                notifyUpdateChange(downloadId);
            }
        }
    }

    public List<UpdateInfo> getUpdates() {
        List<UpdateInfo> updates = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            updates.add(entry.mUpdate);
        }
        return updates;
    }

    public UpdateInfo getUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? entry.mUpdate : null;
    }

    Update getActualUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? entry.mUpdate : null;
    }

    public boolean isDownloading(String downloadId) {
        //noinspection ConstantConditions
        return mDownloads.containsKey(downloadId) &&
                mDownloads.get(downloadId).mDownloadClient != null;
    }

    public boolean hasActiveDownloads() {
        return mActiveDownloads > 0;
    }

    public boolean isVerifyingUpdate() {
        return mVerifyingUpdates.size() > 0;
    }

    public boolean isVerifyingUpdate(String downloadId) {
        return mVerifyingUpdates.contains(downloadId);
    }

    public boolean isInstallingUpdate() {
        return UpdateInstaller.isInstalling() ||
                ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    public boolean isInstallingUpdate(String downloadId) {
        return UpdateInstaller.isInstalling(downloadId) ||
                ABUpdateInstaller.isInstallingUpdate(mContext, downloadId);
    }

    public boolean isInstallingABUpdate() {
        return ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    public boolean isWaitingForReboot(String downloadId) {
        return ABUpdateInstaller.isWaitingForReboot(mContext, downloadId);
    }

    public void setPerformanceMode(boolean enable) {
        if (!Utils.isABDevice()) {
            return;
        }
        ABUpdateInstaller.getInstance(mContext, this).setPerformanceMode(enable);
    }
}
