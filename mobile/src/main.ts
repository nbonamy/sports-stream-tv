import { createApp } from "vue";
import { App as NativeApp } from "@capacitor/app";
import { Capacitor } from "@capacitor/core";
import App from "@sports/ui/App.vue";
import { sportsKey } from "@sports/ui/services";
import { createSportsService } from "@sports/core/service";
import { destination } from "@sports/core/destination";
import "@sports/ui/style.css";
import { request } from "./transport";
import { fullscreen } from "./fullscreen";
const service = createSportsService(request);
createApp(App)
  .provide(sportsKey, {
    ...service,
    fullscreen,
    leagueImage: (url: string) => {
      try {
        return destination(url).href;
      } catch {
        return "";
      }
    },
  })
  .mount("#app");
const back = () =>
  document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
if (Capacitor.isNativePlatform()) void NativeApp.addListener("backButton", back);
window.addEventListener("pagehide", service.clear);
