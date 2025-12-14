/*
 * Copyright (C) 2017-2025 The LineageOS Project
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

import android.app.Activity;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.airbnb.lottie.LottieAnimationView;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarAppCompatActivity;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.Update;
import org.lineageos.updater.model.UpdateInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UpdatesActivity extends CollapsingToolbarAppCompatActivity implements
        UpdateImporter.Callbacks {

    private static final String TAG = "UpdatesActivity";
    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    private UpdatesListAdapter mAdapter;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            mAdapter.setUpdaterController(mUpdaterService.getUpdaterController());
            getUpdatesList();
            updateTitle();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAdapter.setUpdaterController(null);
            mUpdaterService = null;
            mAdapter.notifyDataSetChanged();
        }
    };
    private boolean mIsTV;
    private String mHeaderTitle = "";
    private boolean mIsCollapsed = false;
    private UpdateInfo mToBeExported = null;
    private final ActivityResultLauncher<Intent> mExportUpdate = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent intent = result.getData();
                    if (intent != null) {
                        Uri uri = intent.getData();
                        exportUpdate(uri);
                    }
                }
            });
    private UpdateImporter mUpdateImporter;
    private AlertDialog importDialog;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener;
    private LottieAnimationView mLottieCheckAnimation;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private CollapsingToolbarLayout mCollapsingToolbarLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        mCollapsingToolbarLayout = getCollapsingToolbarLayout();
        if (mCollapsingToolbarLayout != null) {
            mCollapsingToolbarLayout.setTitle(getString(R.string.display_name));
            AppBarLayout appBarLayout = findViewById(R.id.app_bar);
            if (appBarLayout != null) {
                appBarLayout.addOnOffsetChangedListener((appBar, verticalOffset) -> {
                    int totalScrollRange = appBar.getTotalScrollRange();
                    boolean isCollapsed = Math.abs(verticalOffset) >= totalScrollRange;
                    if (isCollapsed != mIsCollapsed) {
                        mIsCollapsed = isCollapsed;
                        updateTitle();
                    }
                });
            }
        }

        mUpdateImporter = new UpdateImporter(this, this);

        UiModeManager uiModeManager = getSystemService(UiModeManager.class);
        mIsTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        mAdapter = new UpdatesListAdapter(this);
        recyclerView.setAdapter(mAdapter);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    handleDownloadStatusChange(downloadId);
                    mAdapter.notifyItemChanged(downloadId);
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.notifyItemChanged(downloadId);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.removeItem(downloadId);
                    List<UpdateInfo> sortedUpdates = mUpdaterService.getUpdaterController().getUpdates();
                    if (sortedUpdates.isEmpty()) {
                        findViewById(R.id.no_new_updates_view).setVisibility(View.VISIBLE);
                        findViewById(R.id.recycler_view).setVisibility(View.GONE);
                    }
                }
                updateTitle();
            }
        };

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefListener = (sharedPreferences, key) -> {
            if (Constants.PREF_LAST_UPDATE_CHECK.equals(key)) {
                runOnUiThread(this::updateLastCheckedString);
            }
        };
        updateLastCheckedString();

        TextView headerBuildVersion = findViewById(R.id.entity_header_summary);
        headerBuildVersion.setText(
                getString(R.string.header_build_version_combined,
                        Utils.getBuildVersion(), Build.VERSION.RELEASE));

        TextView headerBuildDate = findViewById(R.id.entity_header_title);
        headerBuildDate.setText(StringGenerator.getDateLocalizedUTC(this,
                DateFormat.LONG, Utils.getBuildDateTimestamp()));

        if (mIsTV) {
            findViewById(R.id.preferences)
                    .setOnClickListener(v -> startActivity(new Intent(this, UpdaterPreferences.class)));
        }

        View showChangelogButton = findViewById(R.id.show_changelog_button);
        if (showChangelogButton != null) {
            showChangelogButton.setOnClickListener(v -> {
                Intent openUrl = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(Utils.getChangelogURL(this)));
                startActivity(openUrl);
            });
        }



        mLottieCheckAnimation = findViewById(R.id.lottie_check_animation);

        maybeShowWelcomeMessage();
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
        prefs.registerOnSharedPreferenceChangeListener(mPrefListener);

        registerNetworkCallback();

        // Initial check
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        handleNetworkState(cm.getNetworkCapabilities(cm.getActiveNetwork()));
    }

    @Override
    protected void onPause() {
        if (importDialog != null) {
            importDialog.dismiss();
            importDialog = null;
            mUpdateImporter.stopImport();
        }

        super.onPause();
    }

    @Override
    public void onStop() {
        unregisterNetworkCallback();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        prefs.unregisterOnSharedPreferenceChangeListener(mPrefListener);
        super.onStop();
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                runOnUiThread(() -> handleNetworkState(capabilities));
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> handleNetworkState(null));
            }
        };
        cm.registerDefaultNetworkCallback(mNetworkCallback);
    }

    private void unregisterNetworkCallback() {
        if (mNetworkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.unregisterNetworkCallback(mNetworkCallback);
            mNetworkCallback = null;
        }
    }

    private void handleNetworkState(NetworkCapabilities capabilities) {
        boolean isConnected = capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        if (mLottieCheckAnimation != null) {
            if (isConnected) {
                mLottieCheckAnimation.cancelAnimation();
                mLottieCheckAnimation.setVisibility(View.GONE);
            } else {
                mLottieCheckAnimation.setVisibility(View.VISIBLE);
                mLottieCheckAnimation.playAnimation();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (!mUpdateImporter.onResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onImportStarted() {
        if (importDialog != null && importDialog.isShowing()) {
            importDialog.dismiss();
        }

        importDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.local_update_import)
                .setView(R.layout.progress_dialog)
                .setCancelable(false)
                .create();

        importDialog.show();
    }

    @Override
    public void onImportCompleted(Update update) {
        UpdaterController controller = mUpdaterService.getUpdaterController();
        if (importDialog != null) {
            importDialog.dismiss();
            importDialog = null;
        }

        if (update == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.local_update_import)
                    .setMessage(R.string.local_update_import_failure)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        List<String> updateIds = new ArrayList<>();
        List<UpdateInfo> sortedUpdates = controller.getUpdates();
        if (sortedUpdates.isEmpty()) {
            findViewById(R.id.no_new_updates_view).setVisibility(View.VISIBLE);
            findViewById(R.id.recycler_view).setVisibility(View.GONE);
        } else {
            findViewById(R.id.no_new_updates_view).setVisibility(View.GONE);
            findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
            sortedUpdates.sort((u1, u2) -> Long.compare(u2.getTimestamp(), u1.getTimestamp()));
            for (UpdateInfo updateInfo : sortedUpdates) {
                updateIds.add(updateInfo.getDownloadId());
            }
            mAdapter.setData(updateIds);
            mAdapter.notifyDataSetChanged();
        }

        final Runnable deleteUpdate = () -> UpdaterController.getInstance(this)
                .deleteUpdate(update.getDownloadId());

        new AlertDialog.Builder(this)
                .setTitle(R.string.local_update_import)
                .setMessage(getString(R.string.local_update_import_success, update.getVersion()))
                .setPositiveButton(R.string.local_update_import_install, (dialog, which) -> {
                    if (controller.isInstallingUpdate()) {
                        return;
                    }
                    // The item is already in the adapter, just trigger the installation.
                    // The broadcast receiver will handle updating the UI to show progress.

                    // Update UI
                    getUpdatesList();
                    Utils.triggerUpdate(this, update.getDownloadId());
                    updateTitle();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> deleteUpdate.run())
                .setOnCancelListener((dialog) -> deleteUpdate.run())
                .show();
    }

    private void loadUpdatesList(File jsonFile)
            throws IOException, JSONException {
        Log.d(TAG, "Adding remote updates");
        UpdaterController controller = mUpdaterService.getUpdaterController();
        boolean newUpdates = false;

        List<UpdateInfo> updates = Utils.parseJson(jsonFile, true);
        List<String> updatesOnline = new ArrayList<>();
        for (UpdateInfo update : updates) {
            newUpdates |= controller.addUpdate(update);
            updatesOnline.add(update.getDownloadId());
        }
        controller.setUpdatesAvailableOnline(updatesOnline, true);
        updateTitle();

        List<String> updateIds = new ArrayList<>();
        List<UpdateInfo> sortedUpdates = controller.getUpdates();
        if (sortedUpdates.isEmpty()) {
            findViewById(R.id.no_new_updates_view).setVisibility(View.VISIBLE);
            findViewById(R.id.recycler_view).setVisibility(View.GONE);
        } else {
            findViewById(R.id.no_new_updates_view).setVisibility(View.GONE);
            findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
            sortedUpdates.sort((u1, u2) -> Long.compare(u2.getTimestamp(), u1.getTimestamp()));
            for (UpdateInfo update : sortedUpdates) {
                updateIds.add(update.getDownloadId());
            }
            mAdapter.setData(updateIds);
            mAdapter.notifyDataSetChanged();
        }
    }

    private void getUpdatesList() {
        File jsonFile = Utils.getCachedUpdateList(this);
        if (jsonFile.exists()) {
            try {
                loadUpdatesList(jsonFile);
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
        }
        UpdatesCheckWorker.runImmediateCheck(this);
        updateTitle();
    }

    private void updateTitle() {
        if (mUpdaterService == null) {
            return;
        }

        UpdaterController controller = mUpdaterService.getUpdaterController();
        int headerIconResId;
        if (controller.isInstallingUpdate()) {
            mHeaderTitle = getString(R.string.header_title_installing);
            headerIconResId = R.drawable.ic_change_circle;
        } else if (!controller.getUpdates().isEmpty()) {
            mHeaderTitle = getString(R.string.header_title_updates_available);
            headerIconResId = R.drawable.ic_release_alert;
        } else {
            mHeaderTitle = getString(R.string.header_title_updates_unavailable);
            headerIconResId = R.drawable.ic_verified;
        }

        setTitle(mHeaderTitle);
        if (mCollapsingToolbarLayout != null) {
            mCollapsingToolbarLayout.setTitle(mIsCollapsed ? getString(R.string.display_name) : mHeaderTitle);
        }

        ImageView headerIcon = findViewById(R.id.entity_header_icon);
        if (headerIcon != null) {
            headerIcon.setImageResource(headerIconResId);
        }

        invalidateOptionsMenu();
    }

    private void updateLastCheckedString() {
        long lastCheck = prefs.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000;
        String lastCheckString = getString(R.string.header_last_updates_check,
                StringGenerator.getDateLocalized(this, DateFormat.LONG, lastCheck),
                StringGenerator.getTimeLocalized(this, lastCheck));
        TextView headerLastCheck = findViewById(R.id.entity_header_second_summary);
        headerLastCheck.setText(lastCheckString);

        long now = System.currentTimeMillis();
        boolean isRecent = (now - lastCheck * 1000) < 60 * 1000;
        headerLastCheck.setTextColor(getColor(isRecent ? R.color.ic_verified_color : R.color.ic_alert_color));
    }

    private void handleDownloadStatusChange(String downloadId) {
        if (Update.LOCAL_ID.equals(downloadId)) {
            return;
        }

        UpdateInfo update = mUpdaterService.getUpdaterController().getUpdate(downloadId);
        switch (update.getStatus()) {
            case PAUSED_ERROR:
                showSnackbar(R.string.snack_download_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFICATION_FAILED:
                showSnackbar(R.string.snack_download_verification_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFIED:
                showSnackbar(R.string.snack_download_verified, Snackbar.LENGTH_LONG);
                break;
        }
    }

    public void exportUpdate(UpdateInfo update) {
        mToBeExported = update;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, update.getName());

        mExportUpdate.launch(intent);
    }

    private void exportUpdate(Uri uri) {
        Intent intent = new Intent(this, ExportUpdateService.class);
        intent.setAction(ExportUpdateService.ACTION_START_EXPORTING);
        intent.putExtra(ExportUpdateService.EXTRA_SOURCE_FILE, mToBeExported.getFile());
        intent.putExtra(ExportUpdateService.EXTRA_DEST_URI, uri);
        startService(intent);
    }

    public void showSnackbar(int stringId, int duration) {
        Snackbar.make(findViewById(R.id.main_container), stringId, duration).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mIsTV) {
            getMenuInflater().inflate(R.menu.menu_updates, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!mIsTV && mUpdaterService != null) {
            UpdaterController controller = mUpdaterService.getUpdaterController();
            boolean isInstalling = controller.isInstallingUpdate();
            MenuItem localUpdateItem = menu.findItem(R.id.menu_local_update);
            if (localUpdateItem != null) {
                localUpdateItem.setEnabled(!isInstalling);
                // SettingsLib likely handles icon tint, but explicit alpha helps indication
                localUpdateItem.getIcon().setAlpha(isInstalling ? 102 : 255);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_preferences) {
            startActivity(new Intent(this, UpdaterPreferences.class));
            return true;
        } else if (id == R.id.menu_local_update) {
            mUpdateImporter.openImportPicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void maybeShowWelcomeMessage() {
        boolean alreadySeen = prefs.getBoolean(Constants.HAS_SEEN_WELCOME_MESSAGE, false);
        if (alreadySeen) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.welcome_title)
                .setMessage(R.string.welcome_message)
                .setPositiveButton(R.string.info_dialog_ok, (dialog, which) -> prefs.edit()
                        .putBoolean(Constants.HAS_SEEN_WELCOME_MESSAGE, true)
                        .apply())
                .show();
    }
}
