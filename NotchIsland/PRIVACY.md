# Privacy policy — Notch Island

Last updated: 25 September 2026

Notch Island does not collect, transmit or sell any personal data. There is no analytics SDK, no
advertising SDK, no crash reporting service and no account.

## What the app reads, and why

**Notifications** (via notification access). The app reads the notifications your phone receives
so it can show them in the island, drive the media controls, spot one-time passcodes and offer
inline replies. Notification content is rendered straight into the overlay and held only in
memory. The most recent twelve are kept for the history panel, in memory only, and are gone when
the service stops. Nothing is written to disk and nothing leaves the device.

**Installed apps** (via a scoped `<queries>` declaration for launchable apps). Used only to show
you a list when choosing per-app rules. The list is never transmitted.

**Media sessions.** Track title, artist and album art from whatever is playing, used to draw the
now-playing island. Held in memory only.

**Microphone and camera status** (not their content). The app is told *that* the microphone or
camera became active, the same signal Android's own privacy indicator uses. It never records or
accesses audio or images.

**Battery, volume, ringer and screen state.** Read from system broadcasts to draw the matching
live activities.

## What is stored on your device

Your settings, in the app's private storage. They are included in Android's own backup if you
have that switched on, which means they travel to a new phone through Google's backup service
under Google's terms, not ours. You can export them yourself to a JSON file at any time.

If the app crashes, the stack trace and your device model, Android version and screen size are
written to a log file in the app's private storage so you can report the problem. That file is
never uploaded. You can read, share or delete it from About → Diagnostics.

## Network use

The sideloaded build checks a single URL for a newer version, and downloads an APK when you ask
it to. That request carries nothing but the request itself — no identifiers, no settings, no
usage data. You can switch the check off, point it somewhere else, or use the Play build, which
has no updater and no network permission use at all.

## Permissions

| Permission | Why |
| --- | --- |
| Display over other apps | Draw the island. Without it the app does nothing. |
| Notification access | Media controls, notification previews, replies, passcodes, calls |
| Post notifications | The ongoing notification Android requires for a foreground service |
| Modify system settings | The brightness and auto-rotate toggles, if you use them |
| Do Not Disturb access | The DND toggle, if you use it |
| Vibrate | Haptics |
| Receive boot completed | Bring the island back after a restart, if you asked it to |
| Internet, install packages | Sideloaded build only: the in-app updater |

## Contact

Open an issue on the repository.
