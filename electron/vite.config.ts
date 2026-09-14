import { defineConfig, type Plugin } from "vite";
import vue from "@vitejs/plugin-vue";

function desktopDevelopment(): Plugin {
  return {
    name: "sports-desktop-development",
    apply: "serve",
    transformIndexHtml(html, context) {
      const server = context.server!;
      const ws = server.config.server.ws;
      const http =
        server.httpServer ?? (typeof ws === "object" ? ws.server : undefined);
      const address = http?.address();
      if (!address || typeof address === "string")
        throw new Error("Missing dev listener");
      const websocketOrigin = `ws://127.0.0.1:${address.port}`;
      // Keep the production policy intact; permit only this server's HMR socket
      // and the existing image-only custom-protocol proxy during development.
      return html
        .replace("connect-src 'self'", `connect-src 'self' ${websocketOrigin}`)
        .replace("img-src 'self' data:", "img-src 'self' data: sports://app");
    },
  };
}

export default defineConfig({
  plugins: [vue(), desktopDevelopment()],
  base: "./",
  publicDir: "build/public",
  server: { host: "127.0.0.1", port: 0, cors: false },
  build: { outDir: "dist/renderer", emptyOutDir: true },
});
