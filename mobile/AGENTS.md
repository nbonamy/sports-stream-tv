# Working on mobile

The Capacitor app shares `../packages/core` and `../packages/ui` with Electron.
`ios/` and `android/` are the phone shells; the root `../android/` is the separate TV app.
Follow the [shared package boundaries](../packages/AGENTS.md).

- Keep parsing and decoder fixes in the shared core; native code only handles transport
  and platform integration. Read [Mobile playback](docs/player.md) for networking,
  cancellation, fullscreen, and device verification.
- Build the web assets and sync before native builds. Run `npm run check` here for
  TypeScript, transport tests, and the bundled app; compile each affected native target.
- Keep iPhone safe areas, touch targets, portrait and landscape layouts working.
  Reuse shared artwork. The iPhone target requires iOS 17.4 or later for HLS.js/MMS.
- Keep signing identities local to Xcode/Gradle. Generated public assets and Capacitor
  config files stay ignored; edit the source config and run sync.
- Provider URLs and headers may contain credentials. Log only sanitized status codes,
  sizes, and timings during investigation, and remove temporary diagnostics afterward.
