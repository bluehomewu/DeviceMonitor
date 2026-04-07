# 裝置監控精靈 (Device Monitor App) — 專案背景

## 專案概述

開發一個 Android APP，用於監控 20+ 台手機的即時狀態，包含電池電量、WiFi / 行動網路資訊，並在電量低於閾值時向主裝置發送警報。

目標：使用者下載 APK → 登入 Google 帳號 → 直接使用，無需自架 Server。

---

## 專案基本資訊

- Package name：`tw.bluehomewu.devicemonitor`
- Minimum SDK：API 30（Android 11.0）
- 語言：Kotlin
- UI：Jetpack Compose

---

## 已完成進度

### Phase 1｜本地資訊採集 ✅
- [x] `BatteryCollector` — 電量百分比、是否充電
- [x] `NetworkCollector` — WiFi SSID / 行動網路類型 / 電信商名稱
- [x] `DeviceAdminReceiver` — 裝置管理員
- [x] `DeviceMonitorService` — Foreground Service 保活
- [x] `DeviceInfoScreen` + `DeviceInfoViewModel` — 本地資訊顯示 UI

### 前置設定 ✅
- [x] Google Cloud 專案建立（專案名稱：DeviceMonitor）
- [x] Web application OAuth Client ID 建立
- [x] Supabase 專案建立（Region：Southeast Asia）
- [x] devices 資料表建立（含 RLS Policy 與 UNIQUE 約束）
- [x] Supabase Google OAuth Provider 啟用
- [x] Google Cloud Redirect URI 設定完成
- [x] local.properties 填寫完成

---

## 技術選型

| 類別 | 選擇 |
|---|---|
| 語言 | Kotlin |
| UI | Jetpack Compose |
| 背景保活 | Foreground Service + WorkManager |
| 本地快取 | Room Database |
| 雲端後端 | Supabase 雲端（Postgres + Realtime + Auth）|
| Android SDK | supabase-kt（官方 Kotlin SDK）|
| 身份驗證 | Google Sign-In → Supabase Google OAuth |
| 最低 SDK | API 30（Android 11.0）|
| DI 框架 | 手動 DI（Hilt 暫時移除，與 AGP Built-in Kotlin 不相容）|
| 裝置管理員 | DeviceAdminReceiver |

### Hilt 移除說明
Hilt 2.x 目前與 AGP 9.x Built-in Kotlin 架構不相容，出現 `Android BaseExtension not found` 錯誤。
暫時改用手動 DI，待 Hilt 正式支援後再加回。

---

## Supabase 資料表結構

```sql
create table devices (
  id             uuid primary key default gen_random_uuid(),
  owner_uid      text not null,         -- Google UID，隔離不同帳號
  device_name    text not null,
  battery_level  int  not null,
  is_charging    boolean not null,
  network_type   text not null,         -- 'WIFI' | '4G' | 'LTE' | '5G_NSA' | '5G_SA'
  wifi_ssid      text,                  -- 僅 WiFi 時有值
  carrier_name   text,                  -- 僅行動網路時有值
  is_master      boolean default false,
  alert_threshold int default 20,       -- 警報閾值（%），以 10% 為間隔
  is_online      boolean default true,
  updated_at     timestamptz default now()
);

ALTER TABLE devices
  ADD CONSTRAINT devices_owner_device_unique
  UNIQUE (owner_uid, device_name);

alter table devices enable row level security;

create policy "owner only"
  on devices for all
  using (owner_uid = auth.uid()::text);
```

---

## 系統架構

```
每台被監控裝置
  └─ Foreground Service (DeviceMonitorService)
       ├─ 每 30 秒 upsert devices 表
       ├─ 電量變化超過 5% 立即 upsert
       └─ 網路狀態變化立即 upsert

主裝置
  └─ Supabase Realtime WebSocket
       ├─ subscribe channel: devices
       ├─ 收到 UPDATE → 刷新 UI
       └─ battery_level < alert_threshold → 發本地通知

後端
  └─ Supabase 雲端（開發者維護單一專案）
       ├─ Postgres：儲存裝置狀態
       ├─ Realtime：WebSocket 即時推播
       └─ Auth：Google OAuth 驗證 + RLS 資料隔離
```

