# Device Monitor — 架構與實作說明

> 本文件面向想了解或 fork 此專案的開發者，說明整體架構、資料庫設計、核心實作細節與已知陷阱。

---

## 技術架構

| 類別 | 選擇 |
|---|---|
| 語言 | Kotlin |
| UI | Jetpack Compose |
| 背景保活 | Foreground Service + WorkManager + AlarmManager |
| 本地快取 | SharedPreferences + In-memory StateFlow（無 Room / KSP）|
| 雲端後端 | Supabase（Postgres + Realtime + Auth）— 可用雲端版或自架 |
| Android SDK | supabase-kt（官方 Kotlin SDK）|
| 身份驗證 | Google Sign-In → Supabase Google OAuth |
| DI 框架 | 手動 DI（`AppModule` object）|
| QR Code | ZXing Core（產生）+ zxing-android-embedded（掃描）|
| 裝置管理員 | DeviceAdminReceiver |

### 為何不用 Hilt

Hilt 2.x 與 AGP 9.x Built-in Kotlin 不相容（`Android BaseExtension not found`）。所有 singleton 依賴集中在 `di/AppModule.kt`，以 `lazy {}` 延遲初始化，由 `DeviceMonitorApplication.onCreate()` 呼叫 `AppModule.initialize(context)`。

### 為何不用 Room

Room 依賴 KSP，而 KSP 與 AGP 9.x 的版本相容矩陣較複雜。裝置狀態本身是揮發性資料，以 `DeviceStateHolder`（`MutableStateFlow`）做 in-memory 快取，搭配 SharedPreferences 持久化跨重啟所需的最小狀態（置頂順序、夥伴命名等）。

---

## 開發環境設定

### 1. 建立 Supabase 專案

