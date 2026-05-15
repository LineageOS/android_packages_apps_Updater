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
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.data.Update;
import org.lineageos.updater.misc.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UpdatesActivity extends UpdatesScaffoldActivity implements UpdateImporter.Callbacks {

    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

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
        setupCompose();

        mUpdateImporter = new UpdateImporter(this, this);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                notifyControllerStateChanged();
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    handleDownloadStatusChange(downloadId);
                }
            }
        };

        mViewModel = new ViewModelProvider(this, UpdatesViewModel.factory(getApplication()))
                .get(UpdatesViewModel.class);

        mViewModel.getUiStateLive().observe(this, state -> {
            if (mUpdaterService != null) {
                syncControllerUpdates(state.getUpdates());
            }
        });

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

        final Runnable deleteUpdate = () -> UpdaterController.getInstance(this)
                .deleteUpdate(update.getDownloadId());

        new AlertDialog.Builder(this)
                .setTitle(R.string.local_update_import)
                .setMessage(getString(R.string.local_update_import_success, update.getVersion()))
                .setPositiveButton(R.string.local_update_import_install, (dialog, which) -> {
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
            setUpdaterController(mUpdaterService.getUpdaterController());
            syncControllerUpdates(
                    Objects.requireNonNull(mViewModel.getUiState().getValue()).getUpdates());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            setUpdaterController(null);
            mUpdaterService = null;
        }
    };

    private void syncControllerUpdates(List<Update> updates) {
        UpdaterController controller = mUpdaterService.getUpdaterController();
        List<String> onlineUpdateIds = new ArrayList<>();
        for (Update update : updates) {
            boolean availableOnline = !Update.LOCAL_ID.equals(update.getDownloadId());
            controller.addUpdate(update, availableOnline);
            if (availableOnline) {
                onlineUpdateIds.add(update.getDownloadId());
            }
        }

        controller.setUpdatesAvailableOnline(onlineUpdateIds, true);
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

    @Override
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
}
