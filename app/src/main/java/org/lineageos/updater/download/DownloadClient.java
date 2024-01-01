/*
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.download;

import java.io.File;
import java.io.IOException;

public interface DownloadClient {

    interface DownloadCallback {
        void onResponse(Headers headers);

        void onSuccess();

        void onFailure(boolean cancelled);
    }

    interface ProgressListener {
        void update(long bytesRead, long contentLength, long speed, long eta);
    }

    interface Headers {
        String get(String name);
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
        private boolean mUseDuplicateLinks;

        public DownloadClient build() throws IOException {
            if (mUrl == null) {
                throw new IllegalStateException("No download URL defined");
            } else if (mDestination == null) {
                throw new IllegalStateException("No download destination defined");
            } else if (mCallback == null) {
                throw new IllegalStateException("No download callback defined");
            }
            return new HttpURLConnectionClient(mUrl, mDestination, mProgressListener, mCallback,
                    mUseDuplicateLinks);
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

        public Builder setUseDuplicateLinks(boolean useDuplicateLinks) {
            mUseDuplicateLinks = useDuplicateLinks;
            return this;
        }
    }
}
