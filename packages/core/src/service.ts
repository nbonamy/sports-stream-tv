import { getEvents, getCountries } from "./catalog";
import { discover, resolve, type ResolvedStream } from "./resolver";
import { destination } from "./destination";
import { rangeHeader } from "./media";
import { pageClient, type Transport } from "./transport";
import type { Channel, StreamLink, SportsApi } from "./model";

export function createSportsService(
  request: Transport,
): Omit<SportsApi, "fullscreen"> & { clear(): void } {
  const page = pageClient(request);
  const randomUUID = () => crypto.randomUUID();
  const pending = new Map<string, { abort: AbortController; token?: string }>();
  const playback = new Map<string, ResolvedStream>();
  function link(value: StreamLink): StreamLink {
    if (
      !value ||
      typeof value.label !== "string" ||
      value.label.length > 1000 ||
      typeof value.url !== "string" ||
      value.url.length > 16384
    )
      throw new Error("Invalid stream");
    destination(value.url);
    return { label: value.label, url: value.url };
  }
  function channel(value: Channel): Channel {
    if (
      !value ||
      typeof value.name !== "string" ||
      !Array.isArray(value.links) ||
      value.links.length < 1 ||
      value.links.length > 32
    )
      throw new Error("Invalid channel");
    return { name: value.name, links: value.links.map(link) };
  }
  async function operation<T>(
    id: string,
    fn: (signal: AbortSignal) => Promise<T>,
    token?: string,
  ): Promise<T> {
    if (
      typeof id !== "string" ||
      id.length > 100 ||
      pending.has(id) ||
      pending.size >= 64
    )
      throw new Error("Invalid request");
    const abort = new AbortController();
    pending.set(id, { abort, token });
    try {
      return await fn(abort.signal);
    } catch {
      throw new Error("Source unavailable");
    } finally {
      pending.delete(id);
    }
  }
  function release(token: string) {
    playback.delete(token);
    for (const p of pending.values()) if (p.token === token) p.abort.abort();
  }
  function clear() {
    for (const p of pending.values()) p.abort.abort();
    pending.clear();
    playback.clear();
  }
  return {
    events: (id) =>
      operation(randomUUID(), (signal) => getEvents(id, signal, page)),
    countries: () =>
      operation(randomUUID(), (signal) => getCountries(signal, page)),
    streams: (value, id) => {
      const c = channel(value);
      return operation(id, (signal) => discover(c, signal, page));
    },
    resolve: (value, id) => {
      const selected = link(value);
      return operation(id, async (signal) => {
        const resolved = await resolve(selected, signal, page);
        signal.throwIfAborted();
        if (playback.size >= 4) release(playback.keys().next().value!);
        const token = randomUUID();
        playback.set(token, resolved);
        return { token, url: resolved.url, expiresAt: resolved.expiresAt };
      });
    },
    media: (token, url, id, range) => {
      const media = playback.get(token);
      if (!media) return Promise.reject(new Error("Playback ended"));
      if (typeof url !== "string" || url.length > 16384)
        return Promise.reject(new Error("Invalid media"));
      const headers = { ...media.headers };
      const requestedRange = rangeHeader(range);
      if (requestedRange) headers.Range = requestedRange;
      return operation(
        id,
        (signal) => request(url, headers, signal, 32 * 1024 * 1024),
        token,
      );
    },
    cancel: (id) => {
      pending.get(id)?.abort.abort();
    },
    release,
    clear,
  };
}
