import { registerPlugin } from "@capacitor/core";
import { destination } from "@sports/core/destination";
import { USER_AGENT } from "@sports/core/provider";
import type { Transport } from "@sports/core/transport";
export interface NativeTransport {
  request(options: {
    id: string;
    url: string;
    headers: Record<string, string>;
    limit: number;
  }): Promise<{ url: string; data: string; status: number }>;
  cancel(options: { id: string }): Promise<void>;
}
const native = registerPlugin<NativeTransport>("SportsHttp");
export function createTransport(bridge: NativeTransport): Transport {
  return async (value, headers = {}, signal, limit = 2 * 1024 * 1024) => {
    const url = destination(value).href;
    signal?.throwIfAborted();
    const id = crypto.randomUUID();
    const cancel = () => {
      void bridge.cancel({ id }).catch(() => {});
    };
    signal?.addEventListener("abort", cancel, { once: true });
    try {
      const response = await bridge.request({
        id,
        url,
        headers: { "User-Agent": USER_AGENT, ...headers },
        limit,
      });
      signal?.throwIfAborted();
      destination(response.url);
      if (response.status < 200 || response.status >= 300)
        throw new Error("Source unavailable");
      if (response.data.length > Math.ceil(limit / 3) * 4)
        throw new Error("Response too large");
      const data = Uint8Array.from(atob(response.data), (c) => c.charCodeAt(0));
      if (data.length > limit) throw new Error("Response too large");
      return { url: response.url, status: response.status, data };
    } finally {
      signal?.removeEventListener("abort", cancel);
    }
  };
}
export const request = createTransport(native);
