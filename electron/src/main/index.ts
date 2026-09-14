import {
  app,
  BrowserWindow,
  ipcMain,
  Menu,
  net,
  protocol,
  type IpcMainInvokeEvent,
} from "electron";
import { join, resolve as resolvePath, sep } from "node:path";
import { pathToFileURL } from "node:url";
import { randomUUID } from "node:crypto";
import { getCountries, getEvents } from "./catalog";
import { discover, resolve, type ResolvedStream } from "./resolver";
import { destination, request } from "./http";
import { rangeHeader } from "./media";
import { developmentOrigin, isRendererURL } from "./renderer-origin";
import type { Channel, StreamLink } from "../shared/model";

protocol.registerSchemesAsPrivileged([
  {
    scheme: "sports",
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      corsEnabled: true,
    },
  },
]);
app.setName("Sports");
const devOrigin = developmentOrigin(app.isPackaged, process.env.SPORTS_DEV_ORIGIN);
if (devOrigin && process.env.SPORTS_DEV_USER_DATA)
  app.setPath("userData", process.env.SPORTS_DEV_USER_DATA);
let window: BrowserWindow | null = null;
const pending = new Map<string, { abort: AbortController; token?: string }>();
const playback = new Map<string, ResolvedStream>();
const check = (event: IpcMainInvokeEvent) => {
  if (
    !window ||
    event.sender !== window.webContents ||
    event.senderFrame !== window.webContents.mainFrame ||
    !isRendererURL(event.senderFrame.url, devOrigin)
  )
    throw new Error("Invalid sender");
};
function link(value: StreamLink): StreamLink {
  if (
    !value ||
    typeof value.label !== "string" ||
    value.label.length > 1000 ||
    typeof value.url !== "string" ||
    value.url.length > 16384
  )
    throw new Error("Invalid stream");
  destination(value.url);
  return { label: value.label, url: value.url };
}
function channel(value: Channel): Channel {
  if (
    !value ||
    typeof value.name !== "string" ||
    !Array.isArray(value.links) ||
    value.links.length < 1 ||
    value.links.length > 32
  )
    throw new Error("Invalid channel");
  return { name: value.name, links: value.links.map(link) };
}
async function operation<T>(
  id: string,
  fn: (signal: AbortSignal) => Promise<T>,
  token?: string,
): Promise<T> {
  if (
    typeof id !== "string" ||
    id.length > 100 ||
    pending.has(id) ||
    pending.size >= 64
  )
    throw new Error("Invalid request");
  const abort = new AbortController();
  pending.set(id, { abort, token });
  try {
    return await fn(abort.signal);
  } catch {
    throw new Error("Source unavailable");
  } finally {
    pending.delete(id);
  }
}
function release(token: string) {
  playback.delete(token);
  for (const p of pending.values()) if (p.token === token) p.abort.abort();
}
function clear() {
  for (const p of pending.values()) p.abort.abort();
  pending.clear();
  playback.clear();
}

