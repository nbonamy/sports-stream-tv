import { inject, type InjectionKey } from "vue";
import type { SportsApi } from "@sports/core/model";
export interface AppServices extends SportsApi {
  leagueImage(url: string): string;
}
export const sportsKey: InjectionKey<AppServices> = Symbol("sports");
export function useSports(): AppServices {
  const api = inject(sportsKey);
  if (!api) throw new Error("Missing platform services");
  return api;
}
