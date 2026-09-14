import { test } from "node:test";
import assert from "node:assert/strict";
import type { LoaderConfiguration, LoaderContext } from "hls.js";
import { mediaLoader } from "../src/renderer/media-loader";
import { rangeHeader } from "../src/main/media";
import type { SportsApi } from "../src/shared/model";

async function loadFragment(
  start: number,
  end: number,
  destroyOnSuccess = false,
) {
  let header: string | undefined;
  const api = {
    media: async (
      _token: string,
      url: string,
      _id: string,
      range?: [number, number],
    ) => {
      header = rangeHeader(range); // The production main-process validation.
      return { url, status: 200, data: new Uint8Array([0x47, 0, 0, 0]) };
    },
    cancel() {},
  } as unknown as SportsApi;
  const Loader = mediaLoader(api, "test-token");
  const loader = new Loader();
  const context = {
    url: "https://cdn.example.test/segment.ts",
    responseType: "arraybuffer",
    rangeStart: start,
    rangeEnd: end,
  } as LoaderContext;
  const response = await new Promise<ArrayBuffer>((resolve, reject) =>
    loader.load(context, { timeout: 1000 } as LoaderConfiguration, {
      onSuccess: (response) => {
        // Hls.js FragmentLoader destroys the transport before resolving the fragment.
        if (destroyOnSuccess) loader.destroy();
        assert.equal(loader.stats.aborted, false);
        resolve(response.data as ArrayBuffer);
      },
      onError: (error) => reject(new Error(error.text)),
      onTimeout: () => reject(new Error("Timeout")),
      onAbort: () => reject(new Error("Aborted")),
    }),
  );
  loader.destroy();
  return { header, response };
}
test("normal HLS fragments use the default 0/0 range without sending a Range header", async () => {
  const { header, response } = await loadFragment(0, 0);
  assert.equal(header, undefined);
  assert.equal(new Uint8Array(response)[0], 0x47);
});
test("byte-range fragments preserve the exclusive end offset", async () => {
  const { header } = await loadFragment(100, 200);
  assert.equal(header, "bytes=100-199");
});
test("HLS success cleanup does not turn a downloaded fragment into an abort", async () => {
  const { response } = await loadFragment(0, 0, true);
  assert.equal(new Uint8Array(response)[0], 0x47);
});
