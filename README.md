# Device Monitor

> [繁體中文](README_zhTW.md) | **English**

Monitor battery level and network status across multiple Android devices in real time. Automatically notifies the designated master device when battery drops below a configurable threshold.  
No self-hosted server required — just sign in with a Google account and start monitoring.

---

## Features

- Real-time display of battery level, charging state, and network type (Wi-Fi / 4G / LTE / 5G NSA / 5G SA) for all devices
- Wi-Fi shows SSID; mobile network shows carrier name and signal strength (bars + dBm)
- Low-battery alerts: master device receives a local notification when a device drops below the threshold (silenced while charging; alert fires immediately once charging stops if still below threshold)
- Master device setting: designate one device to receive all alerts
- Adjustable alert threshold (10% steps, 10–100%)
- Device alias: set a custom display name for each device
- **Pinned devices**: your own device is always at the top; swipe right on any card to pin it; pinned devices support long-press drag-to-reorder
- **Delete device**: enable delete mode in settings to reveal a delete button on each card (swipe right); supports multi-select — tap cards to select, then tap the header button to batch-delete; delete mode auto-disables 60 seconds after the last deletion
- **In-app update**: automatically checks for new versions on launch; download and install the APK without leaving the app
- **Beta update channel**: opt in to receive beta releases (tagged with `-beta`) instead of stable-only updates
- Continuous background monitoring — uploads status even when the screen is off

---

## Requirements

- Android 10 (API 29) or higher
- Google account
- "Install unknown apps" permission enabled (for sideloading the APK)

---

## Installation

1. Download the latest `app-release.apk`
2. Open the APK on your phone and install it
3. Launch the app → tap **Sign in with Google**
4. After signing in, tap **Start Monitor Service**

Repeat on every device you want to monitor, signing in with the **same Google account**.

---

## Setting the Master Device

The device that should receive low-battery alerts must be set as the master device:

1. Open the app → **My Device** tab
2. Enable the **Master Device** toggle

Only one master device is allowed per account.

---

## Device List Operations

| Action | Description |
|---|---|
| Swipe right on a card | Reveal the pin button (and delete button if delete mode is on) |
| Long-press the drag handle on a pinned card | Drag vertically to reorder pinned devices |
| Tap a card | Expand / collapse device details (or toggle selection in delete mode) |
| Tap the pencil icon | Set a device alias |
| Tap "Delete (N)" in the header | Batch-delete all selected devices (visible when delete mode is on and at least one card is selected) |

Your own device is always fixed at the very top of the list and cannot be displaced.

---

## Recommended Settings (for better background survival)

Go to **Settings → Apps → Device Monitor** on each phone and apply:

| Setting | Recommended value |
|---|---|
| Battery optimization | Unrestricted |
| Background activity | Allow |
| Device admin | Enable (prevents accidental uninstall) |

---

## Tech Stack

| Item | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Backend | Supabase (Postgres + Realtime + Auth) |
| Authentication | Google Sign-In → Supabase Google OAuth |
| Background keep-alive | Foreground Service + WorkManager + AlarmManager |
| Local storage | SharedPreferences (replaces Room; no KSP required) |
| Pin ordering | SharedPreferences (PinnedOrderManager) |
| Min SDK | API 29 (Android 10) |

---

## Privacy

- All device data is stored in Supabase and isolated by Google account UID
- Data from different accounts is completely separate and mutually inaccessible
- No personally identifiable information beyond what is required for authentication is collected
