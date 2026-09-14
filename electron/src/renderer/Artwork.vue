<script setup lang="ts">
import { computed, ref, watch } from "vue";
const props = defineProps<{ art: string; league?: string }>();
const failed = ref(false);
watch(
  () => props.league,
  () => (failed.value = false),
);
const src = computed(() =>
  props.league && !failed.value
    ? `sports://app/league?url=${encodeURIComponent(props.league)}`
    : `./art/${props.art === "live_tv" ? "live_tv" : "sport_" + props.art}_cutout.png`,
);
</script>
<template>
  <img
    class="artwork"
    :src="src"
    alt=""
    draggable="false"
    @error="failed = true"
  />
</template>
