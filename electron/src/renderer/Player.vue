<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from "vue";
import Hls from "hls.js";
import type { Channel, StreamLink } from "../shared/model";
import { mediaLoader } from "./media-loader";
import Chevron from "./Chevron.vue";
import Orbit from "./Orbit.vue";

const props = defineProps<{ channel: Channel; title: string }>();
const emit = defineEmits<{ back: [] }>();
const video = ref<HTMLVideoElement>();
const player = ref<HTMLElement>();
const streams = ref<StreamLink[]>(
  props.channel.links.map((l, i) => ({ ...l, label: `Stream ${i + 1}` })),
);
const index = ref(0);
const mode = ref<"connecting" | "playing" | "paused" | "unavailable">(
  "connecting",
);
const visible = ref(true);
const frame = ref(false);
const snapshot = ref("");
const behind = ref(false);
const live = ref(false);
const volume = ref(1);
const muted = ref(false);
let hls: Hls | undefined;
let token: string | undefined;
let resolveId: string | undefined;
let generation = 0;
let disposed = false;
let attempts = 0;
let selection = streams.value[0];
const discoveryId = crypto.randomUUID();
let hideTimer: ReturnType<typeof setTimeout>,
  retryTimer: ReturnType<typeof setTimeout>,
  renewalTimer: ReturnType<typeof setTimeout>,
  stallTimer: ReturnType<typeof setTimeout>;
