# Notch Island

An iOS-style Dynamic Island for Android. A floating pill sits near the camera cutout and morphs
into whatever the phone is doing right now — music, notifications, charging, volume, timers —
then settles back down.

<img src="docs/preview.svg" width="420" alt="Island states: resting pill, compact readout, expanded panel" />

## What it does

**Three shapes.** The island rests as a bare pill, grows into a compact two-sided readout when
something happens, and expands into a full panel when you tap it. Sizes, corners, colour and
motion are all yours to set.

**Live activities, by priority.** Several things can be live at once, so the island runs a
priority queue and shows the winner:

| Activity | Priority | Lives for |
| --- | --- | --- |
| Notification preview | highest | 1.5–12 s (configurable) |
| Unlock confirmation | | 1.4 s |
| Privacy indicator (mic / camera) | | while the sensor is live |
| Volume change | | 1.6 s |
| Ringer mode change | | 1.5 s |
| Low battery warning | | 5 s |
| Charging / unplugged | | 4.5 s |
| Timer countdown | | until it finishes |
| Now playing | lowest | while a media session exists |

Transient activities fade back to whatever sticky activity is underneath — pause a song mid-way
through a notification and the island returns to the now-playing readout, not to nothing.

**Now playing.** Album art on the left, dancing waveform on the right. Expand for a scrubbable
progress bar, previous / play / next, and a media volume slider. The accent colour is pulled out
of the album art with Palette, so the island takes on the colour of whatever is playing.

**Notifications.** App icon, title preview and — when expanded — the body text plus the
notification's own action buttons, an Open button that fires its content intent, and Dismiss.
Per-app blocking with a searchable app list.

**Quick panel.** Expanding the idle island gives you a clock, date, brightness and volume
sliders, and round toggles for flashlight, Wi-Fi, Bluetooth, Do Not Disturb, ringer mode,
auto-rotate and app settings. Toggles that Android reserves for the system open the matching
settings panel instead of failing silently.

**Timers.** Start one from the app; it takes over the island with a countdown ring and
pause / +1 min / cancel controls.

**Gestures.** Tap, double tap, long press and all four swipes are individually remappable to
twelve actions (expand, collapse, play/pause, next, previous, flashlight, cycle ringer, open the
app, open the last notification, show the quick panel, hide for 30 seconds, or nothing).

## The app

Four tabs plus two sub-screens, all Material 3 Compose:

- **Island** — master switch, a live interactive preview of the three states, a permission
  checklist with one-tap grant buttons, and test actions.
- **Activities** — a switch per live activity, notification preview style and duration, blocked
  apps, and behaviour (always-on pill, hide in landscape, lock screen, dim when expanded, start
  on boot).
- **Look** — resting/compact/expanded widths, height, corner radius, X/Y offset, background and
  accent colour, opacity, outline, shadow, album-art tinting, animation speed, app theme.
- **Gestures** — the seven gesture mappings, haptics and strength, auto-collapse delay.
- **Blocked apps** — searchable list of launchable apps.
- **About** — how priority works, battery optimisation, privacy.

Every setting is stored in DataStore and collected by both the app and the overlay service, so
changes land on screen as you drag the slider.

## Permissions

| Permission | Needed for | Required |
| --- | --- | --- |
| Display over other apps | drawing the island at all | yes |
| Notification access | media sessions, notification previews | for those features |
| Modify system settings | brightness and auto-rotate toggles | optional |
| Do Not Disturb access | the DND toggle | optional |
| Post notifications | the ongoing service notification | Android 13+ |

Nothing leaves the device. Notification content is rendered straight into the overlay and is
never stored, logged or uploaded.

## Install

Download **`apks/NotchIsland-release.apk`** and install it. Open the app, grant *Display over
other apps*, and flip the master switch.

`apks/NotchIsland-debug.apk` is the debuggable build. It installs alongside the release one
under a separate package id (`…notchisland.debug`), which is handy for development and useless
otherwise.

Minimum Android 8.0 (API 26), targets Android 14 (API 34).

## Updating

The app updates itself. On launch it quietly checks
[`update.json`](https://github.com/Fizghin/Joyboard/blob/notch/update.json) at the repository
root, and when a newer `versionCode` is published it raises a dialog with the release notes and
a single **Update** button. That downloads the APK matching your build type, shows the progress
in the dialog, and hands the file to Android's installer — no browser, no file manager, no
sideloading dance. *Island → Updates* has a **Check** button and a switch to turn the automatic
check off; *About* has the same button.

Two one-time hurdles the first time you update:

- Android asks you to allow Notch Island to install apps. The dialog sends you straight to that
  setting; come back and tap **Try again**.
- If you are coming from 1.0, install 1.1 by hand — 1.0 shipped before the updater existed, and
  1.0's release APK was unsigned. From 1.1 onward it is all in-app.

Releases are signed with the committed key in `keystore/notchisland.jks` so each update installs
over the last one. That key is a distribution convenience for *these* public APKs, not a secret —
if you fork this project, generate your own and point `UpdateService.MANIFEST_URL` at your own
manifest.

To publish an update: bump `versionCode`/`versionName` in `app/build.gradle`, build, copy the
APKs into `apks/`, and update `update.json` with the new version, notes and file sizes.

## Build from source

```bash
export ANDROID_HOME=/path/to/android-sdk    # needs platform 34 + build-tools 34.0.0
cd NotchIsland
gradle assembleDebug          # or: gradle assembleRelease
```

Output lands in `app/build/outputs/apk/`.

## How it is put together

```
NotchIsland/app/src/main/java/com/joyboard/notchisland/
├── data/            IslandSettings + DataStore repository
├── island/
│   ├── IslandController.kt   overlay window, priority queue, all the wiring
│   ├── IslandView.kt         the morphing view: three modes, gestures, panels
│   ├── MediaMonitor.kt       MediaSessionManager + Palette accent extraction
│   ├── SystemMonitors.kt     battery, volume, ringer, screen, mic/camera
│   ├── QuickActions.kt       torch, DND, rotation, brightness, settings panels
│   ├── TimerEngine.kt        countdown live activity
│   ├── WaveformView.kt       the dancing bars
│   └── RingProgressView.kt   charging and timer rings
├── service/
│   ├── NotchOverlayService.kt     foreground service that hosts the overlay
│   ├── NotchNotificationListener.kt
│   ├── IslandBus.kt               listener → controller hand-off
│   └── BootReceiver.kt
├── update/          manifest check, APK download, installer hand-off
└── ui/              Compose app: theme, view model, components, six screens
```

The overlay is a `TYPE_APPLICATION_OVERLAY` window with `WRAP_CONTENT` bounds, so it only
consumes touches where the island actually is — the rest of the status bar keeps working
normally. The window grows and shrinks with the island because the view animates its own layout
params.

## Known limits

- **The island sits just below the status bar, not inside it.** Android layers every app overlay
  *underneath* the system status bar, and the status bar consumes all touches in its own band —
  an island drawn over the camera cutout is visible but completely untappable. *Look → Position →
  Keep clear of the status bar* is on by default for that reason. Turn it off if you want the
  cutout look and are willing to give up touch.
- Wi-Fi and Bluetooth cannot be toggled silently by a normal app on Android 10+; those buttons
  open the system panel.
- Brightness and auto-rotate need *Modify system settings*, which Android grants per app.
- Aggressive battery managers on some OEM skins can stop the overlay service; exclude the app
  from battery optimisation (there is a button in About).
