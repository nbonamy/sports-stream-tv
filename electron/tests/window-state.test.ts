import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test, type TestContext } from "node:test";
import {
  restoreWindowBounds,
  saveWindowBounds,
} from "../src/main/window-state";

const primary = { x: 0, y: 25, width: 1440, height: 875 };
const secondary = { x: -1920, y: 0, width: 1920, height: 1080 };

function preferences(t: TestContext) {
  const directory = mkdtempSync(join(tmpdir(), "sports-window-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  return join(directory, "profile", "window-state.json");
}

test("closing and reopening preserves size and position on a secondary monitor", (t) => {
  const path = preferences(t);
  const bounds = { x: -1700, y: 120, width: 1100, height: 750 };
  saveWindowBounds(path, bounds);
  assert.deepEqual(
    restoreWindowBounds(path, [primary, secondary], primary),
    bounds,
  );
  const resized = { x: 300, y: 100, width: 950, height: 650 };
  saveWindowBounds(path, resized);
  assert.deepEqual(
    restoreWindowBounds(path, [primary, secondary], primary),
    resized,
  );
});

test("a disconnected monitor restores the window centered on the primary display", (t) => {
  const path = preferences(t);
  saveWindowBounds(path, { x: -1700, y: 120, width: 1100, height: 750 });
  assert.deepEqual(restoreWindowBounds(path, [primary], primary), {
    x: 170,
    y: 88,
    width: 1100,
    height: 750,
  });
});

test("changed display dimensions keep the restored window inside the work area", (t) => {
  const path = preferences(t);
  saveWindowBounds(path, { x: 900, y: 500, width: 1800, height: 1000 });
  assert.deepEqual(restoreWindowBounds(path, [primary], primary), primary);
});

test("missing, corrupt and invalid preferences fall back to a usable default", (t) => {
  const path = preferences(t);
  const fallback = { x: 80, y: 53, width: 1280, height: 820 };
  assert.deepEqual(restoreWindowBounds(path, [primary], primary), fallback);
  saveWindowBounds(path, fallback);
  for (const value of [
    "broken json",
    "null",
    "{}",
    '{"x":0,"y":0,"width":-1,"height":800}',
    '{"x":"0","y":0,"width":900,"height":700}',
  ]) {
    writeFileSync(path, value);
    assert.deepEqual(restoreWindowBounds(path, [primary], primary), fallback);
  }
});
