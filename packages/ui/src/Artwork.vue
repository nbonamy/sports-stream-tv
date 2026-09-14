<script setup lang="ts">
import { useSports } from "./services";
import { computed, ref, watch } from "vue";
const api = useSports();
const props = defineProps<{ art: string; league?: string }>();
const failed = ref(false);
watch(
  () => props.league,
  () => (failed.value = false),
);
const src = computed(() =>
  props.league && !failed.value
    ? api.leagueImage(props.league)
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
