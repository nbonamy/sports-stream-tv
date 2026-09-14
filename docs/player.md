# Channel support and playback

Sports reads channel listings from FSL, discovers the selected embedded player,
extracts its HLS configuration, and plays it through Media3. Player support depends
on the configuration format in the page; iframe and CDN hostnames can change.

## Diagnose a channel that does not work

1. **Identify the exact selection.** Record the stable channel-page URL and stream
   number. Open that same selection on the provider's website. A channel that also
   fails there may be unavailable at the source; establish a working browser stream
   before treating the failure as missing app support.
2. **Check the listing.** If a channel or stream choice is missing or misnamed,
   compare the provider's listing HTML with `Catalog.kt`, `LiveTv.kt`, or
   `StreamResolver.streamOptions()`. Keep provider names and selections intact.
3. **Run a targeted probe.** From the repository root:

   ```sh
   ./gradlew :core:probe --args=https://freestreams-live1h.pk/tennis-channel/ --console=plain
   ```

   The probe discovers alternatives and stops at the first playable one. Read its
   numbered result: success on stream 2 says nothing about stream 1. It has no
   stream-number argument. Passing an alternative's URL isolates it only if that
   page's chain exposes no other numbered choices. For an exact selection, call
   `StreamResolver.resolve()` with the selected `StreamLink` in a fixture or local
   diagnostic harness, bypassing `streams()`.
4. **Trace the document chain.** Follow the channel page, selected numbered wrapper,
   nested iframes, and final player configuration. Use `PageClient.USER_AGENT` and
   the referring page's URL for each document request. Identify the expression or
   configuration that supplies the HLS URL, including any decoding steps. Separate
   player requests from advertising scripts and unrelated browser errors.
5. **Locate the failing stage.** Use the table below to choose the code to change.
   If a supported configuration is never reached, inspect iframe visibility,
   ranking, lazy-load attributes, and request headers before changing a decoder.

| Evidence | Check |
| --- | --- |
| Listing or stream option is missing | Provider HTML and listing/numbered-link parsing |
| Final player page is never reached | Iframe extraction/ranking, dynamically constructed embeds, destination policy, redirects |
| Player page loads but no HLS URL is extracted | Supported configuration shapes, encoding, and rotating identifiers |
| Playlist, key, or segment returns an HTTP error | Fresh URL, full User-Agent, Referer, Origin, expiry, and provider availability |
| HTTP media probe succeeds but Android playback fails | Media3 error code, Android parser behavior, selected tracks/codecs, and device clock |
| Playback starts and later fails | Signed URL expiry, renewal, network interruption, and bounded recovery |

Keep captured HTML and player configurations outside the repo. Use synthetic URLs
and tokens in fixtures. Diagnostics may include hosts, HTTP status codes, and
exception classes; redact signed URLs, query tokens, cookies, and credentials.

## Add or repair support

1. **Create a failing fixture.** Intercept `PageClient` requests as in
   [SourceTests.kt](../core/src/test/kotlin/fr/bonamy/sports/core/SourceTests.kt).
   Supply sanitized wrapper HTML, configuration data, and a manifest. Assert the
   selected stream's request sequence and headers. Confirm it fails on the current code.
2. **Change the narrowest component.** Reuse a decoder when the format is already
   supported. Adjust iframe discovery for a missing HTML shape. For a dynamically
   constructed iframe, derive its URL only after inspecting its construction.
   Put substantial decoding logic in a pure parser; validate types, sizes, and the
   resulting URL, and return null for unknown or malformed data. Parse data without
   evaluating arbitrary provider JavaScript.
3. **Cover the real variations.** Include malformed encoding, invalid URLs, changed
   identifiers, and unsupported expressions. A stream-2 fixture must prove that
   stream 1 was not selected instead. Keep fullscreen and visibility metadata faithful
   to the source HTML. Follow [PlayerDiscoveryTests.kt](../core/src/test/kotlin/fr/bonamy/sports/core/PlayerDiscoveryTests.kt)
   for renamed domains, iframe ranking, destination checks, and traversal bounds.
4. **Run `make check`.** This runs core and Android tests, lint, and the debug build.
   Check parser behavior on Android when regex or decoding changes: desktop Java
   and Android use different regex implementations. Escape literal braces and
   brackets explicitly, including closing delimiters. A swallowed regex-construction
   exception can look like an unsupported player.
