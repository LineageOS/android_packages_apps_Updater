/*
 * Copyright (C) 2017-2022 The LineageOS Project
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
package org.lineageos.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.lineageos.updater.misc.FileUtils.ProgressCallBack
import org.lineageos.updater.misc.FileUtils.copyFile
import org.lineageos.updater.misc.FileUtils.queryName
import java.io.File
import java.io.IOException
import java.text.NumberFormat

class ExportUpdateService : Service() {
    @Volatile
    private var mIsExporting = false
    private var mExportThread: Thread? = null
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (ACTION_START_EXPORTING == intent.action) {
            if (mIsExporting) {
                Log.e(TAG, "Already exporting an update")
                Toast.makeText(this, R.string.toast_already_exporting, Toast.LENGTH_SHORT).show()
                return START_NOT_STICKY
            }
            mIsExporting = true
            val source = intent.getSerializableExtra(EXTRA_SOURCE_FILE) as File?
            val destination = intent.getParcelableExtra<Uri>(EXTRA_DEST_URI)
            startExporting(source, destination)
            Toast.makeText(this, R.string.toast_export_started, Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG, "No action specified")
        }
        if (!mIsExporting) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private inner class ExportRunnable(
        private val mContentResolver: ContentResolver,
        private val mSource: File?,
        private val mDestination: Uri?,
        private val mProgressCallBack: ProgressCallBack,
        private val mRunnableComplete: Runnable,
        private val mRunnableFailed: Runnable
    ) : Runnable {
        override fun run() {
            try {
                copyFile(mContentResolver, mSource!!, mDestination, mProgressCallBack)
                mIsExporting = false
                if (!mExportThread!!.isInterrupted) {
                    Log.d(TAG, "Completed")
                    mRunnableComplete.run()
                } else {
                    Log.d(TAG, "Aborted")
                }
            } catch (e: IOException) {
                mIsExporting = false
                Log.e(TAG, "Could not copy file", e)
                mRunnableFailed.run()
            } finally {
                stopSelf()
            }
        }
    }

    private fun startExporting(source: File?, destination: Uri?) {
        val fileName = queryName(contentResolver, destination!!)
        val notificationManager = getSystemService(
            NotificationManager::class.java
        )
        val notificationChannel = NotificationChannel(
            EXPORT_NOTIFICATION_CHANNEL,
            getString(R.string.export_channel_title),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(notificationChannel)
        val notificationBuilder = NotificationCompat.Builder(
            this,
            EXPORT_NOTIFICATION_CHANNEL
        )
        val notificationStyle = NotificationCompat.BigTextStyle()
        notificationBuilder.setContentTitle(getString(R.string.dialog_export_title))
        notificationStyle.setBigContentTitle(getString(R.string.dialog_export_title))
        notificationStyle.bigText(fileName)
        notificationBuilder.setStyle(notificationStyle)
        notificationBuilder.setSmallIcon(R.drawable.ic_system_update)
        val progressCallBack: ProgressCallBack = object : ProgressCallBack {
            private var mLastUpdate: Long = -1
            override fun update(progress: Int) {
                val now = SystemClock.elapsedRealtime()
                if (mLastUpdate < 0 || now - mLastUpdate > 500) {
                    val percent =
                        NumberFormat.getPercentInstance().format((progress / 100f).toDouble())
                    notificationStyle.setSummaryText(percent)
                    notificationBuilder.setProgress(100, progress, false)
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        notificationBuilder.build()
                    )
                    mLastUpdate = now
                }
            }
        }
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        val runnableComplete = Runnable {
            notificationStyle.setSummaryText(null)
            notificationStyle.setBigContentTitle(
                getString(R.string.notification_export_success)
            )
            notificationBuilder.setContentTitle(
                getString(R.string.notification_export_success)
            )
            notificationBuilder.setProgress(0, 0, false)
            notificationBuilder.setContentText(fileName)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            stopForeground(STOP_FOREGROUND_DETACH)
        }
        val runnableFailed = Runnable {
            notificationStyle.setSummaryText(null)
            notificationStyle.setBigContentTitle(
                getString(R.string.notification_export_fail)
            )
            notificationBuilder.setContentTitle(
                getString(R.string.notification_export_fail)
            )
            notificationBuilder.setProgress(0, 0, false)
            notificationBuilder.setContentText(null)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            stopForeground(STOP_FOREGROUND_DETACH)
        }
        val exportRunnable = ExportRunnable(
            contentResolver, source,
            destination, progressCallBack, runnableComplete, runnableFailed
        )
        mExportThread = Thread(exportRunnable)
        mExportThread!!.start()
    }

    companion object {
        private const val TAG = "ExportUpdateService"

        private const val NOTIFICATION_ID = 16

        const val ACTION_START_EXPORTING = "start_exporting"
        const val EXTRA_SOURCE_FILE = "source_file"
        const val EXTRA_DEST_URI = "dest_uri"
        private const val EXPORT_NOTIFICATION_CHANNEL = "export_notification_channel"
    }
}
