# AGENTS.md — DeviceMonitor 程式碼導覽

## 架構總覽

Android 應用程式（Kotlin + Jetpack Compose），透過 **Supabase**（Postgres + Realtime WebSocket + Auth）即時監控 20+ 台裝置。無需自架後端。

```
Collectors → DeviceMonitorService → Supabase（upsert）
                                       ↓ Realtime WebSocket
                               DeviceStateHolder（StateFlow）
                                       ↓
                               UI（collectAsStateWithLifecycle）
```

關鍵目錄：
- `data/collector/` — BatteryCollector、NetworkCollector（事件驅動 Flow）
- `data/remote/` — DeviceRepository（upsert/fetch）、RealtimeRepository（WebSocket）
- `data/memory/DeviceStateHolder.kt` — 記憶體快取 + SharedPreferences 持久化（**取代 Room**）
- `di/AppModule.kt` — 手動 DI singleton（無 Hilt）
- `service/DeviceMonitorService.kt` — Foreground Service，核心上傳迴圈
- `auth/GoogleAuthManager.kt` — Credential Manager → Supabase Google OAuth

## 手動 DI — 禁止使用 Hilt

Hilt 因與 AGP 9.x Built-in Kotlin 不相容（`Android BaseExtension not found`）而移除。**請勿在此專案加入 Hilt 或 KSP**。

所有 singleton 集中在 `AppModule`（object），由 `DeviceMonitorApplication.onCreate()` 呼叫 `initialize(context)` 初始化。ViewModel 採手動 factory：

```kotlin
fun factory(): ViewModelProvider.Factory = viewModelFactory {
    initializer { DeviceListViewModel(AppModule.supabase, AppModule.deviceStateHolder, ...) }
}
```

## 機密與 Build Config

機密值存於 `local.properties`（不提交版控），在編譯時注入：

```kotlin
// app/build.gradle.kts
buildConfigField("String", "SUPABASE_URL", "\"${localProps["SUPABASE_URL"] ?: ""}\"")
buildConfigField("String", "SUPABASE_ANON_KEY", ...)
buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", ...)
```

使用 `BuildConfig.SUPABASE_URL` 等存取，**絕對不可硬編碼**。

## 資料模型：DeviceRow vs DeviceRecord

兩個獨立類別，分別對應寫入與讀取：
- **`DeviceRow`**（寫入 / upsert）— nullable 欄位**不加 Kotlin 預設值**；這迫使 supabase-kt 一律序列化 `null`，確保切換網路時舊的 `wifi_ssid` 等欄位能被清除。
- **`DeviceRecord`**（讀取 / Realtime）— 包含完整欄位，如 `id`、`is_master`、`alert_threshold`。

Upsert 衝突鍵：`owner_uid, device_name`（由 DB `UNIQUE` 約束保證）。

## 網路類型字串對應

內部 UI 字串 → DB 字串（定義於 `DeviceRepository.toDbNetworkType()`）：
- `"Wi-Fi"` → `"WIFI"`、`"LTE"` → `"LTE"`、`"5G NSA"` → `"5G_NSA"`、`"5G SA"` → `"5G_SA"`、其餘 → `"4G"`

## 認證：三層 Session Fallback

`DeviceMonitorService.ensureValidSession()` 依序執行：
1. `currentUserOrNull()` — 取得記憶體中的 live session（正常路徑）
2. `refreshCurrentSession()` — 用 refresh token 換取新 JWT
3. `silentSignIn()` — 靜默 Google 重新登入（Mutex + 8 秒逾時 + 冷卻，防止頻繁觸發 Credential Manager）

Session 看門狗 coroutine 每 5 秒檢查一次，session 消失時立即觸發上述流程。

## Service 生命週期

`DeviceMonitorService` 為 `START_STICKY`，持有 `PARTIAL_WAKE_LOCK`（必要，否則螢幕關閉後 `Dispatchers.IO` coroutine 全部暫停）。Service 銷毀時呼叫 `markOffline` 將裝置標記為離線。

上傳觸發時機：
- 電量變化 ≥ 5% → 立即 upsert
- 網路類型變化 → 立即 upsert
- 每 15 秒定時心跳，不論是否有變化

`service_enabled` SharedPreference 鍵控制 APK 更新後是否自動重啟（見 `DeviceMonitorApplication`）。

## DeviceStateHolder（取代 Room）

為消除 KSP 依賴而取代 Room。以 `StateFlow<List<DeviceRecord>>` 儲存記憶體快取，由 Realtime 事件直接更新。同時將裝置清單 JSON 持久化至 SharedPreferences 鍵 `cached_devices`（使用 `kotlinx.serialization`），確保 process 重啟後 UI 能立即還原，不呈現空白。

## 執行期所需權限

於 `MainActivity.LaunchedEffect` 統一請求：`ACCESS_FINE_LOCATION`（API 29+ 取得 WiFi SSID）、`READ_PHONE_STATE`（電信商 / 5G 資訊）、`POST_NOTIFICATIONS`（API 33+）。

## 開發工作流程

每次完成程式碼修改後，**必須**執行 git commit：

```bash
git add -A
git commit -s -m "type: 簡短描述"
```

規則：
- 使用 `git commit -s`（加上 Signed-off-by）
- **不可**加上 `Co-Authored-By` 行
- Commit message 以正體中文或英文描述實際變更內容

## Supabase Realtime 模式

Channel 名稱：`"devices:$ownerUid"`。RLS Policy（`owner_uid = auth.uid()::text`）在伺服器端過濾資料，用戶端 channel 不需額外 filter。解碼方式：`action.decodeRecord<DeviceRecord>()`（INSERT / UPDATE）、`action.decodeOldRecord<DeviceRecord>()`（DELETE）。
