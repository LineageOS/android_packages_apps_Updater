/*
 * Copyright (C) 2018 The LineageOS Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.model.Changelog;


public class ChangelogActivity extends AppCompatActivity {

    private ChangelogAdapter mAdapter;
    private Changelog mChangelog;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_changelog);

        mChangelog = new Changelog(this);
        mAdapter = new ChangelogAdapter(this, mChangelog.getChanges());
        RecyclerView recyclerView = findViewById(R.id.changelog_recycler_view);
        recyclerView.setAdapter(mAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        findViewById(R.id.load_more).setOnClickListener(view -> loadMore());

        Intent intent = new Intent(this, UpdaterService.class);
        startService(intent);
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
                UpdaterService updaterService = binder.getService();
                mChangelog.setUpdates(updaterService.getUpdaterController().getUpdates());
                unbindService(this);
                mChangelog.startFetching(mChangelogCallback);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        mChangelog.stopFetching();
        super.onStop();
    }

    private Changelog.ChangelogCallback mChangelogCallback = new Changelog.ChangelogCallback() {
        @Override
        public void onEnd() {
            findViewById(R.id.loading_animation).setVisibility(View.INVISIBLE);
            findViewById(R.id.load_more).setEnabled(true);
        }

        @Override
        public void onNewChanges() {
            mAdapter.notifyDataSetChanged();
        }
    };

    private void loadMore() {
        findViewById(R.id.loading_animation).setVisibility(View.VISIBLE);
        findViewById(R.id.load_more).setEnabled(false);
        mChangelog.loadMore(mChangelogCallback);
    }
}
