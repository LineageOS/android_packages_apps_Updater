/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import org.lineageos.updater.misc.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;

public class ExportUpdateService extends Service {

    private static final String TAG = "ExportUpdateService";

    private static final int NOTIFICATION_ID = 16;

    public static final String ACTION_START_EXPORTING = "start_exporting";

    public static final String EXTRA_SOURCE_FILE = "source_file";
    public static final String EXTRA_DEST_URI = "dest_uri";

    private static final String EXPORT_NOTIFICATION_CHANNEL =
            "export_notification_channel";

    private volatile boolean mIsExporting = false;

    private Thread mExportThread;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_START_EXPORTING.equals(intent.getAction())) {
            if (mIsExporting) {
                Log.e(TAG, "Already exporting an update");
                Toast.makeText(this, R.string.toast_already_exporting, Toast.LENGTH_SHORT).show();
                return START_NOT_STICKY;
            }
            mIsExporting = true;
            File source = (File) intent.getSerializableExtra(EXTRA_SOURCE_FILE);
            Uri destination = intent.getParcelableExtra(EXTRA_DEST_URI);
            startExporting(source, destination);
            Toast.makeText(this, R.string.toast_export_started, Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "No action specified");
        }

        if (!mIsExporting) {
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private class ExportRunnable implements Runnable {
        private final ContentResolver mContentResolver;
        private final File mSource;
        private final Uri mDestination;
        private final FileUtils.ProgressCallBack mProgressCallBack;
        private final Runnable mRunnableComplete;
        private final Runnable mRunnableFailed;

        private ExportRunnable(ContentResolver cr, File source, Uri destination,
                               FileUtils.ProgressCallBack progressCallBack,
                               Runnable runnableComplete, Runnable runnableFailed) {
            mContentResolver = cr;
            mSource = source;
            mDestination = destination;
            mProgressCallBack = progressCallBack;
            mRunnableComplete = runnableComplete;
            mRunnableFailed = runnableFailed;
        }

        @Override
        public void run() {
            try {
                FileUtils.copyFile(mContentResolver, mSource, mDestination, mProgressCallBack);
                mIsExporting = false;
                if (!mExportThread.isInterrupted()) {
                    Log.d(TAG, "Completed");
                    mRunnableComplete.run();
                } else {
                    Log.d(TAG, "Aborted");
                }
            } catch (IOException e) {
                mIsExporting = false;
                Log.e(TAG, "Could not copy file", e);
                mRunnableFailed.run();
            } finally {
                stopSelf();
            }
        }
    }

    private void startExporting(File source, Uri destination) {
        final String fileName = FileUtils.queryName(getContentResolver(), destination);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        NotificationChannel notificationChannel = new NotificationChannel(
                EXPORT_NOTIFICATION_CHANNEL,
                getString(R.string.export_channel_title),
                NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(notificationChannel);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this,
                EXPORT_NOTIFICATION_CHANNEL);
        NotificationCompat.BigTextStyle notificationStyle = new NotificationCompat.BigTextStyle();
        notificationBuilder.setContentTitle(getString(R.string.dialog_export_title));
        notificationStyle.setBigContentTitle(getString(R.string.dialog_export_title));
        notificationStyle.bigText(fileName);
        notificationBuilder.setStyle(notificationStyle);
        notificationBuilder.setSmallIcon(R.drawable.ic_system_update);

        FileUtils.ProgressCallBack progressCallBack = new FileUtils.ProgressCallBack() {
            private long mLastUpdate = -1;

            @Override
            public void update(int progress) {
                long now = SystemClock.elapsedRealtime();
                if (mLastUpdate < 0 || now - mLastUpdate > 500) {
                    String percent = NumberFormat.getPercentInstance().format(progress / 100.f);
                    notificationStyle.setSummaryText(percent);
                    notificationBuilder.setProgress(100, progress, false);
                    notificationManager.notify(NOTIFICATION_ID,
                            notificationBuilder.build());
                    mLastUpdate = now;
                }
            }
        };

        startForeground(NOTIFICATION_ID, notificationBuilder.build());
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());

        Runnable runnableComplete = () -> {
            notificationStyle.setSummaryText(null);
            notificationStyle.setBigContentTitle(
                    getString(R.string.notification_export_success));
            notificationBuilder.setContentTitle(
                    getString(R.string.notification_export_success));
            notificationBuilder.setProgress(0, 0, false);
            notificationBuilder.setContentText(fileName);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            stopForeground(STOP_FOREGROUND_DETACH);
        };

        Runnable runnableFailed = () -> {
            notificationStyle.setSummaryText(null);
            notificationStyle.setBigContentTitle(
                    getString(R.string.notification_export_fail));
            notificationBuilder.setContentTitle(
                    getString(R.string.notification_export_fail));
            notificationBuilder.setProgress(0, 0, false);
            notificationBuilder.setContentText(null);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            stopForeground(STOP_FOREGROUND_DETACH);
        };

        ExportRunnable exportRunnable = new ExportRunnable(getContentResolver(), source,
                destination, progressCallBack, runnableComplete, runnableFailed);
        mExportThread = new Thread(exportRunnable);
        mExportThread.start();
    }
}
