/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater;

import androidx.appcompat.app.AppCompatActivity;

import org.lineageos.updater.data.Update;

public abstract class UpdatesListActivity extends AppCompatActivity {
    public abstract void exportUpdate(Update update);
    public abstract void showSnackbar(int stringId, int duration);
}
