# Mobile playback

Read the shared [channel support guide](../../docs/player.md) for provider formats
and regression fixtures. Mobile and Electron use the same TypeScript resolver.

## Request path

The shared `createSportsService()` receives the native transport from `src/transport.ts`.
Catalog pages, selected iframe chains, manifests, keys, and media segments all use
that transport. The resolver's final-player Origin, Referer, and User-Agent are
preserved. Nothing is sent through a hosted proxy.

`SportsHttpPlugin.swift` uses ephemeral URLSession requests. It validates initial
and redirect destinations and their DNS answers, bounds redirects, request duration,
and accumulated decoded bytes, and cancels URLSession tasks by request ID. TLS
verification stays enabled. DNS validation precedes URLSession's own connection;
it is not the pinned connection lookup used by Electron.

The Android plugin uses OkHttp with public-address validation in its connection DNS
lookup and Brotli/gzip decoding. Responses cross the native bridge as base64 and become Uint8Array data for
HLS.js. Per-fragment bridge copies cost memory; keep buffering bounded and measure
on physical devices before increasing the limits.

Cancellation must reach the native task. Releasing a shared playback session cancels
its pending media, and late results must never replace the current stream.

## Player

The shared player uses HLS.js with Managed Media Source on supported iPhones.
Ignore `stalled` events while playable data remains buffered: WebKit can stop
fetching deliberately without interrupting playback.
Remote playback is disabled on the video element for this path. iOS 17.4 is the
minimum deployment target. Unsupported codecs still depend on the device's decoder.

Mobile fullscreen uses the element fullscreen API where available, with the iPhone
video fullscreen API as a fallback. Native video fullscreen may use system controls.
Back exits fullscreen before leaving playback when handled as Escape.

## Verification

Run `npm run check` from `mobile/`, sync assets, and compile the affected native app.
Test the exact channel and numbered stream on-device. Verify advancing video, audio,
switching streams, leaving playback, Retry, and reconnecting after a network interruption.
Check portrait/landscape safe areas and fullscreen exit independently. A simulator
build or a successful manifest request alone does not verify device playback.
