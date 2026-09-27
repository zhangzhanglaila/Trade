<template>
  <div class="bg-white rounded-lg shadow-sm p-6">
    <h3 class="text-lg font-medium text-gray-800 mb-4">{{ title }}</h3>
    <slot />
  </div>
</template>

<script setup>
defineProps({ title: String })
</script>