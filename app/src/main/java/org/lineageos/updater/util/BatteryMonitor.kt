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
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.lineageos.updater.misc.Constants

data class BatteryState(
    val level: Int,
    val isAcCharging: Boolean,
    val isCharging: Boolean,
) {

    companion object {
        const val MIN_BATT_PCT_CHARGING = 20
        const val MIN_BATT_PCT_DISCHARGING = 30
    }

    // Battery saver can trigger at up to 20% — keep thresholds at or above that
    // so an installation is never attempted while the device may be in a power-restricted state.
    val isLevelOk: Boolean
        get() = level >= if (isCharging) MIN_BATT_PCT_CHARGING else MIN_BATT_PCT_DISCHARGING
}

class BatteryMonitor private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: BatteryMonitor? = null

        @JvmStatic
        fun getInstance(context: Context): BatteryMonitor =
            instance ?: synchronized(this) {
                instance ?: BatteryMonitor(context.applicationContext).also { instance = it }
            }

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

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val _batteryState = MutableStateFlow(
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { fromIntent(it) }
            ?: BATTERYLESS_STATE
    )
    val batteryState = _batteryState.asStateFlow()

    // Null on non-AB devices where update_engine is absent.
    private val updateEngine: UpdateEngine? = try {
        UpdateEngine()
    } catch (_: Exception) {
        null
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = fromIntent(intent)
            val prev = _batteryState.value
            _batteryState.update { state }
            // On AC, maximize background updates; otherwise respect the user preference.
            // Guard on isAcCharging change to avoid redundant IPC on every battery level tick.
            if (state.isAcCharging != prev.isAcCharging) {
                try {
                    updateEngine?.setPerformanceMode(
                        state.isAcCharging || prefs.getBoolean(Constants.PREF_AB_PERF_MODE, false)
                    )
                } catch (_: Throwable) {
                    // Ignored
                }
            }
        }
    }

    init {
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
}
