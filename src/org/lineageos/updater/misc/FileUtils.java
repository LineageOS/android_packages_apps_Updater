/*
 * Copyright (C) 2017 The LineageOS Project
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
package org.lineageos.updater.misc;

import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;

import org.lineageos.updater.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class FileUtils {

    private static final String TAG = "FileUtils";

    interface ProgressCallBack {
        void update(int progress);
    }

    private static class CallbackByteChannel implements ReadableByteChannel {
        private ProgressCallBack mCallback;
        private long mSize;
        private ReadableByteChannel mReadableByteChannel;
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
                destFile.delete();
            }
            throw e;
        }
    }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        copyFile(sourceFile, destFile, null);
    }

    public static void copyFileWithDialog(Context context, File sourceFile, File destFile)
            throws IOException {

        final int NOTIFICATION_ID = 11;

        new AsyncTask<Void, String, Boolean>() {

            private ProgressDialog mProgressDialog;
            private boolean mCancelled;
            private ProgressCallBack mProgressCallBack;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mProgressDialog = new ProgressDialog(context);
                mProgressDialog.setTitle(context.getString(R.string.dialog_export_title));
                mProgressDialog.setMessage(
                        context.getString(R.string.dialog_export_message, destFile.getName()));
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setCancelable(true);
                mProgressDialog.setProgressNumberFormat(null);
                mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancel(true);
                    }
                });
                mProgressDialog.setCanceledOnTouchOutside(false);
                mProgressCallBack = new ProgressCallBack() {
                    @Override
                    public void update(int progress) {
                        mProgressDialog.setProgress(progress);
                    }
                };
                mProgressDialog.show();
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    copyFile(sourceFile, destFile, mProgressCallBack);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            protected void onCancelled() {
                mCancelled = true;
                destFile.delete();
            }

            @Override
            protected void onPostExecute(Boolean success) {
                mProgressDialog.dismiss();
                if (mCancelled) {
                    destFile.delete();
                } else {
                    NotificationManager nm = (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);

                    String id = "result_channel";
                    CharSequence name = context.getString(R.string.results_channel_title);
                    String description = context.getString(R.string.results_channel_desc);
                    int importance = NotificationManager.IMPORTANCE_LOW;
                    NotificationChannel notificationChannel = new NotificationChannel(id, name,
                            importance);
                    notificationChannel.setDescription(description);
                    nm.createNotificationChannel(notificationChannel);

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                            id);
                    builder.setSmallIcon(R.drawable.ic_system_update);
                    builder.setContentTitle(
                            success ? context.getString(R.string.notification_export_success)
                                    : context.getString(R.string.notification_export_fail));
                    builder.setContentText(destFile.getName());
                    final String notificationTag = destFile.getAbsolutePath();
                    nm.notify(notificationTag, NOTIFICATION_ID, builder.build());
                }
            }
        }.execute();
    }

    public static void prepareForUncrypt(Context context, File updateFile, File uncryptFile,
            Runnable callback) {

        final int NOTIFICATION_ID = 12;

        new AsyncTask<Void, String, Boolean>() {

            private ProgressDialog mProgressDialog;
            private boolean mCancelled;
            private ProgressCallBack mProgressCallBack;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                Log.d(TAG, "Preparing update");
                mProgressDialog = new ProgressDialog(context);
                mProgressDialog.setTitle(R.string.app_name);
                mProgressDialog.setMessage(context.getString(R.string.dialog_prepare_zip_message));
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                mProgressDialog.setCancelable(true);
                mProgressDialog.setProgressNumberFormat(null);
                mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancel(true);
                    }
                });
                mProgressDialog.setCanceledOnTouchOutside(false);
                mProgressCallBack = new ProgressCallBack() {
                    @Override
                    public void update(int progress) {
                        mProgressDialog.setProgress(progress);
                    }
                };
                mProgressDialog.show();
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    copyFile(updateFile, uncryptFile, mProgressCallBack);
                } catch (IOException e) {
                    Log.e(TAG, "Error while copying the file", e);
                }
                return !mCancelled;
            }

            @Override
            protected void onCancelled() {
                mCancelled = true;
                uncryptFile.delete();
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (!success || mCancelled) {
                    Log.e(TAG, "Could not prepare the update, cancelled=" + mCancelled);
                    uncryptFile.delete();
                    NotificationManager nm = (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
                    builder.setSmallIcon(R.drawable.ic_system_update);
                    builder.setContentTitle(
                            context.getString(R.string.notification_prepare_zip_error_title));
                    final String notificationTag = updateFile.getAbsolutePath();
                    nm.notify(notificationTag, NOTIFICATION_ID, builder.build());
                } else {
                    callback.run();
                }
                mProgressDialog.dismiss();
            }
        }.execute();
    }
}
