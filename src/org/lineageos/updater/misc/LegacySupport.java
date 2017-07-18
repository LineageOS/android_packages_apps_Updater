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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.lineageos.updater.model.UpdateDownload;
import org.lineageos.updater.model.UpdateStatus;
import org.lineageos.updater.UpdatesDbHelper;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LegacySupport {

    private static final String TAG = "LegacySupport";

    private static final String IMPORT_DONE = "import_done";

    private static class IllegalFilenameException extends Exception {
        IllegalFilenameException(String message) {
            super(message);
        }
    }

    /**
     * This method imports the updates downloaded with CMUpdater and it adds them to the
     * updates database. It accepts in input a list of updates which it updates with the
     * data of matching imported updates (i.e. same filename). If for a given imported
     * update this method can't find any matching update, it adds a new entry to the
     * given list.
     *
     * @param context
     * @param updatesJson List of updates to be downloaded
     * @return A list with the IDs of the imported updates with no matching updates
     */
    public static List<String> importDownloads(Context context,
            List<UpdateDownload> updatesJson) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean(IMPORT_DONE, false)) {
            return null;
        }

        Log.d(TAG, "Importing downloads");

        List<String> notReplacing = new ArrayList<>();
        File updatesDir = new File(context.getDataDir(), "app_updates/");
        if (updatesDir.isDirectory()) {
            UpdatesDbHelper dbHelper = new UpdatesDbHelper(context);
            File[] files = updatesDir.listFiles();
            if (files != null) {

                Map<String, Integer> updatesMap = new HashMap<>();
                for (UpdateDownload update : updatesJson) {
                    updatesMap.put(update.getName(), updatesJson.indexOf(update));
                }

                for (File file : updatesDir.listFiles()) {
                    if (!file.getName().endsWith(".zip")) {
                        // Note: there could be complete, but unverified downloads here
                        Log.d(TAG, "Deleting " + file.getAbsolutePath());
                        file.delete();
                    } else {
                        Log.d(TAG, "Importing " + file.getAbsolutePath());
                        Integer index = updatesMap.get(file.getName());
                        if (index != null) {
                            UpdateDownload update = updatesJson.get(index);
                            update.setFile(file);
                            update.setFileSize(file.length());
                            update.setStatus(UpdateStatus.DOWNLOADED);
                            update.setPersistentStatus(UpdateStatus.Persistent.INCOMPLETE);
                            dbHelper.addUpdate(update);
                        } else {
                            try {
                                UpdateDownload update = createUpdateFromFile(file);
                                notReplacing.add(update.getDownloadId());
                                updatesJson.add(update);
                                dbHelper.addUpdate(update);
                            } catch (IllegalFilenameException e) {
                                Log.e(TAG, "Deleting " + file.getAbsolutePath(), e);
                                file.delete();
                            }
                        }
                    }
                }
            }
        }
        preferences.edit().putBoolean(IMPORT_DONE, true).apply();
        return notReplacing;
    }

    private static UpdateDownload createUpdateFromFile(File file) throws IllegalFilenameException {
        UpdateDownload update = new UpdateDownload();
        update.setDownloadId(UUID.randomUUID().toString());
        update.setFile(file);
        update.setFileSize(file.length());
        String name = file.getName();
        update.setName(name);
        update.setTimestamp(getTimestampFromFileName(name));
        update.setVersion(getVersionFromFileName(name));
        update.setType(getTypeFromFileName(name));
        update.setPersistentStatus(UpdateStatus.Persistent.INCOMPLETE);

        return update;
    }

    private static long getTimestampFromFileName(String fileName) throws IllegalFilenameException {
        String[] subStrings = fileName.split("-");
        if (subStrings.length < 3 || subStrings[2].length() < 8) {
            throw new  IllegalArgumentException("The given filename is not valid");
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        try {
            return (dateFormat.parse(subStrings[2]).getTime() / 1000);
        } catch (ParseException e) {
            throw new IllegalFilenameException("The given filename is not valid");
        }
    }

    private static String getVersionFromFileName(String fileName) throws IllegalFilenameException {
        String[] subStrings = fileName.split("-");
        if (subStrings.length < 2 || subStrings[1].length() < 4) {
            throw new IllegalFilenameException("The given filename is not valid");
        }
        return subStrings[1];
    }

    private static String getTypeFromFileName(String fileName) throws IllegalFilenameException {
        String[] subStrings = fileName.split("-");
        if (subStrings.length < 4 || subStrings[3].length() < 7) {
            throw new IllegalFilenameException("The given filename is not valid");
        }
        return subStrings[3].toLowerCase();
    }
}
