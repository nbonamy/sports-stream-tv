# Transparent sport icons

The final icons are `app/src/main/res/drawable-nodpi/sport_*_cutout.png`.
All are 512 × 384 RGBA PNGs with actual transparency. Foregrounds are cropped
to their visible bounds, fitted proportionally into a 480 × 352 content box,
and centered on the canvas. The app displays every icon in a 132 × 96 dp box
on home, schedule, and channel screens. Labels occupy separate layout space.

The original imagegen artwork is preserved in [originals](originals).
The [additional sports](more-sports.md) and [LiveTV](live-tv.md) were generated with
real alpha; normalize those with `tools/normalize_transparent_icon.py`, preserving
their alpha rather than applying foreground segmentation again.

For the original ten sports icons:
Built-in imagegen was tried for background extraction, but its results contained
opaque checkerboards. Nicolas approved local background removal instead.
The final images preserve the original artwork using macOS Vision foreground
segmentation. A local color key within the tennis racket rim removes residual
navy between strings while preserving foreground colors elsewhere.

Reproduce on macOS 14 or later, with Pillow installed:

```sh
swift tools/prepare_sport_icons.swift docs/artwork/originals app/src/main/res/drawable-nodpi
python3 tools/refine_tennis_cutout.py app/src/main/res/drawable-nodpi/sport_tennis_cutout.png
```

Original generation prompts are in [artwork-prompts.md](../artwork-prompts.md).
No imagegen output from the rejected transparency attempt is bundled in the app.
