# Shared application code

`core/` contains provider parsing, stream resolution, and playback sessions. It must
build without Node or native SDK imports. Supply a `Transport`; keep provider
formats and their regression fixtures here so fixes reach both desktop and mobile.
Use Cheerio’s default HTML parser (including its browser export). The slim parser
handles malformed provider tables differently. Provider scripts remain data, never executable code.

`ui/` contains the Vue app and HLS player. Obtain platform services through
`useSports()`. Keep Electron IPC, native plugins, and fullscreen implementations in
the app workspaces. Preserve desktop keyboard behavior when adding touch layouts.

For channel failures, read [Channel support](../docs/player.md) and the affected
platform's playback guide. Run the shared checks and both application builds after
changing a package boundary. Native playback must also be verified on its platform;
a successful resolver test does not establish video rendering.
