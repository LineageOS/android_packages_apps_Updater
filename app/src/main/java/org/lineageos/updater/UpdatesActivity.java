/*
 * Copyright (C) 2017-2026 The LineageOS Project
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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.icu.text.DateFormat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.chip.Chip;

import org.json.JSONException;

import com.android.settingslib.utils.ColorUtil;

import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.download.DownloadClient;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.DeviceInfoUtils;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.Update;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.android.settingslib.widget.theme.R.color.*;
import static com.android.settingslib.widget.theme.R.drawable.*;
import static com.android.settingslib.widget.preference.banner.R.drawable.*;

public class UpdatesActivity extends UpdatesListActivity implements UpdateImporter.Callbacks {

    private static final String TAG = "UpdatesActivity";
    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    private UpdatesListAdapter mAdapter;

    private ConnectivityManager mConnectivityManager;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private boolean mIsCheckingForUpdates = false;

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

    private enum FilterMode {
        ALL,
        LATEST,
        DOWNLOADED
    }

    private static final String STATE_CURRENT_FILTER = "current_filter";

    private FilterMode mCurrentFilter = FilterMode.ALL;
    private List<String> mAllUpdateIds;
    private boolean mIsCollapsed = false;
    private ImageView mStatusIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        if (savedInstanceState != null) {
            mCurrentFilter = FilterMode.values()[savedInstanceState.getInt(STATE_CURRENT_FILTER,
                    FilterMode.ALL.ordinal())];
        }

        mUpdateImporter = new UpdateImporter(this, this);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
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
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    handleDownloadStatusChange(downloadId);
                    mAdapter.notifyItemChanged(downloadId);
                    updateHeaderIconAndTitle();
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.notifyItemChanged(downloadId);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.removeItem(downloadId);
                    updateHeaderIconAndTitle();
                }
            }
        };

        updateLastCheckedString();
        updateHeaderSummary();
        setupFilterChips();
        setTitle(R.string.display_name);

        setupHeaderButtons();
        maybeShowWelcomeMessage();
        Objects.requireNonNull(getAppBarLayout()).post(() ->
                getAppBarLayout().setExpanded(true, false));

        setupStatusIcon();
        Objects.requireNonNull(getAppBarLayout())
                .addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                    mIsCollapsed = Math.abs(verticalOffset) == appBarLayout.getTotalScrollRange();
                    updateHeaderIconAndTitle();
        });
    }



    private void setupStatusIcon() {
        CollapsingToolbarLayout collapsingToolbar = getCollapsingToolbarLayout();
        if (collapsingToolbar == null) {
            return;

        }

        mStatusIcon = new ImageView(this);
        int size = getResources().getDimensionPixelSize(R.dimen.toolbar_icon_size);
        CollapsingToolbarLayout.LayoutParams lp =
                new CollapsingToolbarLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER;
        lp.setCollapseMode(CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN);
        mStatusIcon.setVisibility(View.GONE);
        collapsingToolbar.addView(mStatusIcon, lp);
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

        registerNetworkCallback();
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        unregisterNetworkCallback();
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
        int itemId = item.getItemId();
        if (itemId == R.id.menu_preferences) {
            startActivity(new Intent(this, PreferencesActivity.class));
            return true;
        } else if (itemId == R.id.menu_local_update) {
            mUpdateImporter.openImportPicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void registerNetworkCallback() {
        if (mNetworkCallback != null) {
            return;
        }

        mConnectivityManager = getSystemService(ConnectivityManager.class);
        if (mConnectivityManager != null) {
            mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    runOnUiThread(() -> updateNetworkState(isNetworkValidated()));
                }

                @Override
                public void onLost(@NonNull Network network) {
                    runOnUiThread(() -> updateNetworkState(isNetworkValidated()));
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                                                  @NonNull NetworkCapabilities capabilities) {
                    runOnUiThread(() -> {
                        boolean isNetworkValidated =
                                capabilities.hasCapability(
                                        NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                        capabilities.hasCapability(
                                                NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                        updateNetworkState(isNetworkValidated);
                        if (isNetworkValidated) {
                            downloadUpdatesList(false);
                        }
                    });
                }
            };
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build();
            mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);
            updateNetworkState(isNetworkValidated());
        }
    }

    private void unregisterNetworkCallback() {
        if (mConnectivityManager != null && mNetworkCallback != null) {
            try {
                mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            } catch (IllegalArgumentException e) {
                // Callback was not registered
            }
            mNetworkCallback = null;
        }
    }

    private boolean isNetworkValidated() {
        if (mConnectivityManager == null) {
            return false;
        }
        Network activeNetwork = mConnectivityManager.getActiveNetwork();
        NetworkCapabilities networkCapabilities =
                mConnectivityManager.getNetworkCapabilities(activeNetwork);
        return networkCapabilities != null &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void updateNetworkState(boolean isNetworkValidated) {
        View networkWarningCard = findViewById(R.id.network_warning_card);
        if (isNetworkValidated) {
            networkWarningCard.setVisibility(View.GONE);
        } else {
            ImageView icon = networkWarningCard.findViewById(android.R.id.icon);
            ImageView secondaryIcon = networkWarningCard.findViewById(android.R.id.icon1);
            TextView title = networkWarningCard.findViewById(android.R.id.title);
            TextView summary = networkWarningCard.findViewById(android.R.id.summary);

            icon.setImageResource(ic_warning);
            secondaryIcon.setVisibility(View.GONE);
            title.setText(R.string.check_internet_connection);
            summary.setVisibility(View.GONE);

            networkWarningCard.setVisibility(View.VISIBLE);
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
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

        mAdapter.notifyDataSetChanged();

        final Runnable deleteUpdate = () -> UpdaterController.getInstance(this)
                .deleteUpdate(update.getDownloadId());

        new AlertDialog.Builder(this)
                .setTitle(R.string.local_update_import)
                .setMessage(getString(R.string.local_update_import_success, update.getVersion()))
                .setPositiveButton(R.string.local_update_import_install, (dialog, which) -> {
                    mAdapter.addItem(update.getDownloadId());
                    // Update UI
                    getUpdatesList();
                    Utils.triggerUpdate(this, update.getDownloadId());
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> deleteUpdate.run())
                .setOnCancelListener((dialog) -> deleteUpdate.run())
                .show();
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
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
        UpdaterController controller = mUpdaterService.getUpdaterController();
        boolean newUpdates = false;

        List<UpdateInfo> updates = Utils.parseJson(jsonFile, true);
        List<String> updatesOnline = new ArrayList<>();
        for (UpdateInfo update : updates) {
            newUpdates |= controller.addUpdate(update);
            updatesOnline.add(update.getDownloadId());
        }
        controller.setUpdatesAvailableOnline(updatesOnline, true);

        if (manualRefresh) {
            showToast(
                    newUpdates ? R.string.snack_updates_found : R.string.snack_no_updates_found,
                    Toast.LENGTH_SHORT);
        }

        List<UpdateInfo> sortedUpdates = controller.getUpdates();
        if (!sortedUpdates.isEmpty()) {
            sortedUpdates.sort((u1, u2) -> Long.compare(u2.getTimestamp(), u1.getTimestamp()));
        }
        mAllUpdateIds = new ArrayList<>();
        for (UpdateInfo update : sortedUpdates) {
            mAllUpdateIds.add(update.getDownloadId());
        }
        applyFilter();
        updateHeaderIconAndTitle();
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
        }
        if (isNetworkValidated()) {
            downloadUpdatesList(false);
        }
    }

    private void processNewJson(File json, File jsonNew, boolean manualRefresh) {
        try {
            loadUpdatesList(jsonNew, manualRefresh);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            long millis = System.currentTimeMillis();
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK, millis).apply();
            updateLastCheckedString();
            if (json.exists() &&
                    preferences.getBoolean(Constants.PREF_PERIODIC_CHECK_ENABLED, true) &&
                    Utils.checkForNewUpdates(json, jsonNew)) {
                UpdatesCheckReceiver.updateRepeatingUpdatesCheck(this);
            }
            // In case we set a one-shot check because of a previous failure
            UpdatesCheckReceiver.cancelUpdatesCheck(this);
            //noinspection ResultOfMethodCallIgnored
            jsonNew.renameTo(json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not read json", e);
            showToast(R.string.snack_updates_check_failed, Toast.LENGTH_LONG);
        }
    }

    private void downloadUpdatesList(final boolean manualRefresh) {
        if (mIsCheckingForUpdates) {
            return;
        }
        mIsCheckingForUpdates = true;
        final File jsonFile = Utils.getCachedUpdateList(this);
        final File jsonFileTmp = new File(jsonFile.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL(this);
        Log.d(TAG, "Checking " + url);

        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download updates list");
                runOnUiThread(() -> {
                    mIsCheckingForUpdates = false;
                    if (!cancelled) {
                        showToast(R.string.snack_updates_check_failed, Toast.LENGTH_LONG);
                    }
                    setRefreshActionState(false);
                });
            }

            @Override
            public void onResponse(DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    mIsCheckingForUpdates = false;
                    Log.d(TAG, "List downloaded");
                    processNewJson(jsonFile, jsonFileTmp, manualRefresh);
                    setRefreshActionState(false);
                });
            }
        };

        final DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonFileTmp)
                    .setDownloadCallback(callback)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            mIsCheckingForUpdates = false;
            showToast(R.string.snack_updates_check_failed, Toast.LENGTH_LONG);
            return;
        }

        setRefreshActionState(true);
        downloadClient.start();
    }

    private void updateLastCheckedString() {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        long lastCheck = preferences.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000;
        String lastCheckString = getString(R.string.header_last_updates_check,
                StringGenerator.getDateLocalized(this, DateFormat.LONG, lastCheck),
                StringGenerator.getTimeLocalized(this, lastCheck));
        TextView headerLastCheck = findViewById(R.id.header_last_check);
        headerLastCheck.setText(lastCheckString);

        long now = System.currentTimeMillis() / 1000;
        String nowString = getString(R.string.header_last_updates_check,
                StringGenerator.getDateLocalized(this, DateFormat.LONG, now),
                StringGenerator.getTimeLocalized(this, now));

        if (nowString.equals(lastCheckString)) {
            headerLastCheck.setTextColor(getColor(settingslib_colorBackgroundLevel_low));
        } else {
            headerLastCheck.setTextColor(getColor(settingslib_colorBackgroundLevel_medium));
        }
    }

    private void setupFilterChips() {
        Chip filterAll = findViewById(R.id.filter_all);
        Chip filterLatest = findViewById(R.id.filter_latest);
        Chip filterDownloaded = findViewById(R.id.filter_downloaded);

        switch (mCurrentFilter) {
            case DOWNLOADED:
                filterDownloaded.setChecked(true);
                break;
            case LATEST:
                filterLatest.setChecked(true);
                break;
            case ALL:
            default:
                filterAll.setChecked(true);
                break;
        }

        filterAll.setOnClickListener(v -> {
            if (mCurrentFilter != FilterMode.ALL) {
                mCurrentFilter = FilterMode.ALL;
                applyFilter();
            }
        });

        filterLatest.setOnClickListener(v -> {
            if (mCurrentFilter != FilterMode.LATEST) {
                mCurrentFilter = FilterMode.LATEST;
                applyFilter();
            }
        });

        filterDownloaded.setOnClickListener(v -> {
            if (mCurrentFilter != FilterMode.DOWNLOADED) {
                mCurrentFilter = FilterMode.DOWNLOADED;
                applyFilter();
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_FILTER, mCurrentFilter.ordinal());
    }

    private void setupHeaderButtons() {
        findViewById(R.id.refresh_button).setOnClickListener(v -> downloadUpdatesList(true));

        findViewById(R.id.show_changelog_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.menu_changelog_url, DeviceInfoUtils.getDevice())));
            startActivity(intent);
        });

        findViewById(R.id.report_issue_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.report_issue_url)));
            startActivity(intent);
        });
    }

    private void applyFilter() {
        if (mAllUpdateIds == null || mUpdaterService == null) {
            return;
        }

        List<String> filteredIds = new ArrayList<>();
        UpdaterController controller = mUpdaterService.getUpdaterController();

        switch (mCurrentFilter) {
            case ALL:
                filteredIds.addAll(mAllUpdateIds);
                break;
            case LATEST:
                if (!mAllUpdateIds.isEmpty()) {
                    filteredIds.add(mAllUpdateIds.get(0));
                }
                break;
            case DOWNLOADED:
                for (String id : mAllUpdateIds) {
                    UpdateInfo update = controller.getUpdate(id);
                    if (update != null &&
                            update.getPersistentStatus() == UpdateStatus.Persistent.VERIFIED) {
                        filteredIds.add(id);
                    }
                }
                break;
        }

        mAdapter.setData(filteredIds);
        mAdapter.notifyDataSetChanged();
    }

    private void updateHeaderSummary() {
        TextView headerAndroidVersion = findViewById(R.id.header_android_version);
        headerAndroidVersion.setText(getString(R.string.header_android_version,
                Build.VERSION.RELEASE));

        TextView headerLineageVersion = findViewById(R.id.header_lineage_version);
        headerLineageVersion.setText(getString(R.string.header_lineage_version,
                DeviceInfoUtils.getBuildVersion()));

        TextView headerBuildDate = findViewById(R.id.header_build_date);
        headerBuildDate.setText(StringGenerator.getDateLocalizedUTC(this,
                DateFormat.LONG, DeviceInfoUtils.getBuildDateTimestamp()));

        TextView headerSecurityPatch = findViewById(R.id.header_security_patch_level);
        headerSecurityPatch.setText(getString(R.string.header_android_security_update,
                DeviceInfoUtils.getSecurityPatch()));
    }

    private void updateHeaderIconAndTitle() {
        if (mUpdaterService == null) {
            return;
        }

        UpdaterController controller = mUpdaterService.getUpdaterController();
        List<UpdateInfo> updates = controller.getUpdates();

        int iconRes;
        String titleText;

        if (updates.isEmpty()) {
            iconRes = settingslib_expressive_icon_level_low;
            titleText = getString(R.string.snack_no_updates_found);
        } else {
            boolean isInstalling = false;
            boolean isPendingReboot = false;

            for (UpdateInfo update : updates) {
                if (update.getStatus() == UpdateStatus.INSTALLED) {
                    isPendingReboot = true;
                    break;
                }
                if (update.getStatus() == UpdateStatus.INSTALLING ||
                        update.getStatus() == UpdateStatus.INSTALLATION_SUSPENDED) {
                    isInstalling = true;
                }
            }

            if (isPendingReboot) {
                iconRes = R.drawable.settingslib_expressive_icon_pending;
                titleText = getString(R.string.reboot_to_complete_update);
            } else if (isInstalling) {
                iconRes = R.drawable.settingslib_expressive_icon_ongoing;
                titleText = getString(R.string.installing_update);
            } else {
                iconRes = settingslib_expressive_icon_level_medium;
                titleText = getString(R.string.new_updates_found_title);
            }
        }

        if (mStatusIcon != null) {
            mStatusIcon.setImageResource(iconRes);
            if (mIsCollapsed) {
                mStatusIcon.setVisibility(View.VISIBLE);
                setTitle("");
            } else {
                mStatusIcon.setVisibility(View.GONE);
                setTitle(titleText);
            }
        } else {
            setTitle(titleText);
        }
    }

    private void handleDownloadStatusChange(String downloadId) {
        if (Update.LOCAL_ID.equals(downloadId)) {
            return;
        }

        UpdateInfo update = mUpdaterService.getUpdaterController().getUpdate(downloadId);
        switch (update.getStatus()) {
            case PAUSED_ERROR:
                showToast(R.string.snack_download_failed, Toast.LENGTH_LONG);
                break;
            case VERIFICATION_FAILED:
                showToast(R.string.snack_download_verification_failed, Toast.LENGTH_LONG);
                break;
            case VERIFIED:
                showToast(R.string.snack_download_verified, Toast.LENGTH_LONG);
                break;
        }
    }

    @Override
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

    @Override
    public void showToast(int stringId, int duration) {
        Toast.makeText(this, stringId, duration).show();
    }

    private void maybeShowWelcomeMessage() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean alreadySeen = preferences.getBoolean(Constants.PREF_HAS_SEEN_WELCOME_MESSAGE, false);
        if (alreadySeen) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.welcome_title)
                .setMessage(R.string.welcome_message)
                .setPositiveButton(R.string.info_dialog_ok, (dialog, which) -> preferences.edit()
                        .putBoolean(Constants.PREF_HAS_SEEN_WELCOME_MESSAGE, true)
                        .apply())
                .show();
    }
    private void setRefreshActionState(boolean busy) {
        View refreshButton = findViewById(R.id.refresh_button);
        if (refreshButton != null) {
            refreshButton.setEnabled(!busy);
            refreshButton.setAlpha(busy ? ColorUtil.getDisabledAlpha(this) : 1f);
        }
    }
}
