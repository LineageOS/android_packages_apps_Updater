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

import com.android.okhttp.Callback;
import com.android.okhttp.Interceptor;
import com.android.okhttp.MediaType;
import com.android.okhttp.OkHttpClient;
import com.android.okhttp.Request;
import com.android.okhttp.Response;
import com.android.okhttp.ResponseBody;
import com.android.okhttp.okio.Buffer;
import com.android.okhttp.okio.BufferedSink;
import com.android.okhttp.okio.BufferedSource;
import com.android.okhttp.okio.ForwardingSource;
import com.android.okhttp.okio.Okio;
import com.android.okhttp.okio.Source;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

class OkHttpDownloadClient implements DownloadClient {

    private static final String TAG = "DownloadClient";

    private final Object DOWNLOAD_TAG = new Object();

    private final OkHttpClient mClient = new OkHttpClient();

    private final String mUrl;
    private final File mDestination;
    private final DownloadClient.ProgressListener mProgressListener;
    private final DownloadClient.DownloadCallback mCallback;
    private long mResumeOffset = 0;

    private boolean mDownloading = false;
    private boolean mCancelled = false;

    public class Headers implements DownloadClient.Headers {
        private com.android.okhttp.Headers mHeaders;

        private Headers(com.android.okhttp.Headers headers) {
            mHeaders = headers;
        }

        @Override
        public String get(String name) {
            return mHeaders.get(name);
        }

        @Override
        public Map<String, List<String>> getAll() {
            return mHeaders.toMultimap();
        }
    }

    OkHttpDownloadClient(String url, File destination,
            DownloadClient.ProgressListener progressListener,
            DownloadClient.DownloadCallback callback) {
        mUrl = url;
        mDestination = destination;
        mProgressListener = progressListener;
        mCallback = callback;
    }

    @Override
    public void start() {
        if (mDownloading) {
            Log.e(TAG, "Already downloading");
            return;
        }
        mCancelled = false;
        mDownloading = true;
        downloadFileInternal(mCallback, mProgressListener);
    }

    @Override
    public void resume() {
        if (mDownloading) {
            Log.e(TAG, "Already downloading");
            return;
        }
        mCancelled = false;
        mDownloading = true;
        downloadFileResumeInternal(mCallback, mProgressListener);
    }

    public void cancel() {
        if (!mDownloading) {
            Log.e(TAG, "Not downloading");
            return;
        }
        mDownloading = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                mCancelled = true;
                mClient.cancel(DOWNLOAD_TAG);
            }
        }).start();
    }

    private void downloadFileInternal(final DownloadClient.DownloadCallback callback,
            final DownloadClient.ProgressListener progressListener) {
        final Request request = new Request.Builder()
                .url(mUrl)
                .tag(DOWNLOAD_TAG)
                .build();
        downloadFileInternalCommon(request, callback, progressListener, false);
    }

    private void downloadFileResumeInternal(final DownloadClient.DownloadCallback callback,
            final DownloadClient.ProgressListener progressListener) {
        final Request.Builder requestBuilder = new Request.Builder()
                .url(mUrl)
                .tag(DOWNLOAD_TAG);
        long offset = mDestination.length();
        requestBuilder.addHeader("Range", "bytes=" + offset + "-");
        final Request request = requestBuilder.build();
        downloadFileInternalCommon(request, callback, progressListener, true);
    }

    private static boolean isSuccessCode(int statusCode) {
        return (statusCode / 100) == 2;
    }

    private static boolean isPartialContentCode(int statusCode) {
        return statusCode == 206;
    }

    private void downloadFileInternalCommon(final Request request,
            final DownloadClient.DownloadCallback callback,
            final DownloadClient.ProgressListener progressListener,
            final boolean resume) {

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

                final ResponseBody body = response.body();
                if (resume && isPartialContentCode(response.code())) {
                    mResumeOffset = mDestination.length();
                    Log.d(TAG, "The server fulfilled the partial content request");
                } else if (resume || !isSuccessCode(response.code())) {
                    Log.e(TAG, "The server replied with code " + response.code());
                    callback.onFailure(mCancelled);
                    try {
                        body.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Could not close response body", e);
                    }
                    return;
                }

                callback.onResponse(response.code(), response.request().urlString(),
                        new Headers(response.headers()));
                try (BufferedSink sink = Okio.buffer(resume ?
                        Okio.appendingSink(mDestination) : Okio.sink(mDestination))) {
                    sink.writeAll(body.source());
                    Log.d(TAG, "Download complete");
                    sink.flush();
                    callback.onSuccess(null);
                } catch (IOException e) {
                    onFailure(request, e);
                } finally {
                    try {
                        body.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Could not close reponse body", e);
                    }
                }
            }
        });
    }

    private class ProgressResponseBody extends ResponseBody {

        private final ResponseBody mResponseBody;
        private final DownloadClient.ProgressListener mProgressListener;
        private BufferedSource mBufferedSource;

        ProgressResponseBody(ResponseBody responseBody,
                DownloadClient.ProgressListener progressListener) {
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
