import { build } from "vite";
import { prepareAssets } from "../../packages/ui/scripts/assets.mjs";
await prepareAssets();
await build();
