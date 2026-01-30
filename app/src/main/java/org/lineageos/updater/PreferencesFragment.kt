/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.os.Bundle
import android.text.format.DateFormat
import androidx.core.content.edit
import androidx.preference.PreferenceDataStore
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SegmentedButtonPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.DeviceInfoUtils
import org.lineageos.updater.misc.Utils
import java.util.Date

class PreferencesFragment : SettingsBasePreferenceFragment() {

    private var periodicCheckPreference: SwitchPreferenceCompat? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        setupPeriodicCheckSwitch()
        setupPeriodicCheckInterval()
        setupABPerfMode()
        setupUpdateRecovery()
    }

    override fun onResume() {
        super.onResume()
        updateNextCheckSummary()
    }

    private fun setupPeriodicCheckSwitch() {
        periodicCheckPreference =
            findPreference<SwitchPreferenceCompat>(Constants.PREF_PERIODIC_CHECK_ENABLED)?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    if (newValue as Boolean) {
                        UpdatesCheckReceiver.updateRepeatingUpdatesCheck(requireContext())
                    } else {
                        UpdatesCheckReceiver.cancelRepeatingUpdatesCheck(requireContext())
                    }
                    view?.post { updateNextCheckSummary() }
                    true
                }
            }
        updateNextCheckSummary()
    }

    private fun setupPeriodicCheckInterval() {
        findPreference<SegmentedButtonPreference>(Constants.CheckInterval.PREF_KEY)?.apply {
            setupButtons()
            setInitialSelection()
            setClickListener()
        }
    }

    private fun SegmentedButtonPreference.setupButtons() {
        Constants.CheckInterval.entries.forEachIndexed { index, interval ->
            val labelRes = when (interval) {
                Constants.CheckInterval.DAILY ->
                    R.string.menu_auto_updates_check_interval_daily

                Constants.CheckInterval.WEEKLY ->
                    R.string.menu_auto_updates_check_interval_weekly

                Constants.CheckInterval.MONTHLY ->
                    R.string.menu_auto_updates_check_interval_monthly
            }
            val iconRes = when (interval) {
                Constants.CheckInterval.DAILY -> R.drawable.ic_calendar_view_day
                Constants.CheckInterval.WEEKLY -> R.drawable.ic_calendar_view_week
                Constants.CheckInterval.MONTHLY -> R.drawable.ic_calendar_view_month
            }
            setUpButton(index, getString(labelRes), iconRes)
            setButtonVisibility(index, true)
        }
    }

    private fun SegmentedButtonPreference.setInitialSelection() {
        val current = sharedPreferences?.getString(key, null)
        val currentInterval = Constants.CheckInterval.fromValue(context, current)
        setCheckedIndex(currentInterval.ordinal)
    }

    private fun SegmentedButtonPreference.setClickListener() {
        setOnButtonClickListener { group, checkedId, isChecked ->
            if (!isChecked) return@setOnButtonClickListener

            val clickedIndex = (0 until group.childCount).find {
                group.getChildAt(it).id == checkedId
            } ?: return@setOnButtonClickListener

            val selectedInterval = Constants.CheckInterval.entries.getOrNull(clickedIndex)
                ?: return@setOnButtonClickListener

            if (callChangeListener(selectedInterval.value)) {
                sharedPreferences?.edit { putString(key, selectedInterval.value) }
                UpdatesCheckReceiver.updateRepeatingUpdatesCheck(context)
                updateNextCheckSummary()
            }
        }
    }

    private fun updateNextCheckSummary() {
        val context = context ?: return
        val sharedPreferences =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val nextCheckTime = sharedPreferences.getLong(Constants.PREF_NEXT_UPDATE_CHECK, -1)
        periodicCheckPreference?.summary = if (nextCheckTime > 0) {
            val dateFormat = DateFormat.getDateFormat(context)
            val timeFormat = DateFormat.getTimeFormat(context)
            val date = Date(nextCheckTime)
            getString(
                R.string.menu_next_check_time,
                dateFormat.format(date),
                timeFormat.format(date)
            )
        } else {
            getString(R.string.menu_auto_updates_check_summary)
        }
    }

    private fun setupABPerfMode() {
        findPreference<SwitchPreferenceCompat>(Constants.PREF_AB_PERF_MODE)?.apply {
            isVisible = DeviceInfoUtils.isABDevice
            setOnPreferenceChangeListener { _, newValue ->
                Thread {
                    try {
                        val updateEngine = android.os.UpdateEngine()
                        updateEngine.setPerformanceMode(newValue as Boolean)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
                true
            }
        }
    }

    private fun setupUpdateRecovery() {
        findPreference<SwitchPreferenceCompat>(Constants.PREF_UPDATE_RECOVERY)?.apply {
            if (Utils.isRecoveryUpdateExecPresent()) {
                preferenceDataStore = object : PreferenceDataStore() {
                    override fun putBoolean(key: String, value: Boolean) {
                        DeviceInfoUtils.isRecoveryUpdateEnabled = value
                    }

                    override fun getBoolean(key: String, defValue: Boolean): Boolean =
                        DeviceInfoUtils.isRecoveryUpdateEnabled
                }
            } else {
                isVisible = false
            }
        }
    }
}
