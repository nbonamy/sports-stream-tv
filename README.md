# Sports

Live sports and live TV, with clear schedules, channel selection, and fullscreen playback.

![Sports on Android TV](docs/screenshots/home-livetv.png)

## Versions

| Platform | Status | Setup and usage |
| --- | --- | --- |
| Android TV | Available | [Android README](android/README.md) |
| macOS / Electron | Implementation pending | [Desktop README](electron/README.md) |

## Features


- **Sports browsing** — Football, Tennis, Rugby, F1, Golf, NFL, NBA, and MLB on
  the home screen. More includes NHL, MMA, Boxing, Motorsport, College Football,
  Basketball, Volleyball, and Handball.
- **Clear schedules** — separate Current and Upcoming sections, local start
  times, countdowns, and league artwork.
- **LiveTV** — a country-based channel directory, with France, the United States,
  and the United Kingdom first.
- **Stream switching** — move between a channel's available streams from the player.
- **Back to live** — select LIVE to catch up after pausing or falling behind.
- **Made for a remote** — large tiles, visible focus, and Back navigation that
  returns you to your previous selection.
- **Native playback** — fullscreen video without the provider's web page or pop-ups.

Listings come from [FSL](https://freestreams-live1h.pk/). Channel availability
can vary, and commercials within broadcasts remain. The Current section is based
on scheduled start times and estimated event durations, rather than a live-status feed.

## Repository

- `android/` — Android app, Kotlin provider resolver, Gradle, and device tooling.
- `electron/` — desktop app workspace.
- `assets/sports/` — shared sport icons, consumed directly by the Android build.
- `docs/` — shared channel-support and artwork guidance.
- `android/docs/` — Android playback and device checks. Each platform keeps its own specific docs.

The root Makefile forwards Android commands, so `make check`, `make build`,
and `make install ANDROID_TV_DEVICE=…` still work from here. Android build output,
local SDK configuration, and signing files live under `android/`.

## Development

Read [AGENTS.md](AGENTS.md) and the target platform's AGENTS.md before making changes.
For non-working channels, use [Channel support](docs/player.md). For new icons, use
[Artwork](docs/artwork/README.md).

The bundled Lato font is distributed under the [SIL Open Font License](licenses/Lato-OFL.txt).
