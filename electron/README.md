# Sports for Mac

Browse live sports and TV channels, choose a stream, and watch on your Mac.
The desktop app uses the same artwork and layout as [Sports for Android TV](../android/README.md).

## Run

Install Node.js 22.19 or later, then run from `electron/`:

```sh
npm ci
npm start
```

## Controls

- Click tiles to browse; arrow keys and Enter also work.
- Back, Escape, or Backspace returns to the previous screen. Leaving the player exits fullscreen.
- Left/Right switches streams. Space plays or pauses.
- Select LIVE, or press L, to return to the live edge.
- Press F, double-click the video, or use the fullscreen button to toggle fullscreen.
- Volume and mute controls are at the bottom of the player.

## Build a Mac app

```sh
npm run pack
```

The local app is written to `out/mac-arm64/Sports.app` on Apple Silicon
(`out/mac/Sports.app` on Intel). It can be copied to Applications.

For a signed and notarized release, configure the local `.env` with
`IDENTIFY_DARWIN_CODE`, `APPLE_ID`, `APPLE_PASSWORD`, and `APPLE_TEAM_ID`, and make
sure the signing certificate is in your macOS Keychain. Then run:

```sh
npm run release
```

This signs the app, submits it to Apple, staples the notarization ticket, checks
Gatekeeper acceptance, and creates `out/Sports-mac-arm64.zip` (or `x64`). Signing
settings stay local and are excluded from Git and the packaged app.

## Development

```sh
npm run dev    # Vue hot reload; restart automatically for main/preload changes
npm run check  # Type checking, tests, and production build
```

The dev runner uses a local server and temporary app profile. Ctrl+C stops the
app and its watchers.

For non-working channels, read the shared [channel support guide](../docs/player.md)
and [desktop playback notes](docs/player.md). Repository conventions are in
[AGENTS.md](AGENTS.md).
