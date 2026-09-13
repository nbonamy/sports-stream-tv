# Sports

A personal native Android TV sports browser. Start with illustrated sport tiles,
browse current and upcoming events, choose a channel, and watch with Media3.

Home rows: **Football, Tennis, Rugby, F1, Golf**, then **NFL, NBA, MLB, NHL, + More**.
More opens MMA, Boxing, Motorsport, College Football, Basketball,
Volleyball, and Handball. Tennis Channel +1 remains a permanent channel shortcut.

## Build and deploy

Requires Java 17+, Python 3, and Android SDK 36. Set `sdk.dir` in untracked
`local.properties`, or configure your Android SDK.

```sh
make build       # Signed release APK: release/Sports.apk
make check       # Core tests, Android lint, debug build
make probe       # Live schedules, selectable Tennis streams and media probe
make install     # Build and install on 192.168.1.4
make deploy      # Build, install, and launch on 192.168.1.4
```

The `sports`, `sports-build`, `sports-install`, and `sports-deploy` aliases mirror
MediaStation Music. Override `ANDROID_TV_DEVICE`, `ADB`, or `ANDROID_SDK_ROOT`
as needed. Emulator example: `make deploy ANDROID_TV_DEVICE=emulator-5554`.
A debug install uses a different signing identity than the release build.

The first release build generates `keys/sports.jks` and `signing.properties`.
Both are ignored by Git. Preserve both for future in-place updates.

## Navigation

- Home: arrow keys browse sport tiles; OK opens the sport's schedule.
- Schedule: Current and Upcoming are clearly labeled sections in one vertical list
  of full-width event cards. League icons come from the provider. Refresh reloads
  the feed, with pulsing event placeholders while it loads; a compact breadcrumb
  Back control returns to the sport home.
- Event: the league logo appears in the header when available, with the sport
  icon as fallback. Choose a channel; named numbered alternatives are grouped together.
  Generic provider links use their channel page names derived from the URL.
- Player: starts Stream 1 immediately, discovers alternatives in the background.
  Left/Right switches streams and wraps at either end. Up focuses Back; Down
  focuses pause/play or retry. OK activates the focused control, or toggles
  playback when controls are hidden. Controls fade after three seconds of playback.
  Connecting uses a quiet animation; unavailable streams show a compact retry control.
- Back: player → channels → schedule → sport home → exit.
  Returning to a schedule restores event focus and scroll position.

## Schedule timing

Upcoming events show local start times and countdowns such as `in 1h29`, updated
every 15 seconds. Events move between sections without refreshing the page.

The provider does not reliably supply end times or live status. **Current uses
an estimated window after the scheduled start**, stated on the screen: Football
and Rugby 3 hours, Tennis 6, NFL and MLB 5, Golf 12, other sports 4. Older events
are hidden from the schedule. This is not confirmation that a match or stream is live.

Absolute provider timestamps take precedence. Dated table schedules combine the
page's date with its UTC+1 time; US sport cards use America/New_York, including
DST. Yearless dates use the nearest year, preserving stale dates. Undated events
remain under Time unconfirmed. Channels appear separately. The NBA page sometimes
lists WNBA games; these are not presented as NBA fixtures.

## Artwork and design

Music's navy background, Lato typography, and blue focus borders are retained.
Ten original sport illustrations were generated with the built-in imagegen tool.
Their backgrounds were removed locally and the cutouts normalized to identical
512 × 384 transparent canvases. Every screen uses a 132 × 96 dp icon box, with
labels laid out separately. The app caches the bundled bitmaps.

- Artwork: `app/src/main/res/drawable-nodpi/sport_*_cutout.png`
- Cutout preparation: [docs/artwork/transparent-icons.md](docs/artwork/transparent-icons.md)
- Exact prompts: [docs/artwork-prompts.md](docs/artwork-prompts.md)
- TV captures: [docs/screenshots](docs/screenshots)
- Lato font license: `licenses/Lato-OFL.txt`

## Source adapters

`core/` parses table schedules and US sport cards, groups channels, discovers
numbered stream alternatives, and resolves fresh HLS URLs without executing
website scripts. `app/` contains the TV navigation, playback, lifecycle, signed
URL renewal, and three bounded reconnection attempts.

Supported player patterns include wikisport frames, igniteandship embeds, direct
HLS configurations, and the inspected character-array URL format. Selecting a
stream follows that stream's embeds; it never silently jumps to Stream 2.
Unsupported or offline sources retain the stream arrows, retry control, and Back to channels.

Required headers are passed to Media3 for playlists, keys, and media segments.
Signed URLs are not persisted or logged. Diagnostics only identify provider hosts
and error classes or safe HTTP errors. The app has no WebView, ad SDK, server,
or P2P transport. Commercials within broadcasts remain.

## Verification — September 13, 2026

- 14 core tests cover parsing, channel grouping, stream ordering, cancellation,
  timezone conversion, year boundaries, schedule transitions, and countdowns.
- Debug/release builds and Android lint pass (zero errors).
- Live schedules parsed for the original eight featured sports; NBA returned zero
  NBA fixtures. The provider has stale WNBA listings on that page.
- Sony TV at `192.168.1.4`: home artwork, schedule sections and countdowns,
  channel selection, and stream selection visually checked.
- Tennis Channel +1 exposes two selectable streams. During this pass both were
  unavailable; the original 0.1.0 resolver also failed against the same provider.
  Earlier 0.1.0 testing had successfully fetched its HLS and a media segment.
- Sustained native playback and scheduled URL renewal remain unverified with the
  current provider failures. The emulator retains an August clock, so use the
  correctly dated physical TV for network verification.

## Player UI preview

The debug build includes a network-free player preview, excluded from release:

```sh
adb shell am start -n fr.bonamy.sports/.PlayerPreviewActivity --es state unavailable
```

States: `connecting`, `playing`, `paused`, `unavailable`; optional `--ei streams 1`
checks a channel without alternatives. This exercises the real player controls
without depending on provider availability. Emulator checks cover circular button
bounds and centered icons, retry, stream wraparound, control fading, pause, and
disabled arrows for single-stream channels. It does not verify media playback.
