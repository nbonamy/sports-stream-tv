<script setup lang="ts">
import { useSports } from "./services";
import { computed, nextTick, onMounted, onUnmounted, ref } from "vue";
import {
  channelsFor,
  countdown,
  section,
  sports,
  type Sport,
  type SportsEvent,
  type Country,
  type Channel,
} from "@sports/core/model";
import Artwork from "./Artwork.vue";
import Chevron from "./Chevron.vue";
import Orbit from "./Orbit.vue";
import Player from "./Player.vue";
import SwipeBack from "./SwipeBack.vue";

const api = useSports();
const mobile = document.documentElement.dataset.platform === "mobile";
type Route = {
  type:
    "home" | "more" | "schedule" | "event" | "countries" | "country" | "player";
  sport?: Sport;
  event?: SportsEvent;
  country?: Country;
  channel?: Channel;
  focus?: string;
  scroll?: number;
};
const history = ref<Route[]>([{ type: "home" }]);
const route = computed(() => history.value.at(-1)!);
const cache = ref<Record<string, SportsEvent[]>>({});
const countries = ref<Country[]>([]);
const loading = ref(false);
const failed = ref(false);
const notice = ref("");
const now = ref(Date.now());
let loadGeneration = 0;
const crumb = computed(() => {
  const r = route.value;
  if (r.type === "home") return "Home";
  if (r.type === "more") return "More sports";
  if (r.type === "countries") return "LiveTV";
  if (r.type === "country") return `LiveTV / ${r.country?.code}`;
  return `${r.sport?.label ?? "LiveTV"}${r.type === "event" ? " / Channels" : ""}`;
});
const tiles = computed(() =>
  route.value.type === "more"
    ? sports.slice(8)
    : [
        ...sports.slice(0, 8),
        { id: "live_tv", label: "LiveTV", art: "live_tv" },
        { id: "more", label: "+ More", art: "more" },
      ],
);
const schedule = computed(() => {
  const r = route.value;
  if (!r.sport) return [];
  const events = cache.value[r.sport.id] ?? [];
  return ["Current", "Upcoming", "Channels", "Time unconfirmed"]
    .map((label) => ({
      label,
      events: events
        .filter((e) => section(e, r.sport!, now.value) === label)
        .sort((a, b) => (a.startsAt ?? Infinity) - (b.startsAt ?? Infinity)),
    }))
    .filter(
      (g) => g.events.length || ["Current", "Upcoming"].includes(g.label),
    );
});
const eventChannels = computed(() =>
  route.value.event ? channelsFor(route.value.event) : [],
);
const time = (e: SportsEvent) =>
  e.startsAt === null
    ? e.timeLabel
    : new Intl.DateTimeFormat("en-US", {
        hour: "numeric",
        minute: "2-digit",
      }).format(e.startsAt);
const date = (e?: SportsEvent) =>
  e?.startsAt
    ? new Intl.DateTimeFormat("en-US", {
        weekday: "short",
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
      }).format(e.startsAt)
    : "";
