# Privacy policy — Notch Island

Last updated: 27 September 2026 (2.4)

Notch Island has no servers and collects nothing: no analytics SDK, no advertising SDK, no crash
reporting service and no account. Two optional features, both off unless you turn them on, ask
public services for data straight from your phone; they are described under *Network use*.

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
live activities, including the battery's temperature for the heat warning.

**How much data is moving** (only if you switch on *Network speed*). The phone's running totals of
bytes received and sent, read every second and a half while the screen is on, to show a transfer
rate. Nothing about what is transferred, or by which app, is read.

**Calendar** (only if you switch on *Next calendar event*). The title, time and place of events in
roughly the next hour, read to count down to the next one. Held in memory only, never written
anywhere, never sent.

**Which app is in front, and whether a keyboard or the notification shade is open** (sideloaded
build, only if you turn on the helper). The optional helper is an accessibility service used for
exactly these facts, so the island can hide in apps you choose, while you type and while the
shade is down — and for its overlay window, which lets the island be drawn above the status bar.
It does not read what is on screen — no text, no views — and nothing it learns is stored or sent.
The Play build does not include it.

**Approximate location** (only if you switch on *Weather*). Read once when you switch weather on,
and again when you open the app, while it is on screen — never in the background. It is rounded
to one decimal place (about 10 km) before it is stored, and only that rounded area is used.
Turning weather off deletes it.

**Messages from other apps** (only if you switch on *Automation*). Text another app asks the
island to show, held in memory until it times out.

## What is stored on your device

Your settings, in the app's private storage — including the text of a pinned note, if you write
one. They are included in Android's own backup if you
have that switched on, which means they travel to a new phone through Google's backup service
under Google's terms, not ours. You can export them yourself to a JSON file at any time.

If the app crashes, the stack trace and your device model, Android version and screen size are
written to a log file in the app's private storage so you can report the problem. That file is
never uploaded. You can read, share or delete it from About → Diagnostics.

## Network use

The sideloaded build checks a single URL for a newer version, and downloads an APK when you ask
it to. That request carries nothing but the request itself — no identifiers, no settings, no
usage data. You can switch the check off, point it somewhere else, or use the Play build, which
has no updater.

**Synced lyrics** (off unless you turn it on) sends the playing song's title, artist, album and
length to [LRCLIB](https://lrclib.net) to find its lyrics. Each song is looked up once per session
of the island.

**Weather** (off unless you turn it on) sends your rounded area and temperature unit to
[Open-Meteo](https://open-meteo.com) about every half hour while the screen is on.

Those services see the request as any website would, including your IP address; their own
privacy policies apply to it. Nothing about you beyond what is listed here is included.

## Permissions

| Permission | Why |
| --- | --- |
| Display over other apps | Draw the island. Without it the app does nothing. |
| Notification access | Media controls, notification previews, replies, passcodes, calls |
| Post notifications | The ongoing notification Android requires for a foreground service |
| Modify system settings | The brightness and auto-rotate toggles, if you use them |
| Do Not Disturb access | The DND toggle, if you use it |
| Read calendar | The next-event countdown, if you switch it on |
| Approximate location | The weather's area, if you switch weather on; only while the app is open |
| Accessibility service | Sideloaded build only: drawing above the status bar, and hiding in chosen apps and while typing, if you turn it on |
| Vibrate | Haptics |
| Receive boot completed | Bring the island back after a restart, if you asked it to |
| Internet, install packages | Sideloaded build only: the in-app updater |

## Contact

Open an issue on the repository.
