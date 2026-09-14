import https from "node:https";
import { lookup } from "node:dns";
import ipaddr from "ipaddr.js";
import { USER_AGENT } from "./provider";

export function publicAddress(address: string): boolean {
  try {
    return ipaddr.process(address).range() === "unicast";
  } catch {
    return false;
  }
}
export function destination(value: string): URL {
  // WHATWG URL repairs malformed https:/// URLs; reject them before parsing.
  if (!/^https:\/\/[^/\\]/i.test(value) || /[\s\\]/.test(value))
    throw new Error("Invalid destination");
  const url = new URL(value);
  const host = url.hostname.replace(/^\[|\]$/g, "").replace(/\.$/, "");
  if (
    url.protocol !== "https:" ||
    url.username ||
    url.password ||
    (!host.includes(".") && !host.includes(":")) ||
    /\.(localhost|local|internal)$/.test(host) ||
    (ipaddr.isValid(host) && !publicAddress(host))
  ) {
    throw new Error("Invalid destination");
  }
  return url;
}
export interface Bytes {
  url: string;
  data: Buffer;
  status: number;
}
export async function request(
  value: string,
  headers: Record<string, string> = {},
  signal?: AbortSignal,
  limit = 2 * 1024 * 1024,
  redirects = 0,
): Promise<Bytes> {
  const url = destination(value);
  signal?.throwIfAborted();
  if (redirects > 5) throw new Error("Too many redirects");
  return new Promise((resolve, reject) => {
    const req = https.get(
      url,
      {
        headers: {
          "User-Agent": USER_AGENT,
          "Accept-Encoding": "identity",
          ...headers,
        },
        signal,
        timeout: 12_000,
        // Validate addresses in the actual connection lookup, preventing DNS rebinding.
        lookup: (hostname, options, callback) =>
          lookup(hostname, { all: true }, (error, addresses) => {
            if (
              error ||
              !addresses.length ||
              addresses.some((a) => !publicAddress(a.address))
            ) {
              callback(error ?? new Error("Destination is not public"), "", 4);
              return;
            }
            if (options.all) callback(null, addresses);
            else callback(null, addresses[0].address, addresses[0].family);
          }),
      },
      (res) => {
        const status = res.statusCode ?? 0;
        if (
          [301, 302, 303, 307, 308].includes(status) &&
          res.headers.location
        ) {
          res.resume();
          const next = new URL(res.headers.location, url).href;
          request(next, headers, signal, limit, redirects + 1).then(
            resolve,
            reject,
          );
          return;
        }
        if (status < 200 || status >= 300) {
          res.resume();
          reject(new Error(`Provider HTTP ${status}`));
          return;
        }
        let size = 0;
        const chunks: Buffer[] = [];
        res.on("data", (chunk: Buffer) => {
          size += chunk.length;
          if (size > limit) {
            res.destroy(new Error("Response too large"));
            return;
          }
          chunks.push(chunk);
        });
        res.on("end", () =>
          resolve({ url: url.href, data: Buffer.concat(chunks), status }),
        );
        res.on("error", reject);
      },
    );
    const deadline = setTimeout(
      () => req.destroy(new Error("Request timed out")),
      18_000,
    );
    req.on("timeout", () => req.destroy(new Error("Request timed out")));
    req.on("close", () => clearTimeout(deadline));
    req.on("error", reject);
  });
}
export async function page(
  url: string,
  headers: Record<string, string> = {},
  signal?: AbortSignal,
) {
  const response = await request(url, headers, signal);
  return { url: response.url, body: response.data.toString("utf8") };
}
