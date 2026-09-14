import { build } from "esbuild";
import { build as viteBuild } from "vite";
import { prepareAssets } from "../../packages/ui/scripts/assets.mjs";
import { mainBuildOptions } from "./main-build.mjs";

await prepareAssets();
await viteBuild();
await build(mainBuildOptions);
