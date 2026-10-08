<script setup lang="ts">
defineProps<{ title: string; value: string | number; unit?: string; tone?: string; icon: string }>()
</script>

<template>
  <div class="metric-card" :class="tone">
    <div class="metric-icon">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <div>
      <div class="metric-title">{{ title }}</div>
      <div class="metric-value">
        {{ value }}<small>{{ unit }}</small>
      </div>
    </div>
  </div>
</template>
