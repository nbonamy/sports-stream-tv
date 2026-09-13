# Working on Sports

Sports is a native Android TV app. Keep README.md focused on the product,
installation, and usage. Put implementation guidance here; keep test-run logs,
deployment status, and project history out of the README.

## Product conventions

- Read listings and channel names from the provider. A URL used for investigation
  is a test case, not a reason to add a permanent channel shortcut.
- Preserve the user's selected channel and stream. Start the first stream promptly
  while discovering alternatives; switching streams is an explicit user action.
- Keep player copy minimal. Loading uses the blue animation, errors its red variant,
  and Retry is text with a subtle focused background. Back icons are outside remote
  focus navigation. Stream arrows appear only when that direction has an alternative.
- Use `make install` for installation without launch. `make deploy` also launches
  the app. Follow the user's current device-testing instructions; building locally
  does not authorize interrupting their TV session.
- Artwork uses a 512 × 384 RGBA canvas and a 132 × 96 dp display frame. When adding
  or adjusting artwork, read [cutout preparation](docs/artwork/transparent-icons.md)
  and [LiveTV artwork](docs/artwork/live-tv.md). Verify actual alpha transparency.

## Player architecture

| Seam | Responsibility |
| --- | --- |
| `core/.../Catalog.kt`, `LiveTv.kt` | Provider listings, channel names, and stable channel-page URLs |
| `core/.../PageClient.kt` | Cancellable HTTP requests, response-size and request-time limits |
| `core/.../StreamResolver.kt` → `streams()` | Discover numbered stream choices through wrapper pages |
| `StreamResolver.resolve()` / `walk()` | Follow the selected embed chain, validate HLS, return `ResolvedStream` |
| `PlayerPageParser.nextPages()` | Find known iframe destinations and derive inspected dynamic embeds |
| `PlayerPageParser.playlist()` | Extract or decode playlist data, then validate its HTTPS URL and `.m3u8` path |
| `IndexedPlaylistParser.kt`, `BarecropConfigParser.kt` | Pure decoders for individual encoded player formats |
| `app/.../MainActivity.kt` | Media3 setup, stream selection, lifecycle, renewal, and recovery |
| `app/.../PlayerChrome.kt` | Playback controls and connecting/unavailable states |

Core paths above are under `core/src/main/kotlin/fr/bonamy/sports/core/`;
app paths are under `app/src/main/java/fr/bonamy/sports/`.

Discovery and resolution serve different purposes. `streams()` extracts choices
without resolving signed media URLs. Both discovery and playback call
`nextPages(..., false)` so its legacy alternative-link behavior cannot silently
change the selected stream. Keep that distinction when adding an embed type.

## Adding another player type

1. **Reproduce the exact selection.** Record the channel page and stream number.
   Compare that same selection in the browser and app. If it also fails in the
   browser, establish a provider failure before changing the resolver.
2. **Trace the document chain.** Inspect the channel page, numbered alternative,
   nested iframes, and final player's configuration. Find the request that produces
   the HLS URL. Separate player requests from ads and unrelated browser errors.
   Finish this step with the actual chain and the data transformation that constructs
   its media URL, not just a copied URL that happens to play.
3. **Build a sanitized regression fixture.** Follow the examples in
   `core/src/test/kotlin/fr/bonamy/sports/core/SourceTests.kt`: intercept `PageClient`
   requests and supply wrapper HTML, player data, and a manifest. Use synthetic
   URLs/tokens. Assert the selected stream's request sequence and required headers.
   Run the test against the existing code and confirm it catches the failure.
4. **Implement at the narrowest seam.** Add an inspected cross-host iframe destination
   to `nextPages()` only when needed. Put a substantial data decoder in its own pure
   parser. Match the known format, validate types and sizes, and return null for
   unknown or malformed data. Parse data rather than evaluating provider JavaScript.
   Preserve cancellation, timeouts, visited-page limits, and ad exclusion.
5. **Verify failure cases as well as success.** Include changed expressions,
   malformed encoding, invalid URLs, and any observed rotating identifiers. A
   stream-2 test must demonstrate that stream 1 was not tried instead. Run `make check`.
6. **Verify fresh live data.** Re-fetch the page after implementing the decoder.
   Validate the playlist, follow master playlists to a media playlist, then fetch
   media bytes using the same headers. A valid `#EXTM3U` alone is insufficient.
7. **Verify native playback when device testing is authorized.** A browser, JVM
   test, and HTTP media probe cannot prove Android decoding/rendering. Check the
   real selected channel on the TV. Report separately what passed locally and
   whether native playback, sustained playback, and URL renewal were exercised.

Keep captured HTML/configuration outside the repo. Signed URLs, cookies, and tokens
must not appear in fixtures, logs, screenshots, or commits. Trace provider hosts,
HTTP status codes, and exception classes. Remove temporary diagnostic logging.

## Known player formats

