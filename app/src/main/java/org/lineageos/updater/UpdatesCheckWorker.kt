/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.lineageos.updater.download.DownloadClient
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.Utils
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * This worker handles the background task of checking for new updates.
 */
class UpdatesCheckWorker(
    private val context: Context, workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    override suspend fun doWork(): Result {
        if (!Utils.isUpdateCheckEnabled(context)) {
            return Result.success()
        }

        Utils.cleanupDownloadsDir(context)

        val json = Utils.getCachedUpdateList(context)
        val jsonNew = File(json.absolutePath + UUID.randomUUID())
        val url = Utils.getServerURL(context)

        try {
            return suspendCancellableCoroutine { continuation ->
                val callback = object : DownloadClient.DownloadCallback {
                    override fun onFailure(cancelled: Boolean) {
                        Log.e(TAG, "Could not download updates list, retrying.")
                        if (continuation.isActive) {
                            continuation.resume(Result.retry())
                        }
                    }

                    override fun onResponse(headers: DownloadClient.Headers) {}

                    override fun onSuccess() {
                        if (!continuation.isActive) return

                        try {
                            if (json.exists() && Utils.checkForNewUpdates(json, jsonNew)) {
                                showNotification(context)
                            }
                            jsonNew.renameTo(json)
                            preferences.edit {
                                putLong(
                                    Constants.PREF_LAST_UPDATE_CHECK, System.currentTimeMillis()
                                )
                            }
                            continuation.resume(Result.success())
                        } catch (e: IOException) {
                            Log.e(TAG, "Could not parse list, retrying.", e)
                            continuation.resume(Result.retry())
                        } catch (e: JSONException) {
                            Log.e(TAG, "Could not parse list, retrying.", e)
                            continuation.resume(Result.retry())
                        }
                    }
                }

                try {
                    val downloadClient =
                        DownloadClient.Builder().setUrl(url).setDestination(jsonNew)
                            .setDownloadCallback(callback).build()
                    downloadClient.start()

                    continuation.invokeOnCancellation {
                        downloadClient.cancel()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Could not fetch list, retrying.", e)
                    if (continuation.isActive) {
                        continuation.resume(Result.retry())
                    }
                }
            }
        } finally {
            jsonNew.delete()
        }
    }

    private fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notificationChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_NEW_UPDATES,
            context.getString(R.string.new_updates_channel_title),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(notificationChannel)

        val notificationIntent = Intent(context, UpdatesActivity::class.java)
        val intent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context, Constants.NOTIFICATION_CHANNEL_NEW_UPDATES
        ).setSmallIcon(R.drawable.ic_system_update).setContentIntent(intent)
            .setContentTitle(context.getString(R.string.new_updates_found_title))
            .setAutoCancel(true).build()

        notificationManager.notify(Constants.NOTIFICATION_ID_NEW_UPDATES, notification)
    }

    companion object {
        private const val TAG = "UpdatesCheckWorker"
        const val WORK_NAME = "updates_check"
        const val WORK_NAME_IMMEDIATE = "updates_check_immediate"

        private val CONSTRAINTS =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        // Retry periodical check after 2 hours if it fails when there
        // are server issues
        private const val RETRY_DELAY = 2L

        /**
         * Schedule a repeating update check.
         */
        @JvmStatic
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            if (!Utils.isUpdateCheckEnabled(context)) {
                cancel(context)
                return
            }

            val workRequest = PeriodicWorkRequest.Builder(
                UpdatesCheckWorker::class.java,
                Utils.getUpdateCheckInterval(context),
                TimeUnit.MILLISECONDS
            ).setConstraints(CONSTRAINTS).setBackoffCriteria(
                BackoffPolicy.LINEAR, RETRY_DELAY, TimeUnit.HOURS
            ).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, workRequest
            )
        }

        /**
         * Cancel the repeating update check.
         */
        @JvmStatic
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Run an immediate update check.
         */
        @JvmStatic
        fun runImmediateCheck(context: Context) {
            if (!Utils.isUpdateCheckEnabled(context)) {
                return
            }

            val workRequest = OneTimeWorkRequest.Builder(UpdatesCheckWorker::class.java)
                .setConstraints(CONSTRAINTS).build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE, ExistingWorkPolicy.KEEP, workRequest
            )
        }
    }
}
