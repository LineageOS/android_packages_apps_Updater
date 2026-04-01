/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.scaffold.MoreOptionsAction
import com.android.settingslib.spa.widget.scaffold.MoreOptionsScope

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionBar(
    buttons: List<ActionBarButton>,
    menuContent: (@Composable MoreOptionsScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SettingsDimension.buttonPadding),
        horizontalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { button ->
            when (button) {
                is ActionBarButton.Tonal -> FilledTonalButton(
                    onClick = button.onClick,
                    shapes = ButtonDefaults.shapes(),
                    enabled = button.enabled,
                ) {
                    Text(button.text)
                }

                is ActionBarButton.Outlined -> OutlinedButton(
                    onClick = button.onClick,
                    shapes = ButtonDefaults.shapes(),
                    enabled = button.enabled,
                ) {
                    Text(button.text)
                }

                is ActionBarButton.Icon -> FilledIconButton(
                    onClick = button.onClick,
                    shapes = IconButtonDefaults.shapes(),
                    enabled = button.enabled,
                ) {
                    Icon(
                        imageVector = button.imageVector,
                        contentDescription = button.contentDescription,
                        modifier = Modifier.size(SettingsDimension.itemIconSize),
                    )
                }

            }
        }

        if (menuContent != null) {
            Spacer(Modifier.weight(1f))
            MoreOptionsAction(content = menuContent)
        }
    }
}

sealed interface ActionBarButton {
    val enabled: Boolean
    val onClick: () -> Unit

    data class Tonal(
        val text: String,
        override val enabled: Boolean = true,
        override val onClick: () -> Unit,
    ) : ActionBarButton

    data class Outlined(
        val text: String,
        override val enabled: Boolean = true,
        override val onClick: () -> Unit,
    ) : ActionBarButton

    data class Icon(
        val imageVector: ImageVector,
        val contentDescription: String? = null,
        override val enabled: Boolean = true,
        override val onClick: () -> Unit,
    ) : ActionBarButton
}

@UiModePreviews
@Composable
private fun ActionBarDownloadPreview() {
    SettingsTheme {
        ActionBar(
            buttons = listOf(
                ActionBarButton.Tonal(text = "Download") {},
            ),
            menuContent = {
                MenuItem(text = "View downloads") {}
            },
        )
    }
}

@UiModePreviews
@Composable
private fun ActionBarDownloadingPreview() {
    SettingsTheme {
        ActionBar(
            buttons = listOf(
                ActionBarButton.Outlined(text = "Cancel") {},
                ActionBarButton.Icon(
                    imageVector = Icons.Outlined.Pause,
                    contentDescription = "Pause",
                ) {},
            ),
            menuContent = {
                MenuItem(text = "View downloads") {}
            },
        )
    }
}

@UiModePreviews
@Composable
private fun ActionBarPausedPreview() {
    SettingsTheme {
        ActionBar(
            buttons = listOf(
                ActionBarButton.Outlined(text = "Cancel") {},
                ActionBarButton.Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Resume",
                ) {},
            ),
            menuContent = {
                MenuItem(text = "Delete") {}
                MenuItem(text = "View downloads") {}
            },
        )
    }
}

@UiModePreviews
@Composable
private fun ActionBarInstallPreview() {
    SettingsTheme {
        ActionBar(
            buttons = listOf(
                ActionBarButton.Tonal(text = "Install") {},
            ),
            menuContent = {
                MenuItem(text = "Delete") {}
                MenuItem(text = "Export update") {}
            },
        )
    }
}
