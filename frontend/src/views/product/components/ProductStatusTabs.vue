<template>
  <div class="product-status-tabs" data-testid="product-status-tabs">
    <div class="status-group" data-testid="official-status-tabs">
      <span class="status-group-label">精选联盟状态</span>
      <button
        v-for="option in officialStatusOptions"
        :key="option.value"
        type="button"
        class="status-tab"
        :class="{ active: officialStatus === option.value }"
        :data-testid="`official-status-${option.value}`"
        @click="$emit('changeOfficialStatus', officialStatus === option.value ? null : option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  officialStatusOptions,
  type ProductOfficialStatus
} from '../../../types/productManage'

withDefaults(defineProps<{
  officialStatus: ProductOfficialStatus | null
}>(), {
  officialStatus: null
})

defineEmits<{
  changeOfficialStatus: [value: ProductOfficialStatus | null]
}>()
</script>

<style scoped>
.product-status-tabs {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.status-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.status-group-label {
  min-width: 92px;
  font-size: 13px;
  color: var(--text-secondary);
}

.status-tab {
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 13px;
  line-height: 1.2;
  cursor: pointer;
}

.status-tab:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.status-tab.active {
  border-color: var(--color-primary);
  background: rgba(230, 57, 70, 0.08);
  color: var(--color-primary);
  font-weight: 600;
}
</style>
