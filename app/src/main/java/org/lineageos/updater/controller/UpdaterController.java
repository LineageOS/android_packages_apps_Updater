/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import org.lineageos.updater.UpdaterApplication;
import org.lineageos.updater.data.Update;
import org.lineageos.updater.data.UpdateStatus;
import org.lineageos.updater.data.UserPreferencesRepository;
import org.lineageos.updater.data.source.local.UpdatesLocalDataSource;
import org.lineageos.updater.data.source.local.UpdatesDatabase;
import org.lineageos.updater.download.DownloadClient;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final UpdatesLocalDataSource mUpdatesLocalDataSource;

    private final PowerManager.WakeLock mWakeLock;

    private final File mDownloadRoot;

    private int mActiveDownloads = 0;
    private final Set<String> mVerifyingUpdates = new HashSet<>();

    public static synchronized UpdaterController getInstance(Context context) {
        if (sUpdaterController == null) {
            UserPreferencesRepository userPreferencesRepository =
                    ((UpdaterApplication) context.getApplicationContext())
                            .getUserPreferencesRepository();
            sUpdaterController = new UpdaterController(context, userPreferencesRepository);
        }
        return sUpdaterController;
    }

    private UpdaterController(Context context, UserPreferencesRepository userPreferencesRepository) {
        mUpdatesLocalDataSource =
                new UpdatesLocalDataSource(UpdatesDatabase.getInstance(context).updateDao());
        mDownloadRoot = Utils.getDownloadPath(context);
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Updater:wakelock");
        mWakeLock.setReferenceCounted(false);
        mContext = context.getApplicationContext();

        new Thread(() -> {
            Utils.cleanupDownloadsDir(context, userPreferencesRepository);
            for (Update update : mUpdatesLocalDataSource.getUpdates()) {
                addUpdate(update, false);
            }
        }).start();
    }

    private static class DownloadEntry {
        Update mUpdate;
        DownloadClient mDownloadClient;
        private DownloadEntry(Update update) {
            mUpdate = update;
        }
    }

    private final Map<String, DownloadEntry> mDownloads = new ConcurrentHashMap<>();

    void notifyUpdateChange(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_STATUS);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mContext.sendBroadcast(intent);
    }

    void notifyUpdateDelete(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_REMOVED);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mContext.sendBroadcast(intent);
    }

    void notifyDownloadProgress(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_DOWNLOAD_PROGRESS);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mContext.sendBroadcast(intent);
    }

    void notifyInstallProgress(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_INSTALL_PROGRESS);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mContext.sendBroadcast(intent);
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
                final Update newUpdate;
                synchronized (entry) {
                    final Update update = entry.mUpdate;
                    Update.Builder builder = update.toBuilder();
                    String contentLength = headers.get("Content-Length");
                    if (contentLength != null) {
                        try {
                            long size = Long.parseLong(contentLength);
                            if (update.getFileSize() < size) {
                                builder.setFileSize(size);
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Could not get content-length");
                        }
                    }
                    builder.setStatus(UpdateStatus.DOWNLOADING);
                    newUpdate = builder.build();
                    entry.mUpdate = newUpdate;
                }
                new Thread(() -> mUpdatesLocalDataSource.addUpdate(newUpdate)).start();
                notifyUpdateChange(downloadId);
            }

            @Override
            public void onSuccess() {
                Log.d(TAG, "Download complete");
                DownloadEntry entry = mDownloads.get(downloadId);
                if (entry != null) {
                    synchronized (entry) {
                        entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.VERIFYING);
                    }
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
                        Log.e(TAG, "Download failed");
                        removeDownloadClient(entry);
                        synchronized (entry) {
                            entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.PAUSED_ERROR);
                        }
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
                    synchronized (entry) {
                        entry.mUpdate = entry.mUpdate.toBuilder()
                                .setProgress(progress)
                                .setEta(eta)
                                .setSpeed(speed)
                                .build();
                    }
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
                    file.setReadable(true, false);
                    synchronized (entry) {
                        entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.VERIFIED);
                    }
                    mUpdatesLocalDataSource.changeStatus(downloadId, UpdateStatus.VERIFIED);
                } else {
                    mUpdatesLocalDataSource.removeUpdate(downloadId);
                    synchronized (entry) {
                        entry.mUpdate = entry.mUpdate.toBuilder()
                                .setProgress(0)
                                .setStatus(UpdateStatus.VERIFICATION_FAILED)
                                .build();
                    }
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

    public void setUpdatesAvailableOnline(List<String> downloadIds, boolean purgeList) {
        List<String> toRemove = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            boolean online = downloadIds.contains(entry.mUpdate.getDownloadId());
            entry.mUpdate = entry.mUpdate.withAvailableOnline(online);
            if (!online && purgeList &&
                    entry.mUpdate.getStatus().getPersistentStatus() == 0) {
                toRemove.add(entry.mUpdate.getDownloadId());
            }
        }
        for (String downloadId : toRemove) {
            Log.d(TAG, downloadId + " no longer available online, removing");
            mDownloads.remove(downloadId);
            notifyUpdateDelete(downloadId);
        }
    }

    public boolean addUpdate(Update update) {
        return addUpdate(update, true);
    }

    public boolean addUpdate(final Update updateInfo, boolean availableOnline) {
        Log.d(TAG, "Adding download: " + updateInfo.getDownloadId());
        if (mDownloads.containsKey(updateInfo.getDownloadId())) {
            Log.d(TAG, "Download (" + updateInfo.getDownloadId() + ") already added");
            DownloadEntry entry = mDownloads.get(updateInfo.getDownloadId());
            if (entry != null) {
                synchronized (entry) {
                    entry.mUpdate = entry.mUpdate.toBuilder()
                            .setAvailableOnline(availableOnline && entry.mUpdate.isAvailableOnline())
                            .setDownloadUrl(updateInfo.getDownloadUrl())
                            .build();
                }
            }
            return false;
        }
        Update.Builder builder = updateInfo.toBuilder();
        if (updateInfo.getStatus().hasVerifiedPackage()) {
            boolean isLocallyValid = true;
            if (updateInfo.getFile() == null || !updateInfo.getFile().exists()) {
                isLocallyValid = false;
                builder.setStatus(UpdateStatus.UPDATE_AVAILABLE);
                builder.setProgress(0);
            }

            if (!isLocallyValid && !availableOnline) {
                deleteUpdateAsync(updateInfo);
                Log.d(TAG, updateInfo.getDownloadId() + " had an invalid status and is not online");
                return false;
            }
        } else if (updateInfo.getStatus() == UpdateStatus.PAUSED ||
                updateInfo.getStatus() == UpdateStatus.PAUSED_ERROR) {
            boolean isLocallyValid = true;
            if (updateInfo.getFile() == null || !updateInfo.getFile().exists()) {
                isLocallyValid = false;
                builder.setStatus(UpdateStatus.UPDATE_AVAILABLE);
                builder.setProgress(0);
            } else if (updateInfo.getFileSize() > 0) {
                builder.setStatus(UpdateStatus.PAUSED);
                int progress = Math.round(
                        updateInfo.getFile().length() * 100f / updateInfo.getFileSize());
                builder.setProgress(progress);
            }

            if (!isLocallyValid && !availableOnline) {
                deleteUpdateAsync(updateInfo);
                Log.d(TAG, updateInfo.getDownloadId() + " had an invalid status and is not online");
                return false;
            }
        }
        builder.setAvailableOnline(availableOnline);
        mDownloads.put(updateInfo.getDownloadId(), new DownloadEntry(builder.build()));
        return true;
    }

    @SuppressLint("WakelockTimeout")
    public void startDownload(String downloadId) {
        Log.d(TAG, "Starting " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        pauseActiveDownloads();
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
        entry.mUpdate = update.withFile(destination);
        DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(entry.mUpdate.getDownloadUrl())
                    .setDestination(entry.mUpdate.getFile())
                    .setDownloadCallback(getDownloadCallback(downloadId))
                    .setProgressListener(getProgressListener(downloadId))
                    .setUseDuplicateLinks(true)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.PAUSED_ERROR);
            notifyUpdateChange(downloadId);
            return;
        }
        addDownloadClient(entry, downloadClient);
        entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.STARTING);
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
        pauseActiveDownloads();
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry == null) {
            Log.e(TAG, "Could not get download entry");
            return;
        }
        Update update = entry.mUpdate;
        File file = update.getFile();
        if (file == null || !file.exists()) {
            Log.e(TAG, "The destination file of " + downloadId + " doesn't exist, can't resume");
            entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.PAUSED_ERROR);
            notifyUpdateChange(downloadId);
            return;
        }
        if (file.exists() && update.getFileSize() > 0 && file.length() >= update.getFileSize()) {
            Log.d(TAG, "File already downloaded, starting verification");
            entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.VERIFYING);
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
                entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.PAUSED_ERROR);
                notifyUpdateChange(downloadId);
                return;
            }
            addDownloadClient(entry, downloadClient);
            entry.mUpdate = entry.mUpdate.withStatus(UpdateStatus.STARTING);
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
            synchronized (entry) {
                entry.mUpdate = entry.mUpdate.toBuilder()
                        .setStatus(UpdateStatus.PAUSED)
                        .setEta(0)
                        .setSpeed(0)
                        .build();
            }
            notifyUpdateChange(downloadId);
        }
    }

    private void deleteUpdateAsync(final Update update) {
        new Thread(() -> {
            File file = update.getFile();
            if (file.exists() && !file.delete()) {
                Log.e(TAG, "Could not delete " + file.getAbsolutePath());
            }
            mUpdatesLocalDataSource.removeUpdate(update.getDownloadId());
        }).start();
    }

    public void deleteUpdate(String downloadId) {
        Log.d(TAG, "Deleting update: " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry != null) {
            Update update;
            synchronized (entry) {
                update = entry.mUpdate.toBuilder()
                        .setStatus(UpdateStatus.DELETED)
                        .setProgress(0)
                        .build();
                entry.mUpdate = update;
            }
            deleteUpdateAsync(update);

            final boolean isLocalUpdate = Update.LOCAL_ID.equals(downloadId);
            if (!isLocalUpdate && !update.isAvailableOnline()) {
                Log.d(TAG, "Download no longer available online, removing");
                mDownloads.remove(downloadId);
                notifyUpdateDelete(downloadId);
            } else {
                notifyUpdateChange(downloadId);
            }
        }
    }

    public void cancelDownload(String downloadId) {
        Log.d(TAG, "Cancelling download: " + downloadId);
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry == null) {
            return;
        }
        // Pause the download if it's active
        if (isDownloading(downloadId)) {
            entry.mDownloadClient.cancel();
            removeDownloadClient(entry);
        }
        Update update;
        synchronized (entry) {
            update = entry.mUpdate.toBuilder()
                    .setStatus(UpdateStatus.DELETED)
                    .setProgress(0)
                    .setEta(0)
                    .setSpeed(0)
                    .build();
            entry.mUpdate = update;
        }
        deleteUpdateAsync(update);

        final boolean isLocalUpdate = Update.LOCAL_ID.equals(downloadId);
        if (!isLocalUpdate && !update.isAvailableOnline()) {
            Log.d(TAG, "Download no longer available online, removing");
            mDownloads.remove(downloadId);
            notifyUpdateDelete(downloadId);
        } else {
            notifyUpdateChange(downloadId);
        }
        tryReleaseWakelock();
    }

    public List<Update> getUpdates() {
        List<Update> updates = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            updates.add(entry.mUpdate);
        }
        return updates;
    }

    public Update getUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? entry.mUpdate : null;
    }

    public void setUpdate(String downloadId, Update update) {
        DownloadEntry entry = mDownloads.get(downloadId);
        if (entry != null) {
            synchronized (entry) {
                entry.mUpdate = update;
            }
        }
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
        return !mVerifyingUpdates.isEmpty();
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

    private void pauseActiveDownloads() {
        for (DownloadEntry entry : mDownloads.values()) {
            if (isDownloading(entry.mUpdate.getDownloadId())) {
                pauseDownload(entry.mUpdate.getDownloadId());
            }
        }
    }
}
