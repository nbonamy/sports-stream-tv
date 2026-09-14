/** Only the runner's explicit IPv4 loopback origin can enable development. */
export function developmentOrigin(
  packaged: boolean,
  value?: string,
): string | undefined {
  if (packaged || !value) return undefined;
  if (!/^http:\/\/127\.0\.0\.1:[1-9]\d{0,4}$/.test(value))
    throw new Error("Invalid development origin");
  const url = new URL(value);
  if (!url.port || Number(url.port) > 65535)
    throw new Error("Invalid development origin");
  return url.origin;
}

export function isRendererURL(value: string, devOrigin?: string): boolean {
  try {
    const url = new URL(value);
    if (url.username || url.password) return false;
    return devOrigin
      ? url.origin === devOrigin
      : url.protocol === "sports:" && url.host === "app";
  } catch {
    return false;
  }
}
