/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.deviceinfo.actions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.settingslib.qrcode.QrCodeGenerator
import org.lineageos.updater.R

private val QrCodeSize = 200.dp

@Composable
fun QrCodeReportIssues(modifier: Modifier = Modifier) {
    val url = stringResource(R.string.report_issue_url)
    val isDark = isSystemInDarkTheme()
    val sizePx = with(LocalDensity.current) { QrCodeSize.roundToPx() }

    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier
                .size(QrCodeSize)
                .background(if (isDark) Color.White else Color.Black),
        )
    } else {
        val bitmap = remember(url, isDark, sizePx) {
            QrCodeGenerator.encodeQrCode(url, sizePx, isDark).asImageBitmap()
        }
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.report_issues),
            modifier = modifier.size(QrCodeSize),
        )
    }
}
