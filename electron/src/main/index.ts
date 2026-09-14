import {
  app,
  BrowserWindow,
  ipcMain,
  Menu,
  net,
  protocol,
  screen,
  type IpcMainInvokeEvent,
} from "electron";
import { join, resolve as resolvePath, sep } from "node:path";
import { pathToFileURL } from "node:url";
import { createSportsService } from "@sports/core/service";
import { request } from "./http";
import { developmentOrigin, isRendererURL } from "./renderer-origin";
import { restoreWindowState, saveWindowState } from "./window-state";
import type { Channel, StreamLink } from "@sports/core/model";

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
const devOrigin = developmentOrigin(
  app.isPackaged,
  process.env.SPORTS_DEV_ORIGIN,
);
if (devOrigin && process.env.SPORTS_DEV_USER_DATA)
  app.setPath("userData", process.env.SPORTS_DEV_USER_DATA);
let window: BrowserWindow | null = null;
const service = createSportsService(request);
const check = (event: IpcMainInvokeEvent) => {
  if (
    !window ||
    event.sender !== window.webContents ||
    event.senderFrame !== window.webContents.mainFrame ||
    !isRendererURL(event.senderFrame.url, devOrigin)
  )
    throw new Error("Invalid sender");
};

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
    return service.events(id);
  });
  ipcMain.handle("countries", (e) => {
    check(e);
    return service.countries();
  });
  ipcMain.handle("streams", (e, value: Channel, id: string) => {
    check(e);
    return service.streams(value, id);
  });
  ipcMain.handle("resolve", (e, value: StreamLink, id: string) => {
    check(e);
    return service.resolve(value, id);
  });
  ipcMain.handle(
    "media",
    (e, token: string, url: string, id: string, range?: [number, number]) => {
      check(e);
      return service.media(token, url, id, range);
    },
  );
  ipcMain.on("cancel", (e, id: string) => {
    check(e);
    service.cancel(id);
  });
  ipcMain.on("release", (e, id: string) => {
    check(e);
    service.release(id);
  });
  ipcMain.handle("fullscreen", (e, enabled?: boolean) => {
    check(e);
    if (enabled !== undefined && typeof enabled !== "boolean")
      throw new Error("Invalid fullscreen state");
    const wasFullscreen = window!.isFullScreen();
    window!.setFullScreen(enabled ?? !wasFullscreen);
    return wasFullscreen;
  });
  function createWindow() {
    const statePath = join(app.getPath("userData"), "window-state.json");
    const { bounds, maximized } = restoreWindowState(
      statePath,
      screen.getAllDisplays().map((display) => display.workArea),
      screen.getPrimaryDisplay().workArea,
    );
    window = new BrowserWindow({
      ...bounds,
      show: false,
      minWidth: Math.min(800, bounds.width),
      minHeight: Math.min(600, bounds.height),
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
    window.webContents.on("render-process-gone", service.clear);
    const createdWindow = window;
    window.once("ready-to-show", () => {
      if (maximized) createdWindow.maximize();
      createdWindow.show();
    });
    window.on("close", () => {
      saveWindowState(statePath, createdWindow.getNormalBounds(), createdWindow.isMaximized());
    });
    window.on("closed", () => {
      service.clear();
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
app.on("before-quit", service.clear);
