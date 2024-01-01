/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.controller;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import org.lineageos.updater.R;
import org.lineageos.updater.UpdaterReceiver;
import org.lineageos.updater.UpdatesActivity;
import org.lineageos.updater.misc.BuildInfoUtils;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.Update;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;

public class UpdaterService extends Service {

    private static final String TAG = "UpdaterService";

    public static final String ACTION_DOWNLOAD_CONTROL = "action_download_control";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";
    public static final String EXTRA_DOWNLOAD_CONTROL = "extra_download_control";
    public static final String ACTION_INSTALL_UPDATE = "action_install_update";
    public static final String ACTION_INSTALL_STOP = "action_install_stop";

    public static final String ACTION_INSTALL_SUSPEND = "action_install_suspend";
    public static final String ACTION_INSTALL_RESUME = "action_install_resume";

    private static final String ONGOING_NOTIFICATION_CHANNEL =
            "ongoing_notification_channel";

    public static final int DOWNLOAD_RESUME = 0;
    public static final int DOWNLOAD_PAUSE = 1;

    private static final int NOTIFICATION_ID = 10;

    private final IBinder mBinder = new LocalBinder();
    private boolean mHasClients;

    private BroadcastReceiver mBroadcastReceiver;
    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;
    private NotificationCompat.BigTextStyle mNotificationStyle;

    private UpdaterController mUpdaterController;

