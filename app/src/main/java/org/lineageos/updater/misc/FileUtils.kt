/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.misc

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import kotlin.math.roundToInt

object FileUtils {
    private const val TAG = "FileUtils"

    @Throws(IOException::class)
    fun copyFile(sourceFile: File, destFile: File, progressCallBack: ProgressCallBack?) {
        try {
            FileInputStream(sourceFile).channel.use { sourceChannel ->
                FileOutputStream(destFile).channel.use { destChannel ->
                    progressCallBack?.also {
                        val readableByteChannel =
                            CallbackByteChannel(sourceChannel, sourceFile.length(), it)
                        destChannel.transferFrom(readableByteChannel, 0, sourceChannel.size())
                    } ?: run {
                        destChannel.transferFrom(sourceChannel, 0, sourceChannel.size())
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not copy file", e)

            if (destFile.exists()) {
                destFile.delete()
            }

            throw e
        }
    }

    @Throws(IOException::class)
    fun copyFile(
        cr: ContentResolver, sourceFile: File, destUri: Uri,
        progressCallBack: ProgressCallBack?
    ) {
        try {
            FileInputStream(sourceFile).channel.use { sourceChannel ->
                cr.openFileDescriptor(
                    destUri, "w"
                ).use { pfd ->
                    FileOutputStream(pfd!!.fileDescriptor).channel.use { destChannel ->
                        progressCallBack?.also {
                            val readableByteChannel =
                                CallbackByteChannel(sourceChannel, sourceFile.length(), it)
                            destChannel.transferFrom(readableByteChannel, 0, sourceChannel.size())
                        } ?: run {
                            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size())
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not copy file", e)
            throw e
        }
    }

    fun queryName(resolver: ContentResolver, uri: Uri): String? {
        try {
            resolver.query(
                uri, null, null, null, null
            ).use { returnCursor ->
                returnCursor?.let {
                    it.moveToFirst()
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    return it.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            // ignore
            return null
        }

        return null
    }

    interface ProgressCallBack {
        fun update(progress: Int)
    }

    private class CallbackByteChannel(
        private val readableByteChannel: ReadableByteChannel,
        private val size: Long,
        private val callback: ProgressCallBack
    ) : ReadableByteChannel {
        private var sizeRead: Long = 0
        private var progress = 0

        @Throws(IOException::class)
        override fun close() {
            readableByteChannel.close()
        }

        override fun isOpen(): Boolean {
            return readableByteChannel.isOpen
        }

        @Throws(IOException::class)
        override fun read(bb: ByteBuffer): Int {
            var read: Int

            if (readableByteChannel.read(bb).also { read = it } > 0) {
                sizeRead += read.toLong()
                val progress = if (size > 0) (sizeRead * 100f / size).roundToInt() else -1
                if (this.progress != progress) {
                    callback.update(progress)
                    this.progress = progress
                }
            }

            return read
        }
    }
}
