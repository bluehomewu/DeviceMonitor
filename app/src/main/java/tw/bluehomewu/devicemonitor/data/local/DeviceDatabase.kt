package tw.bluehomewu.devicemonitor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import tw.bluehomewu.devicemonitor.data.local.dao.DeviceDao
import tw.bluehomewu.devicemonitor.data.local.entity.DeviceEntity

@Database(
    entities = [DeviceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DeviceDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
}
