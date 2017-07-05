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
package org.lineageos.updater;

import android.os.SystemClock;
import android.util.Log;

import com.android.okhttp.Callback;
import com.android.okhttp.Interceptor;
import com.android.okhttp.MediaType;
import com.android.okhttp.OkHttpClient;
import com.android.okhttp.Response;
import com.android.okhttp.ResponseBody;
import com.android.okhttp.Request;
import com.android.okhttp.okio.BufferedSink;
import com.android.okhttp.okio.BufferedSource;
import com.android.okhttp.okio.Buffer;
import com.android.okhttp.okio.ForwardingSource;
import com.android.okhttp.okio.Okio;
import com.android.okhttp.okio.Source;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DownloadClient {

    private static final String TAG = "DownloadClient";

    private static final Object DOWNLOAD_TAG = new Object();

    private boolean mCancelled = false;

    public interface DownloadCallback {
        void onResponse(int statusCode, String url, Headers headers);
        void onSuccess(String body);
        void onFailure(boolean cancelled);
    }

    public interface ProgressListener {
        void update(long bytesRead, long contentLength, long speed, long eta, boolean done);
    }

    public class Headers {
        private com.android.okhttp.Headers mHeaders;

        private Headers(com.android.okhttp.Headers headers) {
            mHeaders = headers;
        }

        public String get(String name) {
            return mHeaders.get(name);
        }

        public Map<String, List<String>> getAll() {
            return mHeaders.toMultimap();
        }
    }

    private final OkHttpClient mClient = new OkHttpClient();

    private long mResumeOffset = 0;

    private DownloadClient() { }

    public static void download(String url, DownloadCallback callback) {
        DownloadClient downloadClient = new DownloadClient();
        downloadClient.downloadInternal(url, callback);
    }

    public static DownloadClient downloadFile(String url, File destination,
            DownloadCallback callback, ProgressListener progressListener) {
        DownloadClient downloadClient = new DownloadClient();
        downloadClient.downloadFileInternal(url, destination, callback, progressListener);
        return downloadClient;
    }

    public static DownloadClient downloadFile(String url, File destination,
            DownloadCallback callback) {
        return downloadFile(url, destination, callback, null);
    }

    public static DownloadClient downloadFileResume(String url, File destination,
            DownloadCallback callback, ProgressListener progressListener) {
        DownloadClient downloadClient = new DownloadClient();
        downloadClient.downloadFileResumeInternal(url, destination, callback, progressListener);
        return downloadClient;
    }

    public void cancel() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mCancelled = true;
                mClient.cancel(DOWNLOAD_TAG);
            }
        }).start();
    }

    private void downloadInternal(String url, final DownloadCallback callback) {
        final Request request = new Request.Builder()
                .url(url)
                .build();

        mClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                Log.d(TAG, "Download failed, cancelled=" + mCancelled, e);
                callback.onFailure(mCancelled);
            }

            @Override
            public void onResponse(Response response) {
                try {
                    callback.onResponse(response.code(), response.request().urlString(),
                            new Headers(response.headers()));
                    callback.onSuccess(response.body().string());
                } catch (IOException e) {
                    onFailure(request, e);
                }
            }
        });
    }

    private void downloadFileInternal(String url, final File destination,
            final DownloadCallback callback, final ProgressListener progressListener) {
        final Request request = new Request.Builder()
                .url(url)
                .tag(DOWNLOAD_TAG)
                .build();
        downloadFileInternalCommon(request, destination, callback, progressListener);
    }

    private void downloadFileResumeInternal(String url, final File destination,
            final DownloadCallback callback, final ProgressListener progressListener) {
        final Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .tag(DOWNLOAD_TAG);
        long offset = destination.length();
        requestBuilder.addHeader("Range", "bytes=" + offset + "-");
        final Request request = requestBuilder.build();
        downloadFileInternalCommon(request, destination, callback, progressListener);
    }

    private boolean isSuccessful(int statusCode) {
        return (statusCode / 100) == 2;
    }

    private void downloadFileInternalCommon(final Request request, final File destination,
            final DownloadCallback callback, final ProgressListener progressListener) {

        mClient.networkInterceptors().add(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Response originalResponse = chain.proceed(chain.request());
                ProgressResponseBody progressResponseBody =
                        new ProgressResponseBody(originalResponse.body(), progressListener);
                return originalResponse.newBuilder()
                        .body(progressResponseBody)
                        .build();
            }
        });

        mClient.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Request request, IOException e) {
                Log.d(TAG, "Download failed", e);
                callback.onFailure(mCancelled);
            }

            @Override
            public void onResponse(Response response) {
                Log.d(TAG, "Downloading");

                final boolean resume = response.code() == 206;
                if (resume) {
                    mResumeOffset = destination.length();
                    Log.d(TAG, "The server fulfilled the partial content request");
                } else if (!isSuccessful(response.code())) {
                    Log.e(TAG, "The server replied with code " + response.code());
                    callback.onFailure(mCancelled);
                    return;
                }

                callback.onResponse(response.code(), response.request().urlString(),
                        new Headers(response.headers()));
                try (BufferedSink sink = Okio.buffer(resume ?
                        Okio.appendingSink(destination) : Okio.sink(destination))) {
                    sink.writeAll(response.body().source());
                    Log.d(TAG, "Download complete");
                    sink.flush();
                    callback.onSuccess(null);
                } catch (IOException e) {
                    onFailure(request, e);
                }
            }
        });
    }

    private class ProgressResponseBody extends ResponseBody {

        private final ResponseBody mResponseBody;
        private final ProgressListener mProgressListener;
        private BufferedSource mBufferedSource;

        ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
            mResponseBody = responseBody;
            mProgressListener = progressListener;
        }

        @Override
        public MediaType contentType() {
            return mResponseBody.contentType();
        }

        @Override
        public long contentLength() throws IOException {
            return mResponseBody.contentLength();
        }

        @Override
        public BufferedSource source() throws IOException {
            if (mBufferedSource == null) {
                mBufferedSource = Okio.buffer(source(mResponseBody.source()));
            }
            return mBufferedSource;
        }

        private Source source(Source source) {
            return new ForwardingSource(source) {
                private long mTotalBytes = 0;
                private long mTotalBytesRead = mResumeOffset;

                private long mCurSampleBytes = 0;
                private long mLastMillis = 0;
                private long mSpeed = -1;
                private long mEta = -1;

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
                public long read(Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);

                    mTotalBytes = mResponseBody.contentLength() + mResumeOffset;
                    // read() returns the number of bytes read, or -1 if this source is exhausted.
                    mTotalBytesRead += bytesRead != -1 ? bytesRead : 0;

                    calculateSpeed();
                    calculateEta();

                    if (mProgressListener != null) {
                        mProgressListener.update(mTotalBytesRead, mTotalBytes,
                                mSpeed, mEta, bytesRead == -1);
                    }

                    return bytesRead;
                }
            };
        }
    }
}
