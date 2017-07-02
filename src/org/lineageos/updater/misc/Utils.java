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

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lineageos.updater.R;
import org.lineageos.updater.Update;
import org.lineageos.updater.UpdateDownload;
import org.lineageos.updater.UpdateStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static final String TAG = "Utils";

    public static File getDownloadPath(Context context) {
        boolean useCache = context.getResources().getBoolean(R.bool.download_in_cache);
        int id = useCache ? R.string.download_path_cache : R.string.download_path_data;
        return new File(context.getString(id));
    }

    public static File getCachedUpdateList(Context context) {
        return new File(context.getCacheDir(), "updates.json");
    }

    // This should really return an Update object, but currently this only
    // used to initialize UpdateDownload objects
    private static UpdateDownload parseJsonUpdate(JSONObject object) throws JSONException {
        UpdateDownload update = new UpdateDownload();
        update.setTimestamp(object.getLong("datetime"));
        update.setName(object.getString("filename"));
        update.setDownloadId(object.getString("id"));
        update.setType(object.getString("romtype"));
        update.setDownloadUrl(object.getString("url"));
        update.setVersion(object.getString("version"));
        return update;
    }

    public static boolean isCompatible(Update update) {
        if (update.getTimestamp() < SystemProperties.getLong("ro.build.date.utc", 0)) {
            Log.d(TAG, update.getName() + " is older than current build");
            return false;
        }
        if (!update.getType().equalsIgnoreCase(SystemProperties.get("ro.cm.releasetype"))) {
            Log.d(TAG, update.getName() + " has type " + update.getType());
            return false;
        }
        if (!update.getVersion().equalsIgnoreCase(SystemProperties.get("ro.cm.build.version"))) {
            Log.d(TAG, update.getName() + " has version " + update.getVersion());
            return false;
        }
        return true;
    }

    public static List<UpdateDownload> parseJson(File file, boolean compatibleOnly)
            throws IOException, JSONException {
        List<UpdateDownload> updates = new ArrayList<>();

        String json = "";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            for (String line; (line = br.readLine()) != null;) {
                json += line;
            }
        }

        JSONObject obj = new JSONObject(json);
        JSONArray updatesList = obj.getJSONArray("response");
        for (int i = 0; i < updatesList.length(); i++) {
            if (updatesList.isNull(i)) {
                continue;
            }
            try {
                UpdateDownload update = parseJsonUpdate(updatesList.getJSONObject(i));
                if (compatibleOnly && isCompatible(update)) {
                    updates.add(update);
                } else {
                    Log.d(TAG, "Ignoring incompatible update " + update.getName());
                }
            } catch (JSONException e) {
                Log.e(TAG, "Could not parse update object, index=" + i);
            }
        }

        return updates;
    }

    public static String getServerURL(Context context) {
        String serverUrl = SystemProperties.get("cm.updater.uri");
        if (serverUrl.trim().isEmpty()) {
            serverUrl = context.getString(R.string.conf_update_server_url_def);
        }
        String incrementalVersion = SystemProperties.get("ro.build.version.incremental");
        String device = SystemProperties.get("ro.cm.device").toLowerCase();
        String type = SystemProperties.get("ro.cm.releasetype").toLowerCase();
        return serverUrl + "/v1/" + device + "/" + type + "/" + incrementalVersion;
    }

    public static void triggerUpdate(Context context, UpdateDownload update) throws IOException {
        if (update.getStatus() == UpdateStatus.VERIFIED) {
            android.os.RecoverySystem.installPackage(context, update.getFile());
        } else {
            throw new IllegalStateException("Update must be verified");
        }
    }
}
