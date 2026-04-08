package tw.bluehomewu.devicemonitor.di

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import tw.bluehomewu.devicemonitor.auth.GoogleAuthManager
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import tw.bluehomewu.devicemonitor.BuildConfig
import tw.bluehomewu.devicemonitor.data.memory.DeviceStateHolder
import tw.bluehomewu.devicemonitor.data.remote.DeviceRepository
import tw.bluehomewu.devicemonitor.data.remote.RealtimeRepository
import tw.bluehomewu.devicemonitor.service.AlertNotificationManager

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

    private lateinit var _appContext: Context

    /** 記憶體中的裝置狀態快取（取代 Room）。SharedPreferences 用於跨 process 重啟的持久化。 */
    val deviceStateHolder: DeviceStateHolder by lazy {
        DeviceStateHolder(_appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))
    }

    val deviceRepository: DeviceRepository by lazy { DeviceRepository(supabase) }

    val googleAuthManager: GoogleAuthManager by lazy {
        GoogleAuthManager(_appContext, supabase)
    }

    val alertNotificationManager: AlertNotificationManager by lazy {
        AlertNotificationManager(_appContext)
    }

    val realtimeRepository: RealtimeRepository by lazy {
        RealtimeRepository(supabase, deviceStateHolder, alertNotificationManager)
    }

    fun initialize(context: Context) {
        _appContext = context.applicationContext
    }
}
