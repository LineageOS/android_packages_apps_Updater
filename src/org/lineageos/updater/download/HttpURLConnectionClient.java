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
package org.lineageos.updater.download;

import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class HttpURLConnectionClient implements DownloadClient {

    private final static String TAG = "HttpURLConnectionClient";

    private final HttpURLConnection mClient;

    private final File mDestination;
    private final DownloadClient.ProgressListener mProgressListener;
    private final DownloadClient.DownloadCallback mCallback;

    private DownloadThread mDownloadThread;

    public class Headers implements DownloadClient.Headers {
        @Override
        public String get(String name) {
            return mClient.getHeaderField(name);
        }

        @Override
        public Map<String, List<String>> getAll() {
            return mClient.getHeaderFields();
        }
    }

    HttpURLConnectionClient(String url, File destination,
            DownloadClient.ProgressListener progressListener,
            DownloadClient.DownloadCallback callback) {
        try {
            mClient = (HttpURLConnection) new URL(url).openConnection();
        } catch (IOException e) {
            Log.e(TAG, "Invalid URL", e);
            throw new RuntimeException();
        }
        mDestination = destination;
        mProgressListener = progressListener;
        mCallback = callback;
    }

    @Override
    public void start() {
        if (mDownloadThread != null) {
            Log.e(TAG, "Already downloading");
            return;
        }
        downloadFileInternalCommon(false);
    }

    @Override
    public void resume() {
        if (mDownloadThread != null) {
            Log.e(TAG, "Already downloading");
            return;
        }
        downloadFileResumeInternal();
    }

    @Override
    public void cancel() {
        if (mDownloadThread == null) {
            Log.e(TAG, "Not downloading");
            return;
        }
        mDownloadThread.interrupt();
        mDownloadThread = null;
    }

    private void downloadFileResumeInternal() {
        if (!mDestination.exists()) {
            mCallback.onFailure(false);
            return;
        }
        long offset = mDestination.length();
        mClient.setRequestProperty("Range", "bytes=" + offset + "-");
        downloadFileInternalCommon(true);
    }

    private void downloadFileInternalCommon(boolean resume) {
        if (mDownloadThread != null) {
            // This should never happen
            throw new RuntimeException("Already downloading");
        }

        mDownloadThread = new DownloadThread(resume);
        mDownloadThread.start();
    }

    private static boolean isSuccessCode(int statusCode) {
        return (statusCode / 100) == 2;
    }

    private static boolean isPartialContentCode(int statusCode) {
        return statusCode == 206;
    }

    private class DownloadThread extends Thread {

        private long mTotalBytes = 0;
        private long mTotalBytesRead = 0;

        private long mCurSampleBytes = 0;
        private long mLastMillis = 0;
        private long mSpeed = -1;
        private long mEta = -1;

        private final boolean mResume;

        private DownloadThread(boolean resume) {
            mResume = resume;
        }

        private void calculateSpeed() {
            final long millis = SystemClock.elapsedRealtime();
            final long delta = millis - mLastMillis;
            if (delta > 500) {
                final long curSpeed = ((mTotalBytesRead - mCurSampleBytes) * 1000) / delta;
                if (mSpeed == -1) {
                    mSpeed = curSpeed;
                } else {
                    mSpeed = ((mSpeed * 3) + curSpeed) / 4;
                }

                mLastMillis = millis;
                mCurSampleBytes = mTotalBytesRead;
            }
        }

        private void calculateEta() {
            if (mSpeed > 0) {
                mEta = (mTotalBytes - mTotalBytesRead) / mSpeed;
            }
        }

        @Override
        public void run() {
            try {
                mClient.connect();
                int responseCode = mClient.getResponseCode();
                mCallback.onResponse(responseCode, mClient.getURL().toString(),
                        new HttpURLConnectionClient.Headers());

                if (mResume && isPartialContentCode(responseCode)) {
                    mTotalBytesRead = mDestination.length();
                    Log.d(TAG, "The server fulfilled the partial content request");
                } else if (mResume || !isSuccessCode(responseCode)) {
                    Log.e(TAG, "The server replied with code " + responseCode);
                    mCallback.onFailure(isInterrupted());
                }

                try (
                        InputStream inputStream = mClient.getInputStream();
                        OutputStream outputStream = new FileOutputStream(mDestination, mResume)
                ) {
                    mTotalBytes = mClient.getContentLength();
                    byte[] b = new byte[8192];
                    int count;
                    while (!isInterrupted() && (count = inputStream.read(b)) > 0) {
                        outputStream.write(b, 0, count);
                        mTotalBytesRead += count;
                        calculateSpeed();
                        calculateEta();
                        if (mProgressListener != null) {
                            mProgressListener.update(mTotalBytesRead, mTotalBytes, mSpeed, mEta,
                                    false);
                        }
                    }
                    if (mProgressListener != null) {
                        mProgressListener.update(mTotalBytesRead, mTotalBytes, mSpeed, mEta, true);
                    }

                    outputStream.flush();

                    if (isInterrupted()) {
                        mCallback.onFailure(true);
                    } else {
                        mCallback.onSuccess(mDestination);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading file", e);
                mCallback.onFailure(isInterrupted());
            } finally {
                mClient.disconnect();
            }
        }
    }
}
