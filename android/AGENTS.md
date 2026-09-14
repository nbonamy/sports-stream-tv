# Working on Android TV

Follow the [root conventions](../AGENTS.md). Run the commands here from `android/`;
the repository root Makefile also forwards Android targets.

## Android conventions

- Back icons stay outside remote focus navigation. LIVE is focusable only when it can
  return to live. Retry is text with a subtle focused background.
- `make install` installs without launching; `make deploy` also launches. Use an
  explicit `ANDROID_TV_DEVICE` and follow the user's device-testing instructions.
- Preserve `keys/sports.jks` and `signing.properties`; they are the existing release
  signing identity. Keep local SDK configuration in `local.properties`.
- Shared sport artwork is an additional Gradle resource directory at `../assets/sports/`.
  Update the shared source rather than adding platform copies.

## Code and maintenance

- `core/src/main/kotlin/fr/bonamy/sports/core/` contains provider listings, HTTP,
  iframe traversal, and player decoders. Before changing these, read the shared
  [channel support guide](../docs/player.md).
- `app/src/main/java/fr/bonamy/sports/` contains screens and Media3 playback.
  For playback controls, reconnecting, renewal, or device checks, read
  [Android playback](docs/player.md).
- Parser fixtures live in `core/src/test/`; Android UI tests live in `app/src/test/`.
  Run `make check` after code or build changes.
