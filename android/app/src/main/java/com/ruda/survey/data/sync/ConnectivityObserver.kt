package com.ruda.survey.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ConnectivityObserver(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun observe(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        val currentNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(currentNetwork)
        trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    fun isCurrentlyConnected(): Boolean {
        val currentNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(currentNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
