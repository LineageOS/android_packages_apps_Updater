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
import java.util.List;
import java.util.Map;

public interface DownloadClient {

    interface DownloadCallback {
        void onResponse(int statusCode, String url, Headers headers);

        void onSuccess(String body);

        void onFailure(boolean cancelled);
    }

    interface ProgressListener {
        void update(long bytesRead, long contentLength, long speed, long eta, boolean done);
    }

    interface Headers {
        String get(String name);

        Map<String, List<String>> getAll();
    }

    void start();

    void resume();

    void cancel();

    final class Builder {
        private String mUrl;
        private File mDestination;
        private DownloadClient.DownloadCallback mCallback;
        private DownloadClient.ProgressListener mProgressListener;

        public DownloadClient build() {
            if (mUrl == null) {
                throw new IllegalStateException("No download URL defined");
            } else if (mDestination == null) {
                throw new IllegalStateException("No download destination defined");
            } else if (mCallback == null) {
                throw new IllegalStateException("No download callback defined");
            }
            return new OkHttpDownloadClient(mUrl, mDestination, mProgressListener, mCallback);
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
