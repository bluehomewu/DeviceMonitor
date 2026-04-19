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
import androidx.annotation.RequiresApi
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

    /**
     * 最後一次成功取得的 WiFi SSID。
     * Android 10+ 在螢幕關閉後 WifiManager.getConnectionInfo().ssid 回傳 UNKNOWN_SSID，
     * 此快取讓上傳時仍能帶出正確 SSID，避免 DB 欄位被 null 覆蓋。
     * onLost 時清除，確保換連 WiFi 後不會殘留舊 SSID。
     */
    @Volatile private var lastKnownSsid: String? = null

    fun observe(): Flow<NetworkInfo> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // TelephonyDisplayInfo is API 30+; kept as Any? so the field itself has no API-level requirement
        var latestDisplayInfo: Any? = null

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
                        if (raw != null && raw != WifiManager.UNKNOWN_SSID) {
                            // 螢幕開啟且有位置權限：取得有效 SSID 並更新快取
                            raw.removePrefix("\"").removeSuffix("\"").also { lastKnownSsid = it }
                        } else {
                            // 螢幕關閉或系統限制：回傳快取，避免 DB 欄位被 null 覆蓋
                            lastKnownSsid
                        }
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
            override fun onLost(network: Network) {
                lastKnownSsid = null  // 換連或斷線時清除快取，避免殘留舊 SSID
                trySend(currentNetworkInfo())
            }
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
                @RequiresApi(Build.VERSION_CODES.R)
                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    latestDisplayInfo = telephonyDisplayInfo
                    trySend(currentNetworkInfo())
                }
            }
        }

        // LISTEN_DISPLAY_INFO_CHANGED is API 30+; on API 29 we fall back to tm.dataNetworkType
        if (hasPermission(Manifest.permission.READ_PHONE_STATE) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
    // displayInfo is Any? (TelephonyDisplayInfo on API 30+, null on API 29)
    private fun determineCellularType(tm: TelephonyManager, displayInfo: Any?): String {
        if (displayInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = displayInfo as TelephonyDisplayInfo
            return when (info.overrideNetworkType) {
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> "5G NSA"
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G SA"
                else -> when (info.networkType) {
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
