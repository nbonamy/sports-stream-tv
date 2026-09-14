# Desktop playback

Use the shared [channel support guide](../../docs/player.md) for provider formats
and investigation. This document covers Electron's playback boundaries.

## Request flow

1. `catalog.ts` reads sports and LiveTV listings. The provider entry point comes from
   the shared `config/provider.json`, through `provider.ts`.
2. `resolver.ts` discovers numbered alternatives separately from resolving the
   selected iframe chain. `decoders.ts` interprets supported data formats.
3. `http.ts` validates public HTTPS destinations, checks DNS during the actual
   connection lookup, follows bounded redirects, and limits request time and size.
   It decodes gzip, deflate, and Brotli responses even when the provider ignores
   `Accept-Encoding: identity`; both transferred and expanded bytes are bounded.
4. `index.ts` keeps each resolved URL's headers in a short-lived playback session.
   The renderer receives the session token and fresh playlist URL.
5. `media-loader.ts` feeds HLS.js with bytes requested through preload IPC. The main
   process supplies the same Referer, Origin, and User-Agent for playlists, encryption
   keys, and video segments. No provider page or advertising script executes in the renderer.

`Player.vue` begins the first stream while discovering alternatives. Switching stream
or leaving the player cancels obsolete resolution and releases its media session.
Reconnection keeps a bounded snapshot in memory, and URL renewal uses the provider's
`expires` value when present. Initial resolution failures go straight to Retry;
playback interruptions use bounded recovery.

Escape uses the native window's fullscreen state: the first press exits fullscreen
while preserving playback; a press in windowed mode returns to channels. Held-key
repeats are ignored. Back and Backspace leave playback directly.

## Empty schedules

Validate that the provider page contains recognizable listings before applying
sport-specific filters. The NBA page can contain only WNBA fixtures: filtering
those out yields an empty NBA schedule, shown with empty Current and Upcoming
sections. A page with no recognizable listings still reports a load failure.
`tests/provider.test.ts` covers both cases.

## Loader and IPC contracts

- Vue reactive objects must be converted to plain records before crossing the
  context bridge. Construct the channel/link payload from its primitive fields.
- HLS.js uses `rangeStart: 0, rangeEnd: 0` for ordinary fragments. Send no Range
  header in that case. A real byte range has an exclusive end; the HTTP header's
  end is one byte earlier.
- HLS.js destroys a loader while handling its successful response. `destroy()` must
  clear callbacks before aborting pending work, and must preserve completed stats.
  Emitting `onAbort` during successful cleanup rejects an already-downloaded fragment.
- Each loader owns an ID for cancellation. Ignore results after aborting. Releasing
  a playback session aborts its outstanding media requests.

Literal array sources such as `file: streamUrls[0]` resolve only the declared JSON
string array at the player’s exact index. Expressions, invalid indices, and alternate
entries are not fallback streams.

`tests/http.test.ts` covers compressed responses, limits, malformed bodies, and
cancellation. `tests/media-loader.test.ts` exercises ordinary fragments, byte ranges, and HLS.js's
success/cleanup order. `tests/provider.test.ts` covers format recognition, rotating
identifiers, iframe selection, exact stream resolution, headers, and bounded traversal.

## Verify a change

Run `npm run check`, then test the exact channel and numbered stream in Electron.
A successful resolver or media-byte probe does not prove Chromium can decode and
render it. Check stream switching, Retry, leaving playback, and fullscreen separately.
Compare sustained playback and expiry renewal when changing recovery behavior.

The Electron loader interface is documented in [HLS.js](https://hlsjs.video-dev.org/api-docs/hls.js.loader).
Process-boundary requirements follow [Electron security guidance](https://www.electronjs.org/docs/latest/tutorial/security).
