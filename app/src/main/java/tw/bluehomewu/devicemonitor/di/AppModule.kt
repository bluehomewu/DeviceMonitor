package tw.bluehomewu.devicemonitor.di

import android.content.Context
import android.provider.Settings
import io.github.jan.supabase.SupabaseClient
import tw.bluehomewu.devicemonitor.auth.GoogleAuthManager
import tw.bluehomewu.devicemonitor.auth.SessionBackupManager
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import tw.bluehomewu.devicemonitor.BuildConfig
import tw.bluehomewu.devicemonitor.data.local.GroupUidManager
import tw.bluehomewu.devicemonitor.data.local.PinnedOrderManager
import tw.bluehomewu.devicemonitor.data.memory.DeviceStateHolder
import tw.bluehomewu.devicemonitor.data.remote.DeviceRepository
import tw.bluehomewu.devicemonitor.data.remote.PairingRepository
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
            install(Auth) {
                // 明確啟用 session 持久化與自動刷新，
                // 避免背景刷新失敗後 refresh token 被清空
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Realtime)
        }
    }

    private lateinit var _appContext: Context

    /** ANDROID_ID：每台實體裝置唯一，作為 Supabase devices 表的衝突識別鍵。 */
    val thisDeviceId: String by lazy {
        Settings.Secure.getString(_appContext.contentResolver, Settings.Secure.ANDROID_ID)
    }

    /** 記憶體中的裝置狀態快取（取代 Room）。SharedPreferences 用於跨 process 重啟的持久化。 */
    val deviceStateHolder: DeviceStateHolder by lazy {
        DeviceStateHolder(_appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))
    }

    val deviceRepository: DeviceRepository by lazy { DeviceRepository(supabase) }

    val googleAuthManager: GoogleAuthManager by lazy {
        GoogleAuthManager(_appContext, supabase)
    }

    val sessionBackupManager: SessionBackupManager by lazy {
        SessionBackupManager(supabase, _appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))
    }

    val alertNotificationManager: AlertNotificationManager by lazy {
        AlertNotificationManager(_appContext, deviceStateHolder, thisDeviceId)
    }

    val pinnedOrderManager: PinnedOrderManager by lazy { PinnedOrderManager(_appContext) }
    val groupUidManager: GroupUidManager by lazy { GroupUidManager(_appContext) }
    val pairingRepository: PairingRepository by lazy { PairingRepository(supabase) }

    val realtimeRepository: RealtimeRepository by lazy {
        RealtimeRepository(supabase, deviceStateHolder, alertNotificationManager)
    }

    fun initialize(context: Context) {
        _appContext = context.applicationContext
    }
}
