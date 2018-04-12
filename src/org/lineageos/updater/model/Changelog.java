/*
 * Copyright (C) 2018 The LineageOS Project
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
package org.lineageos.updater.model;

import android.content.Context;
import android.os.Handler;
import android.os.SystemProperties;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lineageos.updater.R;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Changelog {

    public interface ChangelogCallback {
        void onNewChanges();
        void onEnd();
    }

    public static class BuildLabel extends ChangelogEntry {

        private long mTimestamp;

        private String mLabel;

        private BuildLabel(long timestamp, String label) {
            mTimestamp = timestamp;
            mLabel = label;
        }

        @Override
        public long getTimestamp() {
            return mTimestamp;
        }

        public String getLabel() {
            return mLabel;
        }
    }

    private static final String TAG = "Changelog";

    private static final long FETCH_TIMESTAMP_DIFF = 7 * 24 * 60 * 60;

    private long mTimestampLimit;
    private String mDevice;
    private Context mContext;

    private Thread mGetThread;
    private long mLastTimestamp;

    private List<ChangelogEntry> mPendingChanges = new ArrayList<>();
    private List<ChangelogEntry> mChanges = new ArrayList<>();
    private List<ChangelogEntry> mLabels = new ArrayList<>();

    private Set<String> mSubjectsToHide = null;

    public Changelog(Context context) {
        mLastTimestamp = -1; // Do not specify a timestamp to allow server side caching
        mTimestampLimit = Math.min(SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0),
                System.currentTimeMillis() / 1000 - FETCH_TIMESTAMP_DIFF);
        mDevice = Utils.getDeviceName(context);
        mContext = context;
    }

    private static void addToSortedChangeList(List<ChangelogEntry> list, ChangelogEntry change) {
        int index = Collections.binarySearch(list, change);
        list.add(index < 0 ? ~index : index, change);
    }

    public void setUpdates(List<UpdateInfo> updates) {
        long buildTimestamp = SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0);
        boolean hasCurrentBuild = false;
        for (UpdateInfo update : updates) {
            hasCurrentBuild |= update.getTimestamp() == buildTimestamp;
            String label = StringGenerator.getDateLocalizedUTC(mContext,
                    DateFormat.LONG, update.getTimestamp());
            BuildLabel buildLabel = new BuildLabel(update.getTimestamp(), label);
            addToSortedChangeList(mLabels, buildLabel);
        }
        String label = StringGenerator.getDateLocalizedUTC(mContext,
                DateFormat.LONG, buildTimestamp);
        addToSortedChangeList(mLabels, new BuildLabel(buildTimestamp,
                mContext.getString(R.string.changelog_current_build_label, label)));
    }

    public synchronized void stopFetching() {
        if (mGetThread != null) {
            mGetThread.interrupt();
        }
    }

    public synchronized void startFetching(ChangelogCallback callback) {
        if (mGetThread != null) {
            Log.e(TAG, "Already fetching changes");
            return;
        }

        Handler handler = new Handler();
        mGetThread = new Thread(() -> {
            while (mLastTimestamp >= mTimestampLimit || mLastTimestamp < 0) {
                String url = mContext.getString(R.string.updater_changelog_url, mDevice,
                        mLastTimestamp);
                mLastTimestamp = getChangelog(url);
                if (mLastTimestamp == -1) {
                    break;
                }
                mLastTimestamp -= 1;
                if (mGetThread.isInterrupted()) {
                    break;
                }
                handler.post(callback::onNewChanges);
            }

            synchronized (Changelog.this) {
                if (mGetThread.isInterrupted()) {
                    mGetThread = null;
                    return;
                }
            }

            boolean notify = false;
            while (mLabels.size() > 0 && mLabels.get(0).getTimestamp() >= mLastTimestamp) {
                addToSortedChangeList(mChanges, mLabels.remove(0));
                notify = true;
            }
            if (notify) {
                handler.post(callback::onNewChanges);
            }

            handler.post(callback::onEnd);
            synchronized (Changelog.this) {
                mGetThread = null;
            }
        });
        mGetThread.start();
    }

    public synchronized void loadMore(ChangelogCallback callback) {
        if (mGetThread != null) {
            Log.e(TAG, "Already fetching changes");
            return;
        }
        mTimestampLimit = mLastTimestamp - FETCH_TIMESTAMP_DIFF;
        startFetching(callback);
    }

    private long updateChangelog(String changelogJson) {
        try {
            JSONObject obj = new JSONObject(changelogJson);
            JSONArray res = obj.getJSONArray("res");
            long last = obj.getLong("last");

            /*
             * Gerrit returns the changes by "update date" rather than "submit date".
             * For this reason, some changes have a submit date smaller than "last".
             * Show only the changes with a submit date greater than "last" and put
             * aside the others.
             */

            while (mPendingChanges.size() > 0 && mPendingChanges.get(0).getTimestamp() > last) {
                addToSortedChangeList(mChanges, mPendingChanges.remove(0));
            }

            if (mSubjectsToHide == null) {
                String[] subjects = mContext.getResources().getStringArray(
                        R.array.changelog_subjects_to_hide);
                mSubjectsToHide = new HashSet<>(Arrays.asList(subjects));
            }

            for (int i = 0; i < res.length(); i++) {
                try {
                    JSONObject jsonChange = res.getJSONObject(i);
                    String subject = jsonChange.getString("subject");
                    if (mSubjectsToHide.contains(subject)) {
                        continue;
                    }
                    Change change = new Change.Builder()
                            .setSubject(subject)
                            .setProject(jsonChange.getString("project"))
                            .setTimestamp(jsonChange.getLong("submitted"))
                            .setUrl(jsonChange.getString("url"))
                            .build();
                    if (change.getTimestamp() < last) {
                        addToSortedChangeList(mPendingChanges, change);
                    } else {
                        while (mLabels.size() > 0 &&
                                mLabels.get(0).getTimestamp() > change.getTimestamp()) {
                            addToSortedChangeList(mChanges, mLabels.remove(0));
                        }
                        addToSortedChangeList(mChanges, change);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Could not parse change", e);
                }
            }
            return last;
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse the changelog", e);
            return -1;
        }
    }

    private long getChangelog(String urlString) {
        HttpURLConnection urlConnection;
        try {
            Log.d(TAG, "Downloading changes: " + urlString);
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            Log.e(TAG, "Could not get the changes", e);
            return -1;
        }

        try {
            urlConnection.connect();
            InputStream is = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                jsonBuilder.append(line);
            }
            return updateChangelog(jsonBuilder.toString());
        } catch (IOException e) {
            Log.e(TAG, "Could not read the changes ", e);
        } finally {
            urlConnection.disconnect();
        }

        return -1;
    }

    public List<ChangelogEntry> getChanges() {
        return mChanges;
    }
}