---

## 環境變數（local.properties）

```properties
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_ANON_KEY=eyJ...
GOOGLE_WEB_CLIENT_ID=xxxx.apps.googleusercontent.com
```

這三個值已填寫完成，透過 `buildConfigField` 注入 BuildConfig。

---

## Gradle 注意事項

### KSP 版本
```toml
ksp = "2.3.0"   # 與 AGP Built-in Kotlin 相容
```

### Hilt 已移除
`libs.versions.toml` 與 `app/build.gradle.kts` 中已無 hilt 相關設定。
如需重新加入，需等待 Hilt 官方支援 AGP 9.x Built-in Kotlin。

### plugins 順序（app/build.gradle.kts）
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // ksp 已隨 Hilt 一起移除，目前無其他 KSP 使用者
}
```

---

## Google OAuth 設定

### Google Cloud Console
- 專案：DeviceMonitor
- Web application Client ID：已建立（Client ID 開頭 582911348993）
- Android Client ID：跳過（Supabase Auth 流程不需要）

### Supabase
- Authentication → Providers → Google：已啟用
- Callback URL 已填入 Google Cloud Redirect URI

---

## 開發階段（Phase）

### Phase 1｜本地資訊採集 ✅ 已完成
- [x] BatteryCollector
- [x] NetworkCollector（WiFi SSID / 5G SA/NSA / 電信商）
- [x] DeviceAdminReceiver
- [x] DeviceMonitorService（Foreground Service）
- [x] DeviceInfoScreen + DeviceInfoViewModel

### Phase 2｜Supabase 串接 ✅ 已完成
- [x] Google Sign-In 整合（Credential Manager → `GoogleAuthManager`）
- [x] Google ID Token → Supabase Google OAuth Session
- [x] supabase-kt SDK 初始化（`AppModule.supabase` singleton）
- [x] `LoginScreen` 實作（未登入時顯示）
- [x] `AuthViewModel` 實作（含 session 還原、登出）
- [x] devices 表 upsert 實作（`DeviceRepository`）
- [x] `DeviceMonitorService` 串接 Supabase upsert（下一步從 Phase 3 開始）

### Phase 3｜即時同步
- [ ] Supabase Realtime WebSocket 訂閱
- [ ] 收到裝置狀態變更 → 更新本地 Room 快取
- [ ] UI 即時刷新（Compose + StateFlow）

### Phase 4｜主裝置 & 警報
- [ ] 主裝置設定（is_master flag）
- [ ] 裝置清單 UI（顯示所有同帳號裝置）
- [ ] 警報閾值設定 UI（10% 間隔，Slider）
- [ ] 低電量觸發本地 Notification（顯示裝置名稱 + 電量）

### Phase 5｜維運
- [ ] GitHub Actions 每日 ping 保持 Supabase 免費專案存活

---

## 重要技術細節

### 5G SA vs NSA 判斷

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

### WiFi SSID 權限
Android 10+ 需要 `ACCESS_FINE_LOCATION`，且 Location Service 需開啟。

### Foreground Service
Android 12+ 需在 Manifest 聲明 `foregroundServiceType="dataSync"`。

### Device Admin
引導使用者手動啟用，啟用後防止 App 被一般方式解除安裝。
僅限 sideload 自用，上架 Google Play 會受審查限制。

---

## 開發指令（給 Claude Code 參考）

- 「繼續開發 Phase 2」→ ✅ 已完成
- 「繼續開發 Phase 3」→ Realtime 同步
- 「繼續開發 Phase 4」→ 主裝置 & 警報
- 「繼續開發 Phase 5」→ GitHub Actions 保活
