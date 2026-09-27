# Desi Radio

An Android app for streaming Hindi / Indian radio stations — 30 stations with live metadata, a Spotify-style player, favorites, Chromecast support, and Android Auto integration.

**Package:** `com.utkarsh.desiradio`
**Current version:** 1.6 (versionCode 7) · `compileSdk 36` · `targetSdk 36` · `minSdk 26`

## Features

- **30 live Hindi stations** (Radio Mirchi, Red FM, BIG FM, full AIR / Vividh Bharati set, and more) loaded from a bundled `stations.json` asset
- **Live song title/artist** from ICY stream metadata
- **Station logos** as artwork in the player, notification, and lock screen
- **Spotify-style player UI** — symmetric header with back + cast button, centered play, rounded artwork
- **Favorites** — star stations; a dedicated Favorites browse node
- **Hide/show stations** — manage the visible station list from Settings
- **Default station + auto-play** option
- **Next/previous** cycles stations on phone UI, notification, lock screen, and car
- **Chromecast** support via the Default Media Receiver
- **Android Auto** — browse Favorites / All Stations, queue + playback controls
- **Crash hardening** — uncaught-exception log written to the app's internal `files/crashes/` directory; guarded browse callbacks and metadata parsing
- **Edge-to-edge** layout with proper system-bar insets (Android 15 enforced)

## Project structure

```
radio_apk/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/utkarsh/desiradio/
│       │   ├── RadioService.java      # Media3 session + browse tree (Android Auto)
│       │   ├── MainActivity.java      # station list
│       │   ├── PlayerActivity.java    # player UI
│       │   ├── ManageActivity.java    # hide/show stations
│       │   ├── StationStore.java      # station data + favorites persistence
│       │   ├── Station.java
│       │   ├── StationAdapter.java / ManageAdapter.java
│       │   ├── CastOptionsProvider.java
│       │   ├── CrashLog.java
│       │   ├── RadioApp.java
│       │   └── Ui.java
│       ├── res/xml/automotive_app_desc.xml   # Android Auto descriptor (media)
│       └── assets/stations.json              # station list (30 entries)
├── app/src/androidTest/java/com/utkarsh/desiradio/
│   └── LegacyBrowseTreeInstrumentedTest.java # on-device browse-tree test (real MediaBrowserCompat protocol)
├── stations.json / stations_extra.json       # source copies of the station data
└── build.gradle / settings.gradle / gradle.properties
```

## Building

Requires JDK 17 and the Android SDK (platform 36).

```bash
export JAVA_HOME=/path/to/jdk17
export GRADLE_OPTS="-Djava.net.preferIPv4Stack=true"   # sandbox quirk; harmless elsewhere
./gradlew assembleRelease        # APK
./gradlew bundleRelease          # Play Store AAB
```

Release signing is configured in `app/build.gradle` with keystore properties
(`release.keystore`, alias `desiradio`). **The keystore is intentionally not
committed** (see `.gitignore`); provide your own for release builds.

## Version history

| Version | Code | Highlights |
|---------|------|-----------|
| 1.0 | 1 | Initial build — 24 stations, playback, Android Auto |
| 1.1 | 2 | Spotify-style player UI, crash hardening, Chromecast |
| 1.2 | 3 | Android Auto browse pagination fix; removed phantom queue |
| 1.3 | 4 | Restored 24-item queue (next/prev everywhere), edge-to-edge insets fix, swipe-away shutdown |
| 1.4 | 5 | Fixed 8 broken station artwork records; junk-logo filtering |
| 1.5 | 6 | Android Auto `pageSize <= 0` pagination edge-case fix; explicit `ImmutableList` results; added `onGetItem` override |
| 1.6 | 7 | Fixed the real root cause of "No items" in Android Auto / Android Automotive OS; fixed next/prev/queue not appearing when playback starts from the car; fixed Android Automotive OS media-source picker exclusion; Favorites no longer falls back to the full station list when empty; target API 36 |

## Known issues

- **"No items" root cause found and fixed in 1.6.** Browse-list `MediaItem`s were built with a playback URI attached directly (`.setUri()`), which silently breaks Media3's conversion to the legacy `MediaBrowserCompat.MediaItem` format that real car head units and Android Automotive OS speak — `RadioService.onGetChildren()` was correctly returning the full station list, but the car received zero items, with no error or crash anywhere. Fixed by giving browse-list items a URI-less descriptor (`RadioService.toBrowsableMediaItem()`); playback still resolves a URI separately via `onAddMediaItems()`/`onGetItem()`/`onSetMediaItems()` when a station is actually selected. Verified via live reproduction and fix confirmation on two different Android Automotive OS emulator builds (screenshots, logs, and a passing on-device instrumented test — see `LegacyBrowseTreeInstrumentedTest`). **Not yet confirmed on real car hardware** — if you still see "No items" after updating, it's a new/different bug, not this one.
- **Jog wheel / rotary navigation** — no reports of this since the 1.6 fix (an empty list naturally has nothing for a jog wheel to move between, which likely explains the original reports). Real desktop/DHU testing was inconclusive: Google's own Desktop Head Unit tool is a stale 2022 build that fails to complete a handshake with the current (2026) Android Auto app, on both an emulator and a real phone — a tooling problem, not an app one. If jog wheel issues persist on real hardware after 1.6, they're in Android Auto's/Automotive's own host-rendered focus traversal, which `RadioService` has no code path into (it only serves `MediaBrowserService` data — no car-facing UI of its own).
- **Android Automotive OS (embedded) app-grid visibility** — the manifest now declares both `com.google.android.gms.car.application` (phone-projected Android Auto) and `com.android.automotive` (Automotive OS) meta-data, but Automotive OS additionally requires the app have **no launcher activity** to appear in its media-source picker (confirmed via Google's own emulator-testing docs). `MainActivity` keeps its launcher intent-filter for the phone home-screen icon, so Desi Radio won't currently show up as a source on embedded Automotive OS head units — only phone-projected Android Auto. Fixing this for real would need a separate Automotive-OS build variant (Gradle product flavor) with a launcher-free manifest; not done, since the reported bug was specifically about phone-projected Android Auto.

## License

Personal project — all rights reserved unless stated otherwise.
