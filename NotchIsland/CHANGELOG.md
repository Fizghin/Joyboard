# Changelog

## 2.4.1

- Fixed: turning on the helper made the island disappear. A window above the status bar is never
  told about the status bar, so the island read that as a full-screen app and hid itself. Full
  screen is now watched from a 1-pixel probe below the bar, where the reading is true.
- The notification-shade check is stricter — it needs a focused system window covering most of
  the screen — so other system windows cannot hide the island either.
- About → Share diagnostics now says whether the helper is connected, whether the island is above
  the status bar, and why it is hidden when it is.

## 2.4

- **Synced lyrics** in the open media panel — the line being sung and the next one — from LRCLIB.
  Off by default; only the song's title, artist, album and length are sent.
- **Weather** in the quick panel, with a *Rain soon* heads-up when rain is due within the hour,
  from Open-Meteo. Off by default; your area is rounded to about 10 km and only taken while the
  app is open.
- The settings preview no longer recomposes while offset sliders are dragged.
- Privacy policy, store listing and in-app wording say exactly what the two online features send.
- 163 tests.

## 2.3

New
- **Above the status bar.** With the helper on (sideloaded build), the island is drawn in an
  accessibility overlay, which Android layers above the status bar: notification icons now go
  under the island instead of across it, the whole island takes taps with no touch strip, and it
  steps aside while the shade is pulled down. The helper dialog explains Android 13's
  "restricted settings" step and links to App info.
- **Presets set the whole shape.** A device preset — and *Detect* and *Fit to my camera* — now set
  every size: the resting pill, corner, offsets, and the compact, small-card and open widths,
  scaled from the iPhone's proportions to the camera and screen.
- **Staying out of the way.** The island hides when a video or game goes full screen (no
  permission needed), and — in the sideloaded build, through an optional helper — in apps you
  pick and while a keyboard is open. A call always breaks through.
- **Next calendar event** as a live activity: a countdown in the pill, time and place when
  opened, the event on a tap. Opt-in; asks for calendar access when switched on.
- **Media:** what is up next, from the player's queue, and an output button beside the volume
  that opens the system's picker for where audio plays.
- **Automation:** Tasker, Automate and friends can post messages to the island through an
  opt-in broadcast. Text only — another app can never make the island open anything.
- **The camera is hardware.** The punch hole is stored against the screen and the island's
  content is laid out clear of it at every size. *Detect from this phone* reads the real cutout;
  presets cover Pixel 6, Galaxy S23 FE, Honor X9b 5G and many more, matched by model.
- **Foldables:** a hole placed on one screen is not applied to the other; the other screen uses
  its own cutout report.

Fixed
- On Android 14 and later, notification *Open* and action buttons could silently do nothing,
  because the island did not lend its permission to start the app. All sends now do.
- The quick settings tile did nothing below Android 14.
- The island treated every notification as urgent: it now follows the phone's own ranking and
  Do Not Disturb, so *Show silent notifications* finally does something.
- Notification history was kept even with the feature off.
- The expanded media panel could open empty; the waveform colour did not follow the accent.

Under the hood
- Targets Android 16 (API 36) on AGP 9, with the Android 16 back-gesture and background-start
  rules handled. Every library is current.
- Every user-facing string is in `strings.xml`, with plurals and locale-aware clock and date.
- The island is a single accessible control with open, close and media actions for screen
  readers.
- The overlay's window plumbing, panels and presentations are split out of the two largest
  classes; no leaked contexts.
- A startup profile is compiled into the APK.
- 143 tests, JVM and Robolectric, including rendered screenshots; lint reports nothing.

## 2.2.1

Fixes the island not going back to its resting size once opened. Two separate causes:

- The auto-collapse countdown was cancelled and re-posted on *every* render. Anything that
  refreshes — a track's playhead, a download's progress — pushed it back, so with something live
  it never fired. It is now armed once per stage.
- Any sticky activity pinned the island at the compact size permanently, because the target was
  simply "an activity exists, so stay compact". A live activity now earns the compact readout for
  a while after something genuinely happens, then the island settles back to the pill. A progress
  tick on the same notification does not count as something happening. *Gestures → Going back to
  rest* sets the delay, or restores the old always-compact behaviour.

Also adds a guard that re-asserts the size if a cancelled animation leaves the island stuck.

## 2.2

- Punch-hole calibration: a full-screen aligner that draws into the cutout area with the system
  bars hidden, so the island can be dragged onto the real camera on any phone. The stand-in lens
  has its own size and position, for off-centre punch holes.
- Production build: R8 with resource shrinking takes the release APK from 12.6 MB to under 2 MB.
  App classes are kept whole so stack traces stay readable and nothing reflective can be stripped.
- Two distribution flavours. `sideload` keeps the in-app updater; `play` compiles it out and drops
  the restricted `REQUEST_INSTALL_PACKAGES` permission, because Play does not allow it.
- `QUERY_ALL_PACKAGES` replaced with a scoped `<queries>` declaration, and the ungrantable
  `MEDIA_CONTENT_CONTROL` removed.
- Crash log written to private storage, with Share diagnostics and Clear in About.
- Backup and device-transfer rules so settings follow you to a new phone.
- Content descriptions on every icon-only control.
- MIT licence, privacy policy and store listing copy.

## 2.1

- Device presets with real iPhone Dynamic Island measurements, and an iOS mode that uses SwiftUI's
  own damped-spring response rather than an ease curve.
- A tap steps the island up one size at a time; swipe down opens everything. Both configurable.
- New medium "small card" size between compact and fully open.

## 2.0

- Calls, ongoing activities with progress, quick reply, passcode detection, notification history,
  per-app rules, stopwatch, quiet hours, cutout fitting, a quick settings tile, launcher
  shortcuts, and settings backup.
- Pure-logic core extracted and unit tested.

## 1.2

- Material You colours, an "over the status bar" anchor with a touch strip, and an optimisation
  pass.

## 1.1

- Fixed the island not responding to taps, and added the in-app updater.

## 1.0

- First release.
