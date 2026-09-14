# Working on Sports

Sports has Android TV, Electron, and Capacitor mobile workspaces. Electron and mobile
share TypeScript and Vue packages under `packages/`. Keep the root README focused
on the product and platform entry points; each platform owns its setup and usage docs.

## Shared conventions

- Read listings and channel names from the provider. Investigation URLs belong in
  regression fixtures, not permanent channel shortcuts.
- Preserve the selected channel and stream. Start the first stream while discovering
  alternatives; changing streams is an explicit user action.
- Keep player copy minimal. Loading is blue, errors are red, and reconnecting overlays
  the last available video frame. Show stream arrows only when that direction is available.
- Keep signed URLs, cookies, tokens, signing keys, and captured provider HTML out of
  the repository and logs.

The provider base URL has one source: `config/provider.json`. Android generates its
Kotlin constant from this file; the shared TypeScript core imports it at build time.

## Task guides

- **Android code, builds, or deployment:** read [android/AGENTS.md](android/AGENTS.md).
- **Shared Vue or TypeScript:** read [packages/AGENTS.md](packages/AGENTS.md).
- **Mobile implementation or builds:** read [mobile/AGENTS.md](mobile/AGENTS.md).
- **Desktop implementation:** read [electron/AGENTS.md](electron/AGENTS.md).
- **Missing channels, failing streams, or decoder support:** read
  [Channel support](docs/player.md). It covers tracing, formats, headers, regression
  fixtures, and verification. New hostnames alone do not need allowlist entries.
- **Artwork:** read [Artwork](docs/artwork/README.md). Use the shared PNGs in
  `assets/sports/`; preserve their dimensions and actual alpha transparency.

Keep cross-platform knowledge in `docs/`, and platform-specific guidance in that
platform's `docs/`. Link to the authoritative document instead of duplicating it.
Run the affected platform's checks after code or build changes. For docs-only changes,
verify links and commands against the current tree.
