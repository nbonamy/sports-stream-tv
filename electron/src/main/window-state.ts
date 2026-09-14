import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import type { Rectangle } from "electron";

const defaults = { width: 1280, height: 820 };

export function restoreWindowBounds(
  path: string,
  workAreas: Rectangle[],
  primary: Rectangle,
): Rectangle {
  let saved: Rectangle | undefined;
  try {
    const value = JSON.parse(readFileSync(path, "utf8"));
    if (
      value &&
      [value.x, value.y, value.width, value.height].every(
        Number.isSafeInteger,
      ) &&
      value.width > 0 &&
      value.height > 0
    )
      saved = value;
  } catch {
    // A missing or damaged preferences file should never prevent startup.
  }

  const overlap = (area: Rectangle) =>
    saved
      ? Math.max(
          0,
          Math.min(saved.x + saved.width, area.x + area.width) -
            Math.max(saved.x, area.x),
        ) *
        Math.max(
          0,
          Math.min(saved.y + saved.height, area.y + area.height) -
            Math.max(saved.y, area.y),
        )
      : 0;
  const visible = workAreas
    .filter((area) => overlap(area) > 0)
    .sort((a, b) => overlap(b) - overlap(a))[0];
  const area = visible ?? primary;
  const width = Math.min(
    area.width,
    Math.max(800, saved?.width ?? defaults.width),
  );
  const height = Math.min(
    area.height,
    Math.max(600, saved?.height ?? defaults.height),
  );
  return {
    width,
    height,
    x:
      saved && visible
        ? Math.max(area.x, Math.min(saved.x, area.x + area.width - width))
        : Math.round(area.x + (area.width - width) / 2),
    y:
      saved && visible
        ? Math.max(area.y, Math.min(saved.y, area.y + area.height - height))
        : Math.round(area.y + (area.height - height) / 2),
  };
}

export function saveWindowBounds(path: string, bounds: Rectangle): void {
  try {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(`${path}.tmp`, JSON.stringify(bounds), "utf8");
    renameSync(`${path}.tmp`, path);
  } catch {
    // Saving preferences must not block closing the app.
  }
}
