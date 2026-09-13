# LiveTV artwork

Generated with the built-in imagegen tool. The original is `originals/live_tv.png`;
the app asset is `app/src/main/res/drawable-nodpi/live_tv_cutout.png`.

Prompt:

> Use case: stylized-concept. Create one polished photorealistic 3D LiveTV home tile icon for a dark navy Android TV sports app. A modern slim widescreen television, near frontal with slight perspective, brushed dark silver bezel, understated blue rim lighting, dark blue glass screen with a simple luminous cyan play triangle in its center, two small elegant feet. The object should look like premium realistic sporting-equipment cutouts, no cartoon style. Isolated object on genuinely transparent background with alpha; no floor, no glow outside the object, no background, no checkerboard pattern, no text, no badge, no frame surrounding the illustration. Landscape 4:3 canvas, television centered with balanced minimal padding, full object visible. Output PNG with real transparency.

The generated PNG has actual alpha transparency. Its foreground was cropped using
alpha > 16 as the bounding box, resized proportionally into a 480 × 352 box, and
centered on a transparent 512 × 384 canvas, preserving the original alpha.
It displays in the same 132 × 96 dp frame as the sports artwork.
