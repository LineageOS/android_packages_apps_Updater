/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import org.lineageos.updater.updatescheck.UpdatesCheckWorker

private const val USER_PREFERENCES_DATASTORE_NAME = "user_prefs"

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES_DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)

private object UserPreferencesKeys {
    val AB_PERF_MODE = booleanPreferencesKey("ab_perf_mode")
    val AUTO_DELETE = booleanPreferencesKey("auto_delete_updates")
    val CHECK_INTERVAL = stringPreferencesKey("check_interval")
    val METERED_NETWORK_WARNING = booleanPreferencesKey("metered_network_warning")
    val PERIODIC_CHECK_ENABLED = booleanPreferencesKey("periodic_check_enabled")
}

class UserPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val userPreferences = appContext.userPreferencesDataStore
    private val userPreferencesFlow = userPreferences.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    val abPerfModeFlow: Flow<Boolean> = userPreferencesFlow.map { preferences ->
        preferences[UserPreferencesKeys.AB_PERF_MODE] ?: false
    }

    suspend fun getAbPerfMode(): Boolean = abPerfModeFlow.first()

    suspend fun setAbPerfMode(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.AB_PERF_MODE] = value }
    }

    val autoDeleteFlow: Flow<Boolean> = userPreferencesFlow.map { preferences ->
        preferences[UserPreferencesKeys.AUTO_DELETE] ?: true
    }

    suspend fun getAutoDelete(): Boolean = autoDeleteFlow.first()

    suspend fun setAutoDelete(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.AUTO_DELETE] = value }
    }

    val meteredNetworkWarningFlow: Flow<Boolean> = userPreferencesFlow.map { preferences ->
        preferences[UserPreferencesKeys.METERED_NETWORK_WARNING] ?: true
    }

    suspend fun getMeteredNetworkWarning(): Boolean = meteredNetworkWarningFlow.first()

    suspend fun setMeteredNetworkWarning(value: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.METERED_NETWORK_WARNING] = value }
    }

    val checkIntervalFlow: Flow<CheckInterval> = userPreferencesFlow.map { preferences ->
        CheckInterval.fromStorageValue(preferences[UserPreferencesKeys.CHECK_INTERVAL])
    }

    suspend fun getCheckInterval(): CheckInterval = checkIntervalFlow.first()

    suspend fun setCheckInterval(interval: CheckInterval) {
        userPreferences.edit { it[UserPreferencesKeys.CHECK_INTERVAL] = interval.storageValue }
        if (getPeriodicCheckEnabled()) {
            UpdatesCheckWorker.reschedulePeriodicCheck(appContext)
        }
    }

    val periodicCheckEnabledFlow: Flow<Boolean> = userPreferencesFlow.map { preferences ->
        preferences[UserPreferencesKeys.PERIODIC_CHECK_ENABLED] ?: true
    }

    suspend fun getPeriodicCheckEnabled(): Boolean = periodicCheckEnabledFlow.first()

    suspend fun setPeriodicCheckEnabled(enabled: Boolean) {
        userPreferences.edit { it[UserPreferencesKeys.PERIODIC_CHECK_ENABLED] = enabled }
        if (enabled) {
            UpdatesCheckWorker.schedulePeriodicCheck(appContext)
        } else {
            UpdatesCheckWorker.cancelPeriodicCheck(appContext)
        }
    }

    fun getRecoveryUpdateEnabled(): Boolean = DeviceInfoUtils.isRecoveryUpdateEnabled

    fun setRecoveryUpdateEnabled(enabled: Boolean) {
        DeviceInfoUtils.isRecoveryUpdateEnabled = enabled
    }

    companion object {
        @JvmStatic
        fun getAbPerfModeBlocking(context: Context): Boolean = runBlocking {
            UserPreferencesRepository(context).abPerfModeFlow.first()
        }

        @JvmStatic
        fun getAutoDeleteBlocking(context: Context): Boolean = runBlocking {
            UserPreferencesRepository(context).autoDeleteFlow.first()
        }

        @JvmStatic
        fun getMeteredNetworkWarningBlocking(context: Context): Boolean = runBlocking {
            UserPreferencesRepository(context).meteredNetworkWarningFlow.first()
        }
    }
}
