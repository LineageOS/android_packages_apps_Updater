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

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.lineageos.updater.controller.Controller;
import org.lineageos.updater.misc.BuildInfoUtils;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.misc.FileUtils;
import org.lineageos.updater.misc.PermissionsUtils;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.List;

public class UpdatesListAdapter extends RecyclerView.Adapter<UpdatesListAdapter.ViewHolder> {

    private static final String TAG = "UpdateListAdapter";

    private final float mAlphaDisabledValue;

    private List<String> mDownloadIds;
    private String mSelectedDownload;
    private Controller mUpdaterController;
    private UpdatesListActivity mActivity;
    private ActionMode mActionMode;

    private enum Action {
        DOWNLOAD,
        PAUSE,
        RESUME,
        INSTALL
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private ImageButton mAction;

        private View mNotActiveLayout;
        private TextView mBuildDate;
        private TextView mBuildVersion;

        private View mActiveLayout;
        private TextView mBuildInfo;
        private ProgressBar mProgressBar;
        private TextView mProgressText;
        private TextView mProgressPercentage;

        public ViewHolder(final View view) {
            super(view);
            mAction = (ImageButton) view.findViewById(R.id.update_action);

            mNotActiveLayout = view.findViewById(R.id.build_layout_not_active);
            mBuildDate = (TextView) view.findViewById(R.id.build_date);
            mBuildVersion = (TextView) view.findViewById(R.id.build_version);

            mActiveLayout = view.findViewById(R.id.build_layout_active);
            mBuildInfo = (TextView) view.findViewById(R.id.build_info);
            mProgressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
            mProgressText = (TextView) view.findViewById(R.id.progress_text);
            mProgressPercentage = (TextView) view.findViewById(R.id.progress_percentage);
        }
    }

