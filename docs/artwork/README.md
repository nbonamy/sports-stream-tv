# Artwork

## App icon

Android's `android/app/src/main/res/drawable/ic_sports.xml` is the launcher icon
source. Electron's `resources/icon.png` and `resources/icon.icns` reuse its paths
and colors with a rounded Mac mask. To regenerate them on macOS, install `librsvg`
(`brew install librsvg`) and run `npm run icon` from `electron/`. Normal builds use
the committed icon files and do not need that tool.

## Sport artwork

Source PNGs live in [originals](originals). Both platforms use the prepared
`*_cutout.png` files in `assets/sports/drawable-nodpi/`.

## Adding an icon

Use this prompt as a starting point, supplying the sport's equipment as the subject:

> One polished realistic 3D sports-equipment icon for a dark navy Android TV app.
> Tactile materials, crisp silhouette, restrained cool blue rim lighting. Landscape
> 4:3 composition, full object centered and readable at small size. Real transparent
> PNG background. No floor, shadow, external glow, checkerboard, text, logos, badge,
> frame, or padding reserved for a label.

Match the existing artwork. MMA uses black fingerless gloves; Boxing uses red padded
gloves. Motorsport uses a silver/blue GT coupe, distinct from F1's open-wheel car.
LiveTV uses a slim television with a cyan play triangle. College Football shares
NFL artwork, and Basketball shares NBA artwork.

Save the source in `originals/`, then normalize it. With Pillow installed, run from
the repository root:

```sh
python3 tools/normalize_transparent_icon.py docs/artwork/originals/sport_mma.png assets/sports/drawable-nodpi/sport_mma_cutout.png
```

The normalizer crops to visible bounds (alpha > 16), fits the foreground
proportionally into 480 × 352, and centers it on a 512 × 384 RGBA canvas.
It preserves transparency and rejects an opaque source.

Android reads `assets/sports/` as an additional resource directory. The desktop build
copies these same PNGs into its packaged renderer assets. Keep labels separate from the artwork. Android uses
a 132 × 96 dp display frame on tiles and page headers.

## Rebuilding existing cutouts

MMA, Boxing, Motorsport, Volleyball, Handball, and LiveTV sources already have
transparency; use the normalizer above. LiveTV's filenames are `live_tv.png` and
`live_tv_cutout.png`.

The Football, Tennis, Rugby, F1, Golf, NFL, NBA, MLB, NHL, and More sources have navy
backgrounds. Rebuild these with macOS Vision on macOS 14 or later, then refine the
tennis racket's strings:

```sh
swift tools/prepare_sport_icons.swift docs/artwork/originals assets/sports/drawable-nodpi
python3 tools/refine_tennis_cutout.py assets/sports/drawable-nodpi/sport_tennis_cutout.png
```

## Check the result

Verify 512 × 384 dimensions and fully transparent pixels outside the object.
An RGBA file with a painted checkerboard is not transparent. Inspect the icon over
the app's dark background for clean edges, balanced placement, and consistent scale.
