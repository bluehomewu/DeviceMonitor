package tw.bluehomewu.devicemonitor.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import tw.bluehomewu.devicemonitor.BuildConfig

/**
 * 應用程式層級的 singleton 依賴。
 * 不使用 Hilt；各元件直接取用此物件的屬性。
 */
object AppModule {

    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
