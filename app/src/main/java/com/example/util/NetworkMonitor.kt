package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors real-time internet connectivity for VYBE's online-first UX.
 * Emits [isOnline] updates whenever Wi-Fi / Cellular validated internet state changes.
 */
class NetworkMonitor private constructor(context: Context) {

  private val appContext = context.applicationContext
  private val connectivityManager =
    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  private val _isOnline = MutableStateFlow(checkCurrentlyOnline(appContext))
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private var onReconnectCallback: (() -> Unit)? = null

  init {
    registerCallback()
  }

  fun setOnReconnectListener(listener: () -> Unit) {
    onReconnectCallback = listener
  }

  fun refreshState(): Boolean {
    val current = checkCurrentlyOnline(appContext)
    val wasOffline = !_isOnline.value
    _isOnline.value = current
    if (wasOffline && current) {
      onReconnectCallback?.invoke()
    }
    return current
  }

  private fun registerCallback() {
    val cm = connectivityManager ?: return
    try {
      val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

      cm.registerNetworkCallback(
        request,
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            val wasOffline = !_isOnline.value
            _isOnline.value = true
            if (wasOffline) {
              onReconnectCallback?.invoke()
            }
          }

          override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
          ) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val wasOffline = !_isOnline.value
            _isOnline.value = hasInternet
            if (wasOffline && hasInternet) {
              onReconnectCallback?.invoke()
            }
          }

          override fun onLost(network: Network) {
            _isOnline.value = checkCurrentlyOnline(appContext)
          }
        }
      )
    } catch (_: Exception) {
      // Fallback to snapshot checks when ConnectivityManager callbacks are unavailable in JVM tests
    }
  }

  companion object {
    @Volatile
    private var instance: NetworkMonitor? = null

    fun getInstance(context: Context): NetworkMonitor {
      return instance ?: synchronized(this) {
        instance ?: NetworkMonitor(context).also { instance = it }
      }
    }

    fun get(): NetworkMonitor? = instance

    fun checkCurrentlyOnline(context: Context): Boolean {
      return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
          ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      } catch (_: Exception) {
        true
      }
    }
  }
}
