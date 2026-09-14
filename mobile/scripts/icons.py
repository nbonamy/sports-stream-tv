"""Rasterize the shared Android launcher vector for the native phone targets."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
android = '{http://schemas.android.com/apk/res/android}'
vector = ET.parse(root / 'android/app/src/main/res/drawable/ic_sports.xml').getroot()
paths = []
for element in vector:
    attrs = {}
    for source, target in [('pathData', 'd'), ('fillColor', 'fill'), ('strokeColor', 'stroke'), ('strokeWidth', 'stroke-width')]:
        value = element.get(android + source)
        if value: attrs[target] = 'none' if value == '@android:color/transparent' else value
    paths.append(ET.tostring(ET.Element('path', attrs), encoding='unicode'))
svg = root / 'mobile/build/icon.svg'
svg.parent.mkdir(parents=True, exist_ok=True)
svg.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">' + ''.join(paths) + '</svg>')
def render(path, size):
    subprocess.run(['rsvg-convert', '-w', str(size), '-h', str(size), '-o', str(path), str(svg)], check=True)
render(root / 'mobile/ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512@2x.png', 1024)
for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    folder = root / f'mobile/android/app/src/main/res/mipmap-{density}'
    for name in ['ic_launcher.png','ic_launcher_round.png','ic_launcher_foreground.png']: render(folder / name, size)
