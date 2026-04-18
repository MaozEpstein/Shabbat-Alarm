<div align="center">

# 🕯️ שעון מעורר - לשבת

### **Shabbat Alarm** — A minimalist Android alarm clock for Shabbat

_An elegant, Hebrew-first alarm app that fires once, plays for exactly the duration you set, and stops itself. Built to wake you reliably — even from Doze mode._

<br>

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: Personal](https://img.shields.io/badge/License-Personal-888?style=for-the-badge)](#-license)

<br>

**[📥 Install](#-installation)** · **[✨ Features](#-features)** · **[🏗 Architecture](#-architecture)** · **[🛠 Build from source](#-building-from-source)**

</div>

<br>

---

## 🎯 Why this app?

Most alarm apps are built for waking up on weekdays — they expect snooze, dismiss, and endless interaction.

**Shabbat observance is different.** You set the alarm, and on Shabbat you shouldn't touch the phone.

This app is built around one promise:

> **Set once. Fires once. Plays exactly N seconds. Stops itself. Never asks you to interact.**

No snooze. No dismiss. No ongoing notification you have to swipe away. Just a reliable reminder that respects Shabbat.

<br>

---

## 📲 Installation

### Quick install (end users)

> 🔗 **[Download ShabbatAlarm.apk](PLACEHOLDER_DOWNLOAD_URL)**
> _Debug build · ~20 MB · Android 8.0+_

<details>
<summary><b>📖 First-time installation walkthrough</b></summary>

1. Open the `.apk` file on your phone (from Downloads or WhatsApp)
2. Android will warn "**Untrusted source**" — tap **Settings** → enable **Allow from this source**
3. Tap **Install**
4. On first launch, grant:
   - 🔔 **Notifications** — so you see the alarm
   - ⏰ **Exact alarms** — so it fires on time
5. In **Settings → Apps → Shabbat Alarm → Battery**, disable battery optimization
   (or tap **"Fix now"** on the warning card shown in-app)

</details>

### Alternative: get it from a friend

Anyone who has the app installed can share it via **⚙️ Settings → "Share app"** — Android's share sheet opens and they send the APK to you through WhatsApp, Drive, or email.

<br>

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🕯 Core alarm
- Up to **5 concurrent alarms** (candle lighting + Havdalah + ...)
- **Adjustable duration** 5–60 seconds (default 15)
- **One-shot or weekly** — per alarm
- Plays on **ALARM audio stream** — respects system alarm volume
- **Fade-in volume** — gentle wake
- **Optional vibration**

### 📅 Shabbat times
- **8 Israeli cities** with accurate candle-lighting + Havdalah calculations
- **Hebrew dates** in dialog (`י״א באייר התשפ״ו`)
- **Advanced zmanim tab** — Mincha Gedola/Ketana, Sof Zman Shma, etc. with city picker
- **Automatic holiday detection** — shows "Yom Tov times" when applicable
- **Pre-Shabbat reminder** — 40 min before candle lighting in Jerusalem

</td>
<td width="50%">

### 🎨 Design
- **Hebrew-first + full RTL** layout
- **Custom Shabbat palette** — gold + deep navy
- **Animated candle** on main screen (flickers when alarm is set)
- **Hand-designed icon** with gradients and warm halo
- **Light + Dark mode** (follows system)
- **Home screen widget** — next alarm always visible

### 🔒 Reliability
- **Doze-mode resilient** — `setExactAndAllowWhileIdle`
- **Partial WakeLock** — CPU stays awake during playback
- **Reboot recovery** — scheduled alarms survive restart
- **Self-healing weekly alarms** — catches up after days offline
- **Battery optimization warning** — detects and nudges user to fix
- **Fallback tone** — if a custom file is deleted, falls back to system default

### 🎵 Personalization
- **Pick any audio file** from the phone as an alarm tone (up to 10 custom tones)
- **System ringtones** with 5-second preview before choosing
- **Share the APK** directly from within the app

</td>
</tr>
</table>

<br>

---

## 🏗 Architecture

### Tech stack

<div align="center">

| Layer | Choice |
|:---:|:---|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material 3 |
| **Scheduling** | `AlarmManager.setExactAndAllowWhileIdle` |
| **Playback** | `MediaPlayer` on `USAGE_ALARM` stream |
| **Reliability** | `PARTIAL_WAKE_LOCK` + Foreground Service |
| **Shabbat math** | [KosherJava](https://github.com/KosherJava/zmanim) 2.5.0 |
| **Storage** | SharedPreferences + JSON |
| **Concurrency** | Kotlin Coroutines |
| **Min SDK** | 26 (Android 8.0 Oreo) |
| **Target SDK** | 34 (Android 14) |

</div>

### Alarm flow

```
MainActivity (Compose UI)
         ↓ user sets alarm
AlarmScheduler → AlarmManager.setExactAndAllowWhileIdle(triggerMillis, PendingIntent)
         ↓ (at trigger time)
AlarmReceiver → acquire PARTIAL_WAKE_LOCK → ContextCompat.startForegroundService(…)
         ↓                                     ↓
  (reschedule weekly)                    AlarmService
                                               ↓
                                     MediaPlayer + vibration
                                     + fade-in + 15s Coroutine
                                               ↓
                                     stopSelf() → release WakeLock
                                               ↓
                                     ShabbatAlarmWidget.updateAll()
```

<br>

---

## 📁 Project structure

<details>
<summary><b>Click to expand</b></summary>

```
app/src/main/java/com/example/shabbatalarm/
├── MainActivity.kt                  · Compose entry point, forces RTL
├── ShabbatAlarmApp.kt               · Application, creates notification channels
│
├── alarm/
│   ├── AlarmRepository.kt           · SharedPreferences — alarm list, settings,
│   │                                  custom tones (with migration from v1)
│   ├── AlarmScheduler.kt            · AlarmManager wrapper (per-alarm IDs)
│   ├── AlarmReceiver.kt             · Catches alarm fire, reschedules weekly
│   ├── AlarmService.kt              · Foreground service — playback + vibration
│   ├── AlarmWakeLock.kt             · PARTIAL_WAKE_LOCK singleton
│   ├── AlarmTones.kt                · System ringtones + user's custom tones
│   ├── BootReceiver.kt              · Restores alarms after reboot
│   ├── ShabbatTimes.kt              · Shabbat + holiday times + Hebrew dates
│   ├── ShabbatReminderScheduler.kt  · Weekly Jerusalem-anchored reminder
│   └── ShabbatReminderReceiver.kt   · 40-min-before notification
│
├── ui/
│   ├── AlarmScreen.kt               · Main screen — TimePicker + list
│   ├── AnimatedCandle.kt            · Canvas drawing with flicker animation
│   ├── ShabbatTimesDialog.kt        · Two tabs: times + advanced zmanim
│   ├── SettingsDialog.kt            · Sub-views: sound picker, reminder
│   ├── BatteryOptimizationCard.kt   · Warning card
│   ├── TonePreview.kt               · 5-second audio preview
│   ├── ApkSharer.kt                 · FileProvider + Intent.ACTION_SEND
│   └── theme/                       · Color, Typography, Theme
│
└── widget/
    └── ShabbatAlarmWidget.kt        · Home screen widget provider
```

</details>

### Manifest permissions

| Permission | Purpose |
|---|---|
| `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` | Exact alarms on API 31+ |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Playback service |
| `WAKE_LOCK` | Keep CPU awake during playback |
| `RECEIVE_BOOT_COMPLETED` | Re-arm alarms after reboot |
| `POST_NOTIFICATIONS` | Display alarm & reminder notifications |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Bypass Doze restrictions |
| `VIBRATE` | Optional vibration |

<br>

---

## 🛠 Building from source

### Requirements

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 17**
- **Android SDK** with API 34

### Clone and run

```bash
git clone <your-repo-url>
cd shabat-alarm

# Open in Android Studio — Gradle sync will run automatically.
# Then press Run ▶
```

### Build an APK for distribution

```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

<br>

---

## 🙏 Acknowledgments

- **[KosherJava](https://github.com/KosherJava/zmanim)** — the astronomical calculations for candle lighting and Havdalah times. The gold standard for Jewish calendar math.
- **Google Material Design** — color system, typography guidelines, and Compose components.
- Built collaboratively with **Claude** (Anthropic) in **Cursor**, deployed through **Android Studio**.

<br>

---

## 📄 License

Personal project · Not for commercial distribution.

**KosherJava** is used under its LGPL license.

<br>

<div align="center">

_Built with ❤️ for Shabbat_

**[⬆ Back to top](#️-שעון-מעורר---לשבת)**

</div>
