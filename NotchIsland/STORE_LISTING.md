# Play Store listing

Use the `play` flavour: `gradle bundlePlayRelease`. It has no in-app updater and does not request
`REQUEST_INSTALL_PACKAGES`.

## Title (30 characters)

Notch Island

## Short description (80 characters)

A Dynamic Island for Android: music, calls, alerts and timers in one live pill.

## Full description

Notch Island puts a floating pill near your camera cutout and lets whatever your phone is doing
take it over — the way the Dynamic Island does.

**Live activities, by priority — and two at once**
Calls outrank everything and stay until the call ends. Notifications, unlock confirmations,
privacy indicators, volume and ringer changes pass through. Music, timers, the stopwatch,
directions and downloads sit underneath and come back when the island is free again. When two
of them run at once, the second waits in a bubble beside the island — tap it to swap.

**Turn-by-turn directions**
The next turn from Google Maps, Waze and other maps apps: the arrow, how far, and when you will
arrive.

**Music that actually works**
Album art, a dancing waveform, a scrubbable progress bar, transport controls, what is up next and
a button for where the sound plays. The accent colour can be pulled out of the album art, taken
from your wallpaper with Material You, or picked by hand.

**Your next meeting, counting down**
Optional: the island counts down to your next calendar event and opens it with a tap.

**Lyrics and weather, if you want them**
Synced lyrics in the music panel, and the weather in the quick panel with a heads-up before
rain. Both optional, both off until you turn them on.

**Small things that help**
One-tap 1, 5, 10 and 25-minute timers. A warning when the battery runs hot. The network speed
while something big downloads. A note pinned to the island until you tick it off.

**Out of the way when it should be**
The island steps aside for full-screen videos and games, for quiet hours and in landscape — and a
call still comes through.

**Notifications it does something with**
Reply to a message without leaving the island. Copy a one-time passcode with a single tap. Answer
or decline a call. Watch a download's progress. Pull the last dozen notifications back up.

**Shaped to your phone**
Start from a device preset, or use the calibrator: a full-screen aligner that lets you drag the
pill straight onto your real camera cutout, wherever the maker put it. Width, height, corner,
offsets, colour, opacity, outline, shadow and animation speed are all yours.

**Yours to drive**
Tap, double tap, long press and all four swipes remap to a dozen actions. A tap can step through
the sizes or open everything at once. There is a quick settings tile, launcher shortcuts, and an
opt-in intent for Tasker and other automation apps to post their own messages.

**Honest about one thing**
Android layers app overlays below the status bar, and the status bar swallows touches in its own
band. Notch Island can draw over it for the look, and hangs a small transparent strip below to
catch the taps. No app without system privileges can do better.

No account, no analytics, no ads. Lyrics and weather are optional and off until you turn them on;
everything else stays on your phone.

## Data safety declaration

- Data collected by the developer: none — there are no servers.
- Shared with third parties, only when the user turns the feature on: approximate location
  (rounded to about 10 km) with Open-Meteo for weather; the playing song's title, artist, album
  and length with LRCLIB for lyrics. Declare these as shared, optional, for app functionality.
- Data encrypted in transit: yes — both services are reached over HTTPS.
- Users can request deletion: turning weather off deletes the stored area; turning lyrics off
  stops lookups. Nothing is held anywhere but the phone.

Declare notification access under the Play policy for notification listeners: the app reads
notifications to display them in its overlay and to power its media controls, and the data is
neither stored nor transmitted.

Calendar (READ_CALENDAR) is requested only when the user switches on the next-event countdown;
event details are read on the device to draw it and are neither stored nor transmitted. The Play
build contains no accessibility service.

## Content rating

Everyone. No user-generated content, no communication features, no purchases.
