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

import android.content.Context;
import android.content.DialogInterface;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.lineageos.updater.controller.UpdaterControllerInt;
import org.lineageos.updater.misc.BuildInfoUtils;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;

import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.List;

public class UpdatesListAdapter extends RecyclerView.Adapter<UpdatesListAdapter.ViewHolder> {

    private static final String TAG = "UpdateListAdapter";

    private final float mAlphaDisabledValue;

    private List<String> mDownloadIds;
    private UpdaterControllerInt mUpdaterController;
    private Context mContext;
    private View mContainerView;

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

    public UpdatesListAdapter(Context context, View containerView) {
        mContext = context;
        mContainerView = containerView;

        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.disabledAlpha, tv, true);
        mAlphaDisabledValue = tv.getFloat();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.update_item_view, viewGroup, false);
        return new ViewHolder(view);
    }

    public void setUpdaterController(UpdaterControllerInt updaterController) {
        mUpdaterController = updaterController;
        notifyDataSetChanged();
    }

    private void handleActiveStatus(ViewHolder viewHolder, UpdateDownload update) {
        String buildDate = StringGenerator.getDateLocalizedUTC(mContext,
                DateFormat.MEDIUM, update.getTimestamp());
        String buildInfoText = mContext.getString(R.string.list_build_version_date,
                BuildInfoUtils.getBuildVersion(), buildDate);
        viewHolder.mBuildInfo.setText(buildInfoText);

        boolean busy = mUpdaterController.hasActiveDownloads() ||
                mUpdaterController.isVerifyingUpdate();

        boolean canDelete = false;

        final String downloadId = update.getDownloadId();
        if (mUpdaterController.isDownloading(downloadId)) {
            String downloaded = StringGenerator.bytesToMegabytes(mContext,
                    update.getFile().length());
            String total = Formatter.formatShortFileSize(mContext, update.getFileSize());
            long eta = update.getEta();
            if (eta > 0) {
                CharSequence etaString = DateUtils.formatDuration(eta * 1000);
                viewHolder.mProgressText.setText(mContext.getString(
                        R.string.list_download_progress_eta, downloaded, total, etaString));
            } else {
                viewHolder.mProgressText.setText(mContext.getString(
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
            setButtonAction(viewHolder.mAction, Action.RESUME, downloadId, !busy);
            String downloaded = StringGenerator.bytesToMegabytes(mContext,
                    update.getFile().length());
            String total = Formatter.formatShortFileSize(mContext, update.getFileSize());
            viewHolder.mProgressText.setText(mContext.getString(R.string.list_download_progress,
                    downloaded, total));
            viewHolder.mProgressBar.setIndeterminate(false);
            viewHolder.mProgressBar.setProgress(update.getProgress());
        }

        viewHolder.itemView.setOnLongClickListener(
                canDelete ? getDeleteClickListener(update.getDownloadId()) : null);
    }

    private void handleNotActiveStatus(ViewHolder viewHolder, UpdateDownload update) {
        String buildDate = StringGenerator.getDateLocalizedUTC(mContext,
                DateFormat.LONG, update.getTimestamp());
        String buildVersion = mContext.getString(R.string.list_build_version,
                BuildInfoUtils.getBuildVersion());
        viewHolder.mBuildDate.setText(buildDate);
        viewHolder.mBuildVersion.setText(buildVersion);

        boolean busy = mUpdaterController.hasActiveDownloads() ||
                mUpdaterController.isVerifyingUpdate();

        if (update.getPersistentStatus() == UpdateStatus.Persistent.VERIFIED) {
            viewHolder.itemView.setOnLongClickListener(
                    getDeleteClickListener(update.getDownloadId()));
            setButtonAction(viewHolder.mAction, Action.INSTALL, update.getDownloadId(), !busy);
        } else {
            viewHolder.itemView.setOnLongClickListener(null);
            setButtonAction(viewHolder.mAction, Action.DOWNLOAD, update.getDownloadId(), !busy);
        }
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, int i) {
        if (mUpdaterController == null) {
            viewHolder.mAction.setEnabled(false);
            return;
        }

        final String downloadId = mDownloadIds.get(i);
        UpdateDownload update = mUpdaterController.getUpdate(downloadId);
        if (update == null) {
            // The update was deleted
            viewHolder.mAction.setEnabled(false);
            viewHolder.mAction.setImageResource(R.drawable.ic_download);
            return;
        }

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

    private void setButtonAction(ImageButton button, Action action, final String downloadId,
            boolean enabled) {
        switch (action) {
            case DOWNLOAD:
                button.setImageResource(R.drawable.ic_download);
                button.setContentDescription(
                        mContext.getString(R.string.action_description_download));
                button.setEnabled(enabled);
                button.setOnClickListener(!enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mUpdaterController.startDownload(downloadId);
                    }
                });
                break;
            case PAUSE:
                button.setImageResource(R.drawable.ic_pause);
                button.setContentDescription(
                        mContext.getString(R.string.action_description_pause));
                button.setEnabled(enabled);
                button.setOnClickListener(!enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mUpdaterController.pauseDownload(downloadId);
                    }
                });
                break;
            case RESUME: {
                button.setImageResource(R.drawable.ic_resume);
                button.setContentDescription(
                        mContext.getString(R.string.action_description_resume));
                button.setEnabled(enabled);
                UpdateDownload update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update);
                button.setOnClickListener(!enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (canInstall) {
                            mUpdaterController.resumeDownload(downloadId);
                        } else {
                            showSnackbar(R.string.snack_update_not_installable,
                                    Snackbar.LENGTH_LONG);
                        }
                    }
                });
            }
            break;
            case INSTALL: {
                button.setImageResource(R.drawable.ic_system_update);
                button.setContentDescription(
                        mContext.getString(R.string.action_description_install));
                button.setEnabled(enabled);
                UpdateDownload update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update);
                button.setOnClickListener(!enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (canInstall) {
                            getInstallDialog(downloadId).show();
                        } else {
                            showSnackbar(R.string.snack_update_not_installable,
                                    Snackbar.LENGTH_LONG);
                        }
                    }
                });
            }
            break;
        }
        button.setAlpha(enabled ? 1.f : mAlphaDisabledValue);
    }

    private AlertDialog.Builder getDeleteDialog(final String downloadId) {
        return new AlertDialog.Builder(mContext)
                .setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mUpdaterController.cancelDownload(downloadId);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private View.OnLongClickListener getDeleteClickListener(final String downloadId) {
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                getDeleteDialog(downloadId).show();
                return true;
            }
        };
    }

    private void showSnackbar(int stringId, int duration) {
        Snackbar.make(mContainerView, stringId, duration).show();
    }

    private AlertDialog.Builder getInstallDialog(final String downloadId) {
        UpdateDownload update = mUpdaterController.getUpdate(downloadId);
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

        String buildDate = StringGenerator.getDateLocalizedUTC(mContext,
                DateFormat.MEDIUM, update.getTimestamp());
        String buildInfoText = mContext.getString(R.string.list_build_version_date,
                BuildInfoUtils.getBuildVersion(), buildDate);
        return new AlertDialog.Builder(mContext)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(mContext.getString(resId, buildInfoText,
                        mContext.getString(android.R.string.ok)))
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Utils.triggerUpdate(mContext, downloadId);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }
}
