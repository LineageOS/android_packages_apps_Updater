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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

class NetworkMonitor(context: Context, coroutineScope: CoroutineScope) {
    data class NetworkState(val isOnline: Boolean, val isMetered: Boolean) {
        constructor(capabilities: NetworkCapabilities) : this(
            isOnline = capabilities.hasCapability(NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NET_CAPABILITY_VALIDATED),
            isMetered = !capabilities.hasCapability(NET_CAPABILITY_NOT_METERED),
        )
    }

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val defaultNetworkState = NetworkState(isOnline = false, isMetered = true)

    val currentNetworkState: NetworkState
        get() = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.let { NetworkState(it) }
            ?: defaultNetworkState

    val networkState: SharedFlow<NetworkState> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(NetworkState(networkCapabilities))
            }

            override fun onLost(network: Network) {
                trySend(defaultNetworkState)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        trySend(currentNetworkState)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = defaultNetworkState
    )
}
