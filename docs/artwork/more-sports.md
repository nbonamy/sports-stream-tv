# More sports artwork

Generated with the built-in imagegen tool. The five new assets use real alpha transparency;
no local background removal was needed. Original PNGs are in `originals/` and normalized
app assets are in `app/src/main/res/drawable-nodpi/`.

| Sport | Artwork |
| --- | --- |
| NHL | Existing `sport_nhl_cutout.png` |
| MMA | New `sport_mma_cutout.png` — fingerless gloves |
| Boxing | New `sport_boxing_cutout.png` — red boxing gloves |
| Motorsport | New `sport_motorsport_cutout.png` — silver/blue GT racing coupe |
| College Football | Reuses `sport_nfl_cutout.png` |
| Basketball | Reuses `sport_nba_cutout.png` |
| Volleyball | New `sport_volleyball_cutout.png` |
| Handball | New `sport_handball_cutout.png` |

F1 retains its open-wheel racing car. All assets share a 512 × 384 RGBA canvas and
fit their visible foreground proportionally into 480 × 352, centered. The shared
artwork mapping also supplies schedule and channel headers.

## Prompts

Each generation used this shared prompt followed by its subject paragraph below:

> Use case: stylized-concept. Asset type: one sport tile icon for a premium dark navy Android TV app. Polished realistic 3D product illustration, tactile materials, crisp silhouette and restrained cool blue rim lighting, matching realistic sports-equipment cutouts. Landscape 4:3 composition, full object centered, compact and clearly readable at small size. Genuinely transparent PNG background with alpha. No floor, no ground shadow, no glow outside the object, no checkerboard, no background scenery, no lettering, no logos, no watermark, no badge, no outer frame, no UI. No padding reserved for a label.

### mma

Subject: a pair of black and dark charcoal MMA fingerless fighting gloves, with clearly open fingers and open thumbs, compact crossed arrangement, front three-quarter product view, realistic stitched leather and wide wrist straps, subtle blue accents. They must be unmistakably fingerless MMA gloves, not boxing mitts. No human hands.

### boxing

Subject: a pair of classic padded boxing gloves, deep crimson red leather with black cuffs and subtle cool blue edge highlights, compact balanced arrangement with one glove turned slightly sideways. Large rounded closed padded fists, realistic seams and leather texture. No human hands.

### motorsport

Subject: one unbranded silver and blue GT endurance racing coupe, closed cockpit, wide wheels and rear wing, low front three-quarter view. A compact realistic racing car silhouette, proportionate vehicle, entire car visible. This represents general motorsport and should be visually distinct from an open-wheel Formula 1 car.

### volleyball

Subject: one regulation volleyball, white with yellow and cobalt-blue curved panel segments, subtle pebbled synthetic leather texture and realistic panel seams, single spherical ball in front three-quarter product lighting.

### handball

Subject: one regulation team handball, spherical, teal blue and warm orange contrasting polygonal panels, small tightly fitted stitched pentagonal and hexagonal panels with grippy pebbled surface texture. Clearly a team handball, no black basketball channels, no American football shape.

## Reproduce normalization

With Pillow installed, run for each new sport (`mma`, `boxing`, `motorsport`,
`volleyball`, `handball`), replacing `mma` in this example:

```sh
python3 tools/normalize_transparent_icon.py docs/artwork/originals/sport_mma.png app/src/main/res/drawable-nodpi/sport_mma_cutout.png
```

The normalizer validates transparency, crops with alpha > 16, resizes proportionally,
and preserves the original alpha. Verify the composited result against the app's dark
background; an RGBA file alone does not prove that the background is transparent.