const hasPicture = computed(() => frame.value || !!snapshot.value);
function reveal() {
  visible.value = true;
  clearTimeout(hideTimer);
  if (mode.value === "playing")
    hideTimer = setTimeout(() => {
      visible.value = false;
      player.value?.focus({ preventScroll: true });
    }, 3000);
}
function capture() {
  const v = video.value;
  if (!v || !frame.value || !v.videoWidth) return;
  try {
    const canvas = document.createElement("canvas");
    const scale = Math.min(1, 1280 / v.videoWidth);
    canvas.width = Math.round(v.videoWidth * scale);
    canvas.height = Math.round(v.videoHeight * scale);
    canvas.getContext("2d")!.drawImage(v, 0, 0, canvas.width, canvas.height);
    snapshot.value = canvas.toDataURL("image/jpeg", 0.8);
  } catch {
    /* Keep the video element's last frame when a snapshot is unavailable. */
  }
}
function detach() {
  hls?.destroy();
  hls = undefined;
  if (token) window.sports.release(token);
  token = undefined;
}
async function connect(retain = false, preservePause = false) {
  const current = ++generation;
  if (resolveId) window.sports.cancel(resolveId);
  clearTimeout(retryTimer);
  clearTimeout(renewalTimer);
  clearTimeout(stallTimer);
  if (retain) capture();
  else {
    snapshot.value = "";
    frame.value = false;
  }
  detach();
  mode.value = "connecting";
  live.value = false;
  reveal();
  const id = (resolveId = crypto.randomUUID());
  try {
    const resolved = await window.sports.resolve(
      { label: selection.label, url: selection.url },
      id,
    );
    if (disposed || current !== generation) {
      window.sports.release(resolved.token);
      return;
    }
    resolveId = undefined;
    token = resolved.token;
    if (!Hls.isSupported()) {
      mode.value = "unavailable";
      return;
    }
    hls = new Hls({
      loader: mediaLoader(window.sports, token),
      enableWorker: true,
      maxBufferLength: 30,
      backBufferLength: 90,
    });
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      if (current !== generation) return;
      if (!preservePause)
        void video.value?.play().catch(() => {
          mode.value = "paused";
          reveal();
        });
      else {
        mode.value = "paused";
        reveal();
      }
    });
    hls.on(Hls.Events.LEVEL_UPDATED, (_, data) => {
      live.value = data.details.live;
      updateLive();
    });
    hls.on(Hls.Events.ERROR, (_, data) => {
      if (current === generation && data.fatal) recover();
    });
    hls.attachMedia(video.value!);
    hls.loadSource(resolved.url);
    stallTimer = setTimeout(recover, 25_000);
    if (resolved.expiresAt)
      renewalTimer = setTimeout(
        () => void connect(true, mode.value === "paused"),
        Math.max(15_000, resolved.expiresAt - Date.now() - 60_000),
      );
  } catch {
    if (!disposed && current === generation) {
      resolveId = undefined;
      mode.value = "unavailable";
      reveal();
      await nextTick();
      player.value?.querySelector<HTMLButtonElement>(".retry")?.focus();
    }
  }
}
function recover() {
  if (disposed || mode.value === "unavailable") return;
  clearTimeout(stallTimer);
  capture();
  mode.value = "connecting";
  reveal();
  if (attempts >= 3) {
    mode.value = "unavailable";
    reveal();
    return;
  }
  clearTimeout(retryTimer);
  retryTimer = setTimeout(() => void connect(true), ++attempts * 3000);
}
function ready() {
  frame.value = true;
  snapshot.value = "";
  clearTimeout(stallTimer);
  clearTimeout(retryTimer);
  attempts = 0;
  mode.value = video.value?.paused ? "paused" : "playing";
  updateLive();
  reveal();
}
function waiting() {
  if (mode.value === "unavailable" || video.value?.paused) return;
  mode.value = "connecting";
  reveal();
  clearTimeout(stallTimer);
  stallTimer = setTimeout(recover, 12_000);
}
function updateLive() {
  const v = video.value,
    target = hls?.liveSyncPosition;
  behind.value =
    !!v &&
    !!live.value &&
    target !== null &&
    target !== undefined &&
    (v.paused || target - v.currentTime > 5);
}
function goLive() {
  const target = hls?.liveSyncPosition;
  if (!video.value || target === null || target === undefined) return;
  video.value.currentTime = target;
  void video.value.play().catch(() => {});
  reveal();
}
function toggle() {
  const v = video.value;
  if (!v || !["playing", "paused"].includes(mode.value)) return;
  if (v.paused) void v.play().catch(() => {});
  else v.pause();
  reveal();
}
function choose(n: number) {
  if (n < 0 || n >= streams.value.length || n === index.value) return;
  index.value = n;
  selection = streams.value[n];
  attempts = 0;
  void connect();
}
function retry() {
  attempts = 0;
  void connect(hasPicture.value);
}
let escapePending = false;
async function key(e: KeyboardEvent) {
  if (e.metaKey || e.ctrlKey || e.altKey) return;
  if (e.key === "Escape") {
    e.preventDefault();
    if (e.repeat || escapePending) return;
    escapePending = true;
    try {
      const wasFullscreen = await window.sports.fullscreen(false);
      if (!wasFullscreen && !disposed) emit("back");
    } finally {
      escapePending = false;
    }
    return;
  }
  if ((e.target as HTMLElement).tagName === "INPUT") return;
  if (e.key === "Backspace") {
    e.preventDefault();
    emit("back");
  } else if (e.key === "ArrowLeft") {
    e.preventDefault();
    choose(index.value - 1);
  } else if (e.key === "ArrowRight") {
    e.preventDefault();
    choose(index.value + 1);
  } else if (e.key === " ") {
    e.preventDefault();
    mode.value === "unavailable" ? retry() : toggle();
  } else if (e.key.toLowerCase() === "f") {
    e.preventDefault();
    void window.sports.fullscreen();
  } else if (e.key.toLowerCase() === "l") {
    e.preventDefault();
    goLive();
  } else if (e.key === "ArrowDown") {
    e.preventDefault();
    reveal();
    player.value
      ?.querySelector<HTMLButtonElement>(
        mode.value === "unavailable" ? ".retry" : ".transport-button",
      )
      ?.focus();
  } else if (e.key === "ArrowUp") {
    e.preventDefault();
    reveal();
    player.value?.focus();
  }
}
const fullscreen = () => window.sports.fullscreen();
const liveTicker = setInterval(updateLive, 1000);
onMounted(() => {
  document.addEventListener("keydown", key);
  player.value?.focus();
  void connect();
  window.sports
    .streams(
      {
        name: props.channel.name,
        links: props.channel.links.map((l) => ({ label: l.label, url: l.url })),
      },
      discoveryId,
    )
    .then((options) => {
      if (!disposed && options.length) {
        const selected = options.findIndex((l) => l.url === selection.url);
        streams.value = options;
        index.value = selected >= 0 ? selected : 0;
      }
    })
    .catch(() => {});
});
onUnmounted(() => {
  disposed = true;
  ++generation;
  window.sports.cancel(discoveryId);
  if (resolveId) window.sports.cancel(resolveId);
  detach();
  clearInterval(liveTicker);
  [hideTimer, retryTimer, renewalTimer, stallTimer].forEach(clearTimeout);
  document.removeEventListener("keydown", key);
  snapshot.value = "";
});
</script>
<template>
  <div
    ref="player"
    class="player"
    :class="{ 'controls-hidden': !visible }"
    tabindex="-1"
    @mousemove="reveal"
    @focusin="reveal"
  >
    <video
      ref="video"
      class="video"
      playsinline
      :volume="volume"
      :muted="muted"
      @playing="ready"
      @loadeddata="video?.paused && ready()"
      @waiting="waiting"
      @stalled="waiting"
      @pause="
        mode !== 'connecting' && mode !== 'unavailable' && (mode = 'paused');
        reveal();
        updateLive();
      "
      @timeupdate="updateLive"
      @dblclick="fullscreen"
      @click="toggle"
    />
    <img v-if="snapshot" :src="snapshot" class="video retained" alt="" />
    <div
      v-if="mode === 'connecting' || mode === 'unavailable'"
      class="player-backdrop"
      :class="{ translucent: hasPicture }"
    ></div>
    <div class="player-top chrome">
      <button
        class="circle"
        aria-label="Back to channels"
        @click="emit('back')"
      >
        <Chevron />
      </button>
      <div class="player-titles">
        <h2>{{ title }}</h2>
        <p>{{ channel.name }}</p>
      </div>
      <span class="stream-count"
        >Stream {{ index + 1 }} / {{ streams.length }}</span
      >
    </div>
    <button
      v-if="index > 0"
      class="circle stream-arrow previous chrome"
      aria-label="Previous stream"
      @click="choose(index - 1)"
    >
      <Chevron />
    </button>
    <button
      v-if="index < streams.length - 1"
      class="circle stream-arrow next chrome"
      aria-label="Next stream"
      @click="choose(index + 1)"
    >
      <Chevron right />
    </button>
    <div
      v-if="mode === 'connecting' || mode === 'unavailable'"
      class="player-status"
    >
      <Orbit :error="mode === 'unavailable'" /><button
        v-if="mode === 'unavailable'"
        class="retry quiet"
        @click="retry"
      >
        Retry
      </button>
    </div>
    <div
      v-if="mode === 'playing' || mode === 'paused'"
      class="player-bottom chrome"
    >
      <div class="volume">
        <button
          class="plain"
          :aria-label="muted ? 'Unmute' : 'Mute'"
          @click="muted = !muted"
        >
          <svg
            width="23"
            height="23"
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="M11 4 5 9H2v6h3l6 5V4Z"
              stroke="currentColor"
              stroke-width="1.7"
            />
            <path
              v-if="!muted"
              d="M15 8c3 2 3 6 0 8m3-12c6 4 6 12 0 16"
              stroke="currentColor"
              stroke-width="1.7"
            />
          </svg></button
        ><input
          v-model.number="volume"
          type="range"
          min="0"
          max="1"
          step=".01"
          aria-label="Volume"
        />
      </div>
      <div class="transport">
        <button
          class="circle transport-button"
          :aria-label="mode === 'paused' ? 'Play' : 'Pause'"
          @click="toggle"
        >
          <svg
            width="24"
            height="24"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
          >
            <path v-if="mode === 'paused'" d="m8 4 13 8-13 8z" />
            <path v-else d="M5 4h5v16H5zM14 4h5v16h-5z" />
          </svg></button
        ><button
          v-if="live"
          class="quiet live"
          :class="{ behind }"
          :disabled="!behind"
          :aria-label="behind ? 'Go live' : 'Live'"
          @click="goLive"
        >
          <i></i>LIVE
        </button>
      </div>
      <button
        class="plain fullscreen"
        aria-label="Toggle fullscreen"
        @click="fullscreen"
      >
        <svg
          width="24"
          height="24"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          aria-hidden="true"
        >
          <path d="M9 3H3v6m12-6h6v6M3 15v6h6m12-6v6h-6" />
        </svg>
      </button>
    </div>
  </div>
</template>
