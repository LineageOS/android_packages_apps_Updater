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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UpdaterBroadcastReceiver extends BroadcastReceiver {

    public static final String ACTION_DOWNLOAD =
            "org.lineageos.updater.action.DOWNLOAD";
    public static final String EXTRA_DOWNLOAD_ID =
            "org.lineageos.updater.extra.DOWNLOAD_ID";
    public static final String EXTRA_DOWNLOAD_ACTION =
            "org.lineageos.updater.extra.DOWNLOAD_CHANGE";

    public static final int PAUSE = 0;
    public static final int RESUME = 1;

    private final static String TAG = "BroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (ACTION_DOWNLOAD.equals(action)) {
            if (intent.hasExtra(EXTRA_DOWNLOAD_ACTION) && intent.hasExtra(EXTRA_DOWNLOAD_ID)) {
                DownloadController downloadController = DownloadController.getInstance();
                if (downloadController == null) {
                    Log.e(TAG, "No download controller instance found");
                    return;
                }
                String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
                int requestedAction = intent.getIntExtra(EXTRA_DOWNLOAD_ACTION, -1);
                if (requestedAction == PAUSE) {
                    downloadController.pauseDownload(downloadId);
                } else if (requestedAction == RESUME) {
                    downloadController.resumeDownload(downloadId);
                } else {
                    Log.e(TAG, "Unknown action");
                }
            } else {
                Log.e(TAG, "Missing extra data");
            }
        }
    }
}
