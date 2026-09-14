import ipaddr from "ipaddr.js";

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
