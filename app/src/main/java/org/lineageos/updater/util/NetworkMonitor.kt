/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NetworkState(val isOnline: Boolean, val isMetered: Boolean) {
    constructor(capabilities: NetworkCapabilities) : this(
        isOnline = capabilities.hasCapability(NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NET_CAPABILITY_VALIDATED),
        isMetered = !capabilities.hasCapability(NET_CAPABILITY_NOT_METERED),
    )
}

class NetworkMonitor private constructor(context: Context) : ConnectivityManager.NetworkCallback() {

    companion object {
        @Volatile
        private var instance: NetworkMonitor? = null

        @JvmStatic
        fun getInstance(context: Context): NetworkMonitor =
            instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context.applicationContext).also { instance = it }
            }
    }

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _networkState = MutableStateFlow(
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.let { NetworkState(it) }
            ?: NetworkState(isOnline = false, isMetered = true)
    )
    val networkState = _networkState.asStateFlow()

    init {
        connectivityManager.registerDefaultNetworkCallback(this)
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        _networkState.update { NetworkState(networkCapabilities) }
    }

    override fun onLost(network: Network) {
        _networkState.update { NetworkState(isOnline = false, isMetered = true) }
    }
}
