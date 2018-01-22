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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpURLConnectionClient implements DownloadClient {

    private final static String TAG = "HttpURLConnectionClient";

    private HttpURLConnection mClient;

    private final File mDestination;
    private final DownloadClient.ProgressListener mProgressListener;
    private final DownloadClient.DownloadCallback mCallback;
    private final boolean mUseDuplicateLinks;

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
            DownloadClient.DownloadCallback callback,
            boolean useDuplicateLinks) throws IOException {
        mClient = (HttpURLConnection) new URL(url).openConnection();
        mDestination = destination;
        mProgressListener = progressListener;
        mCallback = callback;
        mUseDuplicateLinks = useDuplicateLinks;
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
            Log.wtf(TAG, "Already downloading");
            return;
        }

        mDownloadThread = new DownloadThread(resume);
        mDownloadThread.start();
    }

    private static boolean isSuccessCode(int statusCode) {
        return (statusCode / 100) == 2;
    }

    private static boolean isRedirectCode(int statusCode) {
        return (statusCode / 100) == 3;
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

        private void changeClientUrl(URL newUrl) throws IOException {
            String range = mClient.getRequestProperty("Range");
            mClient.disconnect();
            mClient = (HttpURLConnection) newUrl.openConnection();
            if (range != null) {
                mClient.setRequestProperty("Range", range);
            }
        }

        private void handleDuplicateLinks() throws IOException {
            String protocol = mClient.getURL().getProtocol();

            class DuplicateLink {
                private String mUrl;
                private int mPriority;
                private DuplicateLink(String url, int priority) {
                    mUrl = url;
                    mPriority = priority;
                }
            }

            PriorityQueue<DuplicateLink> duplicates = null;

            for (Map.Entry<String, List<String>> entry : mClient.getHeaderFields().entrySet()) {
                if ("Link".equalsIgnoreCase((entry.getKey()))) {
                    duplicates = new PriorityQueue<>(entry.getValue().size(),
                            new Comparator<DuplicateLink>() {
                                @Override
                                public int compare(DuplicateLink d1, DuplicateLink d2) {
                                    return Integer.compare(d1.mPriority, d2.mPriority);
                                }
                            });

                    // https://tools.ietf.org/html/rfc6249
                    // https://tools.ietf.org/html/rfc5988#section-5
                    String regex = "(?i)<(.+)>\\s*;\\s*rel=duplicate(?:.*pri=([0-9]+).*|.*)?";
                    Pattern pattern = Pattern.compile(regex);
                    for (String field : entry.getValue()) {
                        Matcher matcher = pattern.matcher(field);
                        if (matcher.matches()) {
                            String url = matcher.group(1);
                            String pri = matcher.group(2);
                            int priority = pri != null ? Integer.parseInt(pri) : 999999;
                            duplicates.add(new DuplicateLink(url, priority));
                            Log.d(TAG, "Adding duplicate link " + url);
                        } else {
                            Log.d(TAG, "Ignoring link " + field);
                        }
                    }
                }
            }

            String newUrl = mClient.getHeaderField("Location");
            for (;;) {
                try {
                    URL url = new URL(newUrl);
                    if (!url.getProtocol().equals(protocol)) {
                        // If we hadn't handled duplicate links, we wouldn't have
                        // used this url.
                        throw new IOException("Protocol changes are not allowed");
                    }
                    Log.d(TAG, "Downloading from " + newUrl);
                    changeClientUrl(url);
                    mClient.setConnectTimeout(5000);
                    mClient.connect();
                    if (!isSuccessCode(mClient.getResponseCode())) {
                        throw new IOException("Server replied with " + mClient.getResponseCode());
                    }
                    return;
                } catch (IOException e) {
                    if (duplicates != null && !duplicates.isEmpty()) {
                        DuplicateLink link = duplicates.poll();
                        duplicates.remove(link);
                        newUrl = link.mUrl;
                        Log.e(TAG, "Using duplicate link " + link.mUrl, e);
                    } else {
                        throw e;
                    }
                }
            }
        }

        @Override
        public void run() {
            try {
                mClient.setInstanceFollowRedirects(!mUseDuplicateLinks);
                mClient.connect();
                int responseCode = mClient.getResponseCode();

                if (mUseDuplicateLinks && isRedirectCode(responseCode)) {
                    handleDuplicateLinks();
                    responseCode = mClient.getResponseCode();
                }

                mCallback.onResponse(responseCode, mClient.getURL().toString(), new Headers());

                if (mResume && isPartialContentCode(responseCode)) {
                    mTotalBytesRead = mDestination.length();
                    Log.d(TAG, "The server fulfilled the partial content request");
                } else if (mResume || !isSuccessCode(responseCode)) {
                    Log.e(TAG, "The server replied with code " + responseCode);
                    mCallback.onFailure(isInterrupted());
                    return;
                }

                try (
                        InputStream inputStream = mClient.getInputStream();
                        OutputStream outputStream = new FileOutputStream(mDestination, mResume)
                ) {
                    mTotalBytes = mClient.getContentLength() + mTotalBytesRead;
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
            } catch (IOException e) {
                Log.e(TAG, "Error downloading file", e);
                mCallback.onFailure(isInterrupted());
            } finally {
                mClient.disconnect();
            }
        }
    }
}