5. **Verify fresh provider data.** Re-fetch the selected page with the updated code.
   Follow master playlists to a media playlist and fetch media bytes with the resolved
   headers. A valid `#EXTM3U` alone is insufficient. The probe samples MPEG-TS sync;
   other HLS segment containers require an appropriate check.
6. **Verify native playback when device testing is authorized.** Exercise the exact
   channel and stream. Report separately which local checks, native playback,
   sustained playback, and URL renewal were verified. HTTP success alone does not
   establish that the TV can decode and render the stream.

## Architecture and request flow

Core files are under `core/src/main/kotlin/fr/bonamy/sports/core/`;
Android files are under `app/src/main/java/fr/bonamy/sports/`.

| Component | Responsibility |
| --- | --- |
| `Catalog.kt`, `LiveTv.kt` | Provider listings, channel names, stable channel-page URLs |
| `StreamResolver.streams()` | Discover numbered alternatives without resolving signed media URLs |
| `PlayerPageParser.nextPages()`, `PlayerEmbeds.kt` | Rank iframe candidates and derive supported dynamic embeds |
| `PlayerPageParser.playlist()` | Extract supported configurations and validate HTTPS `.m3u8` URLs |
| `IndexedPlaylistParser.kt`, `BarecropConfigParser.kt` | Decode individual encoded configuration formats |
| `StreamResolver.resolve()` / `walk()` | Traverse the selected embed chain, check HLS, return `ResolvedStream` |
| `PageClient.kt`, `PlayerDestination.kt` | Cancellable HTTP, request/size limits, public HTTPS destination policy |
| `MainActivity.kt` | Media3 configuration, selection, lifecycle, renewal, recovery |
| `PlayerChrome.kt`, `LivePlayback.kt`, `RetainedVideoFrame.kt` | Player UI, live seeking, retained frame during reconnection |

`nextPages()` follows iframe candidates and never numbered alternative links.
Only stream discovery reads those links, keeping the user's playback selection stable.
Fullscreen frames and player-related metadata rank ahead of generic visible frames.
Hidden/tiny frames and frames explicitly marked as advertising, tracking, or chat
are excluded. An unmarked unrelated frame may still require a bounded read.
Traversal limits candidates per document, visited documents, depth, and total time.

`getPlayerPage()` accepts public HTTPS destinations, rejects credentials and local
addresses, checks DNS results, and disallows HTTPS-to-HTTP redirects. It covers
resolver document and manifest requests. Media3 uses its own data source for playback.
Preserve destination checks, cancellation, traversal limits, and ad filtering when
extending discovery.

### Request headers and expiry

Document requests use the parent page as Referer. Resolved media requests use the
final player's origin as Origin, its root URL as Referer, and the shared User-Agent.
`ResolvedStream.headers` must reach Media3's data-source factory so playlists,
encryption keys, and segments share that context. If another header or a full-page
Referer is required, establish it with a request comparison and regression fixture.

Use the app's complete User-Agent when probing. A minimal browser-like string can
produce different provider responses. Inspection scripts may require cookies or a
full-page Referer even when playback configuration is already present in the HTML;
establish playback requirements independently from inspection requirements.

Store stable channel-page URLs and resolve media URLs afresh. The resolver reads
an `expires` query parameter and the app schedules renewal from it. Additional
expiry formats need explicit parsing and fixtures. Preserve coroutine cancellation
so a previous channel's resolution cannot replace the current selection.

## Supported configuration formats

These shapes determine decoder support. Provider names are examples of those shapes,
not an allowlist or a guarantee that every stream on a domain works.

