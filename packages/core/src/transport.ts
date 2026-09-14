export interface Bytes {
  url: string;
  data: Uint8Array;
  status: number;
}
/** GET public HTTPS URLs, validate redirects/DNS, bound time and decoded bytes,
 * preserve provider headers, and cancel the underlying request on abort. */
export type Transport = (
  url: string,
  headers?: Record<string, string>,
  signal?: AbortSignal,
  limit?: number,
) => Promise<Bytes>;
export type PageClient = (
  url: string,
  headers?: Record<string, string>,
  signal?: AbortSignal,
) => Promise<{ url: string; body: string }>;
export function pageClient(request: Transport): PageClient {
  return async (url, headers, signal) => {
    const result = await request(url, headers, signal);
    return { url: result.url, body: new TextDecoder().decode(result.data) };
  };
}
