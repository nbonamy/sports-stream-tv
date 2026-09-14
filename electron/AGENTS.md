# Working on the desktop app

Read the [root conventions](../AGENTS.md) first. Desktop implementation and build
configuration belong under `electron/`; shared artwork stays in `assets/sports/`.

Before implementing provider parsing or playback, read
[Channel support](../docs/player.md). The Android resolver and its regression
fixtures are the reference for supported formats, headers, and selection behavior.

Keep desktop setup and usage in README.md, and Electron-specific maintenance
guidance under `docs/` when implementation needs it. Update these documents with
working commands as the desktop implementation takes shape.
