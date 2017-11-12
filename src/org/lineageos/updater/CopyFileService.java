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
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.lineageos.updater.misc.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;

public class CopyFileService extends Service {

    private static final String TAG = "CopyFileService";

    private static final int NOTIFICATION_ID = 16;

    public static final String ACTION_START_COPY = "start_copy";
    public static final String ACTION_STOP_COPY = "stop_copy";

    public static final String EXTRA_SOURCE_FILE = "source_file";
    public static final String EXTRA_DEST_FILE = "dest_file";

    private boolean mIsCopying = false;

    private Thread mCopyThread;
    private CopyRunnable mCopyRunnable;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_START_COPY.equals(intent.getAction())) {
            if (mIsCopying) {
                Log.e(TAG, "A file copy is ongoing");
                return START_NOT_STICKY;
            }
            mIsCopying = true;
            File source = (File) intent.getSerializableExtra(EXTRA_SOURCE_FILE);
            File destination = (File) intent.getSerializableExtra(EXTRA_DEST_FILE);
            startCopying(source, destination);
        } else if (ACTION_STOP_COPY.equals(intent.getAction())) {
            if (mIsCopying) {
                mCopyThread.interrupt();
                stopForeground(true);
                try {
                    mCopyThread.join();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error while waiting for thread");
                }
                mCopyRunnable.cleanUp();
                mIsCopying = false;
            }
            stopSelf();
        } else {
            Log.e(TAG, "No action specified");
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private class CopyRunnable implements Runnable {
        private File mSource;
        private File mDestination;
        private FileUtils.ProgressCallBack mProgressCallBack;
        private Runnable mRunnableComplete;
        private Runnable mRunnableFailed;

        private CopyRunnable(File source, File destination,
                FileUtils.ProgressCallBack progressCallBack,
                Runnable runnableComplete, Runnable runnableFailed) {
            mSource = source;
            mDestination = destination;
            mProgressCallBack = progressCallBack;
            mRunnableComplete = runnableComplete;
            mRunnableFailed = runnableFailed;
        }

        @Override
        public void run() {
            try {
                FileUtils.copyFile(mSource, mDestination, mProgressCallBack);
                Log.d(TAG, "Completed");
                if (!mCopyThread.isInterrupted()) {
                    mRunnableComplete.run();
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not copy file", e);
                mRunnableFailed.run();
            } finally {
                mIsCopying = false;
            }
        }

        private void cleanUp() {
            mDestination.delete();
        }
    }

    private void startCopying(File source, File destination) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
        NotificationCompat.BigTextStyle notificationStyle = new NotificationCompat.BigTextStyle();
        notificationStyle.setBigContentTitle(getString(R.string.dialog_export_title));
        notificationStyle.bigText(destination.getName());
        notificationBuilder.setStyle(notificationStyle);
        notificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        notificationBuilder.addAction(R.drawable.ic_pause,
                getString(android.R.string.cancel),
                getStopPendingIntent());

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

        Runnable runnableEnd = new Runnable() {
            @Override
            public void run() {
                notificationStyle.setBigContentTitle(
                        getString(R.string.notification_export_success));
                notificationBuilder.setProgress(0, 0, false);
                notificationBuilder.setContentText(destination.getName());
                notificationBuilder.mActions.clear();
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
                stopForeground(STOP_FOREGROUND_DETACH);
            }
        };

        Runnable runnableFailed = new Runnable() {
            @Override
            public void run() {
                notificationStyle.setBigContentTitle(
                        getString(R.string.notification_export_fail));
                notificationBuilder.setProgress(0, 0, false);
                notificationBuilder.setContentText(null);
                notificationBuilder.mActions.clear();
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
                stopForeground(STOP_FOREGROUND_DETACH);
            }
        };

        mCopyRunnable = new CopyRunnable(source, destination, progressCallBack,
                runnableEnd, runnableFailed);
        mCopyThread = new Thread(mCopyRunnable);
        mCopyThread.start();
    }

    private PendingIntent getStopPendingIntent() {
        final Intent intent = new Intent(this, CopyFileService.class);
        intent.setAction(ACTION_STOP_COPY);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
