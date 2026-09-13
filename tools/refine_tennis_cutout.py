"""Remove navy left between racket strings by foreground segmentation.

Run after prepare_sport_icons.swift. The polygon stays inside this artwork's
racket rim; blue foreground highlights elsewhere are preserved.
"""
import sys
from PIL import Image, ImageDraw

path = sys.argv[1]
image = Image.open(path).convert("RGBA")
assert image.size == (512, 384), "Expected normalized icon canvas"
region = Image.new("L", image.size)
ImageDraw.Draw(region).polygon(
    [(51, 80), (87, 58), (133, 53), (180, 66), (221, 98),
     (252, 135), (270, 176), (277, 209), (263, 239), (235, 260),
     (201, 267), (163, 256), (129, 244), (89, 224), (56, 195),
     (35, 164), (28, 128), (34, 101)],
    fill=255,
)
pixels = image.load()
for y in range(image.height):
    for x in range(image.width):
        if region.getpixel((x, y)):
            r, g, b, a = pixels[x, y]
            if b > g > r and b > 20:
                coverage = max(0, min(1, (r / b - .24) / .35))
                pixels[x, y] = (r, g, b, round(a * coverage))
image.save(path)
