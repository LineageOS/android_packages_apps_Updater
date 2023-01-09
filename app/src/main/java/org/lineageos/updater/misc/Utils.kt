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
package org.lineageos.updater.misc

import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.SystemProperties
import android.os.storage.StorageManager
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.json.JSONException
import org.json.JSONObject
import org.lineageos.updater.R
import org.lineageos.updater.UpdatesDbHelper
import org.lineageos.updater.controller.UpdaterService
import org.lineageos.updater.model.Update
import org.lineageos.updater.model.UpdateBaseInfo
import org.lineageos.updater.model.UpdateInfo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.zip.ZipFile

object Utils {
    private const val TAG = "Utils"

    fun getDownloadPath(context: Context): File {
        return File(context.getString(R.string.download_path))
    }

    fun getExportPath(context: Context): File {
        val dir = File(
            context.getExternalFilesDir(null),
            context.getString(R.string.export_path)
        )
        if (!dir.isDirectory) {
            if (dir.exists() || !dir.mkdirs()) {
                throw RuntimeException("Could not create directory")
            }
        }
        return dir
    }

    fun getCachedUpdateList(context: Context): File {
        return File(context.cacheDir, "updates.json")
    }

    // This should really return an UpdateBaseInfo object, but currently this only
    // used to initialize UpdateInfo objects
    @Throws(JSONException::class)
    private fun parseJsonUpdate(`object`: JSONObject): UpdateInfo {
        val update = Update()
        update.timestamp = `object`.getLong("datetime")
        update.name = `object`.getString("filename")
        update.downloadId = `object`.getString("id")
        update.type = `object`.getString("romtype")
        update.fileSize = `object`.getLong("size")
        update.downloadUrl = `object`.getString("url")
        update.version = `object`.getString("version")
        return update
    }

