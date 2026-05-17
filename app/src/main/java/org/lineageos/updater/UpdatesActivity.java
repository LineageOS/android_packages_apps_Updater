/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.data.Update;
import org.lineageos.updater.deviceinfo.DeviceInfoUtils;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UpdatesActivity extends UpdatesScaffoldActivity implements UpdateImporter.Callbacks {

    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    private UpdatesListAdapter mAdapter;
    private UpdatesViewModel mViewModel;

    private Update mToBeExported = null;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        mUpdateImporter = new UpdateImporter(this, this);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        mAdapter = new UpdatesListAdapter(this, this::exportUpdate);
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

        mViewModel = new ViewModelProvider(this).get(UpdatesViewModel.class);

        mViewModel.getUiStateLive().observe(this, state -> {
            updateLastCheckedString(state.getLastCheckedTimestamp());
            setRefreshEnabled(((UpdaterApplication) getApplication()).getNetworkMonitor()
                    .getCurrentNetworkState().isOnline()
                    && !state.isCheckingForUpdates());
            if (state.getErrorMessage() != null) {
                showToast(R.string.snack_updates_check_failed, Toast.LENGTH_LONG);
                mViewModel.errorMessageShown();
            }
            if (mUpdaterService != null) {
                refreshUpdatesList(state.getUpdates());
            }
        });

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
        registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
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
        unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (!mUpdateImporter.onResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRefreshClick() {
        mViewModel.fetchUpdates();
    }

    @Override
    public void onLocalUpdateClick() {
        mUpdateImporter.openImportPicker();
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
                    refreshUpdatesList(
                            Objects.requireNonNull(mViewModel.getUiState().getValue()).getUpdates());
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
            refreshUpdatesList(
                    Objects.requireNonNull(mViewModel.getUiState().getValue()).getUpdates());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAdapter.setUpdaterController(null);
            mUpdaterService = null;
            mAdapter.notifyDataSetChanged();
        }
    };

    private void refreshUpdatesList(List<Update> updates) {
        UpdaterController controller = mUpdaterService.getUpdaterController();
        List<String> updateIds = new ArrayList<>();
        for (Update update : updates) {
            controller.addUpdate(update);
            updateIds.add(update.getDownloadId());
        }
        controller.setUpdatesAvailableOnline(updateIds, true);
        mAdapter.setData(updateIds);
        mAdapter.notifyDataSetChanged();
    }

    private void updateLastCheckedString(long timestamp) {
        long lastCheck = timestamp / 1000;
        String lastCheckString = getString(R.string.header_last_updates_check,
                StringGenerator.getDateLocalized(this, DateFormat.LONG, lastCheck),
                StringGenerator.getTimeLocalized(this, lastCheck));
        TextView headerLastCheck = findViewById(R.id.header_last_check);
        headerLastCheck.setText(lastCheckString);
    }

    private void handleDownloadStatusChange(String downloadId) {
        if (Update.LOCAL_ID.equals(downloadId)) {
            return;
        }

        Update update = mUpdaterService.getUpdaterController().getUpdate(downloadId);
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

    public void exportUpdate(Update update) {
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
}
