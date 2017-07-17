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

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.icu.text.DateFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.json.JSONException;
import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterControllerInt;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.download.DownloadClient;
import org.lineageos.updater.misc.BuildInfoUtils;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.LegacySupport;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UpdatesActivity extends AppCompatActivity {

    private static final String TAG = "UpdatesActivity";
    private UpdaterService mUpdaterService;
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
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    mAdapter.notifyDataSetChanged();
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.notifyItemChanged(downloadId);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.removeItem(downloadId);
                }
            }
        };

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView headerTitle = (TextView) findViewById(R.id.header_title);
        headerTitle.setText(getString(R.string.header_title_text,
                BuildInfoUtils.getBuildVersion()));

        updateLastCheckedString();

        TextView headerBuildVersion = (TextView) findViewById(R.id.header_build_version);
        headerBuildVersion.setText(
                getString(R.string.header_android_version, Build.VERSION.RELEASE));

        TextView headerBuildDate = (TextView) findViewById(R.id.header_build_date);
        headerBuildDate.setText(StringGenerator.getDateLocalized(this,
                DateFormat.LONG, BuildInfoUtils.getBuildDateTimestamp()));

        // Switch between header title and appbar title minimizing overlaps
        final CollapsingToolbarLayout collapsingToolbar =
                (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        final AppBarLayout appBar = (AppBarLayout) findViewById(R.id.app_bar);
        appBar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            boolean mIsShown = false;

            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                int scrollRange = appBarLayout.getTotalScrollRange();
                if (!mIsShown && scrollRange + verticalOffset < 10) {
                    collapsingToolbar.setTitle(getString(R.string.display_name));
                    mIsShown = true;
                } else if (mIsShown && scrollRange + verticalOffset > 100) {
                    collapsingToolbar.setTitle(null);
                    mIsShown = false;
                }
            }
        });

        if (!Utils.hasTouchscreen(this)) {
            // This can't be collapsed without a touchscreen
            appBar.setExpanded(false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, UpdaterService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                downloadUpdatesList(true);
                return true;
            case R.id.menu_auto_updates_check:
                boolean enable = !item.isChecked();
                item.setChecked(enable);
                PreferenceManager.getDefaultSharedPreferences(UpdatesActivity.this)
                        .edit()
                        .putBoolean(Constants.PREF_AUTO_UPDATES_CHECK, enable)
                        .apply();
                if (enable) {
                    UpdatesCheckReceiver.scheduleRepeatingUpdatesCheck(this);
                } else {
                    UpdatesCheckReceiver.cancelRepeatingUpdatesCheck(this);
                    UpdatesCheckReceiver.cancelUpdatesCheck(this);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            mAdapter.setUpdaterController(mUpdaterService.getUpdaterController());
            getUpdatesList();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAdapter.setUpdaterController(null);
            mUpdaterService = null;
            mAdapter.notifyDataSetChanged();
        }
    };

    private void loadUpdatesList(File jsonFile, boolean manualRefresh)
            throws IOException, JSONException {
        Log.d(TAG, "Adding remote updates");
        UpdaterControllerInt controller = mUpdaterService.getUpdaterController();
        boolean newUpdates = false;

        List<UpdateDownload> updates = Utils.parseJson(jsonFile, true);

        List<String> importedNotAvailableOnline = LegacySupport.importDownloads(this, updates);

        List<String> updatesOnline = new ArrayList<>();
        for (UpdateDownload update : updates) {
            newUpdates |= controller.addUpdate(update);
            updatesOnline.add(update.getDownloadId());
        }

        if (importedNotAvailableOnline != null) {
            updatesOnline.removeAll(importedNotAvailableOnline);
            controller.setUpdatesNotAvailableOnline(importedNotAvailableOnline);
        }

        controller.setUpdatesAvailableOnline(updatesOnline, true);

        if (manualRefresh) {
            showSnackBar(
                    newUpdates ? R.string.snack_updates_found : R.string.snack_no_updates_found,
                    Snackbar.LENGTH_SHORT);
        }

        List<String> updateIds = new ArrayList<>();
        List<UpdateDownload> sortedUpdates = controller.getUpdates();
        Collections.sort(sortedUpdates, new Comparator<UpdateDownload>() {
            @Override
            public int compare(UpdateDownload u1, UpdateDownload u2) {
                return Long.compare(u2.getTimestamp(), u1.getTimestamp());
            }
        });
        for (Update update : sortedUpdates) {
            updateIds.add(update.getDownloadId());
        }
        mAdapter.setData(updateIds);
        mAdapter.notifyDataSetChanged();
    }

    private void getUpdatesList() {
        File jsonFile = Utils.getCachedUpdateList(this);
        if (jsonFile.exists()) {
            try {
                loadUpdatesList(jsonFile, false);
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
        } else {
            downloadUpdatesList(false);
        }
    }

    private void downloadUpdatesList(final boolean manualRefresh) {
        final File jsonFile = Utils.getCachedUpdateList(this);
        final File jsonFileTmp = new File(jsonFile.getAbsolutePath() + ".tmp");
        String url = Utils.getServerURL(this);
        Log.d(TAG, "Checking " + url);

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(R.string.app_name);
        progressDialog.setMessage(getString(R.string.dialog_checking_for_updates));
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(true);

        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download updates list");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        if (!cancelled) {
                            showSnackBar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
                        }
                    }
                });
            }

            @Override
            public void onResponse(int statusCode, String url,
                    DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.d(TAG, "List downloaded");
                            loadUpdatesList(jsonFileTmp, manualRefresh);
                            long millis = System.currentTimeMillis();
                            PreferenceManager.getDefaultSharedPreferences(UpdatesActivity.this)
                                    .edit()
                                    .putLong(Constants.PREF_LAST_UPDATE_CHECK, millis)
                                    .apply();
                            jsonFileTmp.renameTo(jsonFile);
                            updateLastCheckedString();
                        } catch (IOException | JSONException e) {
                            Log.e(TAG, "Could not read json", e);
                            showSnackBar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
                        }
                        progressDialog.dismiss();
                    }
                });
            }
        };

        final DownloadClient downloadClient = new DownloadClient.Builder()
                .setUrl(url)
                .setDestination(jsonFileTmp)
                .setDownloadCallback(callback)
                .build();

        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                progressDialog.dismiss();
                downloadClient.cancel();
            }
        });
        progressDialog.show();
        downloadClient.start();
    }

    private void updateLastCheckedString() {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        long lastCheck = preferences.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000;
        String lastCheckString = getString(R.string.header_last_updates_check,
                StringGenerator.getDateLocalized(this, DateFormat.LONG, lastCheck),
                StringGenerator.getTimeLocalized(this, lastCheck));
        TextView headerLastCheck = (TextView) findViewById(R.id.header_last_check);
        headerLastCheck.setText(lastCheckString);
    }

    private void showSnackBar(int stringId, int duration) {
        Snackbar.make(findViewById(R.id.main_container), stringId, duration).show();
    }
}
