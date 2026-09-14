# Android playback

For provider tracing and decoder support, read the shared
[channel support guide](../../docs/player.md). Run the commands below from `android/`.

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
