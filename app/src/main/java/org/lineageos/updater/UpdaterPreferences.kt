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
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.os.SystemProperties
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.GroupSectionDividerMixin
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarAppCompatActivity
import org.lineageos.updater.controller.UpdaterService
import org.lineageos.updater.misc.Constants
import org.lineageos.updater.misc.Utils
import androidx.core.content.edit

class UpdaterPreferences : CollapsingToolbarAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_SubSettingsBase)
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, PrefsFragment())
                .commit()
        }

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.support_action_bar)?.let {
            val insetStart = resources.getDimensionPixelSize(R.dimen.settingslib_expressive_space_large3)
            it.setContentInsetStartWithNavigation(insetStart)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class PrefsFragment : PreferenceFragmentCompat() {
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
            preferenceManager.preferenceDataStore = SharedPrefsDataStore(prefs)
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

            val updateRecovery = findPreference<SwitchPreferenceCompat>(Constants.PREF_UPDATE_RECOVERY)
            if (Utils.isRecoveryUpdateExecPresent()) {
                // Initialize with SystemProperties value since this one is special
                updateRecovery?.isChecked = SystemProperties.getBoolean(
                    Constants.UPDATE_RECOVERY_PROPERTY, false
                )
                updateRecovery?.setOnPreferenceChangeListener { _, newValue ->
                    SystemProperties.set(
                        Constants.UPDATE_RECOVERY_PROPERTY,
                        (newValue as Boolean).toString()
                    )
                    true
                }
            } else {
                updateRecovery?.isVisible = false
            }

            // Handle the switch preference for enabling/disabling auto-check
            val autoCheckEnabled = findPreference<SwitchPreferenceCompat>("auto_updates_check_enabled")
            autoCheckEnabled?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val intervalValue = if (enabled) {
                    // When enabling, use weekly as default
                    val storedValue = prefs.getInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL, Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY)
                    if (storedValue == Constants.AUTO_UPDATES_CHECK_INTERVAL_NEVER) {
                        Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY
                    } else {
                        storedValue
                    }
                } else {
                    Constants.AUTO_UPDATES_CHECK_INTERVAL_NEVER
                }
                prefs.edit { putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL, intervalValue) }
                UpdatesCheckWorker.updateSchedule(requireContext())
                true
            }

            // Handle the interval button preference
            findPreference<AutoUpdatesCheckIntervalPreference>(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL)?.apply {
                setOnPreferenceChangeListener { _, _ ->
                    UpdatesCheckWorker.updateSchedule(requireContext())
                    true
                }
            }
        }

        override fun onStart() {
            super.onStart()
            val intent = android.content.Intent(requireContext(), UpdaterService::class.java)
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        override fun onStop() {
            if (updaterService != null) {
                requireContext().unbindService(serviceConnection)
            }
            super.onStop()
        }
    }

    // Bridge between Int-based preferences and String-based ListPreference
    class SharedPrefsDataStore(private val prefs: SharedPreferences) : PreferenceDataStore() {
        override fun getString(key: String, defValue: String?): String? {
            if (key == Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL) {
                return prefs.getInt(key, Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY).toString()
            }
            return prefs.getString(key, defValue)
        }

        override fun putString(key: String, value: String?) {
            if (key == Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL) {
                value?.toIntOrNull()?.let {
                    prefs.edit { putInt(key, it) }
                }
                return
            }
            prefs.edit { putString(key, value) }
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return prefs.getBoolean(key, defValue)
        }

        override fun putBoolean(key: String, value: Boolean) {
            prefs.edit { putBoolean(key, value) }
        }

        override fun getInt(key: String, defValue: Int): Int {
            return prefs.getInt(key, defValue)
        }

        override fun putInt(key: String, value: Int) {
            prefs.edit { putInt(key, value) }
        }
    }

}

class AutoUpdatesCheckIntervalPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {

    private var buttonGroup: MaterialButtonToggleGroup? = null

    init {
        layoutResource = R.layout.preference_auto_updates_check
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedBelow = false
        holder.isDividerAllowedAbove = false

        buttonGroup = holder.findViewById(R.id.button_group) as? MaterialButtonToggleGroup
        buttonGroup?.let { group ->
            group.clearOnButtonCheckedListeners()

            val value = getPersistedInt(Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY)
            val checkedId = when (value) {
                Constants.AUTO_UPDATES_CHECK_INTERVAL_DAILY -> R.id.button_day
                Constants.AUTO_UPDATES_CHECK_INTERVAL_MONTHLY -> R.id.button_month
                else -> R.id.button_week
            }
            group.check(checkedId)

            group.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val newValue = when (checkedId) {
                        R.id.button_day -> Constants.AUTO_UPDATES_CHECK_INTERVAL_DAILY
                        R.id.button_month -> Constants.AUTO_UPDATES_CHECK_INTERVAL_MONTHLY
                        else -> Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY
                    }
                    if (callChangeListener(newValue)) {
                        persistInt(newValue)
                    }
                }
            }
        }
    }
}
