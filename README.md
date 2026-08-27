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
A pure black screen with a white analog clock in the center — nothing else. Swipe up to open the app list, swipe down to see recently opened apps. When Focus Mode is active, a small, discreet indicator appears at the top so you always know it's on.

### App list
All your launchable apps, sorted alphabetically, text-only. A compact, low-profile search bar sits at the top (with a small vector search icon) so you can jump straight to an app by typing part of its name. Apps you've chosen to hide are tucked away behind a small "Hidden Apps" link at the bottom of the list, and Settings is always one tap away at the very end.

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

### More apps
A link at the bottom of Settings pointing to more apps from the same developer.

## Tech stack

- **Kotlin** + **Jetpack Compose** for a fully declarative UI — no XML layouts.
- **AndroidViewModel** + **Kotlin Flows** (`StateFlow`, `combine`) for reactive state management.
- **SharedPreferences** for lightweight, dependency-free persistence (hidden apps, custom labels, recent apps, Focus Mode state).
- **AlarmManager** + a `BroadcastReceiver` to drive Focus Mode's schedule reliably in the background.
- No third-party dependencies beyond AndroidX and Compose — kept intentionally lean.

## Project structure

```
app/src/main/java/br/com/pilovieira/launcher/
├── MainActivity.kt        # All Compose screens: Home, App List, Recents, Settings, etc.
├── LauncherViewModel.kt    # Loads installed apps, custom labels, hidden apps, recents
├── AppInfo.kt              # App data model
├── FocusModeHelper.kt      # Focus Mode scheduling and ringer-mode logic
└── FocusModeReceiver.kt    # Receives boot/time/alarm broadcasts to reapply Focus Mode
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

## Status

This is an actively evolving personal project — simple by design, but not standing still. Contributions and ideas that keep the "no distractions" philosophy intact are welcome.