const eventKey = (e: SportsEvent) => e.links[0].url + "|" + e.title;
async function focusRoute() {
  await nextTick();
  if (window.matchMedia("(pointer: coarse)").matches) {
    window.scrollTo(0, route.value.scroll ?? 0);
    return;
  }
  const buttons = [
    ...document.querySelectorAll<HTMLButtonElement>("main button[data-key]"),
  ];
  (
    buttons.find((b) => b.dataset.key === route.value.focus) ?? buttons[0]
  )?.focus({ preventScroll: true });
  window.scrollTo(0, route.value.scroll ?? 0);
}
async function navigate(next: Route) {
  route.value.focus = (document.activeElement as HTMLElement)?.dataset.key;
  route.value.scroll = window.scrollY;
  history.value.push(next);
  await fetchRoute();
  await focusRoute();
}
async function back() {
  if (history.value.length === 1) return;
  if (route.value.type === "player") await api.fullscreen(false);
  ++loadGeneration;
  history.value.pop();
  await fetchRoute();
  await focusRoute();
}
async function fetchRoute(refresh = false) {
  const r = route.value;
  const generation = ++loadGeneration;
  failed.value = false;
  notice.value = "";
  loading.value = false;
  if (r.type === "schedule" && (refresh || !cache.value[r.sport!.id])) {
    loading.value = true;
    try {
      const data = await api.events(r.sport!.id);
      if (generation === loadGeneration) cache.value[r.sport!.id] = data;
    } catch {
      if (generation === loadGeneration) {
        if (cache.value[r.sport!.id])
          notice.value = "Couldn’t refresh · showing the previous schedule";
        else failed.value = true;
      }
    }
  } else if (
    (r.type === "countries" || r.type === "country") &&
    (refresh || !countries.value.length)
  ) {
    loading.value = true;
    try {
      const data = await api.countries();
      if (generation === loadGeneration) {
        countries.value = data;
        if (r.type === "country") {
          const current = data.find(
            (country) => country.code === r.country?.code,
          );
          if (current) r.country = current;
          else history.value[history.value.length - 1] = { type: "countries" };
        }
      }
    } catch {
      if (generation === loadGeneration) failed.value = true;
    }
  }
  if (generation === loadGeneration) loading.value = false;
}
function openTile(id: string) {
  if (id === "more") void navigate({ type: "more" });
  else if (id === "live_tv") void navigate({ type: "countries" });
  else
    void navigate({
      type: "schedule",
      sport: sports.find((s) => s.id === id)!,
    });
}
function play(channel: Channel) {
  void navigate({
    ...route.value,
    type: "player",
    channel,
    focus: undefined,
    scroll: 0,
  });
}
function keyboard(e: KeyboardEvent) {
  if (route.value.type === "player" || e.metaKey || e.ctrlKey || e.altKey)
    return;
  if (e.key === "Escape" || e.key === "Backspace") {
    e.preventDefault();
    if (!e.repeat) void back();
    return;
  }
  if (!e.key.startsWith("Arrow")) return;
  const candidates = [
    ...document.querySelectorAll<HTMLButtonElement>("main button"),
  ].filter((b) => !b.disabled && b.getBoundingClientRect().height > 0);
  const current = document.activeElement as HTMLElement;
  const box = current?.getBoundingClientRect();
  if (!box || !candidates.includes(current as HTMLButtonElement)) {
    candidates[0]?.focus();
    e.preventDefault();
    return;
  }
  const cx = box.x + box.width / 2,
    cy = box.y + box.height / 2;
  const horizontal = e.key === "ArrowLeft" || e.key === "ArrowRight";
  const sign = e.key === "ArrowLeft" || e.key === "ArrowUp" ? -1 : 1;
  const ranked = candidates
    .filter((b) => b !== current)
    .map((b) => {
      const r = b.getBoundingClientRect();
      const dx = r.x + r.width / 2 - cx,
        dy = r.y + r.height / 2 - cy;
      return {
        button: b,
        primary: (horizontal ? dx : dy) * sign,
        cross: Math.abs(horizontal ? dy : dx),
      };
    })
    .filter((p) => p.primary > 4)
    .sort((a, b) => a.primary + a.cross * 5 - b.primary - b.cross * 5);
  ranked[0]?.button.focus();
  e.preventDefault();
}
const ticker = setInterval(() => (now.value = Date.now()), 1000);
onMounted(() => {
  document.addEventListener("keydown", keyboard);
  void focusRoute();
});
onUnmounted(() => {
  clearInterval(ticker);
  document.removeEventListener("keydown", keyboard);
});
</script>
<template>
  <SwipeBack :enabled="mobile" :depth="history.length" :back="back">
  <div class="titlebar"></div>
  <Player
    v-if="route.type === 'player'"
    :channel="route.channel!"
    :title="route.event?.title ?? route.channel!.name"
    :subtitle="
      route.country ? `LiveTV · ${route.country.code}` : route.channel!.name
    "
    @back="back"
  />
  <div v-else class="browse">
    <header class="app-header">
      <button
        v-if="history.length > 1"
        class="back plain"
        aria-label="Back"
        tabindex="-1"
        @click="back"
      >
        <Chevron />
      </button>
      <span class="brand">SPORTS</span
      ><span class="crumb">/ &nbsp;{{ crumb }}</span>
    </header>
    <main>
      <template v-if="route.type === 'home' || route.type === 'more'">
        <h1>
          {{ route.type === "home" ? "Pick your sport." : "More sports" }}
        </h1>
        <p class="subtitle">Find what’s on. Choose your channel. Settle in.</p>
        <div class="sport-grid">
          <button
            v-for="tile in tiles"
            :key="tile.id"
            class="sport-tile"
            :data-key="tile.id"
            @click="openTile(tile.id)"
          >
            <Artwork :art="tile.art" /><span>{{ tile.label }}</span>
          </button>
        </div>
      </template>
      <template v-else-if="route.type === 'schedule'">
        <section class="page-heading">
          <Artwork :art="route.sport!.art" />
          <div>
            <h1>{{ route.sport!.label }}</h1>
            <p>Schedule</p>
          </div>
          <button
            class="quiet refresh"
            @click="fetchRoute(true)"
            :disabled="loading"
          >
            Refresh
          </button>
        </section>
        <div
          v-if="loading"
          class="skeleton"
          aria-label="Loading schedule"
          role="status"
        >
          <div v-for="n in 5" :key="n" class="skeleton-row">
            <i></i><span></span>
          </div>
        </div>
        <div v-else-if="failed" class="empty">
          <Orbit error />
          <p>Schedule unavailable</p>
          <button class="quiet" @click="fetchRoute(true)">Retry</button>
        </div>
        <div v-else class="schedule">
          <p v-if="notice" class="notice">{{ notice }}</p>
          <section
            v-for="group in schedule"
            :key="group.label"
            class="schedule-section"
          >
            <h2 :class="{ current: group.label === 'Current' }">
              {{ group.label }} <small>{{ group.events.length }}</small>
            </h2>
            <p v-if="!group.events.length" class="empty-section">
              No {{ group.label.toLowerCase() }} events listed
            </p>
            <button
              v-for="event in group.events"
              :key="eventKey(event)"
              class="event-row"
              :data-key="eventKey(event)"
              @click="navigate({ type: 'event', sport: route.sport, event })"
            >
              <span class="event-time"
                >{{ time(event)
                }}<small v-if="event.startsAt && event.startsAt > now">{{
                  countdown(event.startsAt, now)
                }}</small></span
              >
              <img
                v-if="event.leagueIconUrl"
                class="league-icon"
                :src="api.leagueImage(event.leagueIconUrl)"
                alt=""
                @error="
                  ($event.target as HTMLImageElement).style.visibility =
                    'hidden'
                "
              />
              <span class="event-info"
                ><small v-if="event.competition">{{ event.competition }}</small
                ><strong>{{ event.title }}</strong></span
              ><Chevron right />
            </button>
          </section>
        </div>
      </template>
      <template v-else-if="route.type === 'event'">
        <section class="page-heading">
          <Artwork
            :art="route.sport!.art"
            :league="route.event!.leagueIconUrl"
          />
          <div>
            <h1 class="event-title">{{ route.event!.title }}</h1>
            <p v-if="route.event?.startsAt">{{ date(route.event) }}</p>
          </div>
        </section>
        <h2 class="channel-heading">Choose a channel</h2>
        <div class="channel-list">
          <button
            v-for="c in eventChannels"
            :key="c.name"
            :data-key="c.name"
            class="channel-row"
            @click="play(c)"
          >
            <svg
              class="play-triangle"
              width="16"
              height="16"
              viewBox="0 0 16 16"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M3 1 14 8 3 15Z" /></svg
            >{{ c.name }}<Chevron right />
          </button>
        </div>
      </template>
      <template
        v-else-if="route.type === 'countries' || route.type === 'country'"
      >
        <section class="page-heading">
          <Artwork art="live_tv" />
          <div>
            <h1>{{ route.country?.name ?? "LiveTV" }}</h1>
            <p>{{ route.country ? "Choose a channel" : "Choose a country" }}</p>
          </div>
          <button
            class="quiet refresh"
            @click="fetchRoute(true)"
            :disabled="loading"
          >
            Refresh
          </button>
        </section>
        <div
          v-if="loading"
          class="skeleton"
          role="status"
          aria-label="Loading channels"
        >
          <div v-for="n in 5" :key="n" class="skeleton-row">
            <i></i><span></span>
          </div>
        </div>
        <div v-else-if="failed" class="empty">
          <Orbit error />
          <p>Channels unavailable</p>
          <button class="quiet" @click="fetchRoute(true)">Retry</button>
        </div>
        <div v-else-if="route.type === 'countries'" class="country-grid">
          <button
            v-for="c in countries"
            :key="c.code"
            :data-key="c.code"
            class="country-tile"
            @click="navigate({ type: 'country', country: c })"
          >
            <span class="country-code">{{ c.code }}</span
            ><strong>{{ c.name }}</strong>
          </button>
        </div>
        <div v-else class="channel-list">
          <button
            v-for="c in route.country!.channels"
            :key="c.links[0].url"
            :data-key="c.links[0].url"
            class="channel-row"
            @click="play(c)"
          >
            <svg
              class="play-triangle"
              width="16"
              height="16"
              viewBox="0 0 16 16"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M3 1 14 8 3 15Z" /></svg
            >{{ c.name }}<Chevron right />
          </button>
        </div>
      </template>
    </main>
  </div>
  </SwipeBack>
</template>
