import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
export default defineConfig({
  plugins: [vue()],
  base: "./",
  publicDir: "build/public",
  build: { outDir: "dist/renderer", emptyOutDir: true },
});
