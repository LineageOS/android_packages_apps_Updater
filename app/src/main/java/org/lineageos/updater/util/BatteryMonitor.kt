/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.UpdateEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.lineageos.updater.data.UserPreferencesRepository

class BatteryMonitor(
    private val appContext: Context,
    coroutineScope: CoroutineScope,
    userPreferencesRepository: UserPreferencesRepository,
) {
    data class BatteryState(
        val level: Int,
        val isAcCharging: Boolean,
        val isCharging: Boolean,
    ) {
        // Battery saver can trigger at up to 20%, so keep thresholds at or above that
        // to avoid installing while the device may be in a power-restricted state.
        val isLevelOk: Boolean
            get() = level >= if (isCharging) MIN_BATT_PCT_CHARGING else MIN_BATT_PCT_DISCHARGING

        companion object {
            const val MIN_BATT_PCT_CHARGING = 20
            const val MIN_BATT_PCT_DISCHARGING = 30
        }
    }

    val currentBatteryState: BatteryState
        get() = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { fromIntent(it) }
            ?: BATTERYLESS_STATE

    // Null on non-AB devices where update_engine is absent.
    private val updateEngine: UpdateEngine? = try {
        UpdateEngine()
    } catch (_: Exception) {
        null
    }

    private val abPerfMode = userPreferencesRepository.abPerfModeFlow
        .onEach {
            try {
                updateEngine?.setPerformanceMode(currentBatteryState.isAcCharging || it)
            } catch (_: Throwable) {
                // Ignored
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val batteryState: SharedFlow<BatteryState> = callbackFlow {
        var previousState = currentBatteryState

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = fromIntent(intent)
                // On AC, maximize background updates; otherwise respect the user preference.
                // Guard on isAcCharging change to avoid redundant IPC on every battery level tick.
                if (state.isAcCharging != previousState.isAcCharging) {
                    try {
                        updateEngine?.setPerformanceMode(state.isAcCharging || abPerfMode.value)
                    } catch (_: Throwable) {
                        // Ignored
                    }
                }
                previousState = state
                trySend(state)
            }
        }

        appContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        trySend(previousState)

        awaitClose {
            appContext.unregisterReceiver(receiver)
        }
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = currentBatteryState
    )

    companion object {
        private val BATTERYLESS_STATE =
            BatteryState(level = 100, isAcCharging = false, isCharging = false)

        private fun fromIntent(intent: Intent): BatteryState {
            if (!intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)) {
                return BATTERYLESS_STATE
            }

            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale

            val chargePlug: Int = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val isAcCharging: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_AC

            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL

            return BatteryState(
                level = batteryPct,
                isAcCharging = isAcCharging,
                isCharging = isCharging
            )
        }
    }
}
