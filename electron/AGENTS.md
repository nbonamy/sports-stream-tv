# Working on the desktop app

Follow the [root conventions](../AGENTS.md). The desktop app uses Vue 3 and TypeScript.
Preserve the Android design: shared artwork, Lato, navy backgrounds, clear focus,
full-width schedule rows, and minimal player copy.

## Code and checks

- `src/main/` owns Node HTTP, Electron IPC, and the desktop window.
- `src/renderer/` injects the preload services into the shared Vue application.
- Follow [shared package guidance](../packages/AGENTS.md) for UI, provider parsing,
  resolution, or playback-session changes.
- Run `npm run check` from `electron/` after code changes. Use the scripts in
  package.json for running and packaging the app.

## Before changing behavior

- **Channel failures or decoder support:** read the shared [channel support guide](../docs/player.md)
  and [desktop playback notes](docs/player.md). The Android resolver fixtures also
  document supported formats. Keep ported fixes consistent where both platforms apply.
- **IPC, HLS loading, or recovery:** read [desktop playback notes](docs/player.md).
  Pass plain data across the bridge, retain the media request headers, and distinguish
  completed loader cleanup from cancellation.
- **Signing or distribution:** read [the release instructions](README.md#build-a-mac-app).
  `.env` contains private credentials. Load it without displaying it and keep it out
  of artifacts. Verify signing and Gatekeeper acceptance before calling a release ready.

On desktop, shared provider parsing runs in the main process. Keep the renderer sandboxed,
context-isolated, and limited to the explicit preload API. Packaged code loads from
`sports://app/`; restrict any development origin to the development runner.
