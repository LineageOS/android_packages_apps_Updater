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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.util.Log;

import org.json.JSONException;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UpdatesActivity extends AppCompatActivity {

    private static final String TAG = "UpdatesActivity";
    private DownloadService mDownloadService;
    private BroadcastReceiver mBroadcastReceiver;

    private UpdatesListAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mAdapter = new UpdatesListAdapter(this);
        recyclerView.setAdapter(mAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DownloadController.UPDATE_STATUS_ACTION.equals(intent.getAction())) {
                    mAdapter.notifyDataSetChanged();
                } else if (DownloadController.PROGRESS_ACTION.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(DownloadController.DOWNLOAD_ID_EXTRA);
                    mAdapter.notifyItemChanged(downloadId);
                }
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, DownloadService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DownloadController.UPDATE_STATUS_ACTION);
        intentFilter.addAction(DownloadController.PROGRESS_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mDownloadService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            DownloadService.LocalBinder binder = (DownloadService.LocalBinder) service;
            mDownloadService = binder.getService();
            mAdapter.setDownloadController(mDownloadService.getDownloadController());
            getUpdatesList();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAdapter.setDownloadController(null);
            mDownloadService = null;
            mAdapter.notifyDataSetChanged();
        }
    };

    private void loadUpdatesList() throws IOException, JSONException {
        // Process local files first. If they aren't valid, the controller will delete
        // them from the database. If they are valid, they should be prioritized.

        Log.d(TAG, "Getting updates from internal database");

        DownloadControllerInt controller = mDownloadService.getDownloadController();
        UpdatesDbHelper dbHelper = new UpdatesDbHelper(this);
        for (UpdateDownload update : dbHelper.getUpdates()) {
            controller.addUpdate(update, true);
        }

        Log.d(TAG, "Adding remote updates");

        File jsonFile = Utils.getCachedUpdateList(this);
        for (UpdateDownload update : Utils.parseJson(jsonFile, true)) {
            controller.addUpdate(update, true);
        }

        List<String> updateIds = new ArrayList<>();
        updateIds.addAll(controller.getIds());
        Collections.sort(updateIds);
        mAdapter.setData(updateIds);
        mAdapter.notifyDataSetChanged();
    }

    private void getUpdatesList() {
        File jsonFile = Utils.getCachedUpdateList(this);
        if (jsonFile.exists()) {
            try {
                loadUpdatesList();
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
        } else {
            downloadUpdatesList();
        }
    }

    private void downloadUpdatesList() {
        File jsonFile = Utils.getCachedUpdateList(this);
        String url = Utils.getServerURL(this);
        Log.d(TAG, "Checking " + url);
        DownloadClient.downloadFile(url, jsonFile, new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure() {
                Log.e(TAG, "Could not download updates list");
            }

            @Override
            public void onResponse(int statusCode, String url,
                    DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(String response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.d(TAG, "List downloaded");
                            loadUpdatesList();
                        } catch (IOException | JSONException e) {
                            Log.e(TAG, "Could not read json", e);
                        }
                    }
                });
            }
        });
    }
}
