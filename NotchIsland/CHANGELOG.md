# Changelog

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
