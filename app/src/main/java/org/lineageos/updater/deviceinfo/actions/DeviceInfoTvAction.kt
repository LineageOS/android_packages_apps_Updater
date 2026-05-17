/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.deviceinfo.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.ui.SettingsBody
import org.lineageos.updater.R

@Composable
fun DeviceInfoTvAction(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SettingsDimension.itemPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(SettingsDimension.paddingLarge))
        SettingsBody(stringResource(R.string.header_review_updates))
        Text(
            text = stringResource(R.string.header_download_url),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(SettingsDimension.paddingLarge))
        SettingsBody(stringResource(R.string.found_bug))
        Spacer(modifier = Modifier.height(SettingsDimension.paddingLarge))
        QrCodeReportIssues()
    }
}

@UiModePreviews
@Composable
private fun DeviceInfoTvActionPreview() {
    SettingsTheme {
        DeviceInfoTvAction()
    }
}
