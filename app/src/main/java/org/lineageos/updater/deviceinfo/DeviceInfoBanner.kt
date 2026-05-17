/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.deviceinfo

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsRadius
import com.android.settingslib.spa.framework.theme.SettingsShape.CornerExtraLarge1
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.lineageos.updater.deviceinfo.actions.DeviceInfoActionButtons
import org.lineageos.updater.deviceinfo.actions.DeviceInfoTvAction
import org.lineageos.updater.misc.StringGenerator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun DeviceInfoBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = remember { StringGenerator.getCurrentLocale(context) }

    val buildVersion = remember { DeviceInfoUtils.buildVersion }
    val androidVersion = remember { DeviceInfoUtils.androidVersion }
    val buildDate = remember {
        val instant = Instant.ofEpochSecond(DeviceInfoUtils.buildDateTimestamp)
        DateTimeFormatter.ofPattern("MMM d", locale).format(instant.atZone(ZoneOffset.UTC))
    }
    val securityPatch = remember {
        val patchDate = LocalDate.parse(DeviceInfoUtils.buildSecurityPatch)
        DateTimeFormatter.ofPattern("MMM yyyy", locale).format(patchDate)
    }

    DeviceInfoBanner(
        buildVersion = buildVersion,
        androidVersion = androidVersion,
        buildDate = buildDate,
        securityPatch = securityPatch,
        modifier = modifier,
    )
}

@Composable
fun DeviceInfoBanner(
    buildVersion: String,
    androidVersion: String,
    buildDate: String,
    securityPatch: String,
    modifier: Modifier = Modifier,
) {
    val uiMode = LocalConfiguration.current.uiMode
    val isTv = (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SettingsDimension.itemPadding)
    ) {
        UpdaterCard(
            buildVersion = buildVersion,
            androidVersion = androidVersion,
            buildDate = buildDate,
            securityPatch = securityPatch,
            modifier = Modifier.fillMaxWidth(),
            shape = if (isTv) {
                CornerExtraLarge1
            } else {
                CornerExtraLarge1.copy(
                    bottomStart = CornerSize(SettingsRadius.extraSmall2),
                    bottomEnd = CornerSize(SettingsRadius.extraSmall2),
                )
            },
        )

        if (!isTv) {
            Spacer(modifier = Modifier.height(SettingsDimension.paddingTiny))
            DeviceInfoActionButtons()
        } else {
            DeviceInfoTvAction()
        }
    }
}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES
)
@UiModePreviews
@Composable
private fun DeviceInfoBannerPreview() {
    SettingsTheme {
        DeviceInfoBanner(
            buildVersion = "23.2",
            androidVersion = "16",
            buildDate = "Feb 20",
            securityPatch = "Feb 2026",
        )
    }
}
