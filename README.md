# Sports

A personal Android TV sports browser with Soccer and Tennis as the main sections.
Browse current provider listings, search teams and competitions, choose a source,
and watch through native Media3 playback. Tennis Channel +1 is always available
as a shortcut in the Tennis section.

## Build and deploy

Run from this directory. Requires Java 17+, Python 3, and Android SDK 36.
Set `sdk.dir` in your untracked `local.properties`, or configure your Android SDK.

```sh
make build       # Signed release APK: release/Sports.apk
make check       # Parser/network tests, Android lint, debug build
make probe       # Live schedules, Tennis HLS playlist and first media segment
make install     # Build and install on 192.168.1.4
make deploy      # Build, install, and launch on 192.168.1.4
```

`make sports`, `make sports-build`, `make sports-install`, and
`make sports-deploy` mirror MediaStation Music's command naming.

```sh
make deploy ANDROID_TV_DEVICE=192.168.1.4
make deploy ANDROID_TV_DEVICE=emulator-5554
```

Override `ADB` or `ANDROID_SDK_ROOT` when your Android tools are elsewhere.
The first release build generates a dedicated signing identity in `keys/` and
`signing.properties`. Both are ignored by Git. Preserve them for future in-place
updates; a debug-signed emulator install is a different signing identity.

## Using it

- Left/right: move between sports or cards within a competition.
- Up/down: move between controls and competition rows.
- Select: open a match, then choose a source.
- Back: player → sources → browser → exit.
- Search: filter the loaded sport's match titles and competitions.
- Refresh: reload the provider's schedule.
- Player controls: pause/resume, reconnect, or return to sources.

Soccer timestamps are converted to the TV's local time. Tennis rows without an
absolute timestamp retain the source's UTC+1 time and are not labeled live or
assigned an invented date. Listings are not a guarantee that a source is online.

## Design

Matches `~/src/mediastation/android/music`:

- Navy surface gradient: `#07111D` → `#09121C` → `#02060B`.
- Lato regular/bold typography (font license in `licenses/Lato-OFL.txt`).
- Blue `#3498DA` focus borders and 7dp rounded cards.
- 56dp breadcrumb header, 36dp side spacing, horizontal competition rows.
- Muted blue-gray metadata and original locally drawn sports artwork.

## Structure

- `app/`: TV browsing, source selection, native playback, lifecycle, retries.
- `core/`: HTML catalog parsing, cancellable HTTP, stream resolution.
- `tools/ensure_signing.py`: creates the local release signing identity once.
- `Makefile`: build/check/probe/install/deploy, defaulting to the Sony TV.

The source adapter reads HTML as data. It follows supported embedded players and
extracts a fresh HLS URL without executing website scripts or loading web ads.
Supported patterns include wikisport frames, igniteandship's channel embed,
simple direct HLS player configurations, and the inspected character-array URL
format. Other provider formats need additional adapters and show an unavailable
message. The source URL and required headers are passed to Media3 for both
playlists and media segments. URLs refresh shortly before their signed expiry,
and playback failures trigger three bounded reconnection attempts.

The app contains no WebView, advertising SDK, server, or P2P transport. Commercials
already present in the broadcast remain. Signed media URLs are not persisted or
logged; diagnostics only record provider hosts and error classes.

## Verification — September 13, 2026

- 8 parser/network tests passed, including cancellation and source alternatives.
- Debug and signed release builds succeeded; Android lint reported zero errors.
- Live probe read 24 Soccer listings (45 links) and 7 Tennis listings (14 links).
- Tennis Channel +1 resolved to a valid signed HLS playlist; a media segment
  returned HTTP 200 and a valid MPEG-TS sync byte.
- Installed and launched version 0.1.0 on the Sony BRAVIA at 192.168.1.4.
- TV source-selection screen visually inspected in the emulator.
- Sustained native playback, scheduled renewal, and all Soccer source providers
  still require device testing. The saved emulator snapshot had an August clock,
  which predates the providers' current certificates; the physical TV clock was
  verified current. No certificate validation is disabled.
