package tw.bluehomewu.devicemonitor.di

import android.content.Context
import androidx.room.Room
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import tw.bluehomewu.devicemonitor.BuildConfig
import tw.bluehomewu.devicemonitor.data.local.DeviceDatabase
import tw.bluehomewu.devicemonitor.data.local.dao.DeviceDao
import tw.bluehomewu.devicemonitor.data.remote.DeviceRepository
import tw.bluehomewu.devicemonitor.data.remote.RealtimeRepository

/**
 * 應用程式層級的 singleton 依賴（手動 DI）。
 * 由 DeviceMonitorApplication.onCreate() 呼叫 initialize(context)。
 */
object AppModule {

    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    // Room database — 需先呼叫 initialize()
    private lateinit var _database: DeviceDatabase

    val deviceDao: DeviceDao
        get() = _database.deviceDao()

    val deviceRepository: DeviceRepository by lazy { DeviceRepository(supabase) }

    val realtimeRepository: RealtimeRepository by lazy {
        RealtimeRepository(supabase, deviceDao)
    }

    fun initialize(context: Context) {
        _database = Room.databaseBuilder(
            context.applicationContext,
            DeviceDatabase::class.java,
            "device_monitor.db"
        )
            .fallbackToDestructiveMigration()   // 開發期間允許破壞性遷移
            .build()
    }
}
