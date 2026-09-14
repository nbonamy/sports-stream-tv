import { contextBridge, ipcRenderer } from "electron";
import type { SportsApi } from "@sports/core/model";

const api: SportsApi = {
  events: (id) => ipcRenderer.invoke("events", id),
  countries: () => ipcRenderer.invoke("countries"),
  streams: (channel, id) => ipcRenderer.invoke("streams", channel, id),
  resolve: (link, id) => ipcRenderer.invoke("resolve", link, id),
  cancel: (id) => ipcRenderer.send("cancel", id),
  release: (token) => ipcRenderer.send("release", token),
  media: (token, url, id, range) =>
    ipcRenderer.invoke("media", token, url, id, range),
  fullscreen: (enabled) => ipcRenderer.invoke("fullscreen", enabled),
};
contextBridge.exposeInMainWorld("sports", api);
