package tw.bluehomewu.devicemonitor.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tw.bluehomewu.devicemonitor.data.local.entity.DeviceEntity

@Dao
interface DeviceDao {

    /** 觀察同帳號所有裝置，依裝置名稱排序；Flow 在資料變動時自動重新發射。 */
    @Query("SELECT * FROM devices WHERE owner_uid = :ownerUid ORDER BY device_name ASC")
    fun observeAll(ownerUid: String): Flow<List<DeviceEntity>>

    /** Insert or update（以 primary key `id` 判斷）。 */
    @Upsert
    suspend fun upsert(device: DeviceEntity)

    /** 批次 upsert（初始載入時使用）。 */
    @Upsert
    suspend fun upsertAll(devices: List<DeviceEntity>)

    /** 刪除指定裝置（Realtime DELETE 事件時呼叫）。 */
    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 登出時清除本帳號所有快取。 */
    @Query("DELETE FROM devices WHERE owner_uid = :ownerUid")
    suspend fun deleteAll(ownerUid: String)
}
