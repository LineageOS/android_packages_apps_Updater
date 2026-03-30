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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lineageos.updater.data.AppStateRepository
import org.lineageos.updater.data.Update
import org.lineageos.updater.data.UpdatesRepository
import org.lineageos.updater.util.NetworkMonitor

data class UpdatesUiState(
    val updates: List<Update> = emptyList(),
    val isCheckingForUpdates: Boolean = false,
    val lastCheckedTimestamp: Long = 0L,
    val errorMessage: String? = null,
)

class UpdatesViewModel(
    application: Application,
    private val repository: UpdatesRepository,
    private val appStateRepository: AppStateRepository,
) : AndroidViewModel(application) {

    companion object {
        @JvmStatic
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UpdatesViewModel(
                        application,
                        UpdatesRepository.create(application),
                        AppStateRepository(application),
                    ) as T
            }
    }

    private val _uiState = MutableStateFlow(UpdatesUiState())
    val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()
    val uiStateLive: LiveData<UpdatesUiState> = _uiState.asLiveData()

    private val networkMonitor = NetworkMonitor.getInstance(application)

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
                .filter { it.isOnline }
                .collect { fetchUpdates() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }

    fun fetchUpdates() {
        if (_uiState.value.isCheckingForUpdates) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingForUpdates = true, errorMessage = null) }
            try {
                val fetchedAt = repository.fetchUpdates()
                if (fetchedAt != null) {
                    appStateRepository.setLastCheckedTimestamp(fetchedAt)
                }
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
