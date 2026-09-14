import { build } from "esbuild";
import { build as viteBuild } from "vite";
import { prepareAssets } from "./assets.mjs";
import { mainBuildOptions } from "./main-build.mjs";

await prepareAssets();
await viteBuild();
await build(mainBuildOptions);
