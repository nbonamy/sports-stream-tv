import { test } from "vitest";
import assert from "node:assert/strict";
import { createSportsService } from "../src/service";
import { PROVIDER_BASE } from "../src/provider";
import type { Transport } from "../src/transport";
const channel = { label: "Stream 2", url: PROVIDER_BASE + "selected-test/" };
const media = "https://cdn.example.test/live.m3u8";
const player = "https://player.example.test/watch";
function setup() {
  let mediaSignal: AbortSignal | undefined;
  let mediaHeaders: Record<string, string> | undefined;
  const request: Transport = async (url, headers, signal) => {
    if (url.endsWith(".ts")) {
      mediaSignal = signal;
      mediaHeaders = headers;
      return new Promise((_, reject) =>
        signal!.addEventListener("abort", () => reject(signal!.reason), {
          once: true,
        }),
      );
    }
    const body =
      url === channel.url
        ? `<iframe allowfullscreen src="${player}"></iframe>`
        : url === player
          ? `file: '${media}'`
          : "#EXTM3U\n#EXTINF:6\nsegment.ts";
    return { url, status: 200, data: new TextEncoder().encode(body) };
  };
  const service = createSportsService(request);
  return { service, signal: () => mediaSignal, headers: () => mediaHeaders };
}
test("shared sessions keep final-player headers and cancel media when released", async () => {
  const { service, signal, headers } = setup();
  const playback = await service.resolve(channel, "resolve");
  const request = service.media(
    playback.token,
    "https://cdn.example.test/segment.ts",
    "fragment",
    [10, 30],
  );
  const rejected = assert.rejects(request, /Source unavailable/);
  assert.equal(headers()?.Origin, "https://player.example.test");
  assert.equal(headers()?.Referer, "https://player.example.test/");
  assert.equal(headers()?.Range, "bytes=10-29");
  service.release(playback.token);
  assert.equal(signal()?.aborted, true);
  await rejected;
  await assert.rejects(
    service.media(playback.token, media, "next"),
    /Playback ended/,
  );
});
test("clear cancels outstanding work and invalidates sessions", async () => {
  const { service, signal } = setup();
  const playback = await service.resolve(channel, "resolve");
  const request = service.media(
    playback.token,
    "https://cdn.example.test/segment.ts",
    "fragment",
  );
  const rejected = assert.rejects(request);
  service.clear();
  assert.equal(signal()?.aborted, true);
  await rejected;
  await assert.rejects(service.media(playback.token, media, "next"));
});
