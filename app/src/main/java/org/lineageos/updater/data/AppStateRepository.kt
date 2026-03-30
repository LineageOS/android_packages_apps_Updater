/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val APP_STATE_DATASTORE_NAME = "app_state"

private val Context.appStatePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = APP_STATE_DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)

private object AppStatePreferencesKeys {
    val LAST_CHECKED_TIMESTAMP = longPreferencesKey("last_checked_timestamp")
}

class AppStateRepository(context: Context) {

    private val appState = context.applicationContext.appStatePreferencesDataStore

    val lastCheckedTimestampFlow: Flow<Long> = appState.data.map { preferences ->
        preferences[AppStatePreferencesKeys.LAST_CHECKED_TIMESTAMP] ?: 0L
    }

    suspend fun getLastCheckedTimestamp(): Long = lastCheckedTimestampFlow.first()

    suspend fun setLastCheckedTimestamp(timestamp: Long) {
        appState.edit { preferences ->
            preferences[AppStatePreferencesKeys.LAST_CHECKED_TIMESTAMP] = timestamp
        }
    }
}
