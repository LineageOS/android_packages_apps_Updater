/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.misc;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class FileUtils {

    private static final String TAG = "FileUtils";

    public interface ProgressCallBack {
        void update(int progress);
    }

    private static class CallbackByteChannel implements ReadableByteChannel {
        private final ProgressCallBack mCallback;
        private final long mSize;
        private final ReadableByteChannel mReadableByteChannel;
        private long mSizeRead;
        private int mProgress;

        private CallbackByteChannel(ReadableByteChannel readableByteChannel, long expectedSize,
                ProgressCallBack callback) {
            this.mCallback = callback;
            this.mSize = expectedSize;
            this.mReadableByteChannel = readableByteChannel;
        }

        @Override
        public void close() throws IOException {
            mReadableByteChannel.close();
        }

        @Override
        public boolean isOpen() {
            return mReadableByteChannel.isOpen();
        }

        @Override
        public int read(ByteBuffer bb) throws IOException {
            int read;
            if ((read = mReadableByteChannel.read(bb)) > 0) {
                mSizeRead += read;
                int progress = mSize > 0 ? Math.round(mSizeRead * 100.f / mSize) : -1;
                if (mProgress != progress) {
                    mCallback.update(progress);
                    mProgress = progress;
                }
            }
            return read;
        }
    }

    public static void copyFile(File sourceFile, File destFile, ProgressCallBack progressCallBack)
            throws IOException {
        try (FileChannel sourceChannel = new FileInputStream(sourceFile).getChannel();
             FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {
            if (progressCallBack != null) {
                ReadableByteChannel readableByteChannel = new CallbackByteChannel(sourceChannel,
                        sourceFile.length(), progressCallBack);
                destChannel.transferFrom(readableByteChannel, 0, sourceChannel.size());
            } else {
                destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not copy file", e);
            if (destFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                destFile.delete();
            }
            throw e;
        }
    }

    public static void copyFile(ContentResolver cr, File sourceFile, Uri destUri,
                                ProgressCallBack progressCallBack) throws IOException {
        try (FileChannel sourceChannel = new FileInputStream(sourceFile).getChannel();
             ParcelFileDescriptor pfd = cr.openFileDescriptor(destUri, "w");
             FileChannel destChannel = new FileOutputStream(pfd.getFileDescriptor()).getChannel()) {
            if (progressCallBack != null) {
                ReadableByteChannel readableByteChannel = new CallbackByteChannel(sourceChannel,
                        sourceFile.length(), progressCallBack);
                destChannel.transferFrom(readableByteChannel, 0, sourceChannel.size());
            } else {
                destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not copy file", e);
            throw e;
        }
    }

    public static String queryName(@NonNull ContentResolver resolver, Uri uri) {
        try (Cursor returnCursor = resolver.query(uri, null, null, null, null)) {
            returnCursor.moveToFirst();
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            return returnCursor.getString(nameIndex);
        } catch (Exception e) {
            // ignore
            return null;
        }
    }
}
