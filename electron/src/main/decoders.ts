import { load } from "cheerio";
import { destination } from "./http";

const escaped = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const unescapeUrl = (s: string) =>
  s.replace(/\\\//g, "/").replace(/&amp;/g, "&");
export function isHls(value: string): boolean {
  try {
    return destination(value).pathname.endsWith(".m3u8");
  } catch {
    return false;
  }
}
function decode64(value: string): string {
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(value) || value.length % 4 === 1)
    throw new Error("Malformed base64");
  return Buffer.from(value, "base64").toString("utf8");
}
export function barecrop(html: string): string | null {
  try {
    const encoded = /window\._econfig\s*=\s*['"]([A-Za-z0-9+/=]+)['"]/.exec(
      html,
    )?.[1];
    if (!encoded || encoded.length > 512 * 1024) return null;
    const envelope = decode64(encoded);
    const size = envelope.length / 4;
    if (!Number.isInteger(size) || size <= 4) return null;
    const pieces: string[] = [];
    [2, 0, 3, 1].forEach((dest, i) => {
      const p = envelope.slice(i * size, (i + 1) * size);
      pieces[dest] = decode64(p.slice(0, 3) + p.slice(4));
    });
    const config = JSON.parse(decode64(pieces.join("")));
    for (const key of ["stream_url_nop2p", "stream_url"])
      if (typeof config[key] === "string" && config[key].trim())
        return config[key];
  } catch {
    /* Unknown/malformed envelopes are unsupported. */
  }
  return null;
}
export function indexed(html: string): string | null {
  try {
    const name =
      /\b([A-Za-z_$][\w$]*)\.forEach\(e\s*=>\s*\{\s*let\s+v\s*=\s*e\[1\];\s*playbackURL\s*\+=\s*String\.fromCharCode\(parseInt\(atob\(v\)\.replace\(\/\\D\/g,\s*''\)\)\s*-\s*k\)\s*\}\);/.exec(
        html,
      )?.[1];
    if (
      !name ||
      !new RegExp(
        `${escaped(name)}\\.sort\\(\\(a,b\\)\\s*=>\\s*a\\[0\\]\\s*-\\s*b\\[0\\]\\);`,
      ).test(html) ||
      !/\bsource\s*:\s*playbackURL\s*[,}]/.test(html)
    )
      return null;
    const fn = /\bvar\s+k\s*=\s*(\w+)\(\)\s*\+\s*(\w+)\(\)\s*;/.exec(html);
    if (!fn) return null;
    let offset = 0;
    for (const n of fn.slice(1)) {
      const value = new RegExp(
        `function\\s+${escaped(n)}\\(\\)\\s*\\{\\s*return\\s+(\\d+)\\s*;\\s*\\}`,
      ).exec(html)?.[1];
      if (value === undefined) return null;
      offset += Number(value);
    }
    if (offset > 2147483647) return null;
    const raw = new RegExp(
      `\\b${escaped(name)}\\s*=\\s*(\\[\\[.*?\\]\\]);`,
      "s",
    ).exec(html)?.[1];
    if (!raw) return null;
    const pairs = JSON.parse(raw);
    if (!Array.isArray(pairs) || pairs.length < 1 || pairs.length > 8192)
      return null;
    const result: (string | undefined)[] = Array(pairs.length);
    for (const pair of pairs) {
      if (!Array.isArray(pair) || pair.length !== 2) return null;
      const [index, encoded] = pair;
      if (
        !Number.isInteger(index) ||
        index < 0 ||
        index >= result.length ||
        result[index] !== undefined ||
        typeof encoded !== "string" ||
        encoded.length > 64
      )
        return null;
      const digits = decode64(encoded).replace(/\D/g, "");
      if (!digits) return null;
      const code = Number(digits) - offset;
      if (!Number.isInteger(code) || code < 32 || code > 126) return null;
      result[index] = String.fromCharCode(code);
    }
    return result.join("");
  } catch {
    return null;
  }
}
function stringArray(json: string): string {
  const value = JSON.parse(json);
  if (!Array.isArray(value) || value.some((s) => typeof s !== "string"))
    throw new Error("Invalid string array");
  return value.join("");
}
export function playlist(html: string): string | null {
  for (const decoded of [barecrop(html), indexed(html)])
    if (decoded && isHls(decoded)) return decoded;
  // Bind a literal array to the player's exact index, never try other entries.
  for (const match of html.matchAll(
    /\b(?:source|file|src)\s*:\s*([A-Za-z_$][\w$]*)\s*\[\s*(\d{1,4})\s*\]\s*[,}]/g,
  )) {
    const declaration = new RegExp(
      `\\b(?:var|let|const)\\s+${escaped(match[1])}\\s*=\\s*(\\[[^\\]]{0,65536}\\])\\s*;`,
    ).exec(html);
    if (!declaration) continue;
    try {
      const values: unknown = JSON.parse(declaration[1]);
      if (
        !Array.isArray(values) ||
        values.length > 128 ||
        values.some((value) => typeof value !== "string")
      )
        continue;
      const value = values[Number(match[2])];
      if (typeof value === "string" && isHls(value)) return value;
    } catch {
      // Only JSON string arrays are supported; provider expressions stay data.
    }
  }
  const expression =
    /return\s*\(\s*(\[\s*".*?\])\.join\(\s*""\s*\)(.*?)\)\s*;/s.exec(html);
  if (expression) {
    try {
      let value = stringArray(expression[1]);
      let suffix = expression[2];
      while (suffix.trim()) {
        const variable = /^\s*\+\s*(\w+)\.join\(\s*""\s*\)/.exec(suffix);
        const element =
          /^\s*\+\s*document\.getElementById\(['"]([^'"]+)['"]\)\.innerHTML/.exec(
            suffix,
          );
        if (variable) {
          const declaration = new RegExp(
            `\\b(?:var|let|const)\\s+${escaped(variable[1])}\\s*=\\s*(\\[.*?\\]);`,
            "s",
          ).exec(html);
          if (!declaration) return null;
          value += stringArray(declaration[1]);
          suffix = suffix.slice(variable[0].length);
        } else if (element) {
          const $ = load(html);
          const node = $("[id]")
            .filter((_, e) => $(e).attr("id") === element[1])
            .first();
          if (!node.length) return null;
          value += node.html();
          suffix = suffix.slice(element[0].length);
        } else return null;
      }
      if (isHls(value)) return value;
    } catch {
      return null;
    }
  }
  for (const match of html.matchAll(
    /(?:source|file|src)\s*[:=]\s*['"](https?[^'"\s]+)['"]/g,
  )) {
    const value = unescapeUrl(match[1]);
    if (isHls(value)) return value;
  }
  for (const match of html.matchAll(
    /\bsource\s*:\s*([A-Za-z_$][\w$]*)\s*[,}]/g,
  )) {
    const declaration = new RegExp(
      `\\b(?:var|let|const)\\s+${escaped(match[1])}\\s*=\\s*(['"])(https?[^'"\\s]+)\\1\\s*;`,
    ).exec(html);
    if (declaration && isHls(unescapeUrl(declaration[2])))
      return unescapeUrl(declaration[2]);
  }
  return null;
}
