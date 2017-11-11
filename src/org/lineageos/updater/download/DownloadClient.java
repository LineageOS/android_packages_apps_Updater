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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface DownloadClient {

    interface DownloadCallback {
        void onResponse(int statusCode, String url, Headers headers);

        void onSuccess(File destination);

        void onFailure(boolean cancelled);
    }

    interface ProgressListener {
        void update(long bytesRead, long contentLength, long speed, long eta, boolean done);
    }

    interface Headers {
        String get(String name);

        Map<String, List<String>> getAll();
    }

    /**
     * Start the download. This method has no effect if the download already started.
     */
    void start();

    /**
     * Resume the download. The download will fail if the server can't fulfil the
     * partial content request and DownloadCallback.onFailure() will be called.
     * This method has no effect if the download already started or the destination
     * file doesn't exist.
     */
    void resume();

    /**
     * Cancel the download. This method has no effect if the download isn't ongoing.
     */
    void cancel();

    final class Builder {
        private String mUrl;
        private File mDestination;
        private DownloadClient.DownloadCallback mCallback;
        private DownloadClient.ProgressListener mProgressListener;

        public DownloadClient build() throws IOException {
            if (mUrl == null) {
                throw new IllegalStateException("No download URL defined");
            } else if (mDestination == null) {
                throw new IllegalStateException("No download destination defined");
            } else if (mCallback == null) {
                throw new IllegalStateException("No download callback defined");
            }
            return new HttpURLConnectionClient(mUrl, mDestination, mProgressListener, mCallback);
        }

        public Builder setUrl(String url) {
            mUrl = url;
            return this;
        }

        public Builder setDestination(File destination) {
            mDestination = destination;
            return this;
        }

        public Builder setDownloadCallback(DownloadClient.DownloadCallback downloadCallback) {
            mCallback = downloadCallback;
            return this;
        }

        public Builder setProgressListener(DownloadClient.ProgressListener progressListener) {
            mProgressListener = progressListener;
            return this;
        }
    }
}
