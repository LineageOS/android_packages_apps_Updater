/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsShape
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.ui.LinearProgressBar
import org.lineageos.updater.updates.action.UpdateAction
import org.lineageos.updater.updates.action.UpdateActionType
import org.lineageos.updater.updates.action.UpdateActions
import org.lineageos.updater.updates.button.ActionBar
import org.lineageos.updater.updates.button.ActionBarButton
import org.lineageos.updater.updates.state.ProgressState
import org.lineageos.updater.updates.state.UpdateItemState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateItem(
    state: UpdateItemState,
    expanded: Boolean,
    onExpandToggle: (() -> Unit)?,
    onAction: (UpdateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SettingsShape.CornerExtraSmall2)
            .background(MaterialTheme.colorScheme.surfaceBright)
    ) {
        key(state.downloadId) {
            Preference(model = object : PreferenceModel {
                override val title = state.buildDate
                override val summary = {
                    buildString {
                        append(state.buildVersion)
                        if (state.status.isNotEmpty()) {
                            append(" • ")
                            append(state.status)
                        }
                    }
                }
                override val onClick = onExpandToggle
                override val icon = if (!expanded) {
                    @Composable {
                        if (state.isLocal) {
                            Icon(
                                imageVector = Icons.Outlined.Archive,
                                contentDescription = null,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                            )
                        }
                    }
                } else {
                    null
                }
            })
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Column(
                    modifier = Modifier.padding(horizontal = SettingsDimension.itemPaddingStart),
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        LocalTextStyle provides MaterialTheme.typography.bodySmall,
                    ) {
                        when (val progress = state.progress) {
                            is ProgressState.Determinate -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(MaterialTheme.typography.titleLarge.toSpanStyle()) {
                                                append(progress.percent.toInt().toString())
                                            }
                                            append("%")
                                        },
                                        modifier = Modifier.alignByBaseline(),
                                    )

                                    if (progress.downloadedSize.isNotEmpty()) {
                                        Text(
                                            text = " · ${progress.downloadedSize}",
                                            modifier = Modifier
                                                .weight(1f)
                                                .alignByBaseline(),
                                        )
                                    }

                                    if (progress.eta.isNotEmpty()) {
                                        Text(
                                            text = progress.eta,
                                            modifier = Modifier.alignByBaseline(),
                                        )
                                    }
                                }
                                LinearProgressBar(progress = progress.percent / 100f)
                            }

                            ProgressState.Indeterminate -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            null -> {
                                Text(text = state.fileSize)
                            }
                        }
                    }
                }

                ActionBar(
                    buttons = listOfNotNull(
                        state.actions.secondary?.toActionBarButton(context, onAction),
                        state.actions.primary.toActionBarButton(
                            context,
                            onAction,
                            isPrimary = true
                        ),
                    ),
                    menuContent = if (state.actions.overflow.isEmpty()) {
                        null
                    } else {
                        {
                            state.actions.overflow.forEach { action ->
                                MenuItem(
                                    text = action.type.title(context),
                                    enabled = action.enabled,
                                ) {
                                    onAction(action)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun UpdateAction.toActionBarButton(
    context: Context,
    onAction: (UpdateAction) -> Unit,
    isPrimary: Boolean = false,
): ActionBarButton = when (type) {
    UpdateActionType.PAUSE_DOWNLOAD,
    UpdateActionType.PAUSE_INSTALL -> ActionBarButton.Icon(
        imageVector = Icons.Outlined.Pause,
        contentDescription = type.title(context),
        enabled = enabled,
        onClick = { onAction(this) },
    )

    UpdateActionType.RESUME_DOWNLOAD,
    UpdateActionType.RESUME_INSTALL -> ActionBarButton.Icon(
        imageVector = Icons.Outlined.PlayArrow,
        contentDescription = type.title(context),
        enabled = enabled,
        onClick = { onAction(this) },
    )

    else -> if (isPrimary) {
        ActionBarButton.Tonal(
            text = type.title(context),
            enabled = enabled,
            onClick = { onAction(this) },
        )
    } else {
        ActionBarButton.Outlined(
            text = type.title(context),
            enabled = enabled,
            onClick = { onAction(this) },
        )
    }
}

@UiModePreviews
@Composable
private fun UpdateItemIdleCollapsedPreview() {
    SettingsTheme {
        UpdateItem(
            state = UpdateItemState(
                downloadId = "preview",
                buildDate = "April 27, 2026",
                buildVersion = "LineageOS 23.2",
                status = "",
                isLocal = false,
                fileSize = "1.1 GB",
                progress = null,
                actions = UpdateActions(
                    primary = UpdateAction(
                        type = UpdateActionType.START_DOWNLOAD,
                    ),
                    overflow = listOf(
                        UpdateAction(
                            type = UpdateActionType.VIEW_DOWNLOADS,
                        ),
                    ),
                ),
            ),
            expanded = false,
            onExpandToggle = {},
            onAction = {},
        )
    }
}

@UiModePreviews
@Composable
private fun UpdateItemIdleExpandedPreview() {
    SettingsTheme {
        UpdateItem(
            state = UpdateItemState(
                downloadId = "preview",
                buildDate = "April 27, 2026",
                buildVersion = "LineageOS 23.2",
                status = "",
                isLocal = false,
                fileSize = "1.1 GB",
                progress = null,
                actions = UpdateActions(
                    primary = UpdateAction(
                        type = UpdateActionType.START_DOWNLOAD,
                    ),
                    overflow = listOf(
                        UpdateAction(
                            type = UpdateActionType.VIEW_DOWNLOADS,
                        ),
                    ),
                ),
            ),
            expanded = true,
            onExpandToggle = {},
            onAction = {},
        )
    }
}

@UiModePreviews
@Composable
private fun UpdateItemDownloadingPreview() {
    SettingsTheme {
        UpdateItem(
            state = UpdateItemState(
                downloadId = "preview",
                buildDate = "April 27, 2026",
                buildVersion = "LineageOS 23.2",
                status = "Downloading",
                isLocal = false,
                fileSize = "1.1 GB",
                progress = ProgressState.Determinate(
                    percent = 65f,
                    downloadedSize = "715 MB of 1.1 GB",
                    eta = "3 min left",
                ),
                actions = UpdateActions(
                    primary = UpdateAction(
                        type = UpdateActionType.PAUSE_DOWNLOAD,
                    ),
                    secondary = UpdateAction(
                        type = UpdateActionType.CANCEL_DOWNLOAD,
                    ),
                    overflow = listOf(
                        UpdateAction(
                            type = UpdateActionType.VIEW_DOWNLOADS,
                        ),
                    ),
                ),
            ),
            expanded = true,
            onExpandToggle = {},
            onAction = {},
        )
    }
}
