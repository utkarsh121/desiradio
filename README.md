# Desi Radio

An Android app for streaming Hindi / Indian radio stations — 30 stations with live metadata, a Spotify-style player, favorites, Chromecast support, and Android Auto integration.

**Package:** `com.utkarsh.desiradio`
**Current version:** 1.5 (versionCode 6) · `compileSdk 35` · `targetSdk 35` · `minSdk 26`

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
├── stations.json / stations_extra.json       # source copies of the station data
└── build.gradle / settings.gradle / gradle.properties
```

## Building

Requires JDK 17 and the Android SDK (platform 35).

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

## Known issues (under investigation)

- Some head units show **"No items"** in Android Auto and the **jog wheel / rotary focus** doesn't respond. v1.5 addresses a pagination edge case believed to be the cause (`onGetChildren` returning 24 items for a `pageSize <= 0` request, which Media3's `verifyResultItems` rejects), but this needs confirmation on real car hardware.
- If you fix this, the likely area is `RadioService.onGetChildren()` / `onGetItem()` and the browse-tree construction.

## License

Personal project — all rights reserved unless stated otherwise.
