import { test } from "node:test";
import assert from "node:assert/strict";
import { developmentOrigin, isRendererURL } from "../src/main/renderer-origin";

test("development requires an explicit loopback port and is disabled when packaged", () => {
  const origin = "http://127.0.0.1:5173";
  assert.equal(developmentOrigin(false), undefined);
  assert.equal(developmentOrigin(false, origin), origin);
  for (const value of [origin, "https://evil.test", "invalid"])
    assert.equal(developmentOrigin(true, value), undefined);
  for (const value of [
    "http://localhost:5173",
    "http://0.0.0.0:5173",
    "https://127.0.0.1:5173",
    "http://127.0.0.1",
    "http://127.0.0.1:0",
    "http://127.0.0.1:65536",
    `${origin}/`,
    `${origin}/page`,
    `${origin}?x=1`,
    `${origin}#x`,
    "http://user@127.0.0.1:5173",
    "http://127.0.0.1.evil.test:5173",
  ])
    assert.throws(() => developmentOrigin(false, value));
});

test("IPC renderer URL validation permits only the selected origin", () => {
  const origin = "http://127.0.0.1:5173";
  assert.equal(isRendererURL("sports://app/"), true);
  assert.equal(isRendererURL("sports://app/index.html"), true);
  assert.equal(isRendererURL(`${origin}/`, origin), true);
  for (const value of [
    `${origin}/`,
    "sports://app.evil/",
    "sports://user@app/",
    "sports://app:12/",
    "https://app/",
    "file:///index.html",
    "invalid",
  ])
    assert.equal(isRendererURL(value), false);
  for (const value of [
    "sports://app/",
    "http://127.0.0.1:5174/",
    "http://localhost:5173/",
    "http://user@127.0.0.1:5173/",
    "https://127.0.0.1:5173/",
    "http://127.0.0.1.evil.test:5173/",
  ])
    assert.equal(isRendererURL(value, origin), false);
});
