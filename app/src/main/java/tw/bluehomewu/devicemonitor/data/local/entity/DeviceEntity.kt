package tw.bluehomewu.devicemonitor.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "owner_uid")
    val ownerUid: String,

    @ColumnInfo(name = "device_name")
    val deviceName: String,

    @ColumnInfo(name = "battery_level")
    val batteryLevel: Int,

    @ColumnInfo(name = "is_charging")
    val isCharging: Boolean,

    @ColumnInfo(name = "network_type")
    val networkType: String,

    @ColumnInfo(name = "wifi_ssid")
    val wifiSsid: String?,

    @ColumnInfo(name = "carrier_name")
    val carrierName: String?,

    @ColumnInfo(name = "is_master")
    val isMaster: Boolean,

    @ColumnInfo(name = "alert_threshold")
    val alertThreshold: Int,

    @ColumnInfo(name = "is_online")
    val isOnline: Boolean,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?
)