| Format | Extraction and constraints |
| --- | --- |
| Direct HLS config | Quoted `source`, `file`, or `src`; unescape slashes/entities and validate the URL. |
| Joined string arrays | Parse the inspected `return([...].join("") + ...)` shape. Supported suffixes are declared string-array joins and a named DOM element's contents. Reject unknown expressions. |
| igniteandship dynamic iframe | A known `/wiki.js` script plus a restricted `fid` value determines `/wiki.php?player=desktop&live=…`. Derive this URL without executing the script. |
| la18hd / Win Sports | Clappr uses `source: playbackURL`, with the URL in a separate literal `var`/`let`/`const` declaration. Require both the source reference and literal declaration; an unused URL or executable concatenation is insufficient. |
| stream-xhd / DirecTV | `IndexedPlaylistParser` reads shuffled `[index, base64]` pairs. Decode each value, keep its numeric character code, subtract the sum of two literal-return constants, and reconstruct by index. Validate the observed sort/decoding/source operations and reject duplicate indices or invalid characters. |
| barecrop / Tennis stream 2 | `BarecropConfigParser` decodes `window._econfig`: outer base64 → four equal pieces → remove character at index 3 of each piece → base64-decode each → place pieces in destinations `[2, 0, 3, 1]` → concatenate → base64-decode → JSON. Prefer a nonblank `stream_url_nop2p`, otherwise `stream_url`. Ignore advertising and P2P settings. |

Provider names identify inspected formats, not guarantees that every player on
those domains works. The iframe allowlist controls document traversal; extracted
media URLs may use separate, rotating CDN hosts.

## Lessons that matter

**Identifiers rotate.** stream-xhd changes array and function names between page
loads. Capture identifiers from the decoding expression and match their declarations;
hardcoding the name from one response can pass a fixture and fail the next request.
Keep the decoder tied to the inspected operations, not a generic script interpreter.

**Android regex differs from desktop Java.** An indexed decoder passed JVM tests
but threw `PatternSyntaxException` on Android. Explicitly escape literal braces
and brackets, including closing delimiters. Decoder `runCatching` can hide a regex
construction error as an unsupported player; investigate that when JVM and TV disagree.

**Headers are part of the stream.** HTML requests use the parent page as Referer.
Currently, media resolution supplies the final player origin as Origin and its root
URL as Referer, plus the shared User-Agent. Media3 receives these headers through
its data-source factory so playlists, encryption keys, and segments share them.
Preserve `ResolvedStream.headers`; do not pass only its URL to the player. If a new
provider needs a full-page Referer or another header, establish that with a request
comparison and a regression test.

**Inspection can need more browser context than playback.** Fetching barecrop's
player script required its full player-page Referer and the inspection session's
cookies. That does not mean native playback needs a browser cookie jar: the useful
stream settings were already in the HTML. Inspect the script to understand the
format, then implement the data transformation locally.

**Resolution failure is not a playback interruption.** A malformed iframe such as
`https:///wiki.php` should be rejected. `SourceUnavailable` goes directly to the
error UI. Retrying a known failed resolution at 3/6/9-second intervals adds delay
without new information. Media3 interruptions still use bounded recovery, and Retry
lets the user request a fresh resolution.

**Signed URLs expire.** Store channel-page URLs; resolve media URLs afresh. The current
resolver recognizes an `expires` query parameter and the app schedules renewal from
it. Another expiry format needs explicit parsing and tests, not an assumption that
all provider tokens share that parameter. Preserve coroutine cancellation so an old
channel's resolution cannot replace a newer selection.

**Check the device clock.** A restored emulator snapshot had an outdated date and
rejected otherwise valid certificates. Compare emulator/TV time with real time
before diagnosing TLS or signed-expiry failures. Keep certificate validation enabled.

## Diagnostics and completion checks

Run a targeted channel probe instead of scanning unrelated schedules:

```sh
./gradlew :core:probe --args=https://freestreams-live1h.pk/tennis-channel/ --console=plain
./gradlew :core:probe --args=--live-tv --console=plain
make check
```

The channel probe discovers alternatives and stops at the first playable one.
Its overall success does not prove every stream works: inspect the numbered result,
or target the observed alternative's page URL when testing a particular stream.
The probe follows master playlists and samples media bytes. MPEG-TS sync is useful
for TS streams, but other HLS segment containers require their own validation.

For UI-only checks, `LiveTvNavigationTests` renders local previews in
`app/build/previews/`. Robolectric must run outside touch mode for remote focus tests;
inspect the views' `isFocused` state, since the activity shadow's `currentFocus` can
be null while a tile is actually focused.

`PlayerPreviewActivity` is debug-only and exercises the real player chrome without
network access. On an authorized debug device, launch it with `--es state connecting`,
`playing`, `paused`, or `unavailable`; `--ei streams 1` checks a single-stream channel.
It is a UI check, not evidence of working video. Debug and release APKs have different
signatures; preserve the existing installation and its signing identity when testing.
