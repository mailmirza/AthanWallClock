<div align="center">

<img src="design/ic_launcher.png" alt="Athan Clock Icon" width="120" />

# Athan Wall Clock

**A dedicated, full-screen wall clock and prayer time display for Android tablets.**

[![License: MIT](https://img.shields.io/badge/License-MIT-amber.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-blue.svg)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.06.01-brightgreen.svg)](https://developer.android.com/jetpack/compose)

Application ID `com.mailmirza.athanwallclock`

A fork of [Athan Clock](https://github.com/AlmutazYounes/PrayerAthan) by Mutaz Younes, adapted for use in any region.

</div>

---

## Overview

Most prayer apps are built for phones: small fonts, endless menus, widgets that sleep.

Athan Wall Clock is built for wall-mounted 7-inch and 10-inch Android tablets. Always on. Readable from across the room. Dark Mecca wall only.

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Landscape</b></td>
      <td align="center"><b>Portrait</b></td>
    </tr>
    <tr>
      <td><img src="store/listing/play/tablet-10/02-landscape-idle-dark.png" alt="Landscape wall" width="480"/></td>
      <td><img src="store/listing/play/tablet-10/01-portrait-idle-dark.png" alt="Portrait wall" width="280"/></td>
    </tr>
  </table>
</div>

Landscape: horseshoe arc clock on the left, spaced prayer list on the right. Portrait: arc hero above a 2×3 prayer grid. Both show location, weather, date, and a live countdown to the next prayer.

---

## Highlights

- **Arc timer wall.** Current time sits inside a gold horseshoe ring that drains toward the next prayer. Countdown and `NEXT …` sit in the notch.
- **Both orientations.** Native landscape split and portrait stack. No stretched phone UI.
- **Offline prayer math.** [`adhan-kotlin`](https://github.com/batoulapps/adhan-kotlin) with ten calculation methods (Dubai, Umm al-Qura, Karachi, ISNA, Muslim World League and more) and Shafi or Hanafi Asr. Auto picks the method for the saved location's region. No internet required after location is saved.
- **Athan audio.** One bundled recording, or your own audio file, for Fajr through Isha on the alarm stream. Per-prayer mute and volume. Tap or volume-down to stop. Sunrise stays silent.
- **Hourly athkar.** Optional daytime dhikr (8 AM–9 PM, Fajr to Isha). Athan wins if both fire.
- **Keep awake.** `FLAG_KEEP_SCREEN_ON` while the wall is visible. Night blackout 11 PM–4 AM with tap-to-wake.
- **City search + one GPS fix.** Bundled GeoNames catalog. First launch asks for location; deny keeps Albany, NY.
- **Ambient weather.** Open-Meteo temperature and condition in the header.
- **Privacy.** No accounts, ads, trackers, or backend.

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Adhan playing (landscape)</b></td>
      <td align="center"><b>Adhan playing (portrait)</b></td>
    </tr>
    <tr>
      <td><img src="store/listing/play/tablet-10/04-landscape-athan-dark.png" alt="Adhan playing landscape" width="480"/></td>
      <td><img src="store/listing/play/tablet-10/03-portrait-athan-dark.png" alt="Adhan playing portrait" width="280"/></td>
    </tr>
  </table>
</div>

---

## Architecture

```
app/src/main/java/com/mutazyounes/prayerathan/
├── MainActivity.kt          # Host, keep-awake, orientation
├── engine/                  # Pure Kotlin prayer math, cities, NTP wall time
├── audio/                   # AlarmManager, AthanService, MediaPlayer, athkar
├── ui/                      # Compose wall, settings, themes
├── shell/                   # BootReceiver, GPS-once, NTP client
└── weather/                 # Open-Meteo client
```

Specs live in `PROJECT.md` and `DESIGN.md`. Agent standing orders: `AGENTS.md`, `CLAUDE.md`.

---

## Build

Every push builds a debug APK on GitHub. Open the **Actions** tab, pick the latest **Build APK** run, and download `AthanWallClock-debug-apk`.

To build locally:

```bash
git clone https://github.com/mailmirza/AthanWallClock.git
cd AthanWallClock
./gradlew test
./gradlew assembleDebug
```

---

## License

App code is [MIT](LICENSE), copyright Mutaz Younes for the original Athan Clock. The bundled athan MP3 is personal-use only and must be replaced with a cleared recording before any public release. See `audio/SOURCE.md`.
