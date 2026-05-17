/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lineageos.updater.data.Update
import org.lineageos.updater.updatescheck.UpdatesCheckModel
import org.lineageos.updater.updatescheck.UpdatesCheckState

data class UpdatesUiState(
    val updates: List<Update> = emptyList(),
    val isCheckingForUpdates: Boolean = false,
    val isOnline: Boolean = true,
    val lastCheckedTimestamp: Long = 0L,
    val hasUpdateCheckFailed: Boolean = false,
) {
    val updatesCheckModel = UpdatesCheckModel(
        state = when {
            isCheckingForUpdates -> UpdatesCheckState.Checking
            !isOnline -> UpdatesCheckState.NoInternet
            hasUpdateCheckFailed -> UpdatesCheckState.Error
            else -> UpdatesCheckState.Idle
        },
        lastCheckedTimestamp = lastCheckedTimestamp,
        canCheckForUpdates = isOnline && !isCheckingForUpdates,
    )
}

class UpdatesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UpdatesUiState())
    val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()
    val uiStateLive: LiveData<UpdatesUiState> = _uiState.asLiveData()

    private val updaterApplication = getApplication<UpdaterApplication>()
    private val repository = updaterApplication.updatesRepository
    private val appStateRepository = updaterApplication.appStateRepository
    private val networkMonitor = updaterApplication.networkMonitor

    init {
        viewModelScope.launch {
            appStateRepository.lastCheckedTimestampFlow.collect { ts ->
                _uiState.update { it.copy(lastCheckedTimestamp = ts) }
            }
        }

        viewModelScope.launch {
            repository.observeLocalUpdates().collect { updates ->
                _uiState.update { it.copy(updates = updates) }
            }
        }

        viewModelScope.launch {
            networkMonitor.networkState
                .distinctUntilChangedBy { it.isOnline }
                .onEach { networkState ->
                    _uiState.update { it.copy(isOnline = networkState.isOnline) }
                }
                .filter { it.isOnline }
                .collect { fetchUpdates() }
        }
    }

    fun fetchUpdates() {
        if (_uiState.value.isCheckingForUpdates) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingForUpdates = true,
                    hasUpdateCheckFailed = false,
                )
            }
            try {
                val fetchedAt = repository.fetchUpdates()
                if (fetchedAt != null) {
                    appStateRepository.setLastCheckedTimestamp(fetchedAt)
                }
                _uiState.update { it.copy(isCheckingForUpdates = false) }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingForUpdates = false,
                        hasUpdateCheckFailed = true,
                    )
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UpdatesViewModel(application) as T
            }
    }
}
