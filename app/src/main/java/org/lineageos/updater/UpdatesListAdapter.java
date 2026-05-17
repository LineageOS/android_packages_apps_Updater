/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.text.SpannableString;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.spa.framework.theme.SettingsOpacity;

import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.data.Update;
import org.lineageos.updater.data.UpdateStatus;
import org.lineageos.updater.data.UserPreferencesRepository;
import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.util.StringUtil;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.util.BatteryMonitor;
import org.lineageos.updater.util.BatteryMonitor.BatteryState;
import org.lineageos.updater.util.InstallUtils;
import org.lineageos.updater.util.NetworkMonitor;

import java.io.IOException;
import java.time.format.FormatStyle;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UpdatesListAdapter extends RecyclerView.Adapter<UpdatesListAdapter.ViewHolder> {

    private static final String TAG = "UpdateListAdapter";

    private List<String> mDownloadIds;
    private String mSelectedDownload;
    private UpdaterController mUpdaterController;
    private final Activity mActivity;
    private final Consumer<Update> mExportUpdateCallback;

    private AlertDialog infoDialog;
    private final BatteryMonitor mBatteryMonitor;
    private final NetworkMonitor mNetworkMonitor;
    private final UserPreferencesRepository mUserPreferencesRepository;

    private enum Action {
        DOWNLOAD,
        PAUSE,
        RESUME,
        INSTALL,
        INFO,
        DELETE,
        CANCEL_INSTALLATION,
        REBOOT,
        CANCEL,
        SUSPEND_INSTALLATION,
        RESUME_INSTALLATION,
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final Button mAction;
        private final Button mCancel;
        private final ImageButton mMenu;

        private final TextView mBuildDate;
        private final TextView mBuildVersion;
        private final TextView mBuildSize;

        private final LinearLayout mProgress;
        private final ProgressBar mProgressBar;
        private final TextView mProgressText;
        private final TextView mPercentage;

        public ViewHolder(final View view) {
            super(view);
            mAction = view.findViewById(R.id.update_action);
            mCancel = view.findViewById(R.id.update_cancel);
            mMenu = view.findViewById(R.id.update_menu);

            mBuildDate = view.findViewById(R.id.build_date);
            mBuildVersion = view.findViewById(R.id.build_version);
            mBuildSize = view.findViewById(R.id.build_size);

            mProgress = view.findViewById(R.id.progress);
            mProgressBar = view.findViewById(R.id.progress_bar);
            mProgressText = view.findViewById(R.id.progress_text);
            mPercentage = view.findViewById(R.id.progress_percent);
        }
    }

    public UpdatesListAdapter(Activity activity, Consumer<Update> exportUpdateCallback) {
        mActivity = activity;
        UpdaterApplication application = (UpdaterApplication) activity.getApplication();
        mBatteryMonitor = application.getBatteryMonitor();
        mNetworkMonitor = application.getNetworkMonitor();
        mUserPreferencesRepository = application.getUserPreferencesRepository();
        mExportUpdateCallback = exportUpdateCallback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.update_item_view, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);

        if (infoDialog != null) {
            infoDialog.dismiss();
        }
    }

    public void setUpdaterController(UpdaterController updaterController) {
        mUpdaterController = updaterController;
        notifyDataSetChanged();
    }

    private void handleActiveStatus(ViewHolder viewHolder, Update update) {
        final String downloadId = update.getDownloadId();
        boolean isVerifying = mUpdaterController.isVerifyingUpdate(downloadId);
        boolean isInstalling = mUpdaterController.isInstallingUpdate(downloadId)
                || update.getStatus() == UpdateStatus.INSTALLATION_SUSPENDED;
        boolean isABUpdate = mUpdaterController.isInstallingABUpdate();

        if (mUpdaterController.isDownloading(downloadId)) {
            String downloaded = Formatter.formatShortFileSize(mActivity,
                    update.getFile().length());
            String total = Formatter.formatShortFileSize(mActivity, update.getFileSize());
            String percentage = NumberFormat.getPercentInstance().format(
                    update.getProgress() / 100.f);
            viewHolder.mPercentage.setText(percentage);
            long eta = update.getEta();
            if (eta > 0) {
                CharSequence etaString = StringUtil.formatETA(mActivity, eta * 1000);
                viewHolder.mProgressText.setText(mActivity.getString(
                        R.string.list_download_progress_eta_newer, downloaded, total, etaString));
            } else {
                viewHolder.mProgressText.setText(mActivity.getString(
                        R.string.list_download_progress_newer, downloaded, total));
            }
            setButtonAction(viewHolder.mAction, Action.PAUSE, downloadId, true);
            viewHolder.mProgressBar.setIndeterminate(update.getStatus() == UpdateStatus.STARTING);
            viewHolder.mProgressBar.setProgress(update.getProgress());
        } else if (isInstalling) {
            if (isABUpdate) {
                boolean isSuspended = update.getStatus() == UpdateStatus.INSTALLATION_SUSPENDED;
                setButtonAction(viewHolder.mAction,
                        isSuspended ? Action.RESUME_INSTALLATION : Action.SUSPEND_INSTALLATION,
                        downloadId, true);
            } else {
                setButtonAction(viewHolder.mAction, Action.CANCEL_INSTALLATION, downloadId, true);
            }
            viewHolder.mProgressText.setText(!isABUpdate ? R.string.dialog_prepare_zip_message :
                    update.isFinalizing() ?
                            R.string.finalizing_package :
                            R.string.preparing_ota_first_boot);
            String percentage = NumberFormat.getPercentInstance().format(
                    update.getInstallProgress() / 100.f);
            viewHolder.mPercentage.setText(percentage);
            viewHolder.mProgressBar.setIndeterminate(false);
            viewHolder.mProgressBar.setProgress(update.getInstallProgress());
        } else if (isVerifying) {
            setButtonAction(viewHolder.mAction, Action.INSTALL, downloadId, false);
            viewHolder.mProgressText.setText(R.string.list_verifying_update);
            viewHolder.mProgressBar.setIndeterminate(true);
        } else {
            setButtonAction(viewHolder.mAction, Action.RESUME, downloadId,
                    mNetworkMonitor.getCurrentNetworkState().isOnline());
            String downloaded = Formatter.formatShortFileSize(mActivity,
                    update.getFile().length());
            String total = Formatter.formatShortFileSize(mActivity, update.getFileSize());
            String percentage = NumberFormat.getPercentInstance().format(
                    update.getProgress() / 100.f);
            viewHolder.mPercentage.setText(percentage);
            viewHolder.mProgressText.setText(mActivity.getString(
                    R.string.list_download_progress_newer, downloaded, total));
            viewHolder.mProgressBar.setIndeterminate(false);
            viewHolder.mProgressBar.setProgress(update.getProgress());
        }

        boolean showCancel = !isVerifying && (!isInstalling || isABUpdate);
        if (showCancel) {
            viewHolder.mCancel.setVisibility(View.VISIBLE);
            setButtonAction(viewHolder.mCancel,
                    isInstalling ? Action.CANCEL_INSTALLATION : Action.CANCEL, downloadId, true);
        } else {
            viewHolder.mCancel.setVisibility(View.GONE);
        }

        boolean canDelete = update.getStatus().hasVerifiedPackage();
        viewHolder.mMenu.setOnClickListener(getClickListener(update, canDelete, viewHolder.mMenu));
        viewHolder.mProgress.setVisibility(View.VISIBLE);
        viewHolder.mProgressText.setVisibility(View.VISIBLE);
        viewHolder.mBuildSize.setVisibility(View.INVISIBLE);
    }

    private void handleNotActiveStatus(ViewHolder viewHolder, Update update) {
        final String downloadId = update.getDownloadId();
        if (mUpdaterController.isWaitingForReboot(downloadId)) {
            viewHolder.mMenu.setOnClickListener(getClickListener(update, false, viewHolder.mMenu));
            setButtonAction(viewHolder.mAction, Action.REBOOT, downloadId, true);
        } else if (update.getStatus().hasVerifiedPackage()) {
            viewHolder.mMenu.setOnClickListener(getClickListener(update, true, viewHolder.mMenu));
            setButtonAction(viewHolder.mAction,
                    Utils.canInstall(update) ? Action.INSTALL : Action.DELETE,
                    downloadId, !isBusy());
        } else if (!Utils.canInstall(update)) {
            viewHolder.mMenu.setOnClickListener(getClickListener(update, false, viewHolder.mMenu));
            setButtonAction(viewHolder.mAction, Action.INFO, downloadId, !isBusy());
        } else {
            viewHolder.mMenu.setOnClickListener(getClickListener(update, false, viewHolder.mMenu));
            boolean canDownload = !mUpdaterController.isVerifyingUpdate(downloadId)
                    && !mUpdaterController.isInstallingUpdate(downloadId)
                    && mNetworkMonitor.getCurrentNetworkState().isOnline();
            setButtonAction(viewHolder.mAction, Action.DOWNLOAD, downloadId, canDownload);
        }
        String fileSize = Formatter.formatShortFileSize(mActivity, update.getFileSize());
        viewHolder.mBuildSize.setText(fileSize);

        viewHolder.mCancel.setVisibility(View.GONE);
        viewHolder.mProgress.setVisibility(View.INVISIBLE);
        viewHolder.mProgressText.setVisibility(View.INVISIBLE);
        viewHolder.mBuildSize.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder viewHolder, int i) {
        if (mDownloadIds == null) {
            viewHolder.mAction.setEnabled(false);
            return;
        }

        final String downloadId = mDownloadIds.get(i);
        Update update = mUpdaterController.getUpdate(downloadId);
        if (update == null) {
            // The update was deleted
            viewHolder.mAction.setEnabled(false);
            viewHolder.mAction.setText(R.string.action_download);
            return;
        }

        viewHolder.itemView.setSelected(downloadId.equals(mSelectedDownload));

        boolean activeLayout = update.getStatus().isInProgress();

        String buildDate = StringUtil.getDateLocalizedUTC(mActivity,
                FormatStyle.LONG, update.getTimestamp());
        String buildVersion = mActivity.getString(R.string.list_build_version,
                update.getVersion());
        viewHolder.mBuildDate.setText(buildDate);
        viewHolder.mBuildVersion.setText(buildVersion);
        viewHolder.mBuildVersion.setCompoundDrawables(null, null, null, null);

        if (activeLayout) {
            handleActiveStatus(viewHolder, update);
        } else {
            handleNotActiveStatus(viewHolder, update);
        }
    }

    @Override
    public int getItemCount() {
        return mDownloadIds == null ? 0 : mDownloadIds.size();
    }

    public void setData(List<String> downloadIds) {
        mDownloadIds = downloadIds;
    }

    public void addItem(String downloadId) {
        if (mDownloadIds == null) {
            mDownloadIds = new ArrayList<>();
        }
        mDownloadIds.add(0, downloadId);
        notifyItemInserted(0);
    }

    public void notifyItemChanged(String downloadId) {
        if (mDownloadIds == null) {
            return;
        }
        notifyItemChanged(mDownloadIds.indexOf(downloadId));
    }

    public void removeItem(String downloadId) {
        if (mDownloadIds == null) {
            return;
        }
        int position = mDownloadIds.indexOf(downloadId);
        mDownloadIds.remove(downloadId);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, getItemCount());
    }

    private void downloadWithConfirmation(final String downloadId, boolean isResume) {
        if (mUpdaterController.hasActiveDownloads()) {
            new AlertDialog.Builder(mActivity)
                    .setTitle(R.string.download_switch_confirm_title)
                    .setMessage(R.string.download_switch_confirm_message)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> downloadWithWarning(downloadId, isResume))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            downloadWithWarning(downloadId, isResume);
        }
    }

    private void downloadWithWarning(final String downloadId, boolean isResume) {
        Runnable downloadAction = isResume
                ? () -> mUpdaterController.resumeDownload(downloadId)
                : () -> mUpdaterController.startDownload(downloadId);

        boolean warn = mUserPreferencesRepository.getMeteredNetworkWarningBlocking();
        if (!(mNetworkMonitor.getCurrentNetworkState().isMetered() && warn)) {
            downloadAction.run();
            return;
        }

        new AlertDialog.Builder(mActivity)
                .setTitle(R.string.update_over_metered_network_title)
                .setMessage(R.string.update_over_metered_network_message)
                .setPositiveButton(isResume ? R.string.action_resume : R.string.action_download,
                        (dialog, which) -> downloadAction.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setButtonAction(Button button, Action action, final String downloadId,
            boolean enabled) {
        final View.OnClickListener clickListener;
        switch (action) {
            case DOWNLOAD:
                button.setText(R.string.action_download);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> downloadWithConfirmation(downloadId, false) : null;
                break;
            case PAUSE:
                button.setText(R.string.action_pause);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> mUpdaterController.pauseDownload(downloadId)
                        : null;
                break;
            case RESUME: {
                button.setText(R.string.action_resume);
                button.setEnabled(enabled);
                Update update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update) ||
                        update.getFile().length() == update.getFileSize();
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        downloadWithConfirmation(downloadId, true);
                    } else {
                        Toast.makeText(mActivity, R.string.snack_update_not_installable,
                                Toast.LENGTH_LONG).show();
                    }
                } : null;
            }
            break;
            case INSTALL: {
                button.setText(R.string.action_install);
                button.setEnabled(enabled);
                Update update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update);
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        AlertDialog.Builder installDialog = getInstallDialog(downloadId);
                        if (installDialog != null) {
                            installDialog.show();
                        }
                    } else {
                        Toast.makeText(mActivity, R.string.snack_update_not_installable,
                                Toast.LENGTH_LONG).show();
                    }
                } : null;
            }
            break;
            case INFO: {
                button.setText(R.string.action_info);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> showInfoDialog() : null;
            }
            break;
            case DELETE: {
                button.setText(R.string.action_delete);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> getDeleteDialog(downloadId).show() : null;
            }
            break;
            case CANCEL_INSTALLATION: {
                button.setText(android.R.string.cancel);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> getCancelInstallationDialog().show() : null;
            }
            break;
            case REBOOT: {
                button.setText(R.string.reboot);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> {
                    PowerManager pm = mActivity.getSystemService(PowerManager.class);
                    pm.reboot(null);
                } : null;
            }
            break;
            case CANCEL: {
                button.setText(android.R.string.cancel);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> getCancelDownloadDialog(downloadId).show() : null;
            }
            break;
            case SUSPEND_INSTALLATION: {
                button.setText(R.string.action_pause);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> {
                    Intent intent = new Intent(mActivity, UpdaterService.class);
                    intent.setAction(UpdaterService.ACTION_INSTALL_SUSPEND);
                    mActivity.startService(intent);
                } : null;
            }
            break;
            case RESUME_INSTALLATION: {
                button.setText(R.string.action_resume);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> {
                    Intent intent = new Intent(mActivity, UpdaterService.class);
                    intent.setAction(UpdaterService.ACTION_INSTALL_RESUME);
                    mActivity.startService(intent);
                } : null;
            }
            break;
            default:
                clickListener = null;
        }
        button.setAlpha(enabled ? 1.f : SettingsOpacity.Disabled);

        // Disable action mode when a button is clicked
        button.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(v);
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
                        (dialog, which) -> {
                            mUpdaterController.pauseDownload(downloadId);
                            mUpdaterController.deleteUpdate(downloadId);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private AlertDialog.Builder getCancelDownloadDialog(final String downloadId) {
        return new AlertDialog.Builder(mActivity)
                .setTitle(R.string.confirm_cancel_dialog_title)
                .setMessage(R.string.confirm_cancel_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> mUpdaterController.cancelDownload(downloadId))
                .setNegativeButton(android.R.string.cancel, null);
    }

    private View.OnClickListener getClickListener(final Update update,
            final boolean canDelete, View anchor) {
        return view -> startActionMode(update, canDelete, anchor);
    }

    private AlertDialog.Builder getInstallDialog(final String downloadId) {
        if (!mBatteryMonitor.getCurrentBatteryState().isLevelOk()) {
            String message = mActivity.getString(R.string.dialog_battery_low_message_pct,
                    BatteryState.MIN_BATT_PCT_DISCHARGING,
                    BatteryState.MIN_BATT_PCT_CHARGING);
            return new AlertDialog.Builder(mActivity)
                    .setTitle(R.string.dialog_battery_low_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null);
        }
        if (InstallUtils.isScratchMounted()) {
            return new AlertDialog.Builder(mActivity)
                    .setTitle(R.string.dialog_scratch_mounted_title)
                    .setMessage(R.string.dialog_scratch_mounted_message)
                    .setPositiveButton(android.R.string.ok, null);
        }
        Update update = mUpdaterController.getUpdate(downloadId);
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

        String buildDate = StringUtil.getDateLocalizedUTC(mActivity,
                FormatStyle.MEDIUM, update.getTimestamp());
        String buildInfoText = mActivity.getString(R.string.list_build_version_date,
                update.getVersion(), buildDate);
        return new AlertDialog.Builder(mActivity)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(mActivity.getString(resId, buildInfoText,
                        mActivity.getString(android.R.string.ok)))
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            Utils.triggerUpdate(mActivity, downloadId);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private AlertDialog.Builder getCancelInstallationDialog() {
        return new AlertDialog.Builder(mActivity)
                .setTitle(R.string.cancel_installation_dialog_title)
                .setMessage(R.string.cancel_installation_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            Intent intent = new Intent(mActivity, UpdaterService.class);
                            intent.setAction(UpdaterService.ACTION_INSTALL_STOP);
                            mActivity.startService(intent);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private void startActionMode(final Update update, final boolean canDelete, View anchor) {
        mSelectedDownload = update.getDownloadId();
        notifyItemChanged(update.getDownloadId());

        PopupMenu popupMenu = new PopupMenu(mActivity, anchor);
        popupMenu.inflate(R.menu.menu_action_mode);

        boolean shouldShowDelete = canDelete;
        boolean isVerified = update.getStatus().hasVerifiedPackage();
        if (isVerified && !Utils.canInstall(update) && !update.isAvailableOnline()) {
            shouldShowDelete = false;
        }
        popupMenu.getMenu().findItem(R.id.menu_delete_action).setVisible(shouldShowDelete);
        MenuItem viewDownloadsItem = popupMenu.getMenu().findItem(R.id.menu_view_downloads);
        viewDownloadsItem.setTitle(mActivity.getString(R.string.menu_view_downloads,
                mActivity.getString(R.string.brand_name)));
        viewDownloadsItem.setVisible(update.isAvailableOnline());
        popupMenu.getMenu().findItem(R.id.menu_export_update).setVisible(isVerified);

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_delete_action) {
                getDeleteDialog(update.getDownloadId()).show();
                return true;
            } else if (itemId == R.id.menu_view_downloads) {
                mActivity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(Utils.getDownloadsURL(mActivity))));
                return true;
            } else if (itemId == R.id.menu_export_update) {
                if (mActivity != null) {
                    mExportUpdateCallback.accept(update);
                }
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void showInfoDialog() {
        String messageString = mActivity.getString(R.string.blocked_update_dialog_message,
                Utils.getUpgradeBlockedURL(mActivity));
        SpannableString message = new SpannableString(messageString);
        Linkify.addLinks(message, Linkify.WEB_URLS);
        if (infoDialog != null) {
            infoDialog.dismiss();
        }
        infoDialog = new AlertDialog.Builder(mActivity)
                .setTitle(R.string.blocked_update_dialog_title)
                .setPositiveButton(android.R.string.ok, null)
                .setMessage(message)
                .show();
        TextView textView = infoDialog.findViewById(android.R.id.message);
        if (textView != null) {
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }
}