使用 [Supabase 雲端](https://supabase.com) 或自架（Docker Compose）均可。取得：
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

### 2. 建立 Google OAuth Client

1. Google Cloud Console → 建立 **Web application** OAuth Client ID
2. Redirect URI 填入 Supabase 的 Google Provider Callback URL
3. Supabase Dashboard → Authentication → Providers → Google → 貼入 Client ID & Secret

### 3. 填寫 local.properties

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=eyJ...
GOOGLE_WEB_CLIENT_ID=your-client-id.apps.googleusercontent.com
```

這三個值透過 `buildConfigField` 注入 `BuildConfig`，不會出現在版本控制中。

### 4. 建立資料表與 RLS Policy

依序執行下方「資料表結構」與「RLS Policies」章節中的 SQL。

---

## 資料表結構

### devices

```sql
create table devices (
  id              uuid primary key default gen_random_uuid(),
  device_id       text not null,          -- ANDROID_ID，裝置唯一識別
  owner_uid       text not null,          -- Google UID
  device_name     text not null,          -- Build.MODEL
  battery_level   int  not null,
  is_charging     boolean not null,
  network_type    text not null,          -- 'WIFI'|'4G'|'LTE'|'5G_NSA'|'5G_SA'
  wifi_ssid       text,
  carrier_name    text,
  is_master       boolean default false,
  alert_threshold int default 20,         -- 低電量警報閾值（%）
  is_online       boolean default true,
  updated_at      timestamptz default now(),
  android_version text,
  manufacturer    text,
  build_number    text,
  sim_operator    text,
  alias           text,                   -- 使用者自訂顯示名稱
  app_version     text,
  signal_level    int,                    -- 訊號格數（0–4）
  signal_dbm      int,
  fcm_token       text                    -- Firebase Cloud Messaging token（推播通知用）
);

alter table devices
  add constraint devices_owner_device_unique unique (owner_uid, device_id);

alter table devices enable row level security;
```

### partnerships（夥伴模式）

```sql
create table partnerships (
  id          uuid primary key default gen_random_uuid(),
  uid_a       text not null,             -- 邀請方 Google UID
  uid_b       text,                      -- 受邀方 Google UID（pending 時為 null）
  invite_code text,
  status      text default 'pending',    -- 'pending' | 'active'
  created_at  timestamptz default now()
);

alter table partnerships enable row level security;
```

### shared_devices（夥伴模式）

```sql
create table shared_devices (
  id             uuid primary key default gen_random_uuid(),
  device_id      text not null,          -- 對應 devices.id（UUID 存成 text，見注意事項）
  partnership_id uuid references partnerships(id) on delete cascade,
  owner_uid      text not null,
  receive_alerts boolean default false,
  created_at     timestamptz default now(),
  unique (device_id, partnership_id)
);

alter table shared_devices enable row level security;
```

### device_auth（匿名裝置授權）

```sql
create table device_auth (
  anon_uid   text not null,
  owner_uid  text not null,
  created_at timestamptz default now(),
  linked_at  timestamptz
);

alter table device_auth enable row level security;
```

---

## RLS Policies

### devices

```sql
-- 主 policy：owner 本人或已授權的匿名裝置可讀寫
create policy "devices_rls" on devices for all
  using (
    owner_uid = auth.uid()::text
    or exists (
      select 1 from device_auth da
      where da.anon_uid = auth.uid()::text
        and da.owner_uid = devices.owner_uid
    )
  );

-- 夥伴 policy：夥伴關係中的雙方可讀取共享裝置記錄
-- ⚠️ devices.id 是 uuid，shared_devices.device_id 是 text，必須加 ::text 轉型
create policy "partner can read shared devices" on devices for select
  using (
    id::text in (
      select sd.device_id
      from shared_devices sd
      join partnerships p on sd.partnership_id = p.id
      where p.uid_a = auth.uid()::text
         or p.uid_b = auth.uid()::text
    )
  );
```

### partnerships

```sql
-- 任何已登入使用者可讀（認領邀請碼時需要掃描全表）
create policy "partnerships_select" on partnerships for select
  using (auth.role() = 'authenticated');

-- 任何已登入使用者可建立邀請
create policy "partnerships_insert" on partnerships for insert
  with check (true);

-- uid_a、uid_b 或 pending 狀態下的任何人可更新（認領邀請碼時填入 uid_b）
create policy "partnerships_update" on partnerships for update
  using (
    uid_a = auth.uid()::text
    or uid_b = auth.uid()::text
    or (uid_b is null and status = 'pending')
  );

-- 雙方均可刪除（解除夥伴關係）
create policy "partnerships_delete" on partnerships for delete
  using (uid_a = auth.uid()::text or uid_b = auth.uid()::text);
```

### shared_devices

```sql
-- owner 或夥伴關係中的另一方均可存取
create policy "shared_devices_rls" on shared_devices for all
  using (
    owner_uid = auth.uid()::text
    or exists (
      select 1 from partnerships p
      where p.id = shared_devices.partnership_id
        and (p.uid_a = auth.uid()::text or p.uid_b = auth.uid()::text)
    )
  );
```

---

## 系統架構概覽

```
每台被監控裝置
  └─ DeviceMonitorService（Foreground Service）
       ├─ 每 30 秒 upsert devices 表（含指數退避重試）
       ├─ 電量變化 > 5% 立即 upsert
       ├─ 網路狀態變化立即 upsert
       └─ 啟動時同步 FCM token 至 devices.fcm_token

主裝置（接收警報）
  └─ RealtimeRepository（Supabase Realtime WebSocket）
       ├─ 訂閱 devices 表變更 → 更新 DeviceStateHolder
       ├─ 訂閱 shared_devices 表 INSERT → 載入夥伴裝置記錄
       ├─ battery_level < alert_threshold → AlertNotificationManager 發本地通知
       └─ 斷線 / 重連 → isRealtimeConnected StateFlow → UI 顯示提示橫幅

FCM 推播（背景通知）
  FcmService（extends FirebaseMessagingService）
       ├─ onNewToken() → 更新 devices.fcm_token（透過 FcmTokenManager）
       └─ onMessageReceived() → 顯示推播通知

首頁 Widget（Jetpack Glance）
  DeviceWidget（GlanceAppWidget）
       └─ DeviceWidgetReceiver（GlanceAppWidgetReceiver）
            └─ DeviceMonitorService 更新後呼叫 updateAll()

夥伴模式資料流
  邀請方：generateInvite() → partnerships + shared_devices
  受邀方：claimInvite() → partnerships.uid_b + status='active'
  雙方：RealtimeRepository 監聽 shared_devices → PartnerStateHolder
  離線時：PartnerStateHolder 保留最後快取，UI 顯示 last-known 狀態

後端（Supabase）
  ├─ Postgres：儲存所有狀態
  ├─ Realtime：WebSocket 即時推播變更
  └─ Auth：Google OAuth + RLS 資料隔離
```

---

## 核心模組說明

| 模組 | 路徑 | 職責 |
|---|---|---|
| `AppModule` | `di/AppModule.kt` | 全域 singleton DI 容器 |
| `DeviceStateHolder` | `data/memory/` | 裝置清單的 in-memory StateFlow 快取 |
| `PartnerStateHolder` | `data/memory/` | 夥伴關係、共享裝置的 in-memory 快取 |
| `RealtimeRepository` | `data/remote/` | Supabase Realtime 訂閱，驅動兩個 StateHolder；暴露 `isRealtimeConnected` StateFlow |
| `DeviceRepository` | `data/remote/` | devices 表 CRUD |
| `PartnerRepository` | `data/remote/` | partnerships / shared_devices CRUD |
| `PartnerNamingManager` | `data/local/` | 夥伴名稱 / 裝置別名的 SharedPreferences 持久化 |
| `PinnedOrderManager` | `data/local/` | 置頂排序的 SharedPreferences 持久化 |
| `BatteryHistoryManager` | `data/local/` | 電量歷史記錄（最近 5 筆）的 SharedPreferences 持久化 |
| `FcmTokenManager` | `data/local/` | FCM token 本地快取，並在 `DeviceMonitorService` 啟動時同步至 `devices.fcm_token` |
| `AlertNotificationManager` | `service/` | 低電量本地通知邏輯（含雙層閾值、充滿電、離線通知） |
| `GoogleAuthManager` | `auth/` | Google Sign-In → Supabase OAuth |
| `FcmService` | `fcm/` | `FirebaseMessagingService`：處理 token refresh 與背景推播訊息接收 |
| `DeviceWidget` | `widget/` | Jetpack Glance `GlanceAppWidget`，渲染首頁 Widget 內容 |
| `DeviceWidgetReceiver` | `widget/` | `GlanceAppWidgetReceiver`，連結 Glance Widget 與 Android App Widget 系統 |

---

## 重要實作細節

### 5G SA vs NSA 判斷

`TelephonyManager.getNetworkType()` 在 5G NSA 下回傳 LTE，需透過 `TelephonyDisplayInfo.overrideNetworkType` 才能正確區分：

```kotlin
telephonyManager.listen(object : PhoneStateListener() {
    override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
        val networkType = when (info.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA      -> "5G_NSA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G_SA"
            else -> when (info.networkType) {
                TelephonyManager.NETWORK_TYPE_NR  -> "5G_SA"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                else                              -> "4G"
            }
        }
    }
}, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
```

### WiFi SSID 持久化

`WifiManager.connectionInfo.ssid` 在 Foreground Service 重啟後可能暫時回傳 `<unknown ssid>`（即使仍在同一 AP）。做法：每次成功取得有效 SSID 後寫入 SharedPreferences，下次取得失敗時回落至快取值。

取得 SSID 需要 `ACCESS_FINE_LOCATION` 權限，且裝置位置服務必須開啟（Android 10+）。

### Supabase insert 必須加 `{ select() }`

```kotlin
// ✅ 正確：加 select() 讓 Supabase 回傳 201 + 插入的資料列
val row = supabase.from("partnerships")
    .insert(payload) { select() }
    .decodeSingle<Partnership>()

// ❌ 錯誤：不加 select() → Supabase 回傳 204 空 body → JSON EOF parse error
val row = supabase.from("partnerships")
    .insert(payload)
    .decodeSingle<Partnership>()
```

### shared_devices.device_id 型別陷阱

`devices.id` 是 `uuid`，但 `shared_devices.device_id` 是 `text`（歷史設計）。在 RLS policy 或任何 SQL join 中比較時，需明確加 `::text` 轉型，否則 PostgreSQL 會拋出 `operator does not exist: uuid = text`：

```sql
-- ✅
where devices.id::text = shared_devices.device_id

-- ❌ 會報錯
where devices.id = shared_devices.device_id
```

### 夥伴裝置記錄載入時機

夥伴的裝置記錄（`DeviceRecord`）需透過 `fetchDevicesByIds` 從 `devices` 表取得，但預設 RLS 只允許 owner 讀取。必須確保已套用 `partner can read shared devices` policy，否則 `fetchDevicesByIds` 會靜默回傳空陣列（`runCatching.getOrDefault(emptyList())`），造成 UI 永遠顯示「載入中...」。

兩個觸發點：
1. `PartnerViewModel.init {}` — 監聽 `sharedDevices` Flow，發現 `sharedRecords` 中缺少記錄時觸發 fetch
2. `RealtimeRepository` — 收到 `shared_devices INSERT` 且為夥伴裝置時，立即 fetch 對應 `DeviceRecord`

### PartnerNamingManager — 本地命名持久化

夥伴自訂名稱與裝置別名純粹儲存在本機 SharedPreferences（name: `partner_prefs`），不寫入 Supabase：

| Key 格式 | 用途 |
|---|---|
| `pname_$partnershipId` | 夥伴顯示名稱 |
| `dalias_$deviceId` | 夥伴裝置本機別名（也用於低電量通知） |

`_localVersion: MutableStateFlow<Int>` 在名稱變更時遞增，觸發 `combine()` 重新計算，讓 UI 立即反映更新。

### Firebase 程式化初始化（無 google-services.json）

FCM 不透過 `google-services.json`，改用程式化建立 `FirebaseOptions`：

```kotlin
val options = FirebaseOptions.Builder()
    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
    .setApiKey(BuildConfig.FIREBASE_API_KEY)
    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
    .build()
FirebaseApp.initializeApp(context, options)
```

四個參數從 `local.properties` 透過 `buildConfigField` 注入，不進版本控制。`FcmService` 繼承 `FirebaseMessagingService`，在 `AndroidManifest.xml` 中以 `intent-filter` 宣告，不需要 `apply plugin: 'com.google.gms.google-services'`。

### 清單顯示設定（list_display_prefs）

`DeviceListViewModel` 使用 `SharedPreferences` name `"list_display_prefs"` 持久化三個顯示開關：

| Key | 預設值 | 用途 |
|---|---|---|
| `show_warning_threshold` | `false` | 顯示警告閾值滑桿 |
| `show_critical_threshold` | `false` | 顯示緊急閾值滑桿 |
| `show_battery_history` | `false` | 顯示電量歷史圖 |

緊急閾值（critical threshold）純粹儲存在本機 `app_prefs`（key：`critical_threshold_$deviceId`），不寫入 Supabase。警告閾值（`alert_threshold`）才是寫入 `devices` 表的欄位。

### Jetpack Glance Widget

`DeviceWidget` 繼承 `GlanceAppWidget`，透過 `DeviceWidgetReceiver`（繼承 `GlanceAppWidgetReceiver`）與 Android App Widget 系統整合。`DeviceMonitorService` 在每次 upsert 完成後呼叫 `DeviceWidget().updateAll(context)` 觸發 Widget 重繪。

注意：Glance 1.1.1 的 `ColorProvider` 只接受單一參數（不支援 `day`/`night` 兩參數版本）；`clickable` 需從 `androidx.glance.action.clickable` 匯入，而非 `androidx.glance.clickable`。

### 強制重新登入機制

`AppConfig.kt` 中的 `FORCE_RESIGN_FROM_VERSION` 常數：

```kotlin
object AppConfig {
    const val FORCE_RESIGN_FROM_VERSION: String? = "1.13.0"
}
```

App 啟動時若現有 session 的版本記錄與此值不符，自動登出並顯示 Toast 提示。適用於後端遷移或 Auth schema 異動後強制使用者重新取得新 session。改為 `null` 可完全停用。

### Foreground Service 權限（Android 12+）

需在 `AndroidManifest.xml` 中聲明 `foregroundServiceType`：

```xml
<service
    android:name=".service.DeviceMonitorService"
    android:foregroundServiceType="dataSync" />
```

---

## Gradle / 依賴注意事項

### 無 KSP（無 Room、無 Hilt）

`app/build.gradle.kts` 的 plugins：

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // ksp 已移除，目前無 KSP 使用者
}
```

### ZXing 掃描器依賴命名衝突

Version catalog 中已有 `zxing-core`，再新增 `zxing-android-embedded` 會因 hyphenated name 轉 dot accessor 時產生命名衝突。解法：直接以字串宣告，跳過 catalog：

```kotlin
@Suppress("UseTomlInstead")
implementation("com.journeyapps:zxing-android-embedded:4.3.0")
```

### QR 掃描器方向鎖定

`zxing-android-embedded` 的 `ScanOptions` 預設 `orientationLocked = true`（鎖直向）。若設為 `false` 會允許旋轉，在部分裝置上導致掃描器以橫向開啟：

```kotlin
ScanOptions().apply {
    setOrientationLocked(true)  // 鎖直向；不要設為 false
}
```
