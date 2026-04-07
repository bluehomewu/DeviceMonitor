package tw.bluehomewu.devicemonitor.data.collector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

data class NetworkInfo(
    val networkType: String,
    val wifiSsid: String?,
    val carrierName: String?
)

class NetworkCollector(private val context: Context) {

    fun observe(): Flow<NetworkInfo> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        var latestDisplayInfo: TelephonyDisplayInfo? = null

        fun hasPermission(perm: String) =
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

        fun currentNetworkInfo(): NetworkInfo {
            val active = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(active)

            return when {
                caps == null -> NetworkInfo("無連線", null, null)

                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val ssid = if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        @Suppress("DEPRECATION")
                        val raw = wm.connectionInfo?.ssid
                        if (raw != null && raw != WifiManager.UNKNOWN_SSID)
                            raw.removePrefix("\"").removeSuffix("\"")
                        else null
                    } else null
                    NetworkInfo("Wi-Fi", ssid, null)
                }

                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val type = determineCellularType(tm, latestDisplayInfo)
                    val carrier = tm.networkOperatorName.takeIf { it.isNotEmpty() }
                    NetworkInfo(type, null, carrier)
                }

                else -> NetworkInfo("其他", null, null)
            }
        }

        // ConnectivityManager callback
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentNetworkInfo()) }
            override fun onLost(network: Network) { trySend(currentNetworkInfo()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(currentNetworkInfo())
            }
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)

        // PhoneStateListener 的無參建構子內部需要 Looper（呼叫 Looper.myLooper()）。
        // 在 Dispatchers.IO 背景執行緒沒有 Looper 會 NPE，故切換至主執行緒建立並註冊。
        @Suppress("DEPRECATION")
        val phoneStateListener = withContext(Dispatchers.Main) {
            object : PhoneStateListener() {
                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    latestDisplayInfo = telephonyDisplayInfo
                    trySend(currentNetworkInfo())
                }
            }
        }

        if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            withContext(Dispatchers.Main) {
                @Suppress("DEPRECATION")
                tm.listen(phoneStateListener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
            }
        }

        // Emit initial state
        trySend(currentNetworkInfo())

        awaitClose {
            cm.unregisterNetworkCallback(networkCallback)
            // 同樣需要在有 Looper 的執行緒取消監聽
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                @Suppress("DEPRECATION")
                tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    // Logic mirrors CLAUDE.md spec: NR_NSA → 5G NSA, NR_ADVANCED → 5G SA, networkType NR → 5G SA
    private fun determineCellularType(tm: TelephonyManager, displayInfo: TelephonyDisplayInfo?): String {
        if (displayInfo != null) {
            return when (displayInfo.overrideNetworkType) {
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> "5G NSA"
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G SA"
                else -> when (displayInfo.networkType) {
                    TelephonyManager.NETWORK_TYPE_NR  -> "5G SA"
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    else                              -> "4G"
                }
            }
        }

        val hasPhoneState = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasPhoneState) {
            @Suppress("DEPRECATION")
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR  -> "5G SA"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                else                              -> "4G"
            }
        } else "行動網路"
    }
}
