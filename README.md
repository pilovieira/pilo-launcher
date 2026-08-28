# Pilo Launcher

A minimalist Android home screen launcher, built to get out of your way.

Most launchers try to impress you: widgets, feeds, icon packs, animations, "smart" suggestions. **Pilo Launcher** does the opposite. It's black and white, text-only, and has exactly one job — let you get to your apps and get on with your day. No distractions, no clutter, no dopamine-bait. Just your apps, a clock, and the tools to keep yourself honest about how you use your phone.

## Philosophy

This launcher exists to optimize *time*, not engagement. Every design decision follows one rule: if it doesn't help you open the app you need and move on, it doesn't belong here.

- **Pure black & white.** No icons, no color, no visual noise — just clean white text on a pitch-black background.
- **Text-only app list.** Reading a name is faster than scanning a grid of icons.
- **A calm home screen.** No feed, no widgets, no notifications dashboard — just an analog clock, so the first thing you see is the time, not a reason to keep scrolling.
- **Deliberate navigation.** Getting to your apps takes a swipe. Getting back home is instant.
- **Built-in friction where it helps.** Focus Mode and app hiding exist to help you use your phone less, not more.

## Features

### Home screen
A pure black screen with a clock in the center (analog or digital, your choice) — nothing else. Swipe up to open the app list, swipe down to see recently opened apps. When Focus Mode is active, a small, discreet indicator appears at the top so you always know it's on.

### App list
All your launchable apps, sorted alphabetically, text-only, in your choice of compact or normal row size. A compact, low-profile search bar sits at the top (with a small vector search icon), and a gear-icon button right next to it opens Settings in one tap. Long-pressing an app opens a small dialog to jump straight to its system info screen or uninstall it. Apps you've chosen to hide live on a dedicated screen, reachable either by swiping the list left (swipe right on that screen to come back) or via the "Hidden Apps" link at the bottom of the list.

### Recent apps
Swipe down from the home screen to see a simple, text-only list of apps you've opened recently, most recent first — handy for jumping back into what you were just doing without hunting through the full list. A small icon-only button lets you clear the history whenever you want.

### Back button behavior
The back gesture/button is context-aware: from the app list (or any settings screen) it takes you back a step, eventually landing you on the home screen. From the home screen itself, back does nothing — there's nowhere calmer to go.

### App visibility
Hide apps you don't want tempting you from the main list (social media, games, anything distracting) without uninstalling them. Hidden apps are still reachable from a dedicated screen whenever you actually need them.

### Rename apps
Give any app a custom name from Settings. Useful for cutting through marketing names, renaming things in your own language, or just calling an app what you actually think of it as.

### Focus Mode
A schedule-based Do Not Disturb helper: vibrate-only from 9:00 to 18:00, normal ringer the rest of the day. It reapplies itself automatically after boot, time changes, and timezone changes, so it keeps working without you having to think about it.

### Set as default launcher
A simple prompt (and one-tap action) to set Pilo Launcher as your device's home app, using Android's Role Manager where available.

### Clock style
Choose between an analog or digital clock from Settings — applies to both the home screen and the custom lock screen.

### App list size
Choose between a compact or normal row height for the app list, from Settings.

### Custom lock screen
An optional black, text-only lock screen matching the rest of the launcher: your chosen clock style, plus a count of active notifications (requires granting notification access, prompted right in Settings). It appears as soon as the display turns on, and tapping it hands off to Android's own unlock flow (fingerprint/face/PIN/pattern, whatever you already have configured) — real device security is always enforced by the system, never by the app itself.

### More apps
A link at the bottom of Settings pointing to more apps from the same developer.

## Tech stack

- **Kotlin** + **Jetpack Compose** for a fully declarative UI — no XML layouts.
- **AndroidViewModel** + **Kotlin Flows** (`StateFlow`, `combine`) for reactive state management.
- **SharedPreferences** for lightweight, dependency-free persistence (hidden apps, custom labels, recent apps, Focus Mode state, clock style, list density, lock screen preference).
- **AlarmManager** + a `BroadcastReceiver` to drive Focus Mode's schedule reliably in the background.
- **A foreground `Service` + `NotificationListenerService`** to power the optional custom lock screen and its notification count.
- No third-party dependencies beyond AndroidX and Compose — kept intentionally lean.

## Project structure

```
app/src/main/java/br/com/pilovieira/launcher/
├── MainActivity.kt                      # All Compose screens: Home, App List, Recents, Settings, etc.
├── LauncherViewModel.kt                  # Loads installed apps, custom labels, hidden apps, recents, preferences
├── AppInfo.kt                            # App data model
├── FocusModeHelper.kt                    # Focus Mode scheduling and ringer-mode logic
├── FocusModeReceiver.kt                  # Receives boot/time/alarm broadcasts to reapply Focus Mode
├── LockScreenActivity.kt                 # The custom lock screen UI and unlock handoff
├── LockScreenService.kt                  # Foreground service that shows the lock screen when the display turns on
├── PiloNotificationListenerService.kt    # Tracks active notification count for the lock screen
├── NotificationAccessHelper.kt           # Checks whether notification access has been granted
└── BootReceiver.kt                       # Restarts the lock screen service after a reboot
```

Screens live together in `MainActivity.kt` as small, focused `@Composable` functions — no navigation library, just a simple `when` over a `Screen` enum, which keeps the whole flow easy to follow in one place.

## How to build and run

1. Open the project in **Android Studio**.
2. Let Gradle sync.
3. Run the `app` module on an emulator or physical device (minSdk 24).
4. Press the device's Home button and choose **Pilo Launcher** as your default home app (or use the in-app prompt).

## Permissions

- `QUERY_ALL_PACKAGES` — required to list all launchable apps on Android 11+.
- `ACCESS_NOTIFICATION_POLICY` — required for Focus Mode to change the ringer mode.
- `SCHEDULE_EXACT_ALARM` / `RECEIVE_BOOT_COMPLETED` — required to keep Focus Mode's schedule accurate across reboots and time changes.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `POST_NOTIFICATIONS` — required to keep the lock screen service alive and show its (minimized) persistent notification.
- Notification access (`NotificationListenerService`, granted from Settings) — required for the lock screen to show a notification count.
- `DISABLE_KEYGUARD` — used when showing the custom lock screen over the display; actual unlock security is always handled by Android's own keyguard, never bypassed by the app.

## Status

This is an actively evolving personal project — simple by design, but not standing still. Contributions and ideas that keep the "no distractions" philosophy intact are welcome.
