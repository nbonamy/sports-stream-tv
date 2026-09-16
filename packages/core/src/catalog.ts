import { load, type CheerioAPI } from "cheerio";
import type { AnyNode } from "domhandler";
import {
  type SportsEvent,
  type StreamLink,
  type Country,
  sports,
} from "./model";
import { PROVIDER_BASE } from "./provider";
import type { PageClient } from "./transport";

function links($: CheerioAPI, node: AnyNode, pageUrl: string): StreamLink[] {
  const found: StreamLink[] = [];
  $(node)
    .find("a[href]")
    .each((_, a) => {
      try {
        const url = new URL($(a).attr("href")!, pageUrl);
        if (
          !providerLinkHost(new URL(pageUrl).hostname, url.hostname) ||
          !["http:", "https:"].includes(url.protocol) ||
          url.hash ||
          url.username ||
          url.password
        )
          return;
        url.protocol = "https:";
        if (!found.some((l) => l.url === url.href))
          found.push({ label: $(a).text().trim() || "Watch", url: url.href });
      } catch {
        /* Ignore malformed provider links. */
      }
    });
  return found;
}
function providerLinkHost(pageHost: string, targetHost: string): boolean {
  if (pageHost === targetHost) return true;
  const family = /^freestreams-live\d+[a-z]?\.([a-z]{2,})$/i;
  const page = family.exec(pageHost);
  const target = family.exec(targetHost);
  return (
    !!page && !!target && page[1].toLowerCase() === target[1].toLowerCase()
  );
}
function imageUrls($: CheerioAPI, node: AnyNode, pageUrl: string): string[] {
  const urls: string[] = [];
  $(node)
    .find(".teamzlg img[src]")
    .each((_, image) => {
      try {
        const url = new URL($(image).attr("src")!, pageUrl);
        if (url.protocol === "https:") urls.push(url.href);
      } catch {
        /* optional team art */
      }
    });
  return urls;
}
function headingDate(text: string, now: number): Date | null {
  const match =
    /\b(JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\s+(\d{1,2})(?:,?\s+(20\d{2}))?\b/i.exec(
      text,
    );
  if (!match) return null;
  const month = [
    "JANUARY",
    "FEBRUARY",
    "MARCH",
    "APRIL",
    "MAY",
    "JUNE",
    "JULY",
    "AUGUST",
    "SEPTEMBER",
    "OCTOBER",
    "NOVEMBER",
    "DECEMBER",
  ].indexOf(match[1].toUpperCase());
  const year = Number(
    new Intl.DateTimeFormat("en-US", {
      timeZone: "Etc/GMT-1",
      year: "numeric",
    }).format(now),
  );
  const years = match[3] ? [+match[3]] : [year - 1, year, year + 1];
  return (
    years
      .map((y) => new Date(Date.UTC(y, month, +match[2])))
      .filter((d) => d.getUTCMonth() === month)
      .sort((a, b) => Math.abs(+a - now) - Math.abs(+b - now))[0] ?? null
  );
}
function easternTime(date: Date, hours: number, minutes: number): number {
  const utc = Date.UTC(
    date.getUTCFullYear(),
    date.getUTCMonth(),
    date.getUTCDate(),
    hours,
    minutes,
  );
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "America/New_York",
    timeZoneName: "shortOffset",
  }).formatToParts(utc);
  const offset = /GMT([+-]\d+)/.exec(
    parts.find((p) => p.type === "timeZoneName")?.value ?? "",
  );
  return utc - Number(offset?.[1] ?? -5) * 3_600_000;
}
export function parseCatalog(
  html: string,
  pageUrl: string,
  now = Date.now(),
): SportsEvent[] {
  const $ = load(html);
  let heading = "";
  let date: Date | null = null;
  const events: SportsEvent[] = [];
  $("h2,h3,table,.teamz").each((_, element) => {
    const el = $(element);
    if (el.hasClass("teamz")) {
      const section = el.parents(".elementor-top-section").first()[0];
      if (!section) return;
      const images = imageUrls($, section, pageUrl);
      const streamLinks = links($, section, pageUrl).map((link) => ({
        ...link,
        artworkUrl: /^HOME$/i.test(link.label)
          ? images.at(-1)
          : /^AWAY$/i.test(link.label)
            ? images[0]
            : undefined,
      }));
      if (!streamLinks.length) return;
      const rawTime =
        /\d{1,2}:\d{2}\s*[AP]M\s*ET/i.exec($(section).text())?.[0] ?? "";
      const time = /(\d+):(\d+)\s*([AP])M/i.exec(rawTime);
      const startsAt =
        date && time
          ? easternTime(
              date,
              (+time[1] % 12) + (time[3].toUpperCase() === "P" ? 12 : 0),
              +time[2],
            )
          : null;
      events.push({
        title: el.text().trim(),
        competition: heading,
        startsAt,
        timeLabel: rawTime,
        links: streamLinks,
        isChannel: false,
      });
    } else if (element.tagName !== "table") {
      if (el.find(".teamz").length) return;
      const parsed = headingDate(el.text(), now);
      if (parsed) date = parsed;
      else heading = el.text().trim();
    } else {
      let previousMinutes: number | null = null;
      let dayOffset = 0;
      el.find("tr").each((_, row) => {
        const r = $(row);
        const title = r.find(".event-title").text().trim();
        if (!title) return;
        const streamLinks = links($, row, pageUrl);
        if (!streamLinks.length) return;
        const rawTime = r.find(".matchtime").text().trim();
        const competition = r.find(".leaguename").text().trim() || heading;
        // The continuous-channel section ends the dated fixture list. Detached
        // leftover tables must not inherit its earlier date and become live events.
        if (/\b24\s*\/\s*7\s+CHANNELS?\b/i.test(competition) && rawTime) return;
        const time = /^(\d{1,2}):(\d{2})$/.exec(rawTime);
        const minutes =
          time && +time[1] < 24 && +time[2] < 60
            ? +time[1] * 60 + +time[2]
            : null;
        if (minutes !== null) {
          // Evening listings continue at 00:00 on the following source date.
          // Small out-of-order time changes do not indicate a new day.
          if (previousMinutes !== null && previousMinutes - minutes > 12 * 60)
            dayOffset += 86_400_000;
          previousMinutes = minutes;
        }
        const epoch = Number(r.attr("data-timestamp"));
        const startsAt =
          Number.isFinite(epoch) && epoch > 0
            ? epoch * (epoch < 100_000_000_000 ? 1000 : 1)
            : date && minutes !== null
              ? +date + dayOffset + (minutes - 60) * 60_000
              : null;
        let leagueIconUrl: string | undefined;
        try {
          const src = r.find("img.leagueimg").attr("src");
          if (src) {
            const u = new URL(src, pageUrl);
            if (u.protocol === "https:") leagueIconUrl = u.href;
          }
        } catch {
          /* optional art */
        }
        events.push({
          title,
          competition,
          startsAt,
          timeLabel:
            startsAt === null && rawTime
              ? `${rawTime} · source time (UTC+1)`
              : "",
          links: streamLinks,
          isChannel:
            !rawTime &&
            startsAt === null &&
            (/CHANNEL/i.test(title) || heading.includes("24/7")),
          leagueIconUrl,
        });
      });
    }
  });
  return events.filter(
    (e, i) =>
      events.findIndex(
        (other) =>
          other.links[0].url === e.links[0].url && other.title === e.title,
      ) === i,
  );
}
export function parseCountries(html: string, pageUrl: string): Country[] {
  const $ = load(html);
  const groups = new Map<string, Country>();
  $(".dropdown").each((_, el) => {
    const code = $(el).find(".dropbtn").text().trim();
    if (!/^[A-Z]{2}$/.test(code)) return;
    const channels = $(el).find(".dropdown-content")[0];
    if (!channels) return;
    const country = groups.get(code) ?? {
      code,
      name:
        new Intl.DisplayNames(["en"], { type: "region" }).of(
          code === "UK" ? "GB" : code,
        ) ?? code,
      channels: [],
    };
    for (const link of links($, channels, pageUrl))
      if (!country.channels.some((c) => c.links[0].url === link.url))
        country.channels.push({ name: link.label, links: [link] });
    if (country.channels.length) groups.set(code, country);
  });
  const priority = (c: Country) => {
    const i = ["FR", "US", "UK"].indexOf(c.code);
    return i < 0 ? 3 : i;
  };
  return [...groups.values()].sort((a, b) => priority(a) - priority(b));
}
export async function getEvents(
  id: string,
  signal: AbortSignal | undefined,
  client: PageClient,
) {
  const sport = sports.find((s) => s.id === id);
  if (!sport) throw new Error("Unknown sport");
  const response = await client(PROVIDER_BASE + sport.path, {}, signal);
  const events = parseCatalog(response.body, response.url);
  if (!events.length) throw new Error("No schedule available");
  // A valid offseason page can contain only WNBA fixtures.
  return events.filter((e) => id !== "nba" || !/WNBA/i.test(e.competition));
}
export async function getCountries(
  signal: AbortSignal | undefined,
  client: PageClient,
) {
  const response = await client(PROVIDER_BASE + "live-tv/", {}, signal);
  const countries = parseCountries(response.body, response.url);
  if (!countries.length) throw new Error("No channels available");
  return countries;
}
