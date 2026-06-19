/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.lineageos.updater.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme

private val IconSize = 96.dp

@Composable
fun UpdateZeroStatePreference(
    modifier: Modifier = Modifier,
    text: String = "",
    description: String = "",
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            tint = colorScheme.primary,
        )
        Column(
            modifier = Modifier.padding(
                start = SettingsSpace.medium5,
                top = SettingsSpace.small4,
                end = SettingsSpace.medium5,
                bottom = SettingsSpace.small1,
            ),
            verticalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@UiModePreviews
@Composable
private fun UpdateZeroStatePreferencePreview() {
    SettingsTheme {
        UpdateZeroStatePreference(
            text = "No updates available",
            description = "New builds for this device will appear here when available.",
        )
    }
}
