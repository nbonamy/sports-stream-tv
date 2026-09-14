# Sports for Android TV

Browse live sports and TV channels with your remote, then watch in the native
fullscreen player. See the [product overview](../README.md) for features.

## Remote controls

| Button | Browse | Player |
| --- | --- | --- |
| Arrow keys | Move between tiles | Left/Right changes stream; Down enters playback controls or selects Retry; Up returns to stream navigation |
| OK | Open the selected item | Activate the selected control, or toggle playback when controls are hidden |
| Back | Return to the previous screen | Return to the channel list |
| Play/Pause | — | Toggle playback |

Within playback controls, Left/Right moves between play/pause and **LIVE**.
A red dot means you're live. When LIVE is muted, select it to return to live and
resume playback. It appears on streams that support returning to live.

## Requirements

To use Sports, you need Android TV 8.0 or later, an internet connection, and a remote.

## Build and install

On your computer, install Java 17+, Android SDK 36, Python 3, and ADB.
Run the commands below from `android/`. Set the SDK location in `android/local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
```

Build a signed APK:

```sh
make build
```

The APK is written to `release/Sports.apk`. Enable ADB debugging on your TV and
authorize the computer's connection. To build and install on a TV reachable through
ADB, replace the example address with your TV's address:

```sh
make install ANDROID_TV_DEVICE=192.168.1.50
```

Installation does not launch the app. Open **Sports** from your TV's apps, or use
`make deploy ANDROID_TV_DEVICE=192.168.1.50` to install and launch it together.
An ADB device serial can also be used as `ANDROID_TV_DEVICE`.

The first build creates a local signing key in `keys/sports.jks` and its settings
in `signing.properties`. Keep both for future app updates; they are excluded from Git.

## Development

```sh
make check  # Core and Android UI tests, lint, and debug build
```

For a missing or non-working channel, follow [Channel support](../docs/player.md):
identify the failing stream, trace its player, add or repair support, and verify playback.
Repository conventions and task guides are in [AGENTS.md](AGENTS.md).

Artwork style and asset preparation are in [Artwork](../docs/artwork/README.md).
The bundled Lato font is distributed under the [SIL Open Font License](../licenses/Lato-OFL.txt).