    @Override
    public void onCreate() {
        super.onCreate();

        mUpdaterController = UpdaterController.getInstance(this);

        mNotificationManager = getSystemService(NotificationManager.class);
        NotificationChannel notificationChannel = new NotificationChannel(
                ONGOING_NOTIFICATION_CHANNEL,
                getString(R.string.ongoing_channel_title),
                NotificationManager.IMPORTANCE_LOW);
        mNotificationManager.createNotificationChannel(notificationChannel);
        mNotificationBuilder = new NotificationCompat.Builder(this,
                ONGOING_NOTIFICATION_CHANNEL);
        mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        mNotificationBuilder.setShowWhen(false);
        mNotificationStyle = new NotificationCompat.BigTextStyle();
        mNotificationBuilder.setStyle(mNotificationStyle);

        Intent notificationIntent = new Intent(this, UpdatesActivity.class);
        PendingIntent intent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mNotificationBuilder.setContentIntent(intent);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                    setNotificationTitle(update);
                    Bundle extras = new Bundle();
                    extras.putString(UpdaterController.EXTRA_DOWNLOAD_ID, downloadId);
                    mNotificationBuilder.setExtras(extras);
                    handleUpdateStatusChange(update);
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction())) {
                    UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                    handleDownloadProgressChange(update);
                } else if (UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                    setNotificationTitle(update);
                    handleInstallProgress(update);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    final boolean isLocalUpdate = Update.LOCAL_ID.equals(downloadId);
                    Bundle extras = mNotificationBuilder.getExtras();
                    if (extras != null && !isLocalUpdate && downloadId.equals(
                            extras.getString(UpdaterController.EXTRA_DOWNLOAD_ID))) {
                        mNotificationBuilder.setExtras(null);
                        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                        if (update.getStatus() != UpdateStatus.INSTALLED) {
                            mNotificationManager.cancel(NOTIFICATION_ID);
                        }
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
        Log.d(TAG, "Starting service");

        if (intent == null || intent.getAction() == null) {
            if (ABUpdateInstaller.isInstallingUpdate(this)) {
                // The service is being restarted.
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(this,
                        mUpdaterController);
                installer.reconnect();
            }
        } else if (ACTION_DOWNLOAD_CONTROL.equals(intent.getAction())) {
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
            UpdateInfo update = mUpdaterController.getUpdate(downloadId);
            if (update.getPersistentStatus() != UpdateStatus.Persistent.VERIFIED) {
                throw new IllegalArgumentException(update.getDownloadId() + " is not verified");
            }
            try {
                if (Utils.isABUpdate(update.getFile())) {
                    ABUpdateInstaller installer = ABUpdateInstaller.getInstance(this,
                            mUpdaterController);
                    installer.install(downloadId);
                } else {
                    UpdateInstaller installer = UpdateInstaller.getInstance(this,
                            mUpdaterController);
                    installer.install(downloadId);
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not install update", e);
                mUpdaterController.getActualUpdate(downloadId)
                        .setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(downloadId);
            }
        } else if (ACTION_INSTALL_STOP.equals(intent.getAction())) {
            if (UpdateInstaller.isInstalling()) {
                UpdateInstaller installer = UpdateInstaller.getInstance(this,
                        mUpdaterController);
                installer.cancel();
            } else if (ABUpdateInstaller.isInstallingUpdate(this)) {
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(this,
                        mUpdaterController);
                installer.reconnect();
                installer.cancel();
            }
        } else if (ACTION_INSTALL_SUSPEND.equals(intent.getAction())) {
            if (ABUpdateInstaller.isInstallingUpdate(this)) {
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(this,
                        mUpdaterController);
                installer.reconnect();
                installer.suspend();
            }
        } else if (ACTION_INSTALL_RESUME.equals(intent.getAction())) {
            if (ABUpdateInstaller.isInstallingUpdateSuspended(this)) {
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(this,
                        mUpdaterController);
                installer.reconnect();
                installer.resume();
            }
        }
        return ABUpdateInstaller.isInstallingUpdate(this) ? START_STICKY : START_NOT_STICKY;
    }

    public UpdaterController getUpdaterController() {
        return mUpdaterController;
    }

    private void tryStopSelf() {
        if (!mHasClients && !mUpdaterController.hasActiveDownloads() &&
                !mUpdaterController.isInstallingUpdate()) {
            Log.d(TAG, "Service no longer needed, stopping");
            stopSelf();
        }
    }

    private void handleUpdateStatusChange(UpdateInfo update) {
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
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case DOWNLOADING: {
                String text = getString(R.string.downloading_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
                mNotificationBuilder.addAction(android.R.drawable.ic_media_pause,
                        getString(R.string.pause_button),
                        getPausePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
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
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_pause);
                mNotificationBuilder.addAction(android.R.drawable.ic_media_play,
                        getString(R.string.resume_button),
                        getResumePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
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
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_warning);
                mNotificationBuilder.addAction(android.R.drawable.ic_media_play,
                        getString(R.string.resume_button),
                        getResumePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case VERIFYING: {
                mNotificationBuilder.setProgress(0, 0, true);
                mNotificationStyle.setSummaryText(null);
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.mActions.clear();
                String text = getString(R.string.verifying_download_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case VERIFIED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setStyle(null);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.download_completed_notification);
                mNotificationBuilder.setContentText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case VERIFICATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setStyle(null);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_warning);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.verification_failed_notification);
                mNotificationBuilder.setContentText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLING: {
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                mNotificationStyle.setSummaryText(null);
                String text = UpdateInstaller.isInstalling() ?
                        getString(R.string.dialog_prepare_zip_message) :
                        getString(R.string.installing_update);
                mNotificationStyle.bigText(text);
                if (ABUpdateInstaller.isInstallingUpdate(this)) {
                    mNotificationBuilder.addAction(android.R.drawable.ic_media_pause,
                            getString(R.string.suspend_button),
                            getSuspendInstallationPendingIntent());
                }
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case INSTALLED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setStyle(null);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.installing_update_finished);
                mNotificationBuilder.setContentText(text);
                mNotificationBuilder.addAction(R.drawable.ic_system_update,
                        getString(R.string.reboot),
                        getRebootPendingIntent());
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());

                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
                boolean deleteUpdate = pref.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, false);
                boolean isLocal = Update.LOCAL_ID.equals(update.getDownloadId());
                // Always delete local updates
                if (deleteUpdate || isLocal) {
                    mUpdaterController.deleteUpdate(update.getDownloadId());
                }

                tryStopSelf();
                break;
            }
            case INSTALLATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setStyle(null);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_warning);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.installing_update_error);
                mNotificationBuilder.setContentText(text);
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLATION_CANCELLED: {
                stopForeground(true);
                tryStopSelf();
                break;
            }
            case INSTALLATION_SUSPENDED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                // In case we pause before the first progress update
                mNotificationBuilder.setProgress(100, update.getProgress(), false);
                mNotificationBuilder.mActions.clear();
                String text = getString(R.string.installation_suspended_notification);
                mNotificationStyle.bigText(text);
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_pause);
                mNotificationBuilder.addAction(android.R.drawable.ic_media_play,
                        getString(R.string.resume_button),
                        getResumeInstallationPendingIntent());
                mNotificationBuilder.setTicker(text);
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
        }
    }

    private void handleDownloadProgressChange(UpdateInfo update) {
        int progress = update.getProgress();
        mNotificationBuilder.setProgress(100, progress, false);

        String percent = NumberFormat.getPercentInstance().format(progress / 100.f);
        mNotificationStyle.setSummaryText(percent);

        setNotificationTitle(update);

        String speed = Formatter.formatFileSize(this, update.getSpeed());
        CharSequence eta = StringGenerator.formatETA(this, update.getEta() * 1000);
        mNotificationStyle.bigText(
                getString(R.string.text_download_speed, eta, speed));

        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void handleInstallProgress(UpdateInfo update) {
        setNotificationTitle(update);
        int progress = update.getInstallProgress();
        mNotificationBuilder.setProgress(100, progress, false);
        String percent = NumberFormat.getPercentInstance().format(progress / 100.f);
        mNotificationStyle.setSummaryText(percent);
        boolean notAB = UpdateInstaller.isInstalling();
        mNotificationStyle.bigText(notAB ? getString(R.string.dialog_prepare_zip_message) :
                update.getFinalizing() ?
                        getString(R.string.finalizing_package) :
                        getString(R.string.preparing_ota_first_boot));
        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void setNotificationTitle(UpdateInfo update) {
        String buildDate = StringGenerator.getDateLocalizedUTC(this,
                DateFormat.MEDIUM, update.getTimestamp());
        String buildInfo = getString(R.string.list_build_version_date,
                update.getVersion(), buildDate);
        mNotificationStyle.setBigContentTitle(buildInfo);
        mNotificationBuilder.setContentTitle(buildInfo);
    }

    private PendingIntent getResumePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_DOWNLOAD_CONTROL);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_RESUME);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getPausePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_DOWNLOAD_CONTROL);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_PAUSE);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getRebootPendingIntent() {
        final Intent intent = new Intent(this, UpdaterReceiver.class);
        intent.setAction(UpdaterReceiver.ACTION_INSTALL_REBOOT);
        return PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getSuspendInstallationPendingIntent() {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_INSTALL_SUSPEND);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getResumeInstallationPendingIntent() {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_INSTALL_RESUME);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
