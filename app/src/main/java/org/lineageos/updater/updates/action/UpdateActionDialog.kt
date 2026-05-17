/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates.action

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import com.android.settingslib.spa.widget.dialog.AlertDialogButton
import com.android.settingslib.spa.widget.dialog.SettingsAlertDialogWithIcon
import org.lineageos.updater.R

data class AlertDialogState(
    val onConfirm: () -> Unit = {},
    val showDismiss: Boolean = false,
    val title: String,
    val text: AnnotatedString,
)

@Composable
fun UpdateActionDialog(
    dialog: AlertDialogState,
    onDismiss: () -> Unit,
) {
    SettingsAlertDialogWithIcon(
        onDismissRequest = onDismiss,
        icon = ImageVector.vectorResource(R.drawable.ic_notification),
        confirmButton = AlertDialogButton(text = stringResource(android.R.string.ok)) {
            onDismiss()
            dialog.onConfirm()
        },
        dismissButton = if (dialog.showDismiss) {
            AlertDialogButton(text = stringResource(android.R.string.cancel)) {
                onDismiss()
            }
        } else {
            null
        },
        title = dialog.title,
        text = { Text(text = dialog.text) },
    )
}
