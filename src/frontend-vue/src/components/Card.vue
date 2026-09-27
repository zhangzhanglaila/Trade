<template>
  <div class="bg-white p-6 rounded-lg shadow-sm">
    <div class="flex items-center justify-between mb-4">
      <div class="text-sm text-gray-500">{{ title }}</div>
      <div :class="bg" class="w-10 h-10 rounded-lg flex items-center justify-center">
        <i :class="[icon, color]"></i>
      </div>
    </div>
    <div class="text-2xl font-semibold mb-1">{{ value }}</div>
    <div v-if="trend" class="text-sm text-green-500 flex items-center">
      <i class="fas fa-arrow-up mr-1"></i>{{ trend }}
    </div>
    <div v-if="desc" class="text-sm text-gray-500">{{ desc }}</div>
  </div>
</template>

<script setup>
defineProps({
  icon: String,
  title: String,
  value: String,
  trend: String,
  desc: String,
  color: String,
  bg: String
})
</script>