    private fun isCompatible(update: UpdateBaseInfo): Boolean {
        if (update.version < SystemProperties.get(Constants.PROP_BUILD_VERSION)) {
            Log.d(TAG, update.name + " is older than current Android version")
            return false
        }
        if (!SystemProperties.getBoolean(Constants.PROP_UPDATER_ALLOW_DOWNGRADING, false) &&
            update.timestamp <= SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0)
        ) {
            Log.d(TAG, update.name + " is older than/equal to the current build")
            return false
        }
        if (!update.type.equals(
                SystemProperties.get(Constants.PROP_RELEASE_TYPE),
                ignoreCase = true
            )
        ) {
            Log.d(TAG, update.name + " has type " + update.type)
            return false
        }
        return true
    }

    fun canInstall(update: UpdateBaseInfo) =
        (SystemProperties.getBoolean(Constants.PROP_UPDATER_ALLOW_DOWNGRADING, false) ||
                update.timestamp > SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0)) &&
                update.version.equals(
                    SystemProperties.get(Constants.PROP_BUILD_VERSION), ignoreCase = true
                )

    @Throws(IOException::class, JSONException::class)
    fun parseJson(file: File, compatibleOnly: Boolean): List<UpdateInfo> {
        val updates: MutableList<UpdateInfo> = ArrayList()
        val json = StringBuilder()
        BufferedReader(FileReader(file)).use { br ->
            var line: String?
            while (br.readLine().also { line = it } != null) {
                json.append(line)
            }
        }
        val obj = JSONObject(json.toString())
        val updatesList = obj.getJSONArray("response")
        for (i in 0 until updatesList.length()) {
            if (updatesList.isNull(i)) {
                continue
            }
            try {
                val update = parseJsonUpdate(updatesList.getJSONObject(i))
                if (!compatibleOnly || isCompatible(update)) {
                    updates.add(update)
                } else {
                    Log.d(TAG, "Ignoring incompatible update " + update.name)
                }
            } catch (e: JSONException) {
                Log.e(TAG, "Could not parse update object, index=$i", e)
            }
        }
        return updates
    }

    fun getServerURL(context: Context): String {
        val incrementalVersion = SystemProperties.get(Constants.PROP_BUILD_VERSION_INCREMENTAL)
        val device = SystemProperties.get(
            Constants.PROP_NEXT_DEVICE,
            SystemProperties.get(Constants.PROP_DEVICE)
        )
        val type = SystemProperties.get(Constants.PROP_RELEASE_TYPE).lowercase()
        var serverUrl = SystemProperties.get(Constants.PROP_UPDATER_URI)
        if (serverUrl.trim { it <= ' ' }.isEmpty()) {
            serverUrl = context.getString(R.string.updater_server_url)
        }
        return serverUrl.replace("{device}", device)
            .replace("{type}", type)
            .replace("{incr}", incrementalVersion)
    }

    fun getUpgradeBlockedURL(context: Context): String {
        val device = SystemProperties.get(
            Constants.PROP_NEXT_DEVICE,
            SystemProperties.get(Constants.PROP_DEVICE)
        )
        return context.getString(R.string.blocked_update_info_url, device)
    }

    fun getChangelogURL(context: Context): String {
        val device = SystemProperties.get(
            Constants.PROP_NEXT_DEVICE,
            SystemProperties.get(Constants.PROP_DEVICE)
        )
        return context.getString(R.string.menu_changelog_url, device)
    }

    fun triggerUpdate(context: Context, downloadId: String?) {
        val intent = Intent(context, UpdaterService::class.java)
        intent.action = UpdaterService.ACTION_INSTALL_UPDATE
        intent.putExtra(UpdaterService.EXTRA_DOWNLOAD_ID, downloadId)
        context.startService(intent)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(
            ConnectivityManager::class.java
        )

        val info = connectivityManager.activeNetworkInfo ?: return false
        return info.isConnected && info.isAvailable
    }

    fun isOnWifiOrEthernet(context: Context): Boolean {
        val cm = context.getSystemService(
            ConnectivityManager::class.java
        )

        val info = cm.activeNetworkInfo ?: return false

        return info.type == ConnectivityManager.TYPE_ETHERNET ||
                info.type == ConnectivityManager.TYPE_WIFI
    }

    /**
     * Compares two json formatted updates list files
     *
     * @param oldJson old update list
     * @param newJson new update list
     * @return true if newJson has at least a compatible update not available in oldJson
     */
    @Throws(IOException::class, JSONException::class)
    fun checkForNewUpdates(oldJson: File, newJson: File): Boolean {
        val oldList = parseJson(oldJson, true)
        val newList = parseJson(newJson, true)
        val oldIds: MutableSet<String> = HashSet()
        for (update in oldList) {
            oldIds.add(update.downloadId)
        }
        // In case of no new updates, the old list should
        // have all (if not more) the updates
        for (update in newList) {
            if (!oldIds.contains(update.downloadId)) {
                return true
            }
        }
        return false
    }

    private const val FIXED_HEADER_SIZE = 30

    /**
     * Get the offset to the compressed data of a file inside the given zip
     *
     * @param zipFile input zip file
     * @param entryPath full path of the entry
     * @return the offset of the compressed, or -1 if not found
     * @throws IllegalArgumentException if the given entry is not found
     */
    fun getZipEntryOffset(zipFile: ZipFile, entryPath: String): Long {
        // Each entry has an header of (30 + n + m) bytes
        // 'n' is the length of the file name
        // 'm' is the length of the extra field
        val zipEntries = zipFile.entries()
        var offset: Long = 0
        while (zipEntries.hasMoreElements()) {
            val entry = zipEntries.nextElement()
            val n = entry.name.length
            val m = if (entry.extra == null) 0 else entry.extra.size
            val headerSize = FIXED_HEADER_SIZE + n + m
            offset += headerSize.toLong()
            if (entry.name == entryPath) {
                return offset
            }
            offset += entry.compressedSize
        }
        Log.e(TAG, "Entry $entryPath not found")
        throw IllegalArgumentException("The given entry was not found")
    }

    private fun removeUncryptFiles(downloadPath: File) {
        val uncryptFiles = downloadPath.listFiles { _: File?, name: String ->
            name.endsWith(
                Constants.UNCRYPT_FILE_EXT
            )
        } ?: return

        for (file in uncryptFiles) {
            file.delete()
        }
    }

    private const val DOWNLOADS_CLEANUP_DONE = "cleanup_done"

    /**
     * Cleanup the download directory, which is assumed to be a privileged location
     * the user can't access and that might have stale files. This can happen if
     * the data of the application are wiped.
     *
     */
    fun cleanupDownloadsDir(context: Context) {
        val downloadPath = getDownloadPath(context)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        removeUncryptFiles(downloadPath)

        val buildTimestamp = SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0)
        val prevTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, 0)
        val lastUpdatePath = preferences.getString(Constants.PREF_INSTALL_PACKAGE_PATH, null)
        val reinstalling = preferences.getBoolean(Constants.PREF_INSTALL_AGAIN, false)
        val deleteUpdates = preferences.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, false)

        if ((buildTimestamp != prevTimestamp || reinstalling) && deleteUpdates && lastUpdatePath != null) {
            val lastUpdate = File(lastUpdatePath)
            if (lastUpdate.exists()) {
                lastUpdate.delete()
                // Remove the pref not to delete the file if re-downloaded
                preferences.edit().remove(Constants.PREF_INSTALL_PACKAGE_PATH).apply()
            }
        }

        if (preferences.getBoolean(DOWNLOADS_CLEANUP_DONE, false)) {
            return
        }

        Log.d(TAG, "Cleaning $downloadPath")

        if (!downloadPath.isDirectory) {
            return
        }

        val files = downloadPath.listFiles() ?: return

        // Ideally the database is empty when we get here
        val dbHelper = UpdatesDbHelper(context)
        val knownPaths: MutableList<String> = ArrayList()
        for (update in dbHelper.updates) {
            knownPaths.add(update.file!!.absolutePath)
        }

        for (file in files) {
            if (!knownPaths.contains(file.absolutePath)) {
                Log.d(TAG, "Deleting " + file.absolutePath)
                file.delete()
            }
        }

        preferences.edit().putBoolean(DOWNLOADS_CLEANUP_DONE, true).apply()
    }

    fun appendSequentialNumber(file: File): File {
        val name: String
        val extension: String
        val extensionPosition = file.name.lastIndexOf(".")
        if (extensionPosition > 0) {
            name = file.name.substring(0, extensionPosition)
            extension = file.name.substring(extensionPosition)
        } else {
            name = file.name
            extension = ""
        }

        val parent = file.parentFile
        for (i in 1 until Int.MAX_VALUE) {
            val newFile = File(parent, "$name-$i$extension")
            if (!newFile.exists()) {
                return newFile
            }
        }

        throw IllegalStateException()
    }

    val isABDevice: Boolean
        get() = SystemProperties.getBoolean(Constants.PROP_AB_DEVICE, false)

    private fun isABUpdate(zipFile: ZipFile) =
        zipFile.getEntry(Constants.AB_PAYLOAD_BIN_PATH) != null &&
                zipFile.getEntry(Constants.AB_PAYLOAD_PROPERTIES_PATH) != null

    @Throws(IOException::class)
    fun isABUpdate(file: File): Boolean {
        val zipFile = ZipFile(file)
        val isAB = isABUpdate(zipFile)
        zipFile.close()
        return isAB
    }

    fun hasTouchscreen(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

    fun addToClipboard(
        context: Context, label: String?, text: String?,
        toastMessage: String?
    ) {
        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }

    fun isEncrypted(context: Context, file: File?): Boolean {
        val storageManager = context.getSystemService(StorageManager::class.java)
        return storageManager.isEncrypted(file)
    }

    fun getUpdateCheckSetting(context: Context): Int {
        val preferences = PreferenceManager.getDefaultSharedPreferences(
            context
        )

        return preferences.getInt(
            Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL,
            Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY
        )
    }

    fun isUpdateCheckEnabled(context: Context) =
        getUpdateCheckSetting(context) != Constants.AUTO_UPDATES_CHECK_INTERVAL_NEVER

    fun getUpdateCheckInterval(context: Context) = when (getUpdateCheckSetting(context)) {
        Constants.AUTO_UPDATES_CHECK_INTERVAL_DAILY -> AlarmManager.INTERVAL_DAY
        Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY -> AlarmManager.INTERVAL_DAY * 7
        Constants.AUTO_UPDATES_CHECK_INTERVAL_MONTHLY -> AlarmManager.INTERVAL_DAY * 30
        else -> AlarmManager.INTERVAL_DAY * 7
    }

    val isRecoveryUpdateExecPresent: Boolean
        get() = File(Constants.UPDATE_RECOVERY_EXEC).exists()
}
