import { load } from "cheerio";
import { destination } from "./destination";
import { USER_AGENT } from "./provider";
import type { PageClient } from "./transport";

const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const id = "([A-Za-z_$][\\w$]*)";
const value = (v: unknown): string => {
  if (
    (typeof v !== "string" && typeof v !== "number") ||
    !String(v).trim() ||
    String(v).length > 8192
  )
    throw new Error("Invalid player data");
  return String(v);
};
const base64 = (s: string) => {
  if (s.length > 32768 || !/^[A-Za-z0-9+/]+={0,2}$/.test(s))
    throw new Error("Invalid player encoding");
  return atob(s);
};

/** The observed embed script assembles this iframe from attributes and JSON. */
export function sessionEmbed(html: string, pageUrl: string): string | null {
  try {
    const $ = load(html);
    const script = $("script[data-pldm][data-pid][data-play]")
      .toArray()
      .find(
        (e) =>
          destination(new URL($(e).attr("src") ?? "", pageUrl).href)
            .pathname === "/scripts/v1/embed.min.js",
      );
    if (!script || !$(".video-player,.player-embed,.ratio").length) return null;
    const data = JSON.parse(
      /\b(?:const|let|var)\s+siteConfig\s*=\s*(\{[^\n]{1,65536}?\})\s*;/.exec(
        html,
      )?.[1] ?? "",
    );
    const node = $(script),
      host = value(node.attr("data-pldm")).trim();
    if (/[\s/\\?#@]/.test(host)) return null;
    const category = node.attr("data-gc")?.trim() || "sports";
    if (!/^[\w-]+$/.test(category)) return null;
    const url = destination(`https://${host}/sd0embed/${category}`);
    url.search = new URLSearchParams({
      pid: value(node.attr("data-pid")).trim(),
      v: value(node.attr("data-play")).trim(),
      player: node.attr("data-player")?.trim() || "jw",
      csrf: value(data.csrf),
      csrf_ip: value(data.csrf_ip),
      expires: value(data.sec_expires),
      sec_hash: value(data.sec_hash),
    }).toString();
    return url.href;
  } catch {
    return null;
  }
}

function sessionConfiguration(html: string) {
  try {
    const selected = new RegExp(`\\bfile\\s*:\\s*${id}\\.toString\\(\\)`).exec(
      html,
    )?.[1];
    if (!selected) return null;
    const source = new RegExp(
      `\\b(?:let|const|var)\\s+${escape(selected)}\\s*=\\s*new URL\\(${id}\\)`,
    ).exec(html)?.[1];
    if (!source) return null;
    const encoded = new RegExp(
      `\\b${escape(source)}\\s*=\\s*${id}\\(String\\.fromCharCode\\(\\.\\.\\.(\\[[\\d,\\s]{1,65536}\\])\\)\\)`,
    ).exec(html);
    if (!encoded) return null;
    const decode = escape(encoded[1]);
    const decoder = new RegExp(
      `function\\s+${decode}\\(${id}\\)\\s*\\{\\s*return\\s+(?:window\\.)?atob\\(\\1\\)\\s*;?\\s*\\}`,
    ).test(html);
    if (!decoder) return null;
    const chars: unknown = JSON.parse(encoded[2]);
    if (
      !Array.isArray(chars) ||
      chars.length > 16384 ||
      chars.some((c) => !Number.isInteger(c) || c < 0 || c > 127)
    )
      return null;
    const media = destination(base64(String.fromCharCode(...chars)));
    const template = new RegExp(
      `\\$\\{${id}\\}\\?scode=\\$\\{encodeURIComponent\\([^)]*\\.scode\\)\\}&stream=\\$\\{encodeURIComponent\\(${id}\\)\\}`,
    ).exec(html);
    const initial =
      /\b[A-Za-z_$][\w$]*\(\{scode:"([^"\n]{1,8192})",ts:([A-Za-z_$][\w$]*),session_id:"([^"\n]{1,8192})",device_id:([A-Za-z_$][\w$]*)\}\)/.exec(
        html,
      );
    if (!template || !initial) return null;
    const literal = (name: string) =>
      JSON.parse(
        new RegExp(`\\b${escape(name)}\\s*=\\s*("[^"\\n]{1,8192}")`).exec(
          html,
        )?.[1] ?? "",
      );
    const endpoint = new RegExp(
      `\\b${escape(template[1])}\\s*=\\s*${decode}\\("([A-Za-z0-9+/=]+)"\\)`,
    ).exec(html)?.[1];
    const csrf = new RegExp(
      `"X-CSRF-Auth"\\s*:\\s*${decode}\\("([A-Za-z0-9+/=]+)"\\)`,
    ).exec(html)?.[1];
    const expires = new RegExp(
      `\\b${escape(initial[2])}\\s*=\\s*parseInt\\((\\d{1,12}),10\\)`,
    ).exec(html)?.[1];
    const host = /&host_id=`\+encodeURIComponent\("([^"\n]{1,256})"\)/.exec(
      html,
    )?.[1];
    if (!endpoint || !csrf || !expires || !host) return null;
    const auth = destination(base64(endpoint));
    auth.search = new URLSearchParams({
      scode: initial[1],
      stream: literal(template[2]),
      expires,
      u_id: literal(initial[4]),
      session_id: initial[3],
      host_id: host,
    }).toString();
    return { media, auth, csrf: base64(csrf) };
  } catch {
    return null;
  }
}

/** Register the published session, then use its returned device ID and expiry. */
export async function resolveSessionPlayer(
  html: string,
  pageUrl: string,
  client: PageClient,
  signal: AbortSignal,
) {
  const config = sessionConfiguration(html);
  if (!config) return null;
  const origin = new URL(pageUrl).origin;
  const headers = {
    Referer: origin + "/",
    Origin: origin,
    "User-Agent": USER_AGENT,
  };
  const response = await client(
    config.auth.href,
    { ...headers, Accept: "application/json", "X-CSRF-Auth": config.csrf },
    signal,
  );
  const session = JSON.parse(response.body);
  if (
    session.success !== true ||
    typeof session.device_id !== "string" ||
    !session.device_id ||
    session.device_id.length > 8192 ||
    !Number.isSafeInteger(session.valid_until) ||
    session.valid_until <= 0 ||
    session.valid_until > Number.MAX_SAFE_INTEGER / 1000
  )
    throw new Error("Invalid player session");
  config.media.searchParams.set("u_id", session.device_id);
  const manifest = await client(config.media.href, headers, signal);
  if (!manifest.body.trimStart().startsWith("#EXTM3U"))
    throw new Error("Invalid player playlist");
  return {
    url: config.media.href,
    headers,
    expiresAt: session.valid_until * 1000,
  };
}
