/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lineageos.updater.data.Update

class UpdatesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    data class UiState(
        val updates: List<Update> = emptyList(),
        val isCheckingForUpdates: Boolean = false,
        val lastCheckedTimestamp: Long = 0L,
        val errorMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val uiStateLive: LiveData<UiState> = _uiState.asLiveData()

    private val updaterApplication = getApplication<UpdaterApplication>()
    private val repository = updaterApplication.updatesRepository
    private val networkMonitor = updaterApplication.networkMonitor

    init {
        viewModelScope.launch {
            repository.observeLocalUpdates().collect { updates ->
                _uiState.update { it.copy(updates = updates) }
            }
        }

        viewModelScope.launch {
            networkMonitor.networkState
                .distinctUntilChangedBy { it.isOnline }
                .filter { it.isOnline }
                .collect { fetchUpdates() }
        }
    }

    fun fetchUpdates() {
        if (_uiState.value.isCheckingForUpdates) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingForUpdates = true, errorMessage = null) }
            try {
                _uiState.update { it.copy(isCheckingForUpdates = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingForUpdates = false, errorMessage = e.message
                    )
                }
            }
        }
    }

    fun errorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
