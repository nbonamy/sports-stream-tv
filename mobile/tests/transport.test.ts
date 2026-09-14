import { test } from "node:test";
import assert from "node:assert/strict";
import { createTransport, type NativeTransport } from "../src/transport";
const url = "https://cdn.example.test/live.ts";
test("mobile bridge preserves binary bytes and headers", async () => {
  const request = createTransport({
    async request(options) {
      assert.equal(options.headers.Origin, "https://player.example.test");
      assert.equal(options.headers.Range, "bytes=4-8");
      assert.equal(options.limit, 1024);
      return { url, status: 206, data: "AP+A" };
    },
    async cancel() {},
  });
  const response = await request(
    url,
    { Origin: "https://player.example.test", Range: "bytes=4-8" },
    undefined,
    1024,
  );
  assert.deepEqual([...response.data], [0, 255, 128]);
});
test("abort reaches native request and discards a late response", async () => {
  let finish!: (value: Awaited<ReturnType<NativeTransport["request"]>>) => void;
  let started = "",
    cancelled = "";
  const request = createTransport({
    request(options) {
      started = options.id;
      return new Promise((resolve) => {
        finish = resolve;
      });
    },
    async cancel(options) {
      cancelled = options.id;
    },
  });
  const controller = new AbortController();
  const result = request(url, {}, controller.signal);
  controller.abort();
  assert.equal(cancelled, started);
  finish({ url, status: 200, data: "AA==" });
  await assert.rejects(result, { name: "AbortError" });
});
test("invalid destinations and oversized native results are rejected", async () => {
  const request = createTransport({
    async request() {
      return { url, status: 200, data: "AAAA" };
    },
    async cancel() {},
  });
  await assert.rejects(request("https://127.0.0.1/"));
  await assert.rejects(request(url, {}, undefined, 1), /Response too large/);
});
