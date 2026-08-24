package xyz.mpv.rex.feature.webshare

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

object WebShareManager {

  enum class NetworkType {
    HOTSPOT,
    WIFI,
    NONE
  }

  data class WebShareState(
    val isRunning: Boolean = false,
    val ipAddress: String? = null,
    val port: Int = 8080,
    val token: String = "",
    val serverUrl: String? = null,
    val files: List<WebShareServer.ShareableFile> = emptyList(),
    val networkType: NetworkType = NetworkType.NONE,
  )

  private val _state = MutableStateFlow(WebShareState())
  val state: StateFlow<WebShareState> = _state.asStateFlow()

  internal var activeServer: WebShareServer? = null

  fun startSharing(context: Context, files: List<WebShareServer.ShareableFile>) {
    if (files.isEmpty()) return

    val (ip, networkType) = getLocalIpAddress(context)
    val port = findAvailablePort(8080)
    val token = UUID.randomUUID().toString().substring(0, 6)
    val fullUrl = if (ip != null) "http://$ip:$port/?token=$token" else "http://localhost:$port/?token=$token"

    _state.value = WebShareState(
      isRunning = true,
      ipAddress = ip,
      port = port,
      token = token,
      serverUrl = fullUrl,
      files = files,
      networkType = networkType
    )

    val serviceIntent = Intent(context, WebShareService::class.java).apply {
      action = WebShareService.ACTION_START
    }
    context.startForegroundService(serviceIntent)
  }

  fun stopSharing(context: Context) {
    val serviceIntent = Intent(context, WebShareService::class.java).apply {
      action = WebShareService.ACTION_STOP
    }
    context.startService(serviceIntent)

    activeServer?.stop()
    activeServer = null

    _state.value = WebShareState(isRunning = false)
  }

  internal fun updateServerState(server: WebShareServer?) {
    activeServer = server
  }

  fun refreshNetworkState(context: Context) {
    if (!_state.value.isRunning) return
    val (ip, networkType) = getLocalIpAddress(context)
    val port = _state.value.port
    val token = _state.value.token
    val fullUrl = if (ip != null) "http://$ip:$port/?token=$token" else null

    _state.value = _state.value.copy(
      ipAddress = ip,
      networkType = networkType,
      serverUrl = fullUrl
    )
  }

  private fun getLocalIpAddress(context: Context): Pair<String?, NetworkType> {
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return Pair(null, NetworkType.NONE)
      val interfaceList = interfaces.toList()

      // 1. Check for Hotspot (common interface names: ap0, wlan1, swlan0, rndis0, etc. or IP 192.168.43.1)
      for (intf in interfaceList) {
        val name = intf.name.lowercase()
        if (intf.isUp && !intf.isLoopback) {
          for (addr in intf.inetAddresses) {
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
              val ip = addr.hostAddress ?: continue
              if (name.contains("ap") || name.contains("softap") || ip.startsWith("192.168.43.")) {
                return Pair(ip, NetworkType.HOTSPOT)
              }
            }
          }
        }
      }

      // 2. Check for Wi-Fi (wlan0)
      for (intf in interfaceList) {
        val name = intf.name.lowercase()
        if (intf.isUp && !intf.isLoopback && name.contains("wlan")) {
          for (addr in intf.inetAddresses) {
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
              val ip = addr.hostAddress
              if (ip != null) {
                return Pair(ip, NetworkType.WIFI)
              }
            }
          }
        }
      }

      // 3. Fallback to any non-loopback IPv4
      for (intf in interfaceList) {
        if (intf.isUp && !intf.isLoopback) {
          for (addr in intf.inetAddresses) {
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
              val ip = addr.hostAddress
              if (ip != null) {
                return Pair(ip, NetworkType.WIFI)
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      // Ignore
    }

    return Pair(null, NetworkType.NONE)
  }

  private fun findAvailablePort(startPort: Int): Int {
    for (port in startPort..(startPort + 10)) {
      try {
        java.net.ServerSocket(port).use {
          return port
        }
      } catch (e: Exception) {
        // Port taken, try next
      }
    }
    return startPort
  }
}
