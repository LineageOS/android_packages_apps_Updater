/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.spa.widget.preference.ListPreference
import com.android.settingslib.spa.widget.preference.ListPreferenceModel
import com.android.settingslib.spa.widget.preference.ListPreferenceOption
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.scaffold.RegularScaffold
import com.android.settingslib.spa.widget.ui.Category
import kotlinx.coroutines.launch
import org.lineageos.updater.R
import org.lineageos.updater.UpdaterApplication
import org.lineageos.updater.data.CheckInterval
import org.lineageos.updater.data.UserPreferencesRepository
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import org.lineageos.updater.util.BatteryMonitor
import java.io.File

@Composable
fun PreferencesScreen() {
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as UpdaterApplication }
    val repository = application.userPreferencesRepository
    val batteryMonitor = application.batteryMonitor
    val isABDevice = remember { DeviceInfoUtils.isABDevice }
    val showRecoveryUpdate = remember { installRecoveryScriptExists() }
    RegularScaffold(title = stringResource(R.string.display_name)) {
        PreferencesContent(repository, batteryMonitor, isABDevice, showRecoveryUpdate)
    }
}

@Composable
private fun PreferencesContent(
    repository: UserPreferencesRepository,
    batteryMonitor: BatteryMonitor,
    isABDevice: Boolean,
    showRecoveryUpdate: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    val abPerfMode by repository.abPerfModeFlow.collectAsStateWithLifecycle(false)
    val batteryState by batteryMonitor.batteryState.collectAsStateWithLifecycle(
        batteryMonitor.currentBatteryState
    )
    val autoDelete by repository.autoDeleteFlow.collectAsStateWithLifecycle(true)
    val checkInterval by repository.checkIntervalFlow.collectAsStateWithLifecycle(CheckInterval.default)
    val meteredNetworkWarning by repository.meteredNetworkWarningFlow.collectAsStateWithLifecycle(
        true
    )
    val periodicCheckEnabled by repository.periodicCheckEnabledFlow.collectAsStateWithLifecycle(true)
    var recoveryUpdateEnabled by remember { mutableStateOf(repository.getRecoveryUpdateEnabled()) }

    val autoUpdatesCheckSummary = stringResource(R.string.menu_auto_updates_check_summary)
    val autoDeleteUpdatesSummary = stringResource(R.string.menu_auto_delete_updates_summary)
    val meteredNetworkWarningSummary = stringResource(R.string.menu_metered_network_warning_summary)
    val abPerfModeSummary = stringResource(R.string.menu_ab_perf_mode_summary)
    val abPerfModeChargingSummary = stringResource(R.string.menu_ab_perf_mode_summary_charging)
    val updateRecoverySummary = stringResource(R.string.menu_update_recovery_summary)
    val selectedCheckInterval = remember(checkInterval) {
        object : IntState {
            override val intValue = checkInterval.ordinal
        }
    }

    Category(title = stringResource(R.string.pref_category_background_sync)) {
        SwitchPreference(object : SwitchPreferenceModel {
            override val title = stringResource(R.string.menu_auto_updates_check)
            override val summary = { autoUpdatesCheckSummary }
            override val checked = { periodicCheckEnabled }
            override val onCheckedChange: (Boolean) -> Unit = { value ->
                coroutineScope.launch { repository.setPeriodicCheckEnabled(value) }
            }
        })

        ListPreference(object : ListPreferenceModel {
            override val title = stringResource(R.string.menu_auto_updates_check_interval)
            override val enabled = { periodicCheckEnabled }
            override val options = listOf(
                ListPreferenceOption(
                    id = CheckInterval.DAILY.ordinal,
                    text = stringResource(R.string.time_unit_day),
                ),
                ListPreferenceOption(
                    id = CheckInterval.WEEKLY.ordinal,
                    text = stringResource(R.string.time_unit_week),
                ),
                ListPreferenceOption(
                    id = CheckInterval.MONTHLY.ordinal,
                    text = stringResource(R.string.time_unit_month),
                ),
            )
            override val selectedId = selectedCheckInterval
            override val onIdSelected: (Int) -> Unit = { id ->
                val interval = CheckInterval.entries.getOrElse(id) { CheckInterval.default }
                coroutineScope.launch { repository.setCheckInterval(interval) }
            }
        })
    }

    Category(title = stringResource(R.string.pref_category_download_install)) {
        SwitchPreference(object : SwitchPreferenceModel {
            override val title = stringResource(R.string.menu_auto_delete_updates)
            override val summary = { autoDeleteUpdatesSummary }
            override val checked = { autoDelete }
            override val onCheckedChange: (Boolean) -> Unit = { value ->
                coroutineScope.launch { repository.setAutoDelete(value) }
            }
        })

        SwitchPreference(object : SwitchPreferenceModel {
            override val title = stringResource(R.string.menu_metered_network_warning)
            override val summary = { meteredNetworkWarningSummary }
            override val checked = { meteredNetworkWarning }
            override val onCheckedChange: (Boolean) -> Unit = { value ->
                coroutineScope.launch { repository.setMeteredNetworkWarning(value) }
            }
        })

        if (isABDevice) {
            SwitchPreference(object : SwitchPreferenceModel {
                override val title = stringResource(R.string.menu_ab_perf_mode)
                override val summary = {
                    if (batteryState.isAcCharging) {
                        abPerfModeChargingSummary
                    } else {
                        abPerfModeSummary
                    }
                }
                override val changeable = { !batteryState.isAcCharging }
                override val checked = { batteryState.isAcCharging || abPerfMode }
                override val onCheckedChange: (Boolean) -> Unit = { value ->
                    coroutineScope.launch { repository.setAbPerfMode(value) }
                }
            })
        }

        if (showRecoveryUpdate) {
            SwitchPreference(object : SwitchPreferenceModel {
                override val title = stringResource(R.string.menu_update_recovery)
                override val summary = { updateRecoverySummary }
                override val checked = { recoveryUpdateEnabled }
                override val onCheckedChange: (Boolean) -> Unit = { value ->
                    recoveryUpdateEnabled = value
                    repository.setRecoveryUpdateEnabled(value)
                }
            })
        }
    }
}

private fun installRecoveryScriptExists() = File("/vendor/bin/install-recovery.sh").exists()
