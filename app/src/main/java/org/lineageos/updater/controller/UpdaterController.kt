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
package org.lineageos.updater.controller

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.PowerManager
import android.os.RecoverySystem
import android.os.SystemClock
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.lineageos.updater.UpdatesDbHelper
import org.lineageos.updater.controller.ABUpdateInstaller.Companion.getInstance
import org.lineageos.updater.controller.ABUpdateInstaller.Companion.isInstallingUpdate
import org.lineageos.updater.controller.ABUpdateInstaller.Companion.isWaitingForReboot
import org.lineageos.updater.controller.UpdateInstaller.Companion.isInstalling
import org.lineageos.updater.download.DownloadClient
import org.lineageos.updater.download.DownloadClient.DownloadCallback
import org.lineageos.updater.misc.Utils
import org.lineageos.updater.model.Update
import org.lineageos.updater.model.UpdateInfo
import org.lineageos.updater.model.UpdateStatus
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

class UpdaterController private constructor(context: Context) {
    private val applicationContext = context.applicationContext
    private val broadcastManager = LocalBroadcastManager.getInstance(context)
    private val updatesDbHelper = UpdatesDbHelper(context)
    private val powerManager = context.getSystemService(
        PowerManager::class.java
    )
    private val wakeLock =
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Updater:wakelock").apply {
            setReferenceCounted(false)
        }
    private val downloadRoot = Utils.getDownloadPath(context)
    private var activeDownloads = 0
    private val verifyingUpdates: MutableSet<String> = HashSet()

    private class DownloadEntry(val update: Update) {
        var downloadClient: DownloadClient? = null
    }

    private val downloads: MutableMap<String, DownloadEntry> = HashMap()

    init {
        Utils.cleanupDownloadsDir(context)
        for (update in updatesDbHelper.updates) {
            addUpdate(update, false)
        }
    }

    fun notifyUpdateChange(downloadId: String?) {
        val intent = Intent()

        intent.action = ACTION_UPDATE_STATUS
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId)

        broadcastManager.sendBroadcast(intent)
    }

    private fun notifyUpdateDelete(downloadId: String) {
        val intent = Intent()

        intent.action = ACTION_UPDATE_REMOVED
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId)

        broadcastManager.sendBroadcast(intent)
    }

    private fun notifyDownloadProgress(downloadId: String) {
        val intent = Intent()

        intent.action = ACTION_DOWNLOAD_PROGRESS
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId)

        broadcastManager.sendBroadcast(intent)
    }

    fun notifyInstallProgress(downloadId: String?) {
        val intent = Intent()

        intent.action = ACTION_INSTALL_PROGRESS
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId)

        broadcastManager.sendBroadcast(intent)
    }

    private fun tryReleaseWakelock() {
        if (!hasActiveDownloads()) {
            wakeLock.release()
        }
    }

    private fun addDownloadClient(entry: DownloadEntry, downloadClient: DownloadClient) {
        if (entry.downloadClient != null) {
            return
        }

        entry.downloadClient = downloadClient
        activeDownloads++
    }

    private fun removeDownloadClient(entry: DownloadEntry) {
        if (entry.downloadClient == null) {
            return
        }

        entry.downloadClient = null
        activeDownloads--
    }

    private fun getDownloadCallback(downloadId: String) = object : DownloadCallback {
        override fun onResponse(headers: DownloadClient.Headers?) {
            val entry = downloads[downloadId] ?: return
            val update = entry.update
            val contentLength = headers!!["Content-Length"]
            if (contentLength != null) {
                try {
                    val size = contentLength.toLong()
                    if (update.fileSize < size) {
                        update.fileSize = size
                    }
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Could not get content-length")
                }
            }
            update.status = UpdateStatus.DOWNLOADING
            update.persistentStatus = UpdateStatus.Persistent.INCOMPLETE
            Thread {
                updatesDbHelper.addUpdateWithOnConflict(
                    update,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }.start()
            notifyUpdateChange(downloadId)
        }

        override fun onSuccess() {
            Log.d(TAG, "Download complete")
            val entry = downloads[downloadId]
            if (entry != null) {
                val update = entry.update
                update.status = UpdateStatus.VERIFYING
                removeDownloadClient(entry)
                verifyUpdateAsync(downloadId)
                notifyUpdateChange(downloadId)
                tryReleaseWakelock()
            }
        }

        override fun onFailure(cancelled: Boolean) {
            if (cancelled) {
                Log.d(TAG, "Download cancelled")
                // Already notified
            } else {
                val entry = downloads[downloadId]
                if (entry != null) {
                    val update = entry.update
                    Log.e(TAG, "Download failed")
                    removeDownloadClient(entry)
                    update.status = UpdateStatus.PAUSED_ERROR
                    notifyUpdateChange(downloadId)
                }
            }
            tryReleaseWakelock()
        }
    }

    private fun getProgressListener(downloadId: String) = object : DownloadClient.ProgressListener {
        private var lastUpdate: Long = 0
        private var progress = 0

        override fun update(bytesRead: Long, contentLength: Long, speed: Long, eta: Long) {
            var contentLength = contentLength
            val entry = downloads[downloadId] ?: return
            val update = entry.update
            if (contentLength <= 0) {
                contentLength = if (update.fileSize <= 0) {
                    return
                } else {
                    update.fileSize
                }
            }
            if (contentLength <= 0) {
                return
            }
            val now = SystemClock.elapsedRealtime()
            val currentProgress = (bytesRead * 100f / contentLength).roundToInt()
            if (currentProgress != progress || lastUpdate - now > MAX_REPORT_INTERVAL_MS) {
                progress = currentProgress
                lastUpdate = now
                update.progress = currentProgress
                update.eta = eta
                update.speed = speed
                notifyDownloadProgress(downloadId)
            }
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun verifyUpdateAsync(downloadId: String) {
        verifyingUpdates.add(downloadId)
        Thread {
            val entry = downloads[downloadId]
            entry?.let {
                val update = it.update
                val file = update.file
                if (file?.exists() == true && verifyPackage(file)) {
                    file.setReadable(true, false)
                    update.persistentStatus = UpdateStatus.Persistent.VERIFIED
                    updatesDbHelper.changeUpdateStatus(update)
                    update.status = UpdateStatus.VERIFIED
                } else {
                    update.persistentStatus = UpdateStatus.Persistent.UNKNOWN
                    updatesDbHelper.removeUpdate(downloadId)
                    update.progress = 0
                    update.status = UpdateStatus.VERIFICATION_FAILED
                }
                verifyingUpdates.remove(downloadId)
                notifyUpdateChange(downloadId)
            }
        }.start()
    }

    private fun verifyPackage(file: File) = try {
        RecoverySystem.verifyPackage(file, null, null)
        Log.e(TAG, "Verification successful")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Verification failed", e)
        if (file.exists()) {
            file.delete()
        } else {
            // The download was probably stopped. Exit silently
            Log.e(TAG, "Error while verifying the file", e)
        }
        false
    }

    private fun fixUpdateStatus(update: Update): Boolean {
        when (update.persistentStatus) {
            UpdateStatus.Persistent.VERIFIED,
            UpdateStatus.Persistent.INCOMPLETE -> if (
                update.file == null || !update.file!!.exists()
            ) {
                update.status = UpdateStatus.UNKNOWN
                return false
            } else if (update.fileSize > 0) {
                update.status = UpdateStatus.PAUSED
                val progress = (update.file!!.length() * 100f / update.fileSize).roundToInt()
                update.progress = progress
            }
        }
        return true
    }

    fun setUpdatesAvailableOnline(downloadIds: List<String?>, purgeList: Boolean) {
        val toRemove = mutableListOf<String>()

        for (entry in downloads.values) {
            val online = downloadIds.contains(entry.update.downloadId)
            entry.update.availableOnline = online
            if (!online && purgeList && entry.update.persistentStatus == UpdateStatus.Persistent.UNKNOWN) {
                toRemove.add(entry.update.downloadId)
            }
        }

        for (downloadId in toRemove) {
            Log.d(TAG, "$downloadId no longer available online, removing")
            downloads.remove(downloadId)
            notifyUpdateDelete(downloadId)
        }
    }

    fun addUpdate(update: UpdateInfo): Boolean {
        return addUpdate(update, true)
    }

    private fun addUpdate(updateInfo: UpdateInfo, availableOnline: Boolean): Boolean {
        Log.d(TAG, "Adding download: " + updateInfo.downloadId)
        if (downloads.containsKey(updateInfo.downloadId)) {
            Log.d(TAG, "Download (" + updateInfo.downloadId + ") already added")
            val entry = downloads[updateInfo.downloadId]
            if (entry != null) {
                val updateAdded = entry.update
                updateAdded.availableOnline = availableOnline && updateAdded.availableOnline
                updateAdded.downloadUrl = updateInfo.downloadUrl
            }
            return false
        }
        val update = Update(updateInfo)
        if (!fixUpdateStatus(update) && !availableOnline) {
            update.persistentStatus = UpdateStatus.Persistent.UNKNOWN
            deleteUpdateAsync(update)
            Log.d(TAG, update.downloadId + " had an invalid status and is not online")
            return false
        }
        update.availableOnline = availableOnline
        downloads[update.downloadId] = DownloadEntry(update)
        return true
    }

    @SuppressLint("WakelockTimeout")
    fun startDownload(downloadId: String) {
        Log.d(TAG, "Starting $downloadId")
        if (!downloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return
        }
        val entry = downloads[downloadId]
        if (entry == null) {
            Log.e(TAG, "Could not get download entry")
            return
        }
        val update = entry.update
        var destination = File(downloadRoot, update.name)
        if (destination.exists()) {
            destination = Utils.appendSequentialNumber(destination)
            Log.d(TAG, "Changing name with " + destination.name)
        }
        update.file = destination
        val downloadClient: DownloadClient
        try {
            downloadClient = DownloadClient.Builder()
                .setUrl(update.downloadUrl)
                .setDestination(update.file)
                .setDownloadCallback(getDownloadCallback(downloadId))
                .setProgressListener(getProgressListener(downloadId))
                .setUseDuplicateLinks(true)
                .build()
        } catch (exception: IOException) {
            Log.e(TAG, "Could not build download client")
            update.status = UpdateStatus.PAUSED_ERROR
            notifyUpdateChange(downloadId)
            return
        }
        addDownloadClient(entry, downloadClient)
        update.status = UpdateStatus.STARTING
        notifyUpdateChange(downloadId)
        downloadClient.start()
        wakeLock.acquire()
    }

    @SuppressLint("WakelockTimeout")
    fun resumeDownload(downloadId: String) {
        Log.d(TAG, "Resuming $downloadId")
        if (!downloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return
        }
        val entry = downloads[downloadId]
        if (entry == null) {
            Log.e(TAG, "Could not get download entry")
            return
        }
        val update = entry.update
        val file = update.file
        if (file == null || !file.exists()) {
            Log.e(TAG, "The destination file of $downloadId doesn't exist, can't resume")
            update.status = UpdateStatus.PAUSED_ERROR
            notifyUpdateChange(downloadId)
            return
        }
        if (file.exists() && update.fileSize > 0 && file.length() >= update.fileSize) {
            Log.d(TAG, "File already downloaded, starting verification")
            update.status = UpdateStatus.VERIFYING
            verifyUpdateAsync(downloadId)
            notifyUpdateChange(downloadId)
        } else {
            val downloadClient: DownloadClient
            try {
                downloadClient = DownloadClient.Builder()
                    .setUrl(update.downloadUrl)
                    .setDestination(update.file)
                    .setDownloadCallback(getDownloadCallback(downloadId))
                    .setProgressListener(getProgressListener(downloadId))
                    .setUseDuplicateLinks(true)
                    .build()
            } catch (exception: IOException) {
                Log.e(TAG, "Could not build download client")
                update.status = UpdateStatus.PAUSED_ERROR
                notifyUpdateChange(downloadId)
                return
            }
            addDownloadClient(entry, downloadClient)
            update.status = UpdateStatus.STARTING
            notifyUpdateChange(downloadId)
            downloadClient.resume()
            wakeLock.acquire()
        }
    }

    fun pauseDownload(downloadId: String) {
        Log.d(TAG, "Pausing $downloadId")
        if (!isDownloading(downloadId)) {
            return
        }
        val entry = downloads[downloadId]
        if (entry != null) {
            entry.downloadClient!!.cancel()
            removeDownloadClient(entry)
            entry.update.status = UpdateStatus.PAUSED
            entry.update.eta = 0
            entry.update.speed = 0
            notifyUpdateChange(downloadId)
        }
    }

    private fun deleteUpdateAsync(update: Update) {
        Thread {
            val file = update.file
            if (file != null && file.exists() && !file.delete()) {
                Log.e(TAG, "Could not delete " + file.absolutePath)
            }
            updatesDbHelper.removeUpdate(update.downloadId)
        }.start()
    }

    fun deleteUpdate(downloadId: String) {
        Log.d(TAG, "Cancelling $downloadId")
        if (!downloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return
        }
        val entry = downloads[downloadId]
        if (entry != null) {
            val update = entry.update
            update.status = UpdateStatus.DELETED
            update.progress = 0
            update.persistentStatus = UpdateStatus.Persistent.UNKNOWN
            deleteUpdateAsync(update)
            if (!update.availableOnline) {
                Log.d(TAG, "Download no longer available online, removing")
                downloads.remove(downloadId)
                notifyUpdateDelete(downloadId)
            } else {
                notifyUpdateChange(downloadId)
            }
        }
    }

    val updates: List<UpdateInfo>
        get() {
            val updates: MutableList<UpdateInfo> = ArrayList()
            for (entry in downloads.values) {
                updates.add(entry.update)
            }
            return updates
        }

    fun getUpdate(downloadId: String): UpdateInfo? {
        val entry = downloads[downloadId]
        return entry?.update
    }

    fun getActualUpdate(downloadId: String?): Update? {
        val entry = downloads[downloadId]
        return entry?.update
    }

    fun isDownloading(downloadId: String): Boolean {
        return downloads.containsKey(downloadId) &&
                downloads[downloadId]!!.downloadClient != null
    }

    fun hasActiveDownloads(): Boolean {
        return activeDownloads > 0
    }

    val isVerifyingUpdate: Boolean
        get() = verifyingUpdates.size > 0

    fun isVerifyingUpdate(downloadId: String): Boolean {
        return verifyingUpdates.contains(downloadId)
    }

    val isInstallingUpdate: Boolean
        get() = isInstalling ||
                isInstallingUpdate(applicationContext)

    fun isInstallingUpdate(downloadId: String): Boolean {
        return isInstalling(downloadId) ||
                isInstallingUpdate(applicationContext, downloadId)
    }

    val isInstallingABUpdate: Boolean
        get() = isInstallingUpdate(applicationContext)

    fun isWaitingForReboot(downloadId: String?): Boolean {
        return isWaitingForReboot(applicationContext, downloadId)
    }

    fun setPerformanceMode(enable: Boolean) {
        if (!Utils.isABDevice) {
            return
        }
        getInstance(applicationContext, this)!!.setPerformanceMode(enable)
    }

    companion object {
        private const val TAG = "UpdaterController"

        const val ACTION_DOWNLOAD_PROGRESS = "action_download_progress"
        const val ACTION_INSTALL_PROGRESS = "action_install_progress"
        const val ACTION_UPDATE_REMOVED = "action_update_removed"
        const val ACTION_UPDATE_STATUS = "action_update_status_change"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        private const val MAX_REPORT_INTERVAL_MS = 1000

        private var sUpdaterController: UpdaterController? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): UpdaterController? {
            if (sUpdaterController == null) {
                sUpdaterController = UpdaterController(context)
            }
            return sUpdaterController
        }
    }
}