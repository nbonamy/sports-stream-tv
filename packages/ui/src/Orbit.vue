<script setup lang="ts">
defineProps<{ error?: boolean }>();
</script>
<template>
  <div
    class="orbit"
    :class="{ error }"
    role="status"
    :aria-label="error ? 'Stream unavailable' : 'Connecting stream'"
  >
    <div class="orbit-ring"></div>
    <span v-if="error" class="orbit-error">!</span>
    <div v-else class="orbit-bars"><i></i><i></i><i></i></div>
  </div>
</template>
