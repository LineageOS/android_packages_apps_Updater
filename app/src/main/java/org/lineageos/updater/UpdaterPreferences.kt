/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.os.SystemProperties
import android.text.format.DateFormat

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.SegmentedButtonPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

import org.lineageos.updater.controller.UpdaterService
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.Utils

import java.util.Date

class UpdaterPreferences : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, PrefsFragment())
                .commit()
        }
    }

    class PrefsFragment : SettingsBasePreferenceFragment() {
        private var updaterService: UpdaterService? = null

        private val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                val binder = service as UpdaterService.LocalBinder
                updaterService = binder.service
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                updaterService = null
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            setPreferencesFromResource(R.xml.preferences, rootKey)

            setupPerfModePreference()
            setupRecoveryUpdatePreference()
            setupAutoCheckPreference(prefs)
            setupCheckIntervalPreference(prefs)
        }

        override fun onStart() {
            super.onStart()
            val intent = Intent(requireContext(), UpdaterService::class.java)
            requireContext().bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }

        override fun onStop() {
            if (updaterService != null) {
                requireContext().unbindService(serviceConnection)
            }
            super.onStop()
        }

        private fun setupPerfModePreference() {
            findPreference<SwitchPreferenceCompat>(Constants.PREF_AB_PERF_MODE)?.apply {
                if (Utils.isABDevice()) {
                    setOnPreferenceChangeListener { _, newValue ->
                        updaterService?.updaterController?.setPerformanceMode(newValue as Boolean)
                        true
                    }
                } else {
                    isVisible = false
                }
            }
        }

        private fun setupRecoveryUpdatePreference() {
            findPreference<SwitchPreferenceCompat>(Constants.PREF_UPDATE_RECOVERY)?.apply {
                if (Utils.isRecoveryUpdateExecPresent()) {
                    isChecked = SystemProperties.getBoolean(
                        Constants.UPDATE_RECOVERY_PROPERTY, false
                    )
                    setOnPreferenceChangeListener { _, newValue ->
                        SystemProperties.set(
                            Constants.UPDATE_RECOVERY_PROPERTY,
                            (newValue as Boolean).toString()
                        )
                        true
                    }
                } else {
                    isVisible = false
                }
            }
        }

        private fun setupAutoCheckPreference(prefs: SharedPreferences) {
            val autoCheckPref = findPreference<SwitchPreferenceCompat>("auto_updates_check_enabled")
            autoCheckPref?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val intervalValue = if (enabled) {
                    Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY
                } else {
                    Constants.AUTO_UPDATES_CHECK_INTERVAL_NEVER
                }
                prefs.edit {
                    putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL, intervalValue)
                }
                UpdatesCheckWorker.updateSchedule(requireContext())
                true
            }

            WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(UpdatesCheckWorker.WORK_NAME)
                .observe(this) { workInfos ->
                    if (workInfos.isNullOrEmpty()) {
                        return@observe
                    }
                    val workInfo = workInfos[0]
                    if (workInfo.state == WorkInfo.State.ENQUEUED) {
                        val nextRunTime = workInfo.nextScheduleTimeMillis
                        if (nextRunTime != Long.MAX_VALUE && nextRunTime > 0) {
                            val date = Date(nextRunTime)
                            val timeFormat = DateFormat.getTimeFormat(requireContext())
                            val dateFormat = DateFormat.getDateFormat(requireContext())
                            val formatted = "${dateFormat.format(date)} ${timeFormat.format(date)}"
                            autoCheckPref?.summary = getString(
                                R.string.pref_auto_updates_check_interval_summary_next_check,
                                formatted
                            )
                        } else {
                            autoCheckPref?.setSummary(
                                R.string.pref_auto_updates_check_interval_summary
                            )
                        }
                    } else if (workInfo.state == WorkInfo.State.CANCELLED) {
                        autoCheckPref?.setSummary(R.string.pref_auto_updates_check_interval_summary)
                    }
                }
        }

        private fun setupCheckIntervalPreference(prefs: SharedPreferences) {
            findPreference<SegmentedButtonPreference>(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL)
                ?.apply {
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
                            prefs.edit {
                                putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL, newValue)
                            }
                            UpdatesCheckWorker.updateSchedule(requireContext())
                        }
                    }
                }
        }
    }
}
