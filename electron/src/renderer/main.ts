import { createApp } from "vue";
import App from "@sports/ui/App.vue";
import "@sports/ui/style.css";
import { sportsKey } from "@sports/ui/services";
import type { SportsApi } from "@sports/core/model";
declare global {
  interface Window {
    sports: SportsApi;
  }
}
createApp(App)
  .provide(sportsKey, {
    ...window.sports,
    leagueImage: (url: string) =>
      `sports://app/league?url=${encodeURIComponent(url)}`,
  })
  .mount("#app");
