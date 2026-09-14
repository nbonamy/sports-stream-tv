<script setup lang="ts">
defineProps<{ right?: boolean }>();
</script>
<template>
  <svg
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
    :style="right ? 'transform:rotate(180deg)' : ''"
  >
    <path
      d="m14 5-7 7 7 7"
      stroke="currentColor"
      stroke-width="2.5"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>
