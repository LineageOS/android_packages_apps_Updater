/*
 * Copyright (C) 2024 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.lineageos.updater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.lineageos.updater.R

/**
 * Local copy of SettingsLib's private CollapseBar so Updater can reuse the same expand/collapse
 * affordance without depending on TopIntroPreference internals.
 */
@Composable
fun CollapseBar(
    expanded: Boolean,
    hiddenItemCount: Int,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text =
        if (expanded) {
            stringResource(R.string.see_less)
        } else {
            stringResource(R.string.see_more, hiddenItemCount)
        }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!expanded) }
            .padding(
                top = SettingsSpace.extraSmall4,
                bottom = SettingsSpace.small1,
                start = SettingsSpace.small4,
                end = SettingsSpace.small4,
            ),
    ) {
        Icon(
            imageVector =
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .size(SettingsDimension.itemIconSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(SettingsSpace.extraSmall4))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@UiModePreviews
@Composable
private fun CollapseBarCollapsedPreview() {
    SettingsTheme {
        CollapseBar(
            expanded = false,
            hiddenItemCount = 3,
            onExpandedChange = {},
        )
    }
}

@UiModePreviews
@Composable
private fun CollapseBarExpandedPreview() {
    SettingsTheme {
        CollapseBar(
            expanded = true,
            hiddenItemCount = 3,
            onExpandedChange = {},
        )
    }
}