app.whenReady().then(() => {
  if (!app.isPackaged && process.platform === "darwin")
    app.dock?.setIcon(join(__dirname, "../../resources/icon.png"));
  const renderer = join(__dirname, "../renderer");
  protocol.handle("sports", async (req) => {
    const url = new URL(req.url);
    if (url.host !== "app") return new Response(null, { status: 404 });
    if (url.pathname === "/league") {
      try {
        const response = await request(
          url.searchParams.get("url") ?? "",
          {},
          AbortSignal.timeout(8000),
          512 * 1024,
        );
        const b = response.data;
        const type = b
          .subarray(0, 8)
          .equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
          ? "image/png"
          : b[0] === 255 && b[1] === 216
            ? "image/jpeg"
            : b.toString("ascii", 0, 4) === "RIFF" &&
                b.toString("ascii", 8, 12) === "WEBP"
              ? "image/webp"
              : "";
        if (!type) return new Response(null, { status: 415 });
        return new Response(new Uint8Array(b), {
          headers: { "Content-Type": type, "Cache-Control": "max-age=3600" },
        });
      } catch {
        return new Response(null, { status: 404 });
      }
    }
    const file = resolvePath(
      renderer,
      "." +
        decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname),
    );
    if (!file.startsWith(renderer + sep))
      return new Response(null, { status: 403 });
    return net.fetch(pathToFileURL(file).href);
  });
  ipcMain.handle("events", (e, id: string) => {
    check(e);
    return operation(randomUUID(), (signal) => getEvents(id, signal));
  });
  ipcMain.handle("countries", (e) => {
    check(e);
    return operation(randomUUID(), getCountries);
  });
  ipcMain.handle("streams", (e, value: Channel, id: string) => {
    check(e);
    const c = channel(value);
    return operation(id, (signal) => discover(c, signal));
  });
  ipcMain.handle("resolve", (e, value: StreamLink, id: string) => {
    check(e);
    const selected = link(value);
    return operation(id, async (signal) => {
      const resolved = await resolve(selected, signal);
      signal.throwIfAborted();
      if (playback.size >= 4) release(playback.keys().next().value!);
      const token = randomUUID();
      playback.set(token, resolved);
      return { token, url: resolved.url, expiresAt: resolved.expiresAt };
    });
  });
  ipcMain.handle(
    "media",
    (e, token: string, url: string, id: string, range?: [number, number]) => {
      check(e);
      const media = playback.get(token);
      if (!media) throw new Error("Playback ended");
      if (typeof url !== "string" || url.length > 16384)
        throw new Error("Invalid media");
      const headers = { ...media.headers };
      const requestedRange = rangeHeader(range);
      if (requestedRange) headers.Range = requestedRange;
      return operation(
        id,
        async (signal) => {
          const r = await request(url, headers, signal, 32 * 1024 * 1024);
          return { url: r.url, data: new Uint8Array(r.data), status: r.status };
        },
        token,
      );
    },
  );
  ipcMain.on("cancel", (e, id: string) => {
    check(e);
    pending.get(id)?.abort.abort();
  });
  ipcMain.on("release", (e, token: string) => {
    check(e);
    release(token);
  });
  ipcMain.handle("fullscreen", (e, enabled?: boolean) => {
    check(e);
    if (enabled !== undefined && typeof enabled !== "boolean") throw new Error("Invalid fullscreen state");
    const wasFullscreen = window!.isFullScreen();
    window!.setFullScreen(enabled ?? !wasFullscreen);
    return wasFullscreen;
  });
  function createWindow() {
    window = new BrowserWindow({
      width: 1280,
      height: 820,
      minWidth: 800,
      minHeight: 600,
      backgroundColor: "#07111d",
      title: "Sports",
      titleBarStyle: "hiddenInset",
      webPreferences: {
        preload: join(__dirname, "preload.cjs"),
        contextIsolation: true,
        sandbox: true,
        nodeIntegration: false,
        autoplayPolicy: "no-user-gesture-required",
      },
    });
    window.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
    window.webContents.on("will-navigate", (e) => e.preventDefault());
    window.webContents.session.setPermissionRequestHandler((_, __, callback) =>
      callback(false),
    );
    window.webContents.session.setPermissionCheckHandler(() => false);
    window.webContents.on("render-process-gone", clear);
    window.on("closed", () => {
      clear();
      window = null;
    });
    void window.loadURL(devOrigin ? `${devOrigin}/` : "sports://app/");
  }
  Menu.setApplicationMenu(
    Menu.buildFromTemplate([
      { role: "appMenu" },
      { role: "editMenu" },
      {
        label: "View",
        submenu: [
          { role: "togglefullscreen" },
          { role: "zoomIn" },
          { role: "zoomOut" },
          { role: "resetZoom" },
          ...(!app.isPackaged ? [{ role: "toggleDevTools" as const }] : []),
        ],
      },
      { role: "windowMenu" },
    ]),
  );
  createWindow();
  app.on("activate", () => {
    if (!window) createWindow();
  });
});
app.on("window-all-closed", () => {
  if (devOrigin || process.platform !== "darwin") app.quit();
});
app.on("before-quit", clear);
