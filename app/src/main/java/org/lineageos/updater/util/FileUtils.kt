/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import android.os.FileUtils as OsFileUtils

object FileUtils {

    private const val TAG = "FileUtils"

    /**
     * Copies a local file and removes the incomplete destination if the copy fails.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(sourceFile: File, destFile: File) {
        try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    OsFileUtils.copy(input, output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not copy file", e)
            destFile.delete()
            throw e
        }
    }

    /**
     * Copies a local file into a content URI opened for writing.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(cr: ContentResolver, sourceFile: File, destUri: Uri) {
        try {
            FileInputStream(sourceFile).use { input ->
                cr.openFileDescriptor(destUri, "w")!!.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { output ->
                        OsFileUtils.copy(input, output)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not copy file", e)
            throw e
        }
    }

    /**
     * Returns the display name advertised by a content URI.
     */
    @JvmStatic
    fun queryName(resolver: ContentResolver, uri: Uri): String? = try {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    } catch (_: Exception) {
        null
    }
}
