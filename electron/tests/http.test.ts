import { test, type TestContext } from "node:test";
import assert from "node:assert/strict";
import https from "node:https";
import { PassThrough } from "node:stream";
import { EventEmitter } from "node:events";
import { gzipSync, deflateSync, brotliCompressSync } from "node:zlib";
import { request, page } from "../src/main/http";
import { resolve } from "@sports/core/resolver";
import { PROVIDER_BASE, USER_AGENT } from "@sports/core/provider";

function responses(
  t: TestContext,
  replies: { body: Buffer; encoding?: string }[],
) {
  const calls: { url: string; headers: Record<string, string> }[] = [];
  t.mock.method(
    https,
    "get",
    (
      url: URL,
      options: { headers: Record<string, string> },
      callback: (res: PassThrough) => void,
    ) => {
      calls.push({ url: url.href, headers: options.headers });
      const reply = replies.shift();
      assert.ok(reply, "Unexpected request");
      const res = Object.assign(new PassThrough(), {
        statusCode: 200,
        headers: { "content-encoding": reply.encoding },
      });
      const req = Object.assign(new EventEmitter(), {
        destroy(error: Error) {
          res.destroy(error);
          return req;
        },
      });
      queueMicrotask(() => {
        callback(res);
        res.end(reply.body);
        req.emit("close");
      });
      return req;
    },
  );
  return calls;
}

// Synthetic responses retain the provider's compression and embed structure.
test("Vix selected stream follows a gzip wrapper despite requesting identity", async (t) => {
  const selected = "https://freestreams-live1h.pk/vix/";
  const wrapper = "https://quellefrappe.click/player/vix";
  const player = "https://traitaunt.net/embed/vix";
  const media = "https://cdn.example.test/vix.m3u8";
  const calls = responses(t, [
    { body: Buffer.from(`<iframe allowfullscreen src="${wrapper}"></iframe>`) },
    {
      encoding: "gzip",
      body: gzipSync(
        `<a href="${PROVIDER_BASE}other/">Stream 2</a><iframe allowfullscreen src="${player}"></iframe>`,
      ),
    },
    { body: Buffer.from(`source: "${media}"`) },
    { encoding: "gzip", body: gzipSync("#EXTM3U\n#EXTINF:8,\nsegment.ts") },
  ]);
  const result = await resolve({ label: "Stream 1", url: selected }, undefined, page);
  assert.deepEqual(
    calls.map((c) => c.url),
    [selected, wrapper, player, media],
  );
  assert.equal(calls[1].headers.Referer, selected);
  assert.equal(calls[2].headers.Referer, wrapper);
  assert.equal(calls[1].headers["Accept-Encoding"], "identity");
  assert.deepEqual(result.headers, {
    Referer: "https://traitaunt.net/",
    Origin: "https://traitaunt.net",
    "User-Agent": USER_AGENT,
  });
  for (const [key, value] of Object.entries(result.headers))
    assert.equal(calls[3].headers[key], value);
});

for (const [encoding, compress] of [
  ["gzip", gzipSync],
  ["deflate", deflateSync],
  ["br", brotliCompressSync],
] as const) {
  test(`HTTP decodes ${encoding} and bounds expanded bytes`, async (t) => {
    responses(t, [
      { encoding, body: compress("#EXTM3U") },
      { encoding, body: compress("x".repeat(4096)) },
    ]);
    assert.equal(
      (await request("https://player.test/live")).data.toString(),
      "#EXTM3U",
    );
    await assert.rejects(
      request("https://player.test/live", {}, undefined, 100),
      /Response too large/,
    );
  });
}

test("HTTP rejects malformed compression and unsupported encoding", async (t) => {
  responses(t, [
    { encoding: "gzip", body: Buffer.from("broken") },
    { encoding: "unknown", body: Buffer.from("body") },
  ]);
  await assert.rejects(request("https://player.test/live"));
  await assert.rejects(
    request("https://player.test/live"),
    /Unsupported content encoding/,
  );
});

test("HTTP bounds compressed wire bytes and rejects truncated gzip", async (t) => {
  const body = gzipSync(Buffer.from(Array.from({ length: 80 }, (_, i) => i)));
  assert.ok(body.length > 90);
  responses(t, [
    { encoding: "gzip", body },
    { encoding: "gzip", body: body.subarray(0, -4) },
  ]);
  await assert.rejects(
    request("https://player.test/live", {}, undefined, 90),
    /Response too large/,
  );
  await assert.rejects(request("https://player.test/live"));
});

test("HTTP preserves cancellation during a compressed response", async (t) => {
  const controller = new AbortController();
  let response: PassThrough | undefined;
  t.mock.method(
    https,
    "get",
    (
      _url: URL,
      options: { signal: AbortSignal },
      callback: (res: PassThrough) => void,
    ) => {
      const res = Object.assign(new PassThrough(), {
        statusCode: 200,
        headers: { "content-encoding": "gzip" },
      });
      response = res;
      const req = Object.assign(new EventEmitter(), {
        destroy(error: Error) {
          res.destroy(error);
          return req;
        },
      });
      options.signal.addEventListener(
        "abort",
        () => {
          req.destroy(options.signal.reason);
          req.emit("close");
        },
        { once: true },
      );
      queueMicrotask(() => {
        callback(res);
        res.write(gzipSync("body").subarray(0, 10));
        controller.abort(new Error("Selection cancelled"));
      });
      return req;
    },
  );
  await assert.rejects(
    request("https://player.test/live", {}, controller.signal),
    /Selection cancelled/,
  );
  assert.equal(response?.destroyed, true);
});
