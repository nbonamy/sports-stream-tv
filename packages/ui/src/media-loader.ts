import type {
  Loader,
  LoaderCallbacks,
  LoaderConfiguration,
  LoaderContext,
  LoaderStats,
} from "hls.js";
import type { SportsApi } from "@sports/core/model";

/** HLS bytes cross the narrow preload API; provider headers stay in the main process. */
export function mediaLoader(api: SportsApi, token: string) {
  return class MediaLoader implements Loader<LoaderContext> {
    context: LoaderContext | null = null;
    stats: LoaderStats = {
      aborted: false,
      loaded: 0,
      retry: 0,
      total: 0,
      chunkCount: 0,
      bwEstimate: 0,
      loading: { start: 0, first: 0, end: 0 },
      parsing: { start: 0, end: 0 },
      buffering: { start: 0, first: 0, end: 0 },
    };
    private id = crypto.randomUUID();
    private timer?: ReturnType<typeof setTimeout>;
    private callbacks?: LoaderCallbacks<LoaderContext>;
    abort() {
      if (this.stats.aborted || this.stats.loading.end) return;
      this.stats.aborted = true;
      clearTimeout(this.timer);
      api.cancel(this.id);
      if (this.context)
        this.callbacks?.onAbort?.(this.stats, this.context, null);
    }
    destroy() {
      this.callbacks = undefined;
      this.abort();
      this.context = null;
    }
    load(
      context: LoaderContext,
      config: LoaderConfiguration,
      callbacks: LoaderCallbacks<LoaderContext>,
    ) {
      this.context = context;
      this.callbacks = callbacks;
      this.stats.loading.start = performance.now();
      this.timer = setTimeout(
        () => {
          if (this.stats.aborted) return;
          this.stats.aborted = true;
          api.cancel(this.id);
          callbacks.onTimeout(this.stats, context, null);
        },
        Math.min(
          config.loadPolicy?.maxLoadTimeMs || config.timeout || 20_000,
          30_000,
        ),
      );
      // Hls.js initializes ordinary fragments to 0/0; only a nonzero end denotes a byte range.
      const range: [number, number] | undefined = context.rangeEnd
        ? [context.rangeStart ?? 0, context.rangeEnd]
        : undefined;
      api
        .media(token, context.url, this.id, range)
        .then((response) => {
          if (this.stats.aborted) return;
          clearTimeout(this.timer);
          const bytes = new Uint8Array(response.data);
          this.stats.loaded = this.stats.total = bytes.byteLength;
          this.stats.chunkCount = 1;
          this.stats.loading.first = this.stats.loading.end = performance.now();
          const data =
            context.responseType === "arraybuffer"
              ? bytes.buffer
              : new TextDecoder().decode(bytes);
          callbacks.onSuccess(
            { url: response.url, data, code: response.status },
            this.stats,
            context,
            null,
          );
        })
        .catch(() => {
          if (this.stats.aborted) return;
          clearTimeout(this.timer);
          callbacks.onError(
            { code: 0, text: "Media unavailable" },
            context,
            null,
            this.stats,
          );
        });
    }
  };
}
