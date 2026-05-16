package tw.bluehomewu.devicemonitor

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import tw.bluehomewu.devicemonitor.BuildConfig
import tw.bluehomewu.devicemonitor.di.AppModule
import tw.bluehomewu.devicemonitor.service.DeviceMonitorService
import tw.bluehomewu.devicemonitor.service.ServiceWatchdogWorker
import java.util.concurrent.TimeUnit

class DeviceMonitorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)
        initFirebase()

        // APK 更新後 Service 會被殺掉；若使用者先前已啟動監控，自動重啟
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("service_enabled", false)) {
            try {
                startForegroundService(Intent(this, DeviceMonitorService::class.java))
            } catch (e: RuntimeException) {
                android.util.Log.w("DeviceMonitorApp", "Application 自動重啟 Service 失敗（FGS 限制）：${e.message}")
            }
        }

        // WorkManager 看門狗：每 15 分鐘確認 Service 仍在執行，否則重啟
        // KEEP 策略確保不會重複排程
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ServiceWatchdogWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    /**
     * 以程式碼初始化 Firebase，無需 google-services.json。
     * 在 local.properties 填入以下四個欄位後即可啟用 FCM：
     *   FIREBASE_APP_ID=1:PROJECT_NUMBER:android:APP_HASH
     *   FIREBASE_API_KEY=AIza...
     *   FIREBASE_SENDER_ID=PROJECT_NUMBER
     *   FIREBASE_PROJECT_ID=my-project-id
     */
    private fun initFirebase() {
        if (BuildConfig.FIREBASE_APP_ID.isBlank()) return
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        runCatching {
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build()
            FirebaseApp.initializeApp(this, options)
            Log.i("DeviceMonitorApp", "Firebase 初始化成功")
        }.onFailure { Log.w("DeviceMonitorApp", "Firebase 初始化失敗：${it.message}") }
    }
}
