/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import androidx.appcompat.app.AppCompatActivity
import org.lineageos.updater.model.UpdateInfo

abstract class UpdatesListActivity : AppCompatActivity() {
    abstract fun exportUpdate(update: UpdateInfo)
    abstract fun showSnackbar(stringId: Int, duration: Int)
}
