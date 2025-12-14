/*
 * Copyright (C) 2019-2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.updater

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.SystemProperties
import androidx.core.content.edit
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.SegmentedButtonPreference
import org.lineageos.updater.controller.UpdaterService
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.Utils

import com.android.settingslib.widget.SettingsBasePreferenceFragment

class UpdaterPreferences : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(
                    R.id.content_frame,
                    PrefsFragment()
                ).commit()
        }
    }

    class PrefsFragment : SettingsBasePreferenceFragment() {
        private var updaterService: UpdaterService? = null
        private val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                val binder = service as UpdaterService.LocalBinder
                updaterService = binder.service
                // Update bindings if needed
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                updaterService = null
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val abPerfMode = findPreference<SwitchPreferenceCompat>(Constants.PREF_AB_PERF_MODE)
            if (!Utils.isABDevice()) {
                abPerfMode?.isVisible = false
            } else {
                abPerfMode?.setOnPreferenceChangeListener { _, newValue ->
                    updaterService?.updaterController?.setPerformanceMode(newValue as Boolean)
                    true
                }
            }

            val updateRecovery =
                findPreference<SwitchPreferenceCompat>(Constants.PREF_UPDATE_RECOVERY)
            if (Utils.isRecoveryUpdateExecPresent()) {
                // Initialize with SystemProperties value since this one is special
                updateRecovery?.isChecked = SystemProperties.getBoolean(
                    Constants.UPDATE_RECOVERY_PROPERTY, false
                )
                updateRecovery?.setOnPreferenceChangeListener { _, newValue ->
                    SystemProperties.set(
                        Constants.UPDATE_RECOVERY_PROPERTY, (newValue as Boolean).toString()
                    )
                    true
                }
            } else {
                updateRecovery?.isVisible = false
            }

            // Handle the switch preference for enabling/disabling auto-check
            val autoCheckEnabled =
                findPreference<SwitchPreferenceCompat>("auto_updates_check_enabled")
            autoCheckEnabled?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val intervalValue = if (enabled) {
                    Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY
                } else {
                    Constants.AUTO_UPDATES_CHECK_INTERVAL_NEVER
                }
                prefs.edit { putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL, intervalValue) }
                UpdatesCheckWorker.updateSchedule(requireContext())
                true
            }

            // Handle the interval button preference
            findPreference<SegmentedButtonPreference>(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL)?.apply {
                setUpButton(
                    0,
                    getString(R.string.pref_auto_updates_check_interval_button_daily),
                    R.drawable.ic_calendar_view_day
                )
                setUpButton(
                    1,
                    getString(R.string.pref_auto_updates_check_interval_button_weekly),
                    R.drawable.ic_calendar_view_week
                )
                setUpButton(
                    2,
                    getString(R.string.pref_auto_updates_check_interval_button_monthly),
                    R.drawable.ic_calendar_view_month
                )

                setButtonVisibility(0, true)
                setButtonVisibility(1, true)
                setButtonVisibility(2, true)

                val storedValue = prefs.getInt(
                    Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL,
                    Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY
                )
                val checkedIndex = when (storedValue) {
                    Constants.AUTO_UPDATES_CHECK_INTERVAL_DAILY -> 0
                    Constants.AUTO_UPDATES_CHECK_INTERVAL_MONTHLY -> 2
                    else -> 1
                }
                setCheckedIndex(checkedIndex)

                setOnButtonClickListener { _, _, isChecked ->
                    if (isChecked) {
                        val newValue = when (getCheckedIndex()) {
                            0 -> Constants.AUTO_UPDATES_CHECK_INTERVAL_DAILY
                            2 -> Constants.AUTO_UPDATES_CHECK_INTERVAL_MONTHLY
                            else -> Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY
                        }
                        prefs.edit { putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL, newValue) }
                        UpdatesCheckWorker.updateSchedule(requireContext())
                    }
                }
            }
        }

        override fun onStart() {
            super.onStart()
            val intent = Intent(requireContext(), UpdaterService::class.java)
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        override fun onStop() {
            if (updaterService != null) {
                requireContext().unbindService(serviceConnection)
            }
            super.onStop()
        }
    }
}
