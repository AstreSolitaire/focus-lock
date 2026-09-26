# FocusLock — 专注锁机

[![License: MIT](https://img.shields.io/badge/License-MIT-5b4be0.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-3ddc84.svg)](#install)
[![Release](https://img.shields.io/github/v/release/AstreSolitaire/focus-lock?color=5b4be0)](../../releases)

**English** · **[简体中文](README.md)**

**An Android app locker that enforces focus sessions by day-of-week and time range — with an
optional per-app whitelist, and no network permission at all.**

Built for one concrete situation: exam prep. During certain hours the phone has to become
a brick, with only a handful of study apps still usable.

<p align="center">
  <img src="docs/screenshots/lock.png"   width="230" alt="Lock screen">
  <img src="docs/screenshots/home.png"   width="230" alt="Home">
  <img src="docs/screenshots/editor.png" width="230" alt="Schedule editor">
</p>

**[⬇ Download APK](../../releases/latest)** · **[Permissions](#permissions)** · **[How the lock resists bypassing](#how-the-lock-resists-bypassing)**

---

## What it does

| Capability | Detail |
| --- | --- |
| Multiple lock schedules | As many as you want, each enabled/disabled independently |
| Multiple time ranges per schedule | Add any number of start/end pairs; ranges may cross midnight (e.g. 22:30 – 06:00) |
| Day-of-week selection | Pick which weekdays each schedule applies to, plus one-tap "every day / weekdays / weekends" |
| Optional whitelist | Whitelisted apps stay usable during a lock. Turn the whitelist off and everything except system essentials is blocked |
| Launch whitelisted apps from the lock screen | A "Whitelisted apps (N)" button opens the list; tap to launch, press Home to return to the lock screen |
| Whitelist covers every installed app | The picker lists **all** installed packages except this app itself, including system apps with no launcher icon |
| Blocks AI-assistant workarounds | Long-pressing the power button to summon an assistant and having it open an app for you is closed off |
| Normal / Strict mode | Strict additionally blocks System Settings and the notification shade, and prevents uninstall |
| Custom lock-screen wallpaper | Pick any image from the gallery; it's cropped to fill |
| Live clock on the lock screen | Large clock, progress ring, remaining countdown, and expected unlock time |
| Power on/off unaffected | The power key is never intercepted — long-press still shows the power menu, shutdown and reboot work normally |
| Survives reboot | Session duration is written to disk; after a reboot the lock resumes and finishes its remaining time |
| Auto-exit on time | Unlocks and notifies by itself when the window ends |
| Tracks total locked time | Today / this week / this month / all-time, plus a 7-day bar chart and per-session log |
| Centralised permission setup | A permission centre checks each item and deep-links to the right system page |
| Clean UI | Material 3 + Compose, light/dark themes, four bottom tabs |

---

## Install

Prebuilt packages live in `dist/`:

| File | Size | Notes |
| --- | --- | --- |
| `FocusLock-1.0.0-release.apk` | 2.4 MB | **Recommended.** R8-shrunk, signed |
| `FocusLock-1.0.0-debug.apk` | 13 MB | Debug build with full symbols, for troubleshooting |

Transfer to your phone and tap to install; allow "unknown sources" when prompted.

**On first launch, please complete the in-app Permission Centre**, especially the first two:

1. **Accessibility service (app watcher)** — the core of enforcement. Without it there is no lock.
2. **Display over other apps** — since Android 10 an app cannot show UI from the background.
   Without this permission the lock screen will not pop up automatically when a session starts.

---

## Quick start

1. Open the app → **Schedules** tab → `+` at the bottom right
2. Give it a name, e.g. "Morning study"
3. Tick the weekdays it should apply to
4. Add a time range, e.g. `07:00 – 08:30`
5. Decide whether to use the whitelist and whether to use Strict mode
6. Save → back on the home tab you'll see a countdown to the next lock

To try it immediately, the home tab has **Focus now** buttons for 25 / 45 / 60 / 90 minutes.

Configure the whitelist on the **Whitelist** tab — search and tick. Selected apps remain
launchable during a lock.

---

## Permissions

| Permission | Required | Why |
| --- | --- | --- |
| Accessibility service | **Yes** | Reads which app is in the foreground, so non-whitelisted apps get bounced back to the lock screen. **It does not read screen content** (`canRetrieveWindowContent=false`) |
| Display over other apps | **Yes** | Works around Android 10+'s background-activity-launch restriction so the lock screen appears on time |
| Notifications | **Yes** | Persistent notification showing remaining time; end-of-session alert |
| Exact alarms | **Yes** | Start and end sessions punctually |
| Ignore battery optimisation | **Yes** | Keeps the lock service from being frozen in the background |
| Usage access | Optional | A fallback signal alongside accessibility |
| Device admin | Optional | Prevents uninstall / force-stop during a lock |
| List installed apps | Automatic | Used to build the whitelist picker |

**This app is completely offline.** It declares no network permission, phones nothing home,
and collects nothing. Every decision is made on-device.

---

## How the lock resists bypassing

This is where most of the engineering effort went. The usual escape routes, one by one:

### 1. Setting the system clock forward so the countdown finishes instantly

The countdown is computed from **two clocks simultaneously**, and takes the **larger** value:

- **Wall clock**: `startTime + duration − now`
- **Monotonic clock**: `duration − (elapsedRealtime() − elapsedRealtimeAtStart)`

`elapsedRealtime()` only advances with real elapsed time — changing the system clock has no
effect on it. Set the clock to 2030 and the wall-clock term goes negative, while the monotonic
term is untouched. The lock keeps running.

### 2. Setting the system clock backward so the end time "hasn't arrived yet"

If the wall-clock remainder exceeds the monotonic remainder by more than 90 seconds (i.e. the
clock was moved back), the app **re-baselines against the monotonic clock**. A backwards clock
therefore cannot arbitrarily prolong a session either.

### 3. Rebooting the phone

When a session starts, the **total duration** is written to disk along with the wall-clock and
monotonic timestamps at that moment. After a reboot `elapsedRealtime()` is back to zero; the app
detects this, rebuilds the baseline from the wall-clock remainder, and finishes the remaining time.

It also consults a "highest wall clock ever observed" watermark to counter the
*reboot + set the clock back* combination (with a one-hour tolerance, so normal scenarios like
cross-timezone travel aren't misjudged).

A `BOOT_COMPLETED` receiver restarts the lock service, and the lock screen comes straight back.

### 4. Swiping the app out of Recents / letting the system kill it

- Foreground service with `START_STICKY`
- A one-minute `AlarmManager` heartbeat that wakes the process back up if it was killed
- `onTaskRemoved` re-arms the heartbeat immediately — swiping it away brings it back the next second

### 5. Long-pressing the power button to summon an AI assistant

OnePlus/OPPO's assistant can be summoned with the power button, and then asked to open WeChat or
System Settings. This is the easiest way to defeat a local locker — and the power key itself must
*not* be intercepted, or you couldn't shut the phone down.

The approach: **don't touch the power key, but put assistant packages on a "never allowed" list**
that takes priority over the power-key grace period. Verified on a real device:

```
AppWatchService: 拦截（不可放行）com.heytap.speechassist
```

The list also includes the default launcher (otherwise pressing Home escapes) and Google Assistant.
They can still be ticked in the whitelist, but the UI labels them "still blocked during a lock" so
it's not mistaken for a bug.

### 6. The app's own main screen coming to the foreground

Before this was fixed there was a real hole: the accessibility service allowed its own package
unconditionally, so switching back to the main screen from Recents let you use the phone during a
lock. The rule now is: **only the lock screen may be in the foreground.** Anything else gets pushed back:

```
AppWatchService: 本应用的非锁屏界面出现在前台（com.focuslock.app.ui.MainActivity），顶回锁屏
```

Note this only targets **this app's own activity class names**. The whitelist dialog on the lock
screen is a separate window (its `className` is something like `android.widget.FrameLayout`) and
must be allowed through, or the dialog would push itself away.

### 7. System confirmation dialogs killing a whitelist launch

Tapping a whitelisted app makes ColorOS show its own "confirm launch / grant permission" dialog
first. That dialog isn't on the whitelist, so blocking it means the app can never open. The logs
showed exactly this:

```
START u0 {... pkg=com.microsoft.emmx ...} result code=0     ← the app was in fact launched
AppWatchService: 拦截 com.oplus.securitypermission           ← but the system dialog got blocked
```

Two fixes: system permission/install/confirm dialogs go on a permanent allow list, and launching
an app from the lock screen opens a 10-second **launch grace period** so the dialogs that follow
can be shown.

### 8. Turning off the accessibility service during a lock

In Strict mode System Settings is blocked, so that toggle page isn't reachable. The only way out
would be through system settings — but the lock service self-checks every 15 seconds and fires a
notification the moment accessibility goes away.

### 9. Uninstalling the app

Requires device-admin (optional, enabled from the permission centre). Once active,
`setUninstallBlocked` takes effect during a lock and the system refuses the uninstall. Revoking
device admin itself goes through System Settings, which Strict mode also blocks.

### 10. Not ending on time (cheating in the other direction)

The end time is scheduled with `setAlarmClock`, which punches through Doze and fires exactly.

### 11. Not starting on time / starting late

**This one was found by testing on a OnePlus Ace 6 (ColorOS 16 / Android 16) and deserves its own section.**

Start and end were both using `setExactAndAllowWhileIdle`. Testing showed the end fired on time
but **the start was 70 seconds late**. `dumpsys alarm` explained it:

```
Alarm{... com.focuslock.app} windowLength 69111     ← start alarm: a 69-second window
Alarm{... com.focuslock.app} windowLength 0         ← end alarm: exact
```

ColorOS had downgraded `setExactAndAllowWhileIdle` to a **windowed, batched alarm**. The
`WINDOW_HEURISTIC` window scales with how far away the target is — and anything scheduled more
than a day out gets capped at one hour. `setAlarmClock`, by contrast, is treated as a real user
alarm and is never batched, so it stays exact.

The fix: **use `setAlarmClock` for both start and end.** The cost is an alarm icon in the status
bar (the system surfaces the next lock as an alarm). To avoid that icon hanging around all day, the
alarm only upgrades to "alarm clock" form once the start is within 30 minutes; earlier than that a
cheap "pre-arm" alarm is scheduled, which upgrades itself at the 30-minute mark. After the change
`windowLength` is 0 and starts are punctual.

> If sessions still start late on another device, it's probably the same cause: check
> `adb shell dumpsys alarm | grep -A2 focuslock` and confirm `windowLength` is 0.

### Meanwhile: the power key, shutdown and reboot

**The power key is never intercepted.** The accessibility service merely records when it was
pressed and suspends all enforcement for the next 8 seconds — just long enough for the system
power menu to appear. Shutting down and rebooting by long-pressing power work completely normally;
the lock simply comes back afterwards.

---

## Real-device test log

Everything below was measured on a real OnePlus Ace 6 (ColorOS 16 / Android 16 / API 36), not inferred:

| Check | Result |
| --- | --- |
| Install and cold start | Pass, 283 ms, no crash |
| Render of home / schedule list / schedule editor / permission centre / whitelist | Pass |
| Permission state detection (accessibility, overlay, exact alarm, battery) | Pass, matches actual state |
| Focus now (manual lock) | Pass, lock screen appears, clock and countdown tick |
| Home key intercepted | Pass, lock screen stays in front |
| Non-whitelisted apps blocked (WeChat, browser) | Pass, log: `AppWatchService: 拦截 com.heytap.browser` |
| Foreground service + persistent notification + channels | Pass |
| Schedule save (weekday multi-select, time ranges, duration preview) | Pass |
| Auto-start at the scheduled time | Pass (exact after the `setAlarmClock` fix) |
| Auto-exit at the scheduled time | Pass, punctual |
| Accessibility service bound | Pass (`dumpsys accessibility` shows it bound) |
| Lock screen's whitelist button → launching Edge | Pass, foreground becomes `com.microsoft.emmx` |
| Pressing Home after launching a whitelisted app | Pass, bounces back to the lock screen |
| Force-foregrounding the main screen (simulating a bypass) | Pass, pushed back to the lock screen |
| Force-summoning the assistant `com.heytap.speechassist` | Pass, blocked |
| Whitelist lists every installed app (512, including system apps) | Pass |
| New schedule starts with no time ranges; picker opens at 00:00–00:00 | Pass |

Not yet verified on a real device: reboot recovery, custom wallpaper, the emergency-unlock
password, and Strict mode blocking Settings and the notification shade. Their logic is covered by
unit tests but has not been exercised on hardware.

### One known rough edge

The "end the session early" escape hatch in Normal mode lives in the **persistent notification**.
Android only reveals notification actions once the notification is expanded
(`setShowActionsInCompactView` is a `MediaStyle`-only API), and on the OnePlus Ace 6 none of the
gestures I tried would expand it — so in practice that hatch is hard to reach.

Strict mode isn't supposed to have an escape hatch anyway, so the impact is limited. But if you
plan to use timed schedules in Normal mode, treat them as effectively unexitable. The fallback if
you ever do get stuck: connect a cable and run `adb uninstall com.focuslock.app`.

---

## Known limitations (stated plainly)

1. **Time while the phone is off counts as locked time.** The phone is off — you couldn't use it
   anyway. After a reboot the remaining time is restored from the wall clock.
2. **Reboot + clock change** cannot be detected with 100% reliability by a purely local, offline
   app. The "highest wall clock observed" watermark catches a backwards change, but if the clock is
   set *forward* and the phone rebooted, the app has no choice but to trust the system time. Solving
   that properly needs a time server, which is out of scope for a local app.
3. **OEM background policies differ.** Xiaomi / Huawei / OPPO / vivo each have their own
   autostart and background-freeze switches that must be allowed separately for the lock to be
   reliable. The permission centre notes this at the bottom.
4. **A forgotten emergency password is gone.** It's stored locally with no recovery path; you wait
   out the session. Leaving it blank means no mid-session exit at all.
5. **Factory reset.** Nothing can stop it. That's the physical-layer backdoor, and it applies to
   every local locker.

---

## Building from source

### Requirements

- JDK 17
- Android SDK (platform 34 + build-tools 34.0.0)
- Gradle 8.7

Point `local.properties` at your own SDK:

```properties
sdk.dir=/path/to/Android/sdk
```

### Commands

```bash
./gradlew assembleDebug        # debug build, installable, debug-signed
./gradlew assembleRelease      # release build
./gradlew testDebugUnitTest    # 24 unit tests covering the core logic
```

### Three gotchas already hit

**1. Gradle download source**

`gradle/wrapper/gradle-wrapper.properties` points at a Tencent Cloud mirror, because the official
Gradle distribution URL redirects to GitHub and frequently times out from mainland China. Swap it
back to `https://services.gradle.org/distributions/gradle-8.7-bin.zip` if you can reach it.

**2. Do not add `-Dfile.encoding=UTF-8` to `org.gradle.jvmargs`**

On a Chinese Windows install the ANSI code page is GBK. With UTF-8 forced, Gradle writes the test
worker argument file in UTF-8 while the JVM reads it as GBK — mangled paths and a hard
`ClassNotFoundException: worker.gradle.process.internal.worker.GradleWorkerMain`. Kotlin sources
compile as UTF-8 by default, so the flag isn't needed. There's a comment in `gradle.properties`.

**3. Unit tests fail when the project path contains non-ASCII characters**

`assembleDebug` / `assembleRelease` work fine (with `android.overridePathCheck=true`), but the
`testDebugUnitTest` worker still crashes on the path encoding. If you see `GradleWorkerMain` not
found, copy the project somewhere ASCII-only and run there:

```bash
cp -r focus-lock /tmp/fl && cd /tmp/fl && ./gradlew testDebugUnitTest
```

### Signing keys

The project ships a throwaway key at `app/focuslock.keystore` with the password `focuslock`.
For long-term personal use, generate your own:

```bash
keytool -genkeypair -v -keystore my.keystore -alias mykey \
  -keyalg RSA -keysize 2048 -validity 10950
```

Then update `signingConfigs` in `app/build.gradle.kts`. The keystore and its password are
deliberately **not** in this repository.

---

## Code structure

```
app/src/main/java/com/focuslock/app/
├── FocusLockApp.kt              Per-process init + state restore
├── data/
│   ├── Models.kt                TimeRange / Schedule / LockWindow / LockSession
│   ├── Prefs.kt                 All persisted state (SharedPreferences + JSON)
│   ├── StatsStore.kt            Locked-duration statistics
│   └── AppCatalog.kt            Installed-app list (whitelist data source)
├── logic/
│   ├── ScheduleEvaluator.kt     Schedule → concrete lock intervals; cross-midnight, overlap merging
│   ├── LockController.kt        ★ Orchestration + dual-clock tamper-resistant countdown
│   ├── LockRuntime.kt           In-process lock state (fast reads for the accessibility service)
│   ├── AlarmScheduler.kt        Start / end / heartbeat alarms
│   ├── ServiceLauncher.kt       Foreground service start/stop + haptics
│   └── FocusAdmin.kt            Device-admin capabilities
├── service/
│   ├── LockService.kt           Persistent foreground service (1 Hz tick)
│   ├── AppWatchService.kt       ★ Accessibility service: blocks non-whitelisted foreground apps
│   ├── AlarmReceiver.kt         Single entry point for all alarms
│   ├── BootReceiver.kt          Recovery after boot / time change
│   └── FocusDeviceAdminReceiver.kt
├── ui/
│   ├── MainActivity.kt          Bottom tabs + overlay navigation
│   ├── AppViewModel.kt          All UI state
│   ├── LockActivity.kt          Lock screen activity (fullscreen, pinned, swallows Back)
│   ├── LockScreen.kt            Lock visuals: wallpaper / ring / clock / countdown
│   ├── screens/                 Home / schedules / editor / whitelist / stats / settings / permissions
│   ├── components/Common.kt     Shared components
│   └── theme/Theme.kt           Colours and typography
└── util/
    ├── Fmt.kt                   Time formatting
    ├── Notifications.kt         Channels and notification builders
    └── Permissions.kt           Permission detection and deep links

app/src/main/res/xml/
├── accessibility_service_config.xml   Accessibility service declaration
└── device_admin.xml                   Device-admin policy declaration

app/src/test/java/com/focuslock/app/
├── CountdownTest.kt             7 cases: dual-clock countdown under clock changes and reboots
└── ScheduleEvaluatorTest.kt     17 cases: cross-midnight, overlapping schedules, dedup, weekly totals
```

### What the tests cover

`CountdownTest` is the executable form of "neither a reboot nor a clock change can end a session early":

- Normal passage of time → remaining time from the wall clock
- Clock moved 3 hours forward → still monotonic, 110 minutes remain
- Clock moved 5 hours back → re-baselines against the monotonic clock, doesn't stretch the session
- 30 seconds of normal drift → not misdetected
- Reboot (monotonic clock reset) → remaining time restored from the wall clock
- Reboot + 10-hour rollback → caught by the wall-clock watermark
- Time elapsed → returns 0, never a negative number

`ScheduleEvaluatorTest` covers interval arithmetic: cross-midnight ranges landing on the next day,
disabled schedules producing nothing, overlapping schedules taking the latest end and OR-ing
Strict mode, already-completed intervals being excluded, next-interval lookup, today's timeline,
and weekly minute totals.

---

## Download

This repository deliberately **does not contain APKs** (build outputs don't belong in version
control). Packages are published on the [Releases](../../releases) page — grab
`FocusLock-1.0.0-release.apk` and install it.

If you'd rather build it yourself, `./gradlew assembleRelease` produces the APK under
`app/build/outputs/apk/release/`. Without the signing key present the build falls back to the
debug signature, so use your own key for anything long-term.

## Tech stack

- Kotlin 2.0.20 / Jetpack Compose (BOM 2024.09.02) / Material 3
- minSdk 26 (Android 8.0), targetSdk 34
- Zero network dependencies; all data in on-device SharedPreferences
- Main dependencies: `androidx.core`, `activity-compose`, `lifecycle-*`, `compose ui/material3`,
  `coil-compose` (lock-screen wallpaper)

## License

MIT — see [LICENSE](LICENSE).

It was written so I could study for my own postgraduate entrance exams. Take it, modify it,
redistribute it freely. The only request: **don't use it to harm the people running it.** An app
like this is inherently coercive, and locking someone else's phone is not what it's for.
