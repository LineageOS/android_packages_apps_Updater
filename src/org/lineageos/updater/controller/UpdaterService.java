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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.text.format.Formatter;
import android.util.Log;

import org.lineageos.updater.R;
import org.lineageos.updater.model.UpdateDownload;
import org.lineageos.updater.model.UpdateStatus;
import org.lineageos.updater.UpdaterReceiver;
import org.lineageos.updater.UpdatesActivity;
import org.lineageos.updater.misc.BuildInfoUtils;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;

import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;

public class UpdaterService extends Service {

    private static final String TAG = "UpdaterService";

    public static final String ACTION_DOWNLOAD_CONTROL = "action_download_control";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";
    public static final String EXTRA_DOWNLOAD_CONTROL = "extra_download_control";
    public static final String ACTION_INSTALL_UPDATE = "action_install_update";

    public static final int DOWNLOAD_RESUME = 0;
    public static final int DOWNLOAD_PAUSE = 1;

    private static final int NOTIFICATION_ID = 10;

    private final IBinder mBinder = new LocalBinder();
    private boolean mHasClients;

    private BroadcastReceiver mBroadcastReceiver;
    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;
    private NotificationCompat.BigTextStyle mNotificationStyle;;

    private UpdaterController mUpdaterController;

    @Override
    public void onCreate() {
        super.onCreate();

        mUpdaterController = UpdaterController.getInstance(this);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationBuilder = new NotificationCompat.Builder(this);
        mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        mNotificationBuilder.setShowWhen(false);
        mNotificationStyle = new NotificationCompat.BigTextStyle();
        mNotificationBuilder.setStyle(mNotificationStyle);

        Intent notificationIntent = new Intent(this, UpdatesActivity.class);
        PendingIntent intent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mNotificationBuilder.setContentIntent(intent);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    UpdateDownload update = mUpdaterController.getUpdate(downloadId);
                    setNotificationTitle(update);
                    Bundle extras = new Bundle();
                    extras.putString(UpdaterController.EXTRA_DOWNLOAD_ID, downloadId);
                    mNotificationBuilder.setExtras(extras);
                    handleUpdateStatusChange(update);
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction())) {
                    UpdateDownload update = mUpdaterController.getUpdate(downloadId);
                    handleDownloadProgressChange(update);
                } else if (UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    UpdateDownload update = mUpdaterController.getUpdate(downloadId);
                    setNotificationTitle(update);
                    handleInstallProgress(update);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    Bundle extras = mNotificationBuilder.getExtras();
                    if (extras != null && downloadId.equals(
                            extras.getString(UpdaterController.EXTRA_DOWNLOAD_ID))) {
                        mNotificationBuilder.setExtras(null);
                        mNotificationManager.cancel(NOTIFICATION_ID);
                    }
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);

    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        public UpdaterService getService() {
            return UpdaterService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        mHasClients = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mHasClients = false;
        tryStopSelf();
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_DOWNLOAD_CONTROL.equals(intent.getAction())) {
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            int action = intent.getIntExtra(EXTRA_DOWNLOAD_CONTROL, -1);
            if (action == DOWNLOAD_RESUME) {
                mUpdaterController.resumeDownload(downloadId);
            } else if (action == DOWNLOAD_PAUSE) {
                mUpdaterController.pauseDownload(downloadId);
            } else {
                Log.e(TAG, "Unknown download action");
            }
        } else if (ACTION_INSTALL_UPDATE.equals(intent.getAction())) {
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            UpdateDownload update = mUpdaterController.getUpdate(downloadId);
            if (update.getPersistentStatus() != UpdateStatus.Persistent.VERIFIED) {
                throw new IllegalArgumentException(update.getDownloadId() + " is not verified");
            }
            try {
                if (Utils.isABUpdate(update.getFile())) {
                    ABUpdateInstaller.start(mUpdaterController, downloadId);
                } else {
                    android.os.RecoverySystem.installPackage(this, update.getFile());
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not install update", e);
                // TODO: user facing message
            }
        }
        Log.d(TAG, "Service started");
        return START_NOT_STICKY;
    }

    public UpdaterControllerInt getUpdaterController() {
        return mUpdaterController;
    }

    private void tryStopSelf() {
        if (!mHasClients && !mUpdaterController.hasActiveDownloads()) {
            Log.d(TAG, "Service no longer needed, stopping");
            stopSelf();
        }
    }

    private void handleUpdateStatusChange(UpdateDownload update) {
        switch (update.getStatus()) {
            case DELETED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.cancel(NOTIFICATION_ID);
                tryStopSelf();
                break;
            }
            case STARTING: {
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setProgress(0, 0, true);
                mNotificationStyle.setSummaryText(null);
                String text = getString(R.string.download_starting_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(true);
                startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case DOWNLOADING: {
                String text = getString(R.string.downloading_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.addAction(com.android.internal.R.drawable.ic_media_pause,
                        getString(R.string.pause_button),
                        getPausePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(true);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case PAUSED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                // In case we pause before the first progress update
                mNotificationBuilder.setProgress(100, update.getProgress(), false);
                mNotificationBuilder.mActions.clear();
                String text = getString(R.string.download_paused_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.addAction(com.android.internal.R.drawable.ic_media_play,
                        getString(R.string.resume_button),
                        getResumePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case PAUSED_ERROR: {
                stopForeground(STOP_FOREGROUND_DETACH);
                int progress = update.getProgress();
                // In case we pause before the first progress update
                mNotificationBuilder.setProgress(progress > 0 ? 100 : 0, progress, false);
                mNotificationBuilder.mActions.clear();
                String text = getString(R.string.download_paused_error_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.addAction(com.android.internal.R.drawable.ic_media_play,
                        getString(R.string.resume_button),
                        getResumePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case VERIFYING: {
                mNotificationBuilder.setProgress(0, 0, true);
                mNotificationStyle.setSummaryText(null);
                mNotificationBuilder.mActions.clear();
                String text = getString(R.string.verifying_download_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case VERIFIED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setProgress(100, 100, false);
                String text = getString(R.string.download_completed_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case VERIFICATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.verification_failed_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLING: {
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setProgress(0, 0, true);
                mNotificationStyle.setSummaryText(null);
                String text = getString(R.string.installing_update);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(true);
                startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case INSTALLED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setProgress(100, 100, false);
                String text = getString(R.string.installing_update_finished);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.addAction(R.drawable.ic_tab_install,
                        getString(R.string.reboot),
                        getRebootPendingIntent());
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.installing_update_error);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
        }
    }

    private void handleDownloadProgressChange(UpdateDownload update) {
        int progress = update.getProgress();
        mNotificationBuilder.setProgress(100, progress, false);

        String percent = NumberFormat.getPercentInstance().format(progress / 100.f);
        mNotificationStyle.setSummaryText(percent);

        setNotificationTitle(update);

        String speed = Formatter.formatFileSize(this, update.getSpeed());
        CharSequence eta = StringGenerator.formatDuration(this, update.getEta() * 1000);
        mNotificationStyle.bigText(
                getString(R.string.text_download_speed, eta, speed));

        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void handleInstallProgress(UpdateDownload update) {
        int progress = update.getInstallProgress();
        mNotificationBuilder.setProgress(100, progress, false);

        setNotificationTitle(update);

        if (progress == 0) {
            mNotificationStyle.bigText(getString(R.string.finalizing_package));
            mNotificationBuilder.setProgress(0, 0, true);
        } else {
            String percent = NumberFormat.getPercentInstance().format(progress / 100.f);
            mNotificationStyle.setSummaryText(percent);
            mNotificationStyle.bigText(getString(R.string.preparing_ota_first_boot));
        }

        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void setNotificationTitle(UpdateDownload update) {
        String buildDate = StringGenerator.getDateLocalizedUTC(this,
                DateFormat.MEDIUM, update.getTimestamp());
        String buildInfo = getString(R.string.list_build_version_date,
                BuildInfoUtils.getBuildVersion(), buildDate);
        mNotificationStyle.setBigContentTitle(buildInfo);
        mNotificationBuilder.setContentTitle(buildInfo);
    }

    private PendingIntent getResumePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_DOWNLOAD_CONTROL);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_RESUME);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getPausePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_DOWNLOAD_CONTROL);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_PAUSE);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getRebootPendingIntent() {
        final Intent intent = new Intent(this, UpdaterReceiver.class);
        intent.setAction(UpdaterReceiver.ACTION_INSTALL_REBOOT);
        return PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
    }

}
