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
package org.lineageos.updater;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;

import java.text.NumberFormat;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";

    private static final int NOTIFICATION_ID = 10;

    private final IBinder mBinder = new LocalBinder();
    private boolean mHasClients;

    private BroadcastReceiver mBroadcastReceiver;
    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;
    private NotificationCompat.BigTextStyle mNotificationStyle;;

    private DownloadControllerInt mDownloadController;

    @Override
    public void onCreate() {
        super.onCreate();

        mDownloadController = DownloadController.newInstance(this);

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
                String downloadId = intent.getStringExtra(DownloadController.DOWNLOAD_ID_EXTRA);
                if (DownloadController.UPDATE_STATUS_ACTION.equals(intent.getAction())) {
                    UpdateDownload update = mDownloadController.getUpdate(downloadId);
                    mNotificationBuilder.setContentTitle(update.getName());
                    handleDownloadStatusChange(update);
                } else if (DownloadController.PROGRESS_ACTION.equals(intent.getAction())) {
                    UpdateDownload update = mDownloadController.getUpdate(downloadId);
                    int progress = update.getProgress();
                    mNotificationBuilder.setProgress(100, progress, false);

                    String percent = NumberFormat.getPercentInstance().format(progress / 100.f);
                    mNotificationStyle.setSummaryText(percent);

                    mNotificationStyle.setBigContentTitle(update.getName());
                    mNotificationBuilder.setContentTitle(update.getName());

                    String speed = Formatter.formatFileSize(context, update.getSpeed());
                    CharSequence eta = DateUtils.formatDuration(update.getEta() * 1000);
                    mNotificationStyle.bigText(
                            getString(R.string.text_download_speed, eta, speed));

                    mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DownloadController.PROGRESS_ACTION);
        intentFilter.addAction(DownloadController.UPDATE_STATUS_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);

    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
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
        if (intent == null) {
            Log.d(TAG, "The service is being restarted, exit");
            // The service is being restarted, exit without doing anything
            stopForeground(STOP_FOREGROUND_DETACH);
            mNotificationBuilder.setOngoing(false);
            mNotificationManager.cancel(NOTIFICATION_ID);
            stopSelf();
        }
        Log.d(TAG, "Service started");
        return START_STICKY;
    }

    public DownloadControllerInt getDownloadController() {
        return mDownloadController;
    }

    private void tryStopSelf() {
        if (!mHasClients && !mDownloadController.hasActiveDownloads()) {
            Log.d(TAG, "Service no longer needed, stopping");
            stopSelf();
        }
    }

    private void handleDownloadStatusChange(UpdateDownload update) {
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
                mNotificationBuilder.addAction(R.drawable.ic_tab_install,
                        getString(R.string.install_button),
                        getInstallPendingIntent(update.getDownloadId()));
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
        }
    }

    private PendingIntent getResumePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdaterBroadcastReceiver.class);
        intent.setAction(UpdaterBroadcastReceiver.ACTION_DOWNLOAD);
        intent.putExtra(UpdaterBroadcastReceiver.EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(UpdaterBroadcastReceiver.EXTRA_DOWNLOAD_ACTION,
                UpdaterBroadcastReceiver.RESUME);
        return PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getPausePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdaterBroadcastReceiver.class);
        intent.setAction(UpdaterBroadcastReceiver.ACTION_DOWNLOAD);
        intent.putExtra(UpdaterBroadcastReceiver.EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(UpdaterBroadcastReceiver.EXTRA_DOWNLOAD_ACTION,
                UpdaterBroadcastReceiver.PAUSE);
        return PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getInstallPendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdaterBroadcastReceiver.class);
        intent.setAction(UpdaterBroadcastReceiver.ACTION_INSTALL_UPDATE);
        intent.putExtra(UpdaterBroadcastReceiver.EXTRA_DOWNLOAD_ID, downloadId);
        return PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
