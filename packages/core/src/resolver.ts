import { load } from "cheerio";
import { destination } from "./destination";
import type { PageClient } from "./transport";
import { playlist } from "./decoders";
import { PROVIDER_BASE, USER_AGENT } from "./provider";
import type { Channel, StreamLink } from "./model";

export interface ResolvedStream {
  url: string;
  headers: Record<string, string>;
  expiresAt: number | null;
}
const accepted = (url: string) => {
  try {
    destination(url);
    return true;
  } catch {
    return false;
  }
};
const absolute = (value: string | undefined, base: string) => {
  if (!value || /^https?:\/{3}|\\/.test(value)) return "";
  try {
    return new URL(value, base).href;
  } catch {
    return "";
  }
};
export function nextPages(html: string, pageUrl: string): string[] {
  const $ = load(html);
  const candidates: { url: string; score: number }[] = [];
  const wiki = $("script[src]")
    .toArray()
    .some(
      (e) =>
        absolute($(e).attr("src"), pageUrl) ===
        "https://igniteandship.com/wiki.js",
    );
  const fid = /\bfid\s*=\s*['"]([a-zA-Z0-9_-]+)['"]/.exec(html)?.[1];
  if (wiki && fid)
    candidates.push({
      url: `https://igniteandship.com/wiki.php?player=desktop&live=${fid}`,
      score: 100,
    });
  $("iframe[src],iframe[data-src]").each((_, frame) => {
    const node = $(frame);
    const family = [frame, ...node.parents().toArray()];
    const role = (e: typeof frame) =>
      ["id", "class", "title", "name", "aria-label"]
        .map((k) => $(e).attr(k) ?? "")
        .join(" ");
    if (
      family.some(
        (e) =>
          $(e).attr("hidden") !== undefined ||
          $(e).attr("aria-hidden") === "true" ||
          /(display\s*:\s*none|visibility\s*:\s*hidden)/i.test(
            $(e).attr("style") ?? "",
          ) ||
          /(^|[\s_-])(ad|ads|advert|advertisement|advertising|banner|tracking|analytics|chat|sponsor)([\s_-]|$)/i.test(
            role(e),
          ),
      ) ||
      ["width", "height"].some(
        (d) =>
          /^\d+(px)?$/.test(node.attr(d) ?? "") && parseInt(node.attr(d)!) <= 2,
      )
    )
      return;
    const url = ["src", "data-src"]
      .map((a) => absolute(node.attr(a), pageUrl))
      .find(accepted);
    if (!url) return;
    const score =
      (node.attr("allowfullscreen") !== undefined ||
      node.attr("allow")?.includes("fullscreen")
        ? 4
        : 0) +
      (family.some((e) => /(player|video|stream|embed)/i.test(role(e)))
        ? 2
        : 0) +
      (/(player|video|stream|embed)/i.test(new URL(url).pathname) ? 1 : 0);
    candidates.push({ url, score });
  });
  return [
    ...new Set(
      candidates
        .sort((a, b) => b.score - a.score)
        .map((c) => c.url)
        .filter(accepted),
    ),
  ].slice(0, 8);
}
export function streamOptions(html: string, pageUrl: string): StreamLink[] {
  const $ = load(html);
  const options: { n: number; url: string }[] = [];
  $("a[href]").each((_, el) => {
    const number = /^stream\s*#?\s*(\d+)$/i.exec($(el).text().trim())?.[1];
    if (!number) return;
    const url = absolute($(el).attr("href"), pageUrl);
    if (accepted(url) && new URL(url).host === new URL(pageUrl).host)
      options.push({ n: +number, url });
  });
  return options
    .sort((a, b) => a.n - b.n)
    .filter((o, i, all) => all.findIndex((a) => a.url === o.url) === i)
    .map((o) => ({ label: `Stream ${o.n}`, url: o.url }));
}
export async function discover(
  channel: Channel,
  signal: AbortSignal | undefined,
  client: PageClient,
): Promise<StreamLink[]> {
  const found: StreamLink[] = [];
  for (const link of channel.links) {
    const scoped = AbortSignal.any([
      AbortSignal.timeout(20_000),
      ...(signal ? [signal] : []),
    ]);
    const visited = new Set<string>();
    const walk = async (
      url: string,
      parent: string,
      depth: number,
    ): Promise<StreamLink[]> => {
      scoped.throwIfAborted();
      if (depth > 3 || visited.size >= 12 || visited.has(url)) return [];
      visited.add(url);
      try {
        const p = await client(url, { Referer: parent }, scoped);
        const options = streamOptions(p.body, p.url);
        if (options.length > 1) return options;
        for (const next of nextPages(p.body, p.url)) {
          const result = await walk(next, p.url, depth + 1);
          if (result.length) return result;
        }
      } catch {
        scoped.throwIfAborted();
      }
      return [];
    };
    try {
      const options = await walk(link.url, PROVIDER_BASE, 0);
      found.push(...(options.length ? options : [link]));
    } catch {
      signal?.throwIfAborted();
      found.push(link);
    }
  }
  return found
    .filter((l, i) => found.findIndex((other) => other.url === l.url) === i)
    .map((link, i) => ({ ...link, label: `Stream ${i + 1}` }));
}
export async function resolve(
  link: StreamLink,
  signal: AbortSignal | undefined,
  client: PageClient,
): Promise<ResolvedStream> {
  const scoped = AbortSignal.any([
    AbortSignal.timeout(60_000),
    ...(signal ? [signal] : []),
  ]);
  const visited = new Set<string>();
  const walk = async (
    url: string,
    parent: string,
    depth: number,
  ): Promise<ResolvedStream | null> => {
    scoped.throwIfAborted();
    if (depth > 6 || visited.size >= 12 || visited.has(url)) return null;
    visited.add(url);
    try {
      const p = await client(url, { Referer: parent }, scoped);
      const media = playlist(p.body);
      if (media) {
        const origin = new URL(p.url).origin;
        const headers = {
          Referer: origin + "/",
          Origin: origin,
          "User-Agent": USER_AGENT,
        };
        const manifest = await client(media, headers, scoped);
        if (manifest.body.trimStart().startsWith("#EXTM3U")) {
          const expiry = new URL(media).searchParams.get("expires");
          return {
            url: media,
            headers,
            expiresAt: expiry && /^\d+$/.test(expiry) ? +expiry * 1000 : null,
          };
        }
      }
      for (const next of nextPages(p.body, p.url)) {
        const result = await walk(next, p.url, depth + 1);
        if (result) return result;
      }
    } catch {
      scoped.throwIfAborted();
    }
    return null;
  };
  const result = await walk(link.url, PROVIDER_BASE, 0);
  if (!result) throw new Error("Stream unavailable");
  return result;
}
