"""Fit an existing transparent cutout to the app's shared artwork canvas."""
import argparse
from pathlib import Path

from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("source", type=Path)
parser.add_argument("destination", type=Path)
args = parser.parse_args()

image = Image.open(args.source).convert("RGBA")
alpha = image.getchannel("A")
if alpha.getextrema()[0] != 0:
    parser.error("The source must contain actual transparent pixels.")
bounds = alpha.point(lambda value: 255 if value > 16 else 0).getbbox()
if bounds is None:
    parser.error("The source has no visible foreground.")
foreground = image.crop(bounds)
scale = min(480 / foreground.width, 352 / foreground.height)
foreground = foreground.resize((round(foreground.width * scale), round(foreground.height * scale)), Image.Resampling.LANCZOS)
canvas = Image.new("RGBA", (512, 384))
canvas.alpha_composite(foreground, ((512 - foreground.width) // 2, (384 - foreground.height) // 2))
args.destination.parent.mkdir(parents=True, exist_ok=True)
canvas.save(args.destination)
print(f"{args.destination.name}: 512×384 RGBA, {foreground.width}×{foreground.height} foreground")
