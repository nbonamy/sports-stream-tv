"""Render the Android TV banner with librsvg and the bundled Nunito font."""
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent.parent
destination = ROOT / "android/app/src/main/res/drawable-xhdpi/tv_banner.png"

# Isolate font discovery so the SVG never silently uses a system fallback.
font_config = ET.Element("fontconfig")
ET.SubElement(font_config, "dir").text = str(ROOT / "branding/fonts")
with tempfile.NamedTemporaryFile(suffix=".conf") as config:
    config.write(ET.tostring(font_config, encoding="utf-8", xml_declaration=True))
    config.flush()
    environment = dict(os.environ, FONTCONFIG_FILE=config.name, PANGOCAIRO_BACKEND="fc")
    result = subprocess.run(
        ["rsvg-convert", "-w", "320", "-h", "180", str(ROOT / "branding/sports-banner.svg")],
        env=environment, capture_output=True, check=True,
    )

destination.write_bytes(result.stdout)
print(f"{destination.relative_to(ROOT)}: 320 x 180 PNG")
