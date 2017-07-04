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
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.lineageos.updater.controller.UpdaterControllerInt;
import org.lineageos.updater.misc.Utils;

import java.io.IOException;
import java.util.List;

public class UpdatesListAdapter extends RecyclerView.Adapter<UpdatesListAdapter.ViewHolder> {

    private static final String TAG = "UpdateListAdapter";

    private List<String> mDownloadIds;
    private UpdaterControllerInt mUpdaterController;
    private Context mContext;

    private enum Action {
        DOWNLOAD,
        PAUSE,
        RESUME,
        CANCEL,
        INSTALL
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mName;
        private Button mButton1;
        private Button mButton2;
        private ProgressBar mProgressBar;

        public ViewHolder(final View view) {
            super(view);
            mName = (TextView) view.findViewById(R.id.name);
            mButton1 = (Button) view.findViewById(R.id.button1);
            mButton2 = (Button) view.findViewById(R.id.button2);
            mProgressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        }
    }

    public UpdatesListAdapter(Context context) {
        mContext = context;
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

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, int i) {
        if (mUpdaterController == null) {
            viewHolder.mButton1.setEnabled(false);
            viewHolder.mButton2.setEnabled(false);
            return;
        }

        final String downloadId = mDownloadIds.get(i);
        UpdateDownload update = mUpdaterController.getUpdate(downloadId);
        viewHolder.mName.setText(update.getName());

        viewHolder.mProgressBar.setProgress(update.getProgress());
        boolean indeterminate = update.getStatus() == UpdateStatus.STARTING ||
                update.getStatus() == UpdateStatus.VERIFYING ||
                update.getStatus() == UpdateStatus.INSTALLING;
        viewHolder.mProgressBar.setIndeterminate(indeterminate);

        if (mUpdaterController.isDownloading(downloadId)) {
            setButtonAction(viewHolder.mButton1, Action.PAUSE, downloadId, true);
            viewHolder.mButton2.setEnabled(false);
        } else {
            // Allow one active download
            boolean enabled = !mUpdaterController.hasActiveDownloads() &&
                    Utils.canInstall(update) && !mUpdaterController.isVerifyingUpdate();
            int persistentStatus = update.getPersistentStatus();
            if (persistentStatus == UpdateStatus.Persistent.INCOMPLETE) {
                setButtonAction(viewHolder.mButton1, Action.RESUME, downloadId, enabled);
                setButtonAction(viewHolder.mButton2, Action.CANCEL, downloadId, enabled);
            } else if (persistentStatus == UpdateStatus.Persistent.VERIFIED) {
                setButtonAction(viewHolder.mButton1, Action.INSTALL, downloadId, enabled);
                setButtonAction(viewHolder.mButton2, Action.CANCEL, downloadId, enabled);
            } else {
                setButtonAction(viewHolder.mButton1, Action.DOWNLOAD, downloadId, enabled);
                setButtonAction(viewHolder.mButton2, Action.CANCEL, downloadId, false);
            }
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

    private void setButtonAction(Button button, Action action, final String downloadId,
            boolean enabled) {
        switch (action) {
            case DOWNLOAD:
                button.setText(R.string.download_button);
                button.setEnabled(enabled);
                button.setOnClickListener(!enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mUpdaterController.startDownload(downloadId);
                    }
                });
                break;
            case PAUSE:
                button.setText(R.string.pause_button);
                button.setEnabled(enabled);
                button.setOnClickListener(!enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mUpdaterController.pauseDownload(downloadId);
                    }
                });
                break;
            case RESUME:
                button.setText(R.string.resume_button);
                button.setEnabled(enabled);
                button.setOnClickListener(!enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mUpdaterController.resumeDownload(downloadId);
                    }
                });
                break;
            case CANCEL:
                button.setText(R.string.cancel_button);
                button.setEnabled(enabled);
                button.setOnClickListener(!enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mUpdaterController.cancelDownload(downloadId);
                    }
                });
                break;
            case INSTALL:
                button.setText(R.string.install_button);
                button.setEnabled(enabled);
                button.setOnClickListener(!enabled ? null : new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            Utils.triggerUpdate(mContext,
                                    mUpdaterController.getUpdate(downloadId));
                        } catch (IOException e) {
                            Log.e(TAG, "Could not trigger the update", e);
                            // TODO: show error message
                        }
                    }
                });
                break;
        }
    }
}