    public UpdatesListAdapter(UpdatesListActivity activity) {
        mActivity = activity;

        TypedValue tv = new TypedValue();
        mActivity.getTheme().resolveAttribute(android.R.attr.disabledAlpha, tv, true);
        mAlphaDisabledValue = tv.getFloat();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.update_item_view, viewGroup, false);
        view.setBackground(mActivity.getDrawable(R.drawable.list_item_background));
        return new ViewHolder(view);
    }

    public void setUpdaterController(Controller updaterController) {
        mUpdaterController = updaterController;
        notifyDataSetChanged();
    }

    private void handleActiveStatus(ViewHolder viewHolder, UpdateInfo update) {
        String buildDate = StringGenerator.getDateLocalizedUTC(mActivity,
                DateFormat.MEDIUM, update.getTimestamp());
        String buildInfoText = mActivity.getString(R.string.list_build_version_date,
                BuildInfoUtils.getBuildVersion(), buildDate);
        viewHolder.mBuildInfo.setText(buildInfoText);

        boolean canDelete = false;

        final String downloadId = update.getDownloadId();
        if (mUpdaterController.isDownloading(downloadId)) {
            canDelete = true;
            String downloaded = StringGenerator.bytesToMegabytes(mActivity,
                    update.getFile().length());
            String total = Formatter.formatShortFileSize(mActivity, update.getFileSize());
            long eta = update.getEta();
            if (eta > 0) {
                CharSequence etaString = StringGenerator.formatDuration(mActivity, eta * 1000);
                viewHolder.mProgressText.setText(mActivity.getString(
                        R.string.list_download_progress_eta, downloaded, total, etaString));
            } else {
                viewHolder.mProgressText.setText(mActivity.getString(
                        R.string.list_download_progress, downloaded, total));
            }
            setButtonAction(viewHolder.mAction, Action.PAUSE, downloadId, true);
            viewHolder.mProgressBar.setIndeterminate(update.getStatus() == UpdateStatus.STARTING);
            viewHolder.mProgressBar.setProgress(update.getProgress());
            String percentage = NumberFormat.getPercentInstance().format(
                    update.getProgress() / 100.f);
            viewHolder.mProgressPercentage.setText(percentage);
        } else if (mUpdaterController.isInstallingUpdate(downloadId)) {
            setButtonAction(viewHolder.mAction, Action.INSTALL, downloadId, false);
            viewHolder.mProgressText.setText(R.string.list_installing_update);
            viewHolder.mProgressBar.setIndeterminate(true);
        } else if (mUpdaterController.isVerifyingUpdate(downloadId)) {
            setButtonAction(viewHolder.mAction, Action.INSTALL, downloadId, false);
            viewHolder.mProgressText.setText(R.string.list_verifying_update);
            viewHolder.mProgressBar.setIndeterminate(true);
            viewHolder.mProgressPercentage.setText(NumberFormat.getPercentInstance().format(1));
        } else {
            canDelete = true;
            setButtonAction(viewHolder.mAction, Action.RESUME, downloadId, !isBusy());
            String downloaded = StringGenerator.bytesToMegabytes(mActivity,
                    update.getFile().length());
            String total = Formatter.formatShortFileSize(mActivity, update.getFileSize());
            viewHolder.mProgressText.setText(mActivity.getString(R.string.list_download_progress,
                    downloaded, total));
            viewHolder.mProgressBar.setIndeterminate(false);
            viewHolder.mProgressBar.setProgress(update.getProgress());
        }

        viewHolder.itemView.setOnLongClickListener(getLongClickListener(update, canDelete));
    }

    private void handleNotActiveStatus(ViewHolder viewHolder, UpdateInfo update) {
        String buildDate = StringGenerator.getDateLocalizedUTC(mActivity,
                DateFormat.LONG, update.getTimestamp());
        String buildVersion = mActivity.getString(R.string.list_build_version,
                update.getVersion());
        viewHolder.mBuildDate.setText(buildDate);
        viewHolder.mBuildVersion.setText(buildVersion);

        if (update.getPersistentStatus() == UpdateStatus.Persistent.VERIFIED) {
            viewHolder.itemView.setOnLongClickListener(getLongClickListener(update, true));
            setButtonAction(viewHolder.mAction, Action.INSTALL, update.getDownloadId(), !isBusy());
        } else {
            viewHolder.itemView.setOnLongClickListener(getLongClickListener(update, false));
            setButtonAction(viewHolder.mAction, Action.DOWNLOAD, update.getDownloadId(), !isBusy());
        }
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, int i) {
        if (mUpdaterController == null) {
            viewHolder.mAction.setEnabled(false);
            return;
        }

        final String downloadId = mDownloadIds.get(i);
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        if (update == null) {
            // The update was deleted
            viewHolder.mAction.setEnabled(false);
            viewHolder.mAction.setImageResource(R.drawable.ic_download);
            return;
        }

        viewHolder.itemView.setSelected(downloadId.equals(mSelectedDownload));

        boolean activeLayout;
        switch (update.getPersistentStatus()) {
            case UpdateStatus.Persistent.UNKNOWN:
                activeLayout = update.getStatus() == UpdateStatus.STARTING;
                break;
            case UpdateStatus.Persistent.VERIFIED:
                activeLayout = false;
                break;
            case UpdateStatus.Persistent.INCOMPLETE:
                activeLayout = true;
                break;
            default:
                throw new RuntimeException("Unknown update status");
        }

        if (activeLayout) {
            handleActiveStatus(viewHolder, update);
            viewHolder.mActiveLayout.setVisibility(View.VISIBLE);
            viewHolder.mNotActiveLayout.setVisibility(View.INVISIBLE);
        } else {
            handleNotActiveStatus(viewHolder, update);
            viewHolder.mActiveLayout.setVisibility(View.INVISIBLE);
            viewHolder.mNotActiveLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return mDownloadIds == null ? 0 : mDownloadIds.size();
    }

    public void setData(List<String> downloadIds) {
        mDownloadIds = downloadIds;
    }

    public void notifyItemChanged(String downloadId) {
        notifyItemChanged(mDownloadIds.indexOf(downloadId));
    }

    public void removeItem(String downloadId) {
        int position = mDownloadIds.indexOf(downloadId);
        mDownloadIds.remove(downloadId);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, getItemCount());
    }

    private void startDownloadWithWarning(final String downloadId) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean warn = preferences.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true);
        if (Utils.isOnWifiOrEthernet(mActivity) || !warn) {
            mUpdaterController.startDownload(downloadId);
            return;
        }

        View checkboxView = LayoutInflater.from(mActivity).inflate(R.layout.checkbox_view, null);
        CheckBox checkbox = (CheckBox) checkboxView.findViewById(R.id.checkbox);
        checkbox.setText(R.string.checkbox_mobile_data_warning);

        new AlertDialog.Builder(mActivity)
                .setTitle(R.string.update_on_mobile_data_title)
                .setMessage(R.string.update_on_mobile_data_message)
                .setView(checkboxView)
                .setPositiveButton(R.string.action_description_download,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (checkbox.isChecked()) {
                                    preferences.edit()
                                            .putBoolean(Constants.PREF_MOBILE_DATA_WARNING, false)
                                            .apply();
                                    mActivity.supportInvalidateOptionsMenu();
                                }
                                mUpdaterController.startDownload(downloadId);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setButtonAction(ImageButton button, Action action, final String downloadId,
            boolean enabled) {
        final View.OnClickListener clickListener;
        switch (action) {
            case DOWNLOAD:
                button.setImageResource(R.drawable.ic_download);
                button.setContentDescription(
                        mActivity.getString(R.string.action_description_download));
                button.setEnabled(enabled);
                clickListener = !enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startDownloadWithWarning(downloadId);
                    }
                };
                break;
            case PAUSE:
                button.setImageResource(R.drawable.ic_pause);
                button.setContentDescription(
                        mActivity.getString(R.string.action_description_pause));
                button.setEnabled(enabled);
                clickListener = !enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mUpdaterController.pauseDownload(downloadId);
                    }
                };
                break;
            case RESUME: {
                button.setImageResource(R.drawable.ic_resume);
                button.setContentDescription(
                        mActivity.getString(R.string.action_description_resume));
                button.setEnabled(enabled);
                UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update) ||
                        update.getFile().length() == update.getFileSize();
                clickListener = !enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (canInstall) {
                            mUpdaterController.resumeDownload(downloadId);
                        } else {
                            mActivity.showSnackbar(R.string.snack_update_not_installable,
                                    Snackbar.LENGTH_LONG);
                        }
                    }
                };
            }
            break;
            case INSTALL: {
                button.setImageResource(R.drawable.ic_system_update);
                button.setContentDescription(
                        mActivity.getString(R.string.action_description_install));
                button.setEnabled(enabled);
                UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update);
                clickListener = !enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (canInstall) {
                            getInstallDialog(downloadId).show();
                        } else {
                            mActivity.showSnackbar(R.string.snack_update_not_installable,
                                    Snackbar.LENGTH_LONG);
                        }
                    }
                };
            }
            break;
            default:
                clickListener = null;
        }
        button.setAlpha(enabled ? 1.f : mAlphaDisabledValue);

        // Disable action mode when a button is clicked
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListener != null) {
                    clickListener.onClick(v);
                    stopActionMode();
                }
            }
        });
    }

    private boolean isBusy() {
        return mUpdaterController.hasActiveDownloads() || mUpdaterController.isVerifyingUpdate()
                || mUpdaterController.isInstallingUpdate();
    }

    private AlertDialog.Builder getDeleteDialog(final String downloadId) {
        return new AlertDialog.Builder(mActivity)
                .setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mUpdaterController.pauseDownload(downloadId);
                                mUpdaterController.deleteUpdate(downloadId);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private View.OnLongClickListener getLongClickListener(final UpdateInfo update,
            final boolean canDelete) {
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (mActionMode == null) {
                    startActionMode(update, canDelete);
                }
                return true;
            }
        };
    }

    private AlertDialog.Builder getInstallDialog(final String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        int resId;
        try {
            if (Utils.isABUpdate(update.getFile())) {
                resId = R.string.apply_update_dialog_message_ab;
            } else {
                resId = R.string.apply_update_dialog_message;
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not determine the type of the update");
            return null;
        }

        String buildDate = StringGenerator.getDateLocalizedUTC(mActivity,
                DateFormat.MEDIUM, update.getTimestamp());
        String buildInfoText = mActivity.getString(R.string.list_build_version_date,
                BuildInfoUtils.getBuildVersion(), buildDate);
        return new AlertDialog.Builder(mActivity)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(mActivity.getString(resId, buildInfoText,
                        mActivity.getString(android.R.string.ok)))
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Utils.triggerUpdate(mActivity, downloadId);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private void stopActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }

    private void startActionMode(final UpdateInfo update, final boolean canDelete) {
        if (mActionMode != null) {
            Log.d(TAG, "Action mode already enabled");
            return;
        }

        mSelectedDownload = update.getDownloadId();
        notifyItemChanged(update.getDownloadId());

        // Hide Action Bar not to steal the focus when using a D-pad
        final boolean showActionBar;
        if (mActivity.getSupportActionBar() != null &&
                mActivity.getSupportActionBar().isShowing()) {
            showActionBar = true;
            mActivity.getSupportActionBar().hide();
        } else {
            showActionBar = false;
        }

        mActionMode = mActivity.startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.menu_action_mode, menu);
                menu.findItem(R.id.menu_delete_action).setVisible(canDelete);
                menu.findItem(R.id.menu_copy_url).setVisible(update.getAvailableOnline());
                menu.findItem(R.id.menu_export_update).setVisible(
                        update.getPersistentStatus() == UpdateStatus.Persistent.VERIFIED);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_delete_action:
                        getDeleteDialog(update.getDownloadId())
                                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                    @Override
                                    public void onDismiss(DialogInterface dialog) {
                                        mode.finish();
                                    }
                                })
                                .show();
                        return true;
                    case R.id.menu_copy_url:
                        Utils.addToClipboard(mActivity,
                                mActivity.getString(R.string.label_download_url),
                                update.getDownloadUrl(),
                                mActivity.getString(R.string.toast_download_url_copied));
                        mode.finish();
                        return true;
                    case R.id.menu_export_update:
                        // TODO: start exporting once the permission has been granted
                        boolean hasPermission = PermissionsUtils.checkAndRequestStoragePermission(
                                mActivity, 0);
                        if (hasPermission) {
                            exportUpdate(update);
                        }
                        return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mSelectedDownload = null;
                mActionMode = null;
                notifyItemChanged(update.getDownloadId());

                if (showActionBar) {
                    mActivity.getSupportActionBar().show();
                }
            }
        });
    }

    private void exportUpdate(UpdateInfo update) {
        try {
            File dest = new File(Utils.getExportPath(mActivity), update.getName());
            if (dest.exists()) {
                dest = Utils.appendSequentialNumber(dest);
            }
            FileUtils.copyFileWithDialog(mActivity, update.getFile(), dest);
        } catch (IOException e) {
            Log.e(TAG, "Export failed", e);
            mActivity.showSnackbar(R.string.snack_export_failed,
                    Snackbar.LENGTH_LONG);
        }
        stopActionMode();
    }
}
