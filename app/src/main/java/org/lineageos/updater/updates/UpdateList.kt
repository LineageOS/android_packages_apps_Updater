/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.settingslib.spa.widget.preference.ZeroStatePreference
import com.android.settingslib.spa.widget.ui.Category
import org.lineageos.updater.R
import org.lineageos.updater.ui.CollapseBar
import org.lineageos.updater.updates.action.UpdateAction
import org.lineageos.updater.updates.state.UpdateItemState

@Composable
fun UpdateList(
    items: List<UpdateItemState>,
    onAction: (UpdateAction, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        ZeroStatePreference(
            icon = Icons.Outlined.Check,
            text = stringResource(R.string.updates_zero_state_title),
            description = stringResource(R.string.updates_zero_state_description),
        )
        return
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var expandedItemIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val activeItems = items.filter { it.progress != null }
    val collapsedVisibleItems =
        (listOf(items.first()) + activeItems).distinctBy { it.downloadId }
    val hiddenItemCount = items.size - collapsedVisibleItems.size
    val visibleItemIds = if (expanded) {
        items.map { it.downloadId }.toSet()
    } else {
        collapsedVisibleItems.map { it.downloadId }.toSet()
    }

    Column(modifier = modifier) {
        Category {
            items.forEach { item ->
                AnimatedVisibility(visible = item.downloadId in visibleItemIds) {
                    val staysExpanded = item.progress != null
                    val itemExpanded = staysExpanded || (item.downloadId in expandedItemIds)
                    val onExpandToggle = if (staysExpanded) {
                        null
                    } else {
                        {
                            expandedItemIds =
                                if (item.downloadId in expandedItemIds) {
                                    expandedItemIds - item.downloadId
                                } else {
                                    expandedItemIds + item.downloadId
                                }
                        }
                    }

                    UpdateItem(
                        state = item,
                        expanded = itemExpanded,
                        onExpandToggle = onExpandToggle,
                        onAction = { action -> onAction(action, item.downloadId) },
                    )
                }
            }
        }

        AnimatedVisibility(visible = hiddenItemCount > 0) {
            CollapseBar(
                expanded = expanded,
                hiddenItemCount = hiddenItemCount,
                onExpandedChange = {
                    expanded = it
                    if (!it) expandedItemIds = emptySet()
                },
            )
        }
    }
}
