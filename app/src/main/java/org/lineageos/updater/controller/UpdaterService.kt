/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.controller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import org.lineageos.updater.R
import org.lineageos.updater.UpdaterReceiver
import org.lineageos.updater.UpdatesActivity
import org.lineageos.updater.controller.ABUpdateInstaller.Companion.isInstallingUpdate
import org.lineageos.updater.controller.ABUpdateInstaller.Companion.isInstallingUpdateSuspended
import org.lineageos.updater.controller.UpdateInstaller.Companion.isInstalling
import org.lineageos.updater.controller.UpdaterController.Companion.getInstance
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.StringGenerator.formatETA
import org.lineageos.updater.misc.StringGenerator.getDateLocalizedUTC
import org.lineageos.updater.misc.Utils
import org.lineageos.updater.model.UpdateInfo
import org.lineageos.updater.model.UpdateStatus
import java.io.IOException
import java.text.DateFormat
import java.text.NumberFormat

class UpdaterService : Service() {
    private val binder: IBinder = LocalBinder()
    private var hasClients = false
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationStyle: NotificationCompat.BigTextStyle
    var updaterController: UpdaterController? = null
        private set

    override fun onCreate() {
        super.onCreate()
        updaterController = getInstance(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        val notificationChannel = NotificationChannel(
            ONGOING_NOTIFICATION_CHANNEL,
            getString(R.string.ongoing_channel_title),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(notificationChannel)
        notificationBuilder = NotificationCompat.Builder(
            this,
            ONGOING_NOTIFICATION_CHANNEL
        )
        notificationBuilder.setSmallIcon(R.drawable.ic_system_update)
        notificationBuilder.setShowWhen(false)
        notificationStyle = NotificationCompat.BigTextStyle()
        notificationBuilder.setStyle(notificationStyle)
        val notificationIntent = Intent(this, UpdatesActivity::class.java)
        val intent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationBuilder.setContentIntent(intent)
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID)
                if (UpdaterController.ACTION_UPDATE_STATUS == intent.action) {
                    val update = updaterController!!.getUpdate(downloadId!!)
                    setNotificationTitle(update)
                    val extras = Bundle()
                    extras.putString(UpdaterController.EXTRA_DOWNLOAD_ID, downloadId)
                    notificationBuilder.setExtras(extras)
                    handleUpdateStatusChange(update)
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS == intent.action) {
                    val update = updaterController!!.getUpdate(downloadId!!)
                    handleDownloadProgressChange(update)
                } else if (UpdaterController.ACTION_INSTALL_PROGRESS == intent.action) {
                    val update = updaterController!!.getUpdate(downloadId!!)
                    setNotificationTitle(update)
                    handleInstallProgress(update)
                } else if (UpdaterController.ACTION_UPDATE_REMOVED == intent.action) {
                    val extras = notificationBuilder.extras
                    if (downloadId == extras.getString(UpdaterController.EXTRA_DOWNLOAD_ID)) {
                        notificationBuilder.setExtras(null)
                        val update = updaterController!!.getUpdate(downloadId!!)
                        if (update!!.status !== UpdateStatus.INSTALLED) {
                            notificationManager.cancel(NOTIFICATION_ID)
                        }
                    }
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS)
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS)
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS)
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            broadcastReceiver
        )
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        val service: UpdaterService
            get() = this@UpdaterService
    }

    override fun onBind(intent: Intent): IBinder {
        hasClients = true
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        hasClients = false
        tryStopSelf()
        return false
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting service")

        if (intent.action == null) {
            if (isInstallingUpdate(this)) {
                // The service is being restarted.
                val installer = ABUpdateInstaller.getInstance(
                    this,
                    updaterController!!
                )!!
                installer.reconnect()
            }
        } else if (ACTION_DOWNLOAD_CONTROL == intent.action) {
            val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)!!
            when (intent.getIntExtra(EXTRA_DOWNLOAD_CONTROL, -1)) {
                DOWNLOAD_RESUME -> updaterController!!.resumeDownload(downloadId)
                DOWNLOAD_PAUSE -> updaterController!!.pauseDownload(downloadId)
                else -> Log.e(TAG, "Unknown download action")
            }
        } else if (ACTION_INSTALL_UPDATE == intent.action) {
            val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)!!
            val update = updaterController!!.getUpdate(downloadId)!!
            require(update.persistentStatus == UpdateStatus.Persistent.VERIFIED) {
                update.downloadId + " is not verified"
            }

            try {
                if (Utils.isABUpdate(update.file!!)) {
                    val installer = ABUpdateInstaller.getInstance(
                        this,
                        updaterController!!
                    )!!
                    installer.install(downloadId)
                } else {
                    val installer = UpdateInstaller.getInstance(
                        this,
                        updaterController!!
                    )!!
                    installer.install(downloadId)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Could not install update", e)
                updaterController!!.getActualUpdate(downloadId)!!
                    .status = UpdateStatus.INSTALLATION_FAILED
                updaterController!!.notifyUpdateChange(downloadId)
            }
        } else if (ACTION_INSTALL_STOP == intent.action) {
            if (isInstalling) {
                val installer = UpdateInstaller.getInstance(
                    this,
                    updaterController!!
                )
                installer!!.cancel()
            } else if (isInstallingUpdate(this)) {
                val installer = ABUpdateInstaller.getInstance(
                    this,
                    updaterController!!
                )!!
                installer.reconnect()
                installer.cancel()
            }
        } else if (ACTION_INSTALL_SUSPEND == intent.action) {
            if (isInstallingUpdate(this)) {
                val installer = ABUpdateInstaller.getInstance(
                    this,
                    updaterController!!
                )!!
                installer.reconnect()
                installer.suspend()
            }
        } else if (ACTION_INSTALL_RESUME == intent.action) {
            if (isInstallingUpdateSuspended(this)) {
                val installer = ABUpdateInstaller.getInstance(
                    this,
                    updaterController!!
                )!!
                installer.reconnect()
                installer.resume()
            }
        }
        return if (isInstallingUpdate(this)) START_STICKY else START_NOT_STICKY
    }

    private fun tryStopSelf() {
        if (!hasClients && !updaterController!!.hasActiveDownloads() &&
            !updaterController!!.isInstallingUpdate
        ) {
            Log.d(TAG, "Service no longer needed, stopping")
            stopSelf()
        }
    }

    private fun handleUpdateStatusChange(update: UpdateInfo?) {
        when (update!!.status) {
            UpdateStatus.DELETED -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                notificationBuilder.setOngoing(false)
                notificationManager.cancel(NOTIFICATION_ID)
                tryStopSelf()
            }
            UpdateStatus.STARTING -> {
                notificationBuilder.mActions.clear()
                notificationBuilder.setProgress(0, 0, true)
                notificationStyle.setSummaryText(null)
                val text = getString(R.string.download_starting_notification)
                notificationStyle.bigText(text)
                notificationBuilder.setStyle(notificationStyle)
                notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download)
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(true)
                notificationBuilder.setAutoCancel(false)
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            }
            UpdateStatus.DOWNLOADING -> {
                val text = getString(R.string.downloading_notification)
                notificationStyle.bigText(text)
                notificationBuilder.setStyle(notificationStyle)
                notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download)
                notificationBuilder.addAction(
                    android.R.drawable.ic_media_pause,
                    getString(R.string.pause_button),
                    getPausePendingIntent(update.downloadId)
                )
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(true)
                notificationBuilder.setAutoCancel(false)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            }
            UpdateStatus.PAUSED -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                // In case we pause before the first progress update
                notificationBuilder.setProgress(100, update.progress, false)
                notificationBuilder.mActions.clear()
                val text = getString(R.string.download_paused_notification)
                notificationStyle.bigText(text)
                notificationBuilder.setStyle(notificationStyle)
                notificationBuilder.setSmallIcon(R.drawable.ic_pause)
                notificationBuilder.addAction(
                    android.R.drawable.ic_media_play,
                    getString(R.string.resume_button),
                    getResumePendingIntent(update.downloadId)
                )
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(false)
                notificationBuilder.setAutoCancel(false)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                tryStopSelf()
            }
            UpdateStatus.PAUSED_ERROR -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                val progress = update.progress
                // In case we pause before the first progress update
                notificationBuilder.setProgress(if (progress > 0) 100 else 0, progress, false)
                notificationBuilder.mActions.clear()
                val text = getString(R.string.download_paused_error_notification)
                notificationStyle.bigText(text)
                notificationBuilder.setStyle(notificationStyle)
                notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_warning)
                notificationBuilder.addAction(
                    android.R.drawable.ic_media_play,
                    getString(R.string.resume_button),
                    getResumePendingIntent(update.downloadId)
                )
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(false)
                notificationBuilder.setAutoCancel(false)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                tryStopSelf()
            }
            UpdateStatus.VERIFYING -> {
                notificationBuilder.setProgress(0, 0, true)
                notificationStyle.setSummaryText(null)
                notificationBuilder.setStyle(notificationStyle)
                notificationBuilder.setSmallIcon(R.drawable.ic_system_update)
                notificationBuilder.mActions.clear()
                val text = getString(R.string.verifying_download_notification)
                notificationStyle.bigText(text)
                notificationBuilder.setTicker(text)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            }
            UpdateStatus.VERIFIED -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                notificationBuilder.setStyle(null)
                notificationBuilder.setSmallIcon(R.drawable.ic_system_update)
                notificationBuilder.setProgress(0, 0, false)
                val text = getString(R.string.download_completed_notification)
                notificationBuilder.setContentText(text)
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(false)
                notificationBuilder.setAutoCancel(true)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                tryStopSelf()
            }
            UpdateStatus.VERIFICATION_FAILED -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                notificationBuilder.setStyle(null)
                notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_warning)
                notificationBuilder.setProgress(0, 0, false)
                val text = getString(R.string.verification_failed_notification)
                notificationBuilder.setContentText(text)
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(false)
                notificationBuilder.setAutoCancel(true)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                tryStopSelf()
            }
            UpdateStatus.INSTALLING -> {
                notificationBuilder.mActions.clear()
                notificationBuilder.setStyle(notificationStyle)
                notificationBuilder.setSmallIcon(R.drawable.ic_system_update)
                notificationBuilder.setProgress(0, 0, false)
                notificationStyle.setSummaryText(null)
                val text =
                    if (isInstalling) getString(R.string.dialog_prepare_zip_message) else getString(
                        R.string.installing_update
                    )
                notificationStyle.bigText(text)
                if (isInstallingUpdate(this)) {
                    notificationBuilder.addAction(
                        android.R.drawable.ic_media_pause,
                        getString(R.string.suspend_button),
                        suspendInstallationPendingIntent
                    )
                }
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(true)
                notificationBuilder.setAutoCancel(false)
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            }
            UpdateStatus.INSTALLED -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                notificationBuilder.mActions.clear()
                notificationBuilder.setStyle(null)
                notificationBuilder.setSmallIcon(R.drawable.ic_system_update)
                notificationBuilder.setProgress(0, 0, false)
                val text = getString(R.string.installing_update_finished)
                notificationBuilder.setContentText(text)
                notificationBuilder.addAction(
                    R.drawable.ic_system_update,
                    getString(R.string.reboot),
                    rebootPendingIntent
                )
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(false)
                notificationBuilder.setAutoCancel(true)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val deleteUpdate = pref.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, false)
                if (deleteUpdate) {
                    updaterController!!.deleteUpdate(update.downloadId)
                }
                tryStopSelf()
            }
            UpdateStatus.INSTALLATION_FAILED -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                notificationBuilder.setStyle(null)
                notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_warning)
                notificationBuilder.setProgress(0, 0, false)
                val text = getString(R.string.installing_update_error)
                notificationBuilder.setContentText(text)
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(false)
                notificationBuilder.setAutoCancel(true)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                tryStopSelf()
            }
            UpdateStatus.INSTALLATION_CANCELLED -> {
                stopForeground(true)
                tryStopSelf()
            }
            UpdateStatus.INSTALLATION_SUSPENDED -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                // In case we pause before the first progress update
                notificationBuilder.setProgress(100, update.progress, false)
                notificationBuilder.mActions.clear()
                val text = getString(R.string.installation_suspended_notification)
                notificationStyle.bigText(text)
                notificationBuilder.setStyle(notificationStyle)
                notificationBuilder.setSmallIcon(R.drawable.ic_pause)
                notificationBuilder.addAction(
                    android.R.drawable.ic_media_play,
                    getString(R.string.resume_button),
                    resumeInstallationPendingIntent
                )
                notificationBuilder.setTicker(text)
                notificationBuilder.setOngoing(true)
                notificationBuilder.setAutoCancel(false)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                tryStopSelf()
            }
            else -> {}
        }
    }

    private fun handleDownloadProgressChange(update: UpdateInfo?) {
        val progress = update!!.progress
        notificationBuilder.setProgress(100, progress, false)
        val percent = NumberFormat.getPercentInstance().format((progress / 100f).toDouble())
        notificationStyle.setSummaryText(percent)
        setNotificationTitle(update)
        val speed = Formatter.formatFileSize(this, update.speed)
        val eta: CharSequence = formatETA(this, update.eta * 1000)
        notificationStyle.bigText(
            getString(R.string.text_download_speed, eta, speed)
        )
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun handleInstallProgress(update: UpdateInfo?) {
        setNotificationTitle(update)
        val progress = update!!.installProgress
        notificationBuilder.setProgress(100, progress, false)
        val percent = NumberFormat.getPercentInstance().format((progress / 100f).toDouble())
        notificationStyle.setSummaryText(percent)
        val notAB = isInstalling
        notificationStyle.bigText(
            if (notAB) {
                getString(R.string.dialog_prepare_zip_message)
            } else if (update.finalizing) {
                getString(
                    R.string.finalizing_package
                )
            } else {
                getString(R.string.preparing_ota_first_boot)
            }
        )
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun setNotificationTitle(update: UpdateInfo?) {
        val buildDate = getDateLocalizedUTC(
            this,
            DateFormat.MEDIUM, update!!.timestamp
        )
        val buildInfo = getString(
            R.string.list_build_version_date,
            update.version, buildDate
        )
        notificationStyle.setBigContentTitle(buildInfo)
        notificationBuilder.setContentTitle(buildInfo)
    }

    private fun getResumePendingIntent(downloadId: String): PendingIntent {
        val intent = Intent(this, UpdaterService::class.java)
        intent.action = ACTION_DOWNLOAD_CONTROL
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_RESUME)
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPausePendingIntent(downloadId: String): PendingIntent {
        val intent = Intent(this, UpdaterService::class.java)
        intent.action = ACTION_DOWNLOAD_CONTROL
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_PAUSE)
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val rebootPendingIntent: PendingIntent
        get() {
            val intent = Intent(this, UpdaterReceiver::class.java)
            intent.action = UpdaterReceiver.ACTION_INSTALL_REBOOT
            return PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    private val suspendInstallationPendingIntent: PendingIntent
        get() {
            val intent = Intent(this, UpdaterService::class.java)
            intent.action = ACTION_INSTALL_SUSPEND
            return PendingIntent.getService(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    private val resumeInstallationPendingIntent: PendingIntent
        get() {
            val intent = Intent(this, UpdaterService::class.java)
            intent.action = ACTION_INSTALL_RESUME
            return PendingIntent.getService(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    companion object {
        private const val TAG = "UpdaterService"

        const val ACTION_DOWNLOAD_CONTROL = "action_download_control"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_DOWNLOAD_CONTROL = "extra_download_control"
        const val ACTION_INSTALL_UPDATE = "action_install_update"
        const val ACTION_INSTALL_STOP = "action_install_stop"
        const val ACTION_INSTALL_SUSPEND = "action_install_suspend"
        const val ACTION_INSTALL_RESUME = "action_install_resume"

        private const val ONGOING_NOTIFICATION_CHANNEL = "ongoing_notification_channel"

        const val DOWNLOAD_RESUME = 0
        const val DOWNLOAD_PAUSE = 1

        private const val NOTIFICATION_ID = 10
    }
}
