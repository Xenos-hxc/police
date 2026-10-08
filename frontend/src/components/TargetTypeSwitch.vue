<script setup lang="ts">
interface SwitchOption {
  value: string
  label: string
  unfinishedCount: number
  lazy?: boolean
}

defineProps<{
  modelValue: string
  options: SwitchOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const select = (value: string | number | boolean | undefined) => {
  const selected = String(value || '')
  emit('update:modelValue', selected)
  emit('change', selected)
}
</script>

<template>
  <el-radio-group class="target-type-switch" :model-value="modelValue" @update:model-value="select">
    <el-radio-button v-for="option in options" :key="option.value" :value="option.value">
      <span class="target-type-label">{{ option.label }}</span>
      <span class="unfinished-badge">{{ option.unfinishedCount }}</span>
      <span v-if="option.lazy" class="lazy-mark">待加载</span>
    </el-radio-button>
  </el-radio-group>
</template>

<style scoped>
.target-type-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.target-type-switch :deep(.el-radio-button) {
  position: relative;
  margin: 0;
}

.target-type-switch :deep(.el-radio-button__inner) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 156px;
  height: 46px;
  padding: 0 20px;
  border: 1px solid #cfdbea;
  border-radius: 5px !important;
  box-shadow: none !important;
  font-size: 15px;
}

.target-type-switch :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-left: 1px solid #cfdbea;
}

.target-type-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: var(--el-color-primary);
}

.unfinished-badge {
  position: absolute;
  z-index: 2;
  top: -8px;
  right: -8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border: 2px solid #fff;
  border-radius: 12px;
  color: #fff;
  background: #d93025;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
}

.lazy-mark {
  margin-left: 7px;
  color: #8a97a8;
  font-size: 11px;
}

.target-type-switch :deep(.is-active .lazy-mark) {
  color: #d8e8fb;
}
</style>
