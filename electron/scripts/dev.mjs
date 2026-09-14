import { spawn } from "node:child_process";
import { createServer as createHttpServer } from "node:http";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { context } from "esbuild";
import { createServer } from "vite";
import electron from "electron";
import { prepareAssets } from "../../packages/ui/scripts/assets.mjs";
import { mainBuildOptions } from "./main-build.mjs";

process.chdir(fileURLToPath(new URL("..", import.meta.url)));
let server, builder, child, profile, restartTimer;
const http = createHttpServer();
let stopping = false;
let restarts = Promise.resolve();
let finish;
const done = new Promise((resolve) => {
  finish = resolve;
});
function stop(code = 0) {
  stopping = true;
  process.exitCode = code;
  clearTimeout(restartTimer);
  finish();
}
for (const signal of ["SIGINT", "SIGTERM", "SIGHUP"])
  process.on(signal, () => stop());

async function stopElectron() {
  const running = child;
  if (!running) return;
  child = undefined;
  const closed = new Promise((resolve) => running.once("close", resolve));
  // Each launch owns a process group. Never target other Electron instances.
  const kill = (signal) => {
    try {
      process.kill(-running.pid, signal);
    } catch (error) {
      if (error.code !== "ESRCH") throw error;
    }
  };
  kill("SIGTERM");
  const timeout = setTimeout(() => kill("SIGKILL"), 5000);
  try {
    await closed;
  } finally {
    clearTimeout(timeout);
  }
}

function scheduleRestart(origin) {
  clearTimeout(restartTimer);
  restartTimer = setTimeout(() => {
    restarts = restarts
      .then(async () => {
        await stopElectron();
        if (stopping) return;
        const env = {
          ...process.env,
          SPORTS_DEV_ORIGIN: origin,
          SPORTS_DEV_USER_DATA: profile,
        };
        delete env.ELECTRON_RUN_AS_NODE;
        const launched = spawn(electron, [".", ...process.argv.slice(2)], {
          stdio: "inherit",
          detached: true,
          env,
        });
        child = launched;
        launched.once("error", () => {
          console.error("[dev] Electron failed to launch");
          child = undefined;
          stop(1);
        });
        launched.once("close", (code) => {
          if (child !== launched) return;
          child = undefined;
          stop(code ?? 1);
        });
        console.log(`[dev] Electron PID ${launched.pid}; renderer ${origin}`);
      })
      .catch((error) => {
        console.error(error);
        stop(1);
      });
  }, 100);
}

try {
  await prepareAssets();
  if (!stopping) {
    profile = await mkdtemp(join(tmpdir(), "sports-dev-"));
    // Own the HTTP listener and shutdown; standalone Vite installs exit handlers
    // that can terminate Node before Electron has been cleaned up.
    server = await createServer({
      server: { middlewareMode: true, ws: { server: http } },
    });
    http.on("request", server.middlewares);
    await new Promise((resolve, reject) => {
      http.once("error", reject);
      http.listen(0, "127.0.0.1", resolve);
    });
    const address = http.address();
    const origin = `http://127.0.0.1:${address.port}`;
    console.log(`[dev] Vite ready at ${origin}`);
    builder = await context({
      ...mainBuildOptions,
      plugins: [
        {
          name: "restart-electron",
          setup(build) {
            build.onStart(() => {
              clearTimeout(restartTimer);
            });
            build.onEnd((result) => {
              if (!stopping && result.errors.length === 0)
                scheduleRestart(origin);
            });
          },
        },
      ],
    });
    if (!stopping) await builder.watch();
  }
  await done;
} catch (error) {
  console.error(error);
  stop(1);
} finally {
  stopping = true;
  clearTimeout(restartTimer);
  await builder?.dispose();
  await restarts;
  await stopElectron();
  await server?.close();
  await new Promise((resolve) => http.close(resolve));
  if (profile) await rm(profile, { recursive: true, force: true });
  console.log("[dev] Stopped Electron, build watcher, and Vite");
}
