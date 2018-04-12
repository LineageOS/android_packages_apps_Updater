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
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.lineageos.updater.model.Change;
import org.lineageos.updater.model.Changelog;
import org.lineageos.updater.model.ChangelogEntry;

import java.util.List;

public class ChangelogAdapter extends RecyclerView.Adapter<ChangelogAdapter.ViewHolder> {

    private final List<ChangelogEntry> mChanges;
    private final Context mContext;

    public ChangelogAdapter(Context context, List<ChangelogEntry> changes) {
        mContext = context;
        mChanges = changes;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mProject;
        private TextView mSubject;
        private TextView mBuildName;
        private View mChangeLayout;

        ViewHolder(View view) {
            super(view);
            mProject = view.findViewById(R.id.project);
            mSubject = view.findViewById(R.id.subject);
            mBuildName = view.findViewById(R.id.build_name);
            mChangeLayout = view.findViewById(R.id.change_layout);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.changelog_item_view, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        ChangelogEntry changelogEntry = mChanges.get(position);
        if (changelogEntry instanceof Change) {
            Change change = (Change) changelogEntry;
            viewHolder.mSubject.setText(change.getSubject());
            viewHolder.mProject.setText(change.getProject());
            viewHolder.mBuildName.setVisibility(View.GONE);
            viewHolder.mSubject.setVisibility(View.VISIBLE);
            viewHolder.mProject.setVisibility(View.VISIBLE);
            viewHolder.mChangeLayout.setOnClickListener(view -> {
                Intent openUrl = new Intent(Intent.ACTION_VIEW, Uri.parse(change.getUrl()));
                mContext.startActivity(openUrl);
            });
        } else {
            Changelog.BuildLabel buildLabel = (Changelog.BuildLabel) changelogEntry;
            viewHolder.mBuildName.setText(buildLabel.getLabel());
            viewHolder.mBuildName.setVisibility(View.VISIBLE);
            viewHolder.mSubject.setVisibility(View.GONE);
            viewHolder.mProject.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return mChanges.size();
    }
}
