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

import org.lineageos.updater.misc.Utils;

import java.io.IOException;

public class UpdaterBroadcastReceiver extends BroadcastReceiver {

    public static final String EXTRA_DOWNLOAD_ID =
            "org.lineageos.updater.extra.DOWNLOAD_ID";
    public static final String ACTION_INSTALL_UPDATE =
            "org.lineageos.updater.action.INSTALL_UPDATE";

    private final static String TAG = "BroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (ACTION_INSTALL_UPDATE.equals(action)) {
            if (!intent.hasExtra(EXTRA_DOWNLOAD_ID)) {
                Log.e(TAG, "Missing download ID");
                return;
            }
            DownloadController downloadController = DownloadController.getInstance(context);
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            UpdateDownload update = downloadController.getUpdate(downloadId);
            try {
                Utils.triggerUpdate(context, update);
            } catch (IOException e) {
                Log.e(TAG, "Could not trigger update");
                // TODO: show error
            }
        }
    }
}
