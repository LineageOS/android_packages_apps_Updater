/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updatescheck

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lineageos.updater.data.AppStateRepository
import org.lineageos.updater.data.CheckInterval
import org.lineageos.updater.data.UserPreferencesRepository
import org.lineageos.updater.data.UpdatesRepository
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

class UpdatesCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val fetchedAt = UpdatesRepository.create(applicationContext).use { it.fetchUpdates() }
            if (fetchedAt != null) {
                AppStateRepository(applicationContext).setLastCheckedTimestamp(fetchedAt)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UpdatesCheckWorker"

        private const val WORK_NAME_ONESHOT = "update_check_oneshot"
        private const val WORK_NAME_PERIODIC = "update_check_periodic"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        @JvmStatic
        fun cancelPeriodicCheck(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }

        @JvmStatic
        fun scheduleOneshotCheck(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONESHOT,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdatesCheckWorker>()
                    .setConstraints(networkConstraints)
                    .build(),
            )
        }

        @JvmStatic
        fun schedulePeriodicCheck(context: Context) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val prefs = UserPreferencesRepository(context)
                if (prefs.getPeriodicCheckEnabled()) {
                    enqueuePeriodicCheck(
                        context,
                        prefs.getCheckInterval(),
                        ExistingPeriodicWorkPolicy.KEEP
                    )
                }
            }
        }

        @JvmStatic
        fun reschedulePeriodicCheck(context: Context) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val prefs = UserPreferencesRepository(context)
                if (!prefs.getPeriodicCheckEnabled()) {
                    cancelPeriodicCheck(context)
                } else {
                    enqueuePeriodicCheck(
                        context,
                        prefs.getCheckInterval(),
                        ExistingPeriodicWorkPolicy.UPDATE
                    )
                }
            }
        }

        private fun enqueuePeriodicCheck(
            context: Context,
            interval: CheckInterval,
            policy: ExistingPeriodicWorkPolicy,
        ) {
            val request =
                PeriodicWorkRequestBuilder<UpdatesCheckWorker>(interval.duration.toJavaDuration())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1.hours.toJavaDuration())
                    .setConstraints(networkConstraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                policy,
                request,
            )
            Log.d(TAG, "Scheduled periodic check (policy=$policy, interval=${interval.duration})")
        }
    }
}
