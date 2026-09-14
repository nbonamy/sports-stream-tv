# Working on Sports

Sports is a native Android TV app. Document its current behavior and maintenance
procedures. Keep README.md focused on the product, installation, and remote controls.

## Product conventions

- Read listings and channel names from the provider. Investigation URLs belong in
  test cases, not permanent channel shortcuts.
- Preserve the selected channel and stream. Start the first stream while discovering
  alternatives; changing streams is an explicit user action.
- Keep player copy minimal. Loading is blue, errors are red, and Retry is text with
  a subtle focused background. Reconnecting overlays the last available video frame.
- Back icons are outside remote focus navigation. Stream arrows appear only when
  that direction has an alternative. LIVE is focusable only when it can return to live.
- `make install` installs without launching; `make deploy` also launches. Use an
  explicit `ANDROID_TV_DEVICE` when targeting an emulator or a particular TV, and
  follow the user's device-testing instructions.

## Code map

| Area | Location |
| --- | --- |
| Listings, iframe discovery, player decoding, HTTP | `core/src/main/kotlin/fr/bonamy/sports/core/` |
| Screens, Media3 playback, controls, artwork mapping | `app/src/main/java/fr/bonamy/sports/` |
| Parser fixtures and live channel probe | `core/src/test/kotlin/fr/bonamy/sports/core/` |
| Android UI checks and local previews | `app/src/test/java/fr/bonamy/sports/` |

## Task guides

- **Channel missing, stream failing, or player support changing:** read
  [Channel support and playback](docs/player.md). It covers failure classification,
  document tracing, supported formats, request headers, decoder implementation,
  regression fixtures, and live verification. A new hostname alone needs no allowlist entry.
- **Playback controls, reconnecting, or URL renewal changing:** read the
  [playback behavior](docs/player.md#playback-behavior) and
  [device and ui checks](docs/player.md#device-and-ui-checks) sections before editing.
- **Artwork being added or changed:** read [Artwork](docs/artwork/README.md).
  Preserve the shared canvas and display dimensions;
  verify actual alpha transparency.

Run `make check` after code changes. For documentation-only changes, check links,
commands, and descriptions against the current code. Keep signed URLs, cookies,
tokens, signing keys, and captured provider HTML out of the repository and logs.
