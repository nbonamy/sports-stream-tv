export interface StreamLink {
  label: string;
  url: string;
  artworkUrl?: string;
}
export interface Channel {
  name: string;
  links: StreamLink[];
  side?: "HOME" | "AWAY";
  teamName?: string;
  artworkUrl?: string;
}
export interface SportsEvent {
  title: string;
  competition: string;
  startsAt: number | null;
  timeLabel: string;
  links: StreamLink[];
  isChannel: boolean;
  leagueIconUrl?: string;
}
export interface Country {
  code: string;
  name: string;
  channels: Channel[];
}
export interface Sport {
  id: string;
  label: string;
  path: string;
  hours: number;
  art: string;
}
export const sports: Sport[] = [
  ["football", "Football", "football-streamz5/", 3, "football"],
  ["tennis", "Tennis", "tennis-live-stream10/", 6, "tennis"],
  ["rugby", "Rugby", "rugby-live-stream10/", 3, "rugby"],
  ["f1", "F1", "f1-live-stream99/", 4, "f1"],
  ["golf", "Golf", "golf-live-stream98/", 12, "golf"],
  ["nfl", "NFL", "nfl-live-stream20/", 5, "nfl"],
  ["nba", "NBA", "nba-stream70/", 4, "nba"],
  ["mlb", "MLB", "mlb-stream2/", 5, "mlb"],
  ["nhl", "NHL", "nhl-live-stream33/", 4, "nhl"],
  ["mma", "MMA", "ufc-live-stream2/", 4, "mma"],
  ["boxing", "Boxing", "boxing-live-stream10/", 4, "boxing"],
  ["motorsport", "Motorsport", "motorsports-streams5/", 4, "motorsport"],
  ["ncaaf", "College Football", "ncaaf-live-stream01/", 5, "nfl"],
  ["basketball", "Basketball", "basketball-live-stream2/", 4, "nba"],
  ["volleyball", "Volleyball", "volleyball-live-streams/", 4, "volleyball"],
  ["handball", "Handball", "handball-live-streaming/", 4, "handball"],
].map(
  ([id, label, path, hours, art]) => ({ id, label, path, hours, art }) as Sport,
);

export function channelsFor(event: SportsEvent): Channel[] {
  const groups = new Map<string, StreamLink[]>();
  for (const link of event.links) {
    const label = link.label.trim();
    const name = /^(LINK\s*#?\d+|WATCH)$/i.test(label)
      ? new URL(link.url).pathname
          .replace(/^\/|\/$/g, "")
          .split("-")
          .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
          .join(" ")
      : label.replace(/\s+#\d+$/, "");
    groups.set(name, [...(groups.get(name) ?? []), link]);
  }
  const teams = event.title.split(/\s+@\s+/, 2);
  return [...groups].map(([name, links]) => {
    const side = /^(HOME|AWAY)$/i.exec(name)?.[1].toUpperCase() as
      | "HOME"
      | "AWAY"
      | undefined;
    return {
      name,
      links,
      side,
      teamName:
        teams.length === 2 && side
          ? side === "HOME"
            ? teams[1]
            : teams[0]
          : undefined,
      artworkUrl: links[0].artworkUrl,
    };
  });
}
export function section(event: SportsEvent, sport: Sport, now: number): string {
  if (event.isChannel) return "Channels";
  if (event.startsAt === null) return "Time unconfirmed";
  if (event.startsAt > now) return "Upcoming";
  return now - event.startsAt < sport.hours * 3_600_000 ? "Current" : "Earlier";
}
export function countdown(start: number, now: number): string {
  const m = Math.max(1, Math.ceil((start - now) / 60_000));
  if (m >= 1440)
    return `in ${Math.floor(m / 1440)}d ${Math.floor((m % 1440) / 60)}h`;
  return m >= 60
    ? `in ${Math.floor(m / 60)}h${String(m % 60).padStart(2, "0")}`
    : `in ${m}m`;
}
export interface Playback {
  token: string;
  url: string;
  expiresAt: number | null;
}
export interface MediaResponse {
  url: string;
  data: Uint8Array;
  status: number;
}
export interface SportsApi {
  events(sportId: string): Promise<SportsEvent[]>;
  countries(): Promise<Country[]>;
  streams(channel: Channel, requestId: string): Promise<StreamLink[]>;
  resolve(link: StreamLink, requestId: string): Promise<Playback>;
  cancel(requestId: string): void;
  release(token: string): void;
  media(
    token: string,
    url: string,
    id: string,
    range?: [number, number],
  ): Promise<MediaResponse>;
  /** Returns whether the window was fullscreen before applying the change. */
  fullscreen(enabled?: boolean): Promise<boolean>;
}
