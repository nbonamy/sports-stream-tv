# Sports for mobile

Live sports and TV on iPhone and Android, using the same Vue interface and stream
resolver as [Sports for Mac](../electron/README.md). Streams are fetched directly
on the device; no hosted backend is required.

## Development

Install Node.js 22.19 or later and run `npm ci` from the repository root.
From `mobile/`:

```sh
npm run ios       # Build, sync, and open the iOS project in Xcode
npm run android   # Build, sync, and open the Android project in Android Studio
```

Choose a simulator or device and run the app in the native IDE. For a physical
iPhone, select your development team in Xcode's Signing & Capabilities settings.
The iPhone app requires iOS 17.4 or later; build it with Xcode 26 or later.
Android builds require Java 21 and Android SDK 36.

```sh
npm run dev       # Browser layout preview with Vue hot reload
npm run check     # Type checking, transport tests, and web build
npm run sync      # Rebuild and copy web assets into both native projects
```

The browser preview can display the interface, but provider requests require the
native app. After changing shared code, sync before running the native build again.

## Controls

Tap a sport, event, and channel to play. Use the side arrows to change streams,
LIVE to catch up, and the fullscreen button to expand playback. Back returns to
channels. Drag right from the left edge to reveal the previous screen; release
past a third of the screen to go back, or release earlier to cancel.
Loading uses a blue animation; errors use red and offer Retry.

## Code

- `../packages/ui/` — shared Vue screens, responsive styles, and player.
- `../packages/core/` — shared catalog, decoders, resolution, and playback sessions.
- `src/` — native bridge adapter and mobile fullscreen behavior.
- `ios/`, `android/` — native HTTP implementations and app projects.

For failing channels, read [Channel support](../docs/player.md) and
[Mobile playback](docs/player.md). Contribution guidance is in [AGENTS.md](AGENTS.md).
The launcher artwork comes from the Android TV vector; `python3 scripts/icons.py`
regenerates the phone icons using `rsvg-convert`.
