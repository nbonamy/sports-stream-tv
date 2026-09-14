import { createServer } from "vite";
import { prepareAssets } from "../../packages/ui/scripts/assets.mjs";
await prepareAssets();
const server = await createServer();
await server.listen();
server.printUrls();
