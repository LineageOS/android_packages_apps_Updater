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

import android.content.Context
import android.os.RecoverySystem
import android.os.SystemClock
import android.os.SystemProperties
import android.util.Log
import androidx.preference.PreferenceManager
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.FileUtils.ProgressCallBack
import org.lineageos.updater.misc.FileUtils.copyFile
import org.lineageos.updater.misc.Utils
import org.lineageos.updater.model.UpdateInfo
import org.lineageos.updater.model.UpdateStatus
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

internal class UpdateInstaller private constructor(
    context: Context,
    private val updaterController: UpdaterController
) {
    private var prepareUpdateThread: Thread? = null

    @Volatile
    private var canCancel = false
    private val applicationContext = context.applicationContext

    fun install(downloadId: String) {
        if (isInstalling) {
            Log.e(TAG, "Already installing an update")
            return
        }

        val update = updaterController.getUpdate(downloadId)!!
        val file = update.file!!

        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val buildTimestamp = SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0)
        val lastBuildTimestamp = preferences.getLong(
            Constants.PREF_INSTALL_OLD_TIMESTAMP,
            buildTimestamp
        )
        val isReinstalling = buildTimestamp == lastBuildTimestamp

        preferences.edit()
            .putLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, buildTimestamp)
            .putLong(Constants.PREF_INSTALL_NEW_TIMESTAMP, update.timestamp)
            .putString(Constants.PREF_INSTALL_PACKAGE_PATH, file.absolutePath)
            .putBoolean(Constants.PREF_INSTALL_AGAIN, isReinstalling)
            .putBoolean(Constants.PREF_INSTALL_NOTIFIED, false)
            .apply()

        if (Utils.isEncrypted(applicationContext, file)) {
            // uncrypt rewrites the file so that it can be read without mounting
            // the filesystem, so create a copy of it.
            prepareForUncryptAndInstall(update)
        } else {
            installPackage(file, downloadId)
        }
    }

    private fun installPackage(update: File, downloadId: String) {
        try {
            RecoverySystem.installPackage(applicationContext, update)
        } catch (e: IOException) {
            Log.e(TAG, "Could not install update", e)
            updaterController.getActualUpdate(downloadId)!!
                .status = UpdateStatus.INSTALLATION_FAILED
            updaterController.notifyUpdateChange(downloadId)
        }
    }

    @Synchronized
    private fun prepareForUncryptAndInstall(update: UpdateInfo) {
        val uncryptFilePath = update.file!!.absolutePath + Constants.UNCRYPT_FILE_EXT
        val uncryptFile = File(uncryptFilePath)
        val copyUpdateRunnable: Runnable = object : Runnable {
            private var mLastUpdate: Long = -1
            val mProgressCallBack: ProgressCallBack = object : ProgressCallBack {
                override fun update(progress: Int) {
                    val now = SystemClock.elapsedRealtime()
                    if (mLastUpdate < 0 || now - mLastUpdate > 500) {
                        updaterController.getActualUpdate(update.downloadId)!!
                            .installProgress = progress
                        updaterController.notifyInstallProgress(update.downloadId)
                        mLastUpdate = now
                    }
                }
            }

            override fun run() {
                try {
                    canCancel = true
                    copyFile(update.file!!, uncryptFile, mProgressCallBack)
                    try {
                        val perms: MutableSet<PosixFilePermission> = HashSet()
                        perms.add(PosixFilePermission.OWNER_READ)
                        perms.add(PosixFilePermission.OWNER_WRITE)
                        perms.add(PosixFilePermission.OTHERS_READ)
                        perms.add(PosixFilePermission.GROUP_READ)
                        Files.setPosixFilePermissions(uncryptFile.toPath(), perms)
                    } catch (_: IOException) {
                    }
                    canCancel = false
                    if (prepareUpdateThread!!.isInterrupted) {
                        val actualUpdate = updaterController.getActualUpdate(update.downloadId)!!
                        actualUpdate.status = UpdateStatus.INSTALLATION_CANCELLED
                        actualUpdate.installProgress = 0
                        uncryptFile.delete()
                    } else {
                        installPackage(uncryptFile, update.downloadId)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Could not copy update", e)
                    uncryptFile.delete()
                    updaterController.getActualUpdate(update.downloadId)!!
                        .status = UpdateStatus.INSTALLATION_FAILED
                } finally {
                    synchronized(this@UpdateInstaller) {
                        canCancel = false
                        prepareUpdateThread = null
                        sInstallingUpdate = null
                    }
                    updaterController.notifyUpdateChange(update.downloadId)
                }
            }
        }
        prepareUpdateThread = Thread(copyUpdateRunnable)
        prepareUpdateThread!!.start()
        sInstallingUpdate = update.downloadId
        canCancel = false
        updaterController.getActualUpdate(update.downloadId)!!
            .status = UpdateStatus.INSTALLING
        updaterController.notifyUpdateChange(update.downloadId)
    }

    @Synchronized
    fun cancel() {
        if (!canCancel) {
            Log.d(TAG, "Nothing to cancel")
            return
        }
        prepareUpdateThread!!.interrupt()
    }

    companion object {
        private const val TAG = "UpdateInstaller"

        private var sInstance: UpdateInstaller? = null
        private var sInstallingUpdate: String? = null

        @Synchronized
        fun getInstance(
            context: Context,
            updaterController: UpdaterController
        ): UpdateInstaller? {
            if (sInstance == null) {
                sInstance = UpdateInstaller(context, updaterController)
            }
            return sInstance
        }

        @get:Synchronized
        val isInstalling: Boolean
            get() = sInstallingUpdate != null

        @Synchronized
        fun isInstalling(downloadId: String): Boolean {
            return sInstallingUpdate != null && sInstallingUpdate == downloadId
        }
    }
}
