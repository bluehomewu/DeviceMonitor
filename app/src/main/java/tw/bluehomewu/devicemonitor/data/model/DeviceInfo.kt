package tw.bluehomewu.devicemonitor.data.model

data class DeviceInfo(
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val networkType: String = "未知",
    val wifiSsid: String? = null,
    val carrierName: String? = null,
    val signalLevel: Int? = null
)