| Format | Recognition and extraction |
| --- | --- |
| Direct HLS value | Quoted `source`, `file`, or `src`; unescape slashes/entities and validate HTTPS with a `.m3u8` path. |
| Literal source variable | `source: variableName` plus its literal `var`/`let`/`const` URL declaration. Require both; an unused URL or executable concatenation is insufficient. Used by la18hd/Win Sports. |
| Joined string arrays | Inspected `return([...].join("") + ...)` expression. Supported suffixes are declared string-array joins and a named DOM element's contents. Reject unknown expressions. |
| Indexed character encoding | `IndexedPlaylistParser` reads shuffled `[index, base64]` pairs. Decode each value, keep its numeric character code, subtract the sum of two literal-return constants, then reconstruct by index. Validate the sort/decoding/source operations; reject duplicate indices and invalid characters. Used by stream-xhd/DirecTV. |
| `_econfig` envelope | `BarecropConfigParser` reads `window._econfig`: outer base64 → four equal pieces → remove character at index 3 of each → base64-decode each → place at destinations `[2, 0, 3, 1]` → concatenate → base64-decode → JSON. Prefer nonblank `stream_url_nop2p`, otherwise `stream_url`; ignore advertising and P2P settings. Used by barecrop, traitaunt, and assetrage. |
| Dynamic iframe | An `igniteandship.com/wiki.js` script and restricted `fid` determine `https://igniteandship.com/wiki.php?player=desktop&live=…`. Derive the URL without executing the script. |

Encoded players can rotate array and function names between requests. Capture names
from the decoding expression and match their declarations. Bind the parser to the
recognized operations instead of an identifier from one response.

Nested wrapper chains need no special host entries. For example, quellefrappe →
traitaunt and dlive → assetrage lead to the same `_econfig` decoder. Derive media
headers from the final player's URL, regardless of the wrapper's domain.

## Playback behavior

The first stream starts while alternatives are discovered. Previous/next controls
only appear when an alternative exists in that direction. Back returns to channels.
Resolution failures (`SourceUnavailable`) go directly to the error state; Media3
interruptions have bounded retries at 3/6/9 seconds. Retry requests a fresh resolution.
Malformed iframe URLs such as `https:///wiki.php` are rejected promptly.

After a rendered frame, buffering and same-stream retries use a translucent backdrop.
Initial loading and explicit stream changes use an opaque backdrop. `PlayerView`
keeps content on reset, and `RetainedVideoFrame` captures one bounded PixelCopy image
at interruption to retain the picture across decoder resets. Keep it through retries;
clear it on resumed playback, stream changes, and release. Invalidate pending copies
so late callbacks cannot cover resumed video or a different channel. Snapshots stay
in memory. A ready video track must render its first frame before loading disappears;
audio-only playback does not require that event.

LIVE compares playback with the live window's default position, allowing five seconds
for playlist refresh differences. Normal broadcast delay does not count as being
behind. Paused playback offers Go live, which seeks to the default position and resumes.
See [Media3 live seeking](https://developer.android.com/media/media3/exoplayer/live-streaming#seeking_in_live_streams)
for the meaning of the default position.
Hide LIVE for non-live, ended, unknown-target, or unavailable-seek streams. Update it
on player events and while paused; cancel polling on release. Down enters playback
controls, Left/Right stays in that row, and Up returns to stream navigation. When
LIVE becomes an indicator, move its focus to play/pause.

## Device and UI checks

Check the device's date/time before investigating TLS or expired-token failures,
especially after restoring an emulator snapshot. Keep certificate validation enabled.
Debug and release APKs have different signing identities; preserve the installed
app's signing identity when choosing a build to install.

For directory parsing, run:

```sh
./gradlew :core:probe --args=--live-tv --console=plain
```

Android tests render previews under `app/build/previews/`:

| Test | Coverage |
| --- | --- |
| `LiveTvNavigationTests` | Browse navigation and focus restoration |
| `LivePlaybackTests` | LIVE availability, seeking, and remote navigation |
| `PlayerReconnectionTests` | Translucent recovery and opaque initial loading |

Run Robolectric outside touch mode for remote focus checks. Inspect each view's
`isFocused`; the activity shadow's `currentFocus` can be null despite a focused tile.

`PlayerPreviewActivity` is debug-only and requires no provider connection. On an
authorized emulator with the debug APK installed:

```sh
adb -s emulator-5554 shell am start -n fr.bonamy.sports/.PlayerPreviewActivity --es state reconnecting --ei streams 2
```

States: `connecting`, `playing`, `paused`, `live`, `behind`, `reconnecting`,
`unavailable`. Set `streams` to 1 for single-stream controls. The reconnection preview
uses a colored stand-in for video; actual frame retention and playback need a device
test with media. UI previews do not establish that a channel plays successfully.
