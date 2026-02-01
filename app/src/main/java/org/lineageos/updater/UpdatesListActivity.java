/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import org.lineageos.updater.model.UpdateInfo;

public abstract class UpdatesListActivity extends CollapsingToolbarBaseActivity {
    public abstract void exportUpdate(UpdateInfo update);
    public abstract void showToast(int stringId, int duration);
}
