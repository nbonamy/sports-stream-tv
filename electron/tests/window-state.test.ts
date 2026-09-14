import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, test } from "vitest";
import { restoreWindowState, saveWindowState } from "../src/main/window-state";

const primary = { x: 0, y: 25, width: 1440, height: 875 };
const secondary = { x: -1920, y: 0, width: 1920, height: 1080 };

const temporaryDirectories = new Set<string>();

afterEach(() => {
  for (const directory of temporaryDirectories)
    rmSync(directory, { recursive: true, force: true });
  temporaryDirectories.clear();
});

function preferences() {
  const directory = mkdtempSync(join(tmpdir(), "sports-window-"));
  temporaryDirectories.add(directory);
  return join(directory, "profile", "window-state.json");
}

test("closing and reopening preserves size and position on a secondary monitor", () => {
  const path = preferences();
  const bounds = { x: -1700, y: 120, width: 1100, height: 750 };
  saveWindowState(path, bounds);
  assert.deepEqual(
    restoreWindowState(path, [primary, secondary], primary).bounds,
    bounds,
  );
  const resized = { x: 300, y: 100, width: 950, height: 650 };
  saveWindowState(path, resized);
  assert.deepEqual(
    restoreWindowState(path, [primary, secondary], primary).bounds,
    resized,
  );
});

test("a disconnected monitor restores the window centered on the primary display", () => {
  const path = preferences();
  saveWindowState(path, { x: -1700, y: 120, width: 1100, height: 750 });
  assert.deepEqual(restoreWindowState(path, [primary], primary).bounds, {
    x: 170,
    y: 88,
    width: 1100,
    height: 750,
  });
});

test("changed display dimensions keep the restored window inside the work area", () => {
  const path = preferences();
  saveWindowState(path, { x: 900, y: 500, width: 1800, height: 1000 });
  assert.deepEqual(
    restoreWindowState(path, [primary], primary).bounds,
    primary,
  );
});

test("missing, corrupt and invalid preferences fall back to a usable default", () => {
  const path = preferences();
  const fallback = { x: 80, y: 53, width: 1280, height: 820 };
  assert.deepEqual(
    restoreWindowState(path, [primary], primary).bounds,
    fallback,
  );
  saveWindowState(path, fallback);
  for (const value of [
    "broken json",
    "null",
    "{}",
    '{"x":0,"y":0,"width":-1,"height":800}',
    '{"x":"0","y":0,"width":900,"height":700}',
  ]) {
    writeFileSync(path, value);
    assert.deepEqual(
      restoreWindowState(path, [primary], primary).bounds,
      fallback,
    );
  }
});

test("maximized state survives reopening without replacing the normal bounds", () => {
  const path = preferences();
  const bounds = { x: 180, y: 100, width: 1000, height: 700 };
  saveWindowState(path, bounds, true);
  assert.deepEqual(restoreWindowState(path, [primary], primary), {
    bounds,
    maximized: true,
  });
  saveWindowState(path, bounds, false);
  assert.deepEqual(restoreWindowState(path, [primary], primary), {
    bounds,
    maximized: false,
  });
});

test("legacy preferences and invalid maximized flags restore a normal window", () => {
  const path = preferences();
  const bounds = { x: 180, y: 100, width: 1000, height: 700 };
  saveWindowState(path, bounds);
  for (const value of [
    bounds,
    { ...bounds, maximized: "false" },
    { maximized: true },
  ]) {
    writeFileSync(path, JSON.stringify(value));
    assert.equal(restoreWindowState(path, [primary], primary).maximized, false);
  }
});
