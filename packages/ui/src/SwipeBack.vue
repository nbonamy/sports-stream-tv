<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

const props = defineProps<{
  enabled: boolean;
  depth: number;
  back: () => Promise<void>;
}>();
const screen = ref<HTMLElement>();
const preview = ref<HTMLElement>();
const distance = ref(0);
const active = ref(false);
const settling = ref(false);
const width = ref(1);
// Inert previews preserve the previous screen's exact scroll position without
// mounting a second player or running a second set of provider requests.
const previous: { content: HTMLElement; scroll: number }[] = [];
let touch: { id: number; x: number; y: number } | undefined;
let revision = 0;
let suppressClick = false;
let timer: ReturnType<typeof setTimeout> | undefined;

function reset() {
  ++revision;
  touch = undefined;
  distance.value = 0;
  active.value = false;
  settling.value = false;
  preview.value?.replaceChildren();
}

watch(() => props.depth, (next, old) => {
  if (!props.enabled || !screen.value) return;
  if (next > old) {
    const content = screen.value.cloneNode(true) as HTMLElement;
    content.removeAttribute("style");
    previous[old - 1] = { content, scroll: window.scrollY };
  } else {
    previous.length = Math.max(0, next - 1);
  }
  reset();
}, { flush: "pre" });

function begin(event: TouchEvent) {
  if (!props.enabled || settling.value || props.depth < 2 ||
      document.fullscreenElement) return;
  if (event.touches.length !== 1) { void finish(false); return; }
  const point = event.touches[0];
  if (point.clientX > 32) return;
  const target = event.target as Element | null;
  if (target?.closest("input, textarea, select, [contenteditable]")) return;
  suppressClick = false;
  touch = { id: point.identifier, x: point.clientX, y: point.clientY };
  width.value = window.innerWidth;
}

function move(event: TouchEvent) {
  if (!touch || settling.value) return;
  const point = event.touches[0];
  if (event.touches.length !== 1 || point.identifier !== touch.id) {
    void finish(false);
    return;
  }
  const dx = point.clientX - touch.x;
  const dy = Math.abs(point.clientY - touch.y);
  if (!active.value) {
    if (dy > 10 || dx < -10) { touch = undefined; return; }
    if (dx < 8 || dx < dy * 1.5) return;
    const saved = previous[props.depth - 2];
    if (!saved || !preview.value) { touch = undefined; return; }
    const content = saved.content.cloneNode(true) as HTMLElement;
    content.style.position = "relative";
    content.style.top = `${-saved.scroll}px`;
    preview.value.replaceChildren(content);
    active.value = true;
  }
  event.preventDefault();
  suppressClick = true;
  distance.value = Math.min(width.value, Math.max(0, dx));
}

async function finish(commit: boolean) {
  touch = undefined;
  if (!active.value || settling.value) return;
  const current = revision;
  settling.value = true;
  distance.value = commit ? width.value : 0;
  const duration = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 220;
  await new Promise<void>(resolve => { timer = setTimeout(resolve, duration); });
  if (current !== revision) return;
  try {
    if (commit) await props.back();
  } finally {
    reset();
  }
}

function end(event: TouchEvent) {
  if (!active.value) { touch = undefined; return; }
  event.preventDefault();
  void finish(event.touches.length === 0 && distance.value >= width.value * 0.35);
}

function click(event: MouseEvent) {
  if (!suppressClick && !settling.value) return;
  event.preventDefault();
  event.stopPropagation();
  suppressClick = false;
}

onMounted(() => {
  if (props.enabled) window.addEventListener("resize", reset);
});
onBeforeUnmount(() => {
  window.removeEventListener("resize", reset);
  clearTimeout(timer);
  reset();
});
</script>

<template>
  <template v-if="!enabled"><slot /></template>
  <div
    v-else
    class="mobile-navigation"
    :class="{ 'swipe-active': active, 'swipe-settling': settling }"
    :style="{ '--swipe-x': `${distance}px`, '--swipe-under': `${(distance / width - 1) * 25}%`, '--swipe-shade': String((1 - distance / width) * 0.22) }"
    @touchstart.passive="begin"
    @touchmove="move"
    @touchend="end"
    @touchcancel="finish(false)"
    @click.capture="click"
  >
    <div class="swipe-previous" aria-hidden="true" inert>
      <div ref="preview" class="swipe-preview"></div>
      <div class="swipe-shade"></div>
    </div>
    <div ref="screen" class="navigation-screen"><slot /></div>
  </div>
</template>

<style scoped>
.mobile-navigation { overflow-x: clip; }
.navigation-screen {
  min-height: 100dvh;
  background: linear-gradient(115deg, #06121c, #08121b 48%, #050c11);
}
.swipe-previous {
  display: none;
  position: fixed;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  background: #06121c;
}
.swipe-active .swipe-previous { display: block; }
.swipe-preview { transform: translateX(var(--swipe-under)); }
.swipe-shade {
  position: absolute;
  inset: 0;
  background: #000;
  opacity: var(--swipe-shade);
}
.swipe-active > .navigation-screen {
  position: relative;
  transform: translateX(var(--swipe-x));
  box-shadow: -6px 0 24px #0005;
  pointer-events: none;
}
.swipe-settling > .navigation-screen,
.swipe-settling .swipe-preview { transition: transform 220ms cubic-bezier(.2, .7, .3, 1); }
.swipe-settling .swipe-shade { transition: opacity 220ms ease; }
@media (prefers-reduced-motion: reduce) {
  .swipe-settling > .navigation-screen,
  .swipe-settling .swipe-preview,
  .swipe-settling .swipe-shade { transition: none; }
}
</style>
