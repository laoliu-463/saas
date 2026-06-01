<template>
  <div class="filter-row" data-testid="product-other-filter">
    <div class="filter-row-label">其他筛选</div>
    <div class="filter-options">
      <button
        v-for="option in otherOptions"
        :key="option.key"
        type="button"
        class="filter-chip"
        :class="{ active: isActive(option) }"
        data-testid="other-filter-option"
        :data-filter-value="option.key"
        @click="toggleOption(option)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ProductFilterState } from '../product-filters'

type OtherOption =
  | { key: 'supportsAds' | 'hasSample' | 'published' | 'listed' | 'freeSample'; label: string; value: string }
  | { key: 'materialDownload' | 'exclusivePrice' | 'productChain' | 'handCard' | 'doubleCommission' | 'notInLibrary' | 'dedup'; label: string; value: boolean }

const props = defineProps<{
  filters: ProductFilterState
}>()

const emit = defineEmits<{
  'update:filters': [value: ProductFilterState]
}>()

const otherOptions: OtherOption[] = [
  { key: 'supportsAds', label: '支持投流', value: '1' },
  { key: 'hasSample', label: '支持寄样', value: '1' },
  { key: 'published', label: '已发布', value: '1' },
  { key: 'listed', label: '已挂车', value: '1' },
  { key: 'freeSample', label: '免费寄样', value: '1' },
  { key: 'materialDownload', label: '素材下载', value: true },
  { key: 'exclusivePrice', label: '专属价', value: true },
  { key: 'productChain', label: '商品链组', value: true },
  { key: 'handCard', label: '手卡', value: true },
  { key: 'doubleCommission', label: '双佣金', value: true },
  { key: 'notInLibrary', label: '仅未加入货盘', value: true },
  { key: 'dedup', label: '选品去重', value: true }
]

function isActive(option: OtherOption) {
  return props.filters[option.key] === option.value
}

function toggleOption(option: OtherOption) {
  const nextValue = isActive(option) ? (typeof option.value === 'boolean' ? false : null) : option.value
  emit('update:filters', {
    ...props.filters,
    [option.key]: nextValue
  } as ProductFilterState)
}
</script>

<style scoped>
.filter-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 14px;
  align-items: flex-start;
  padding: 12px 0;
  border-bottom: 1px solid rgba(232, 69, 85, 0.1);
}

.filter-row-label {
  color: #6b1f2b;
  font-size: 13px;
  font-weight: 700;
  line-height: 30px;
  white-space: nowrap;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.filter-chip {
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(232, 69, 85, 0.16);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.72);
  color: #475569;
  cursor: pointer;
  font-size: 13px;
  line-height: 28px;
  transition: all 0.15s ease;
}

.filter-chip:hover {
  border-color: rgba(232, 69, 85, 0.36);
  color: #cf3344;
}

.filter-chip.active {
  border-color: #e84555;
  background: #e84555;
  color: #fff;
}

@media (max-width: 720px) {
  .filter-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .filter-row-label {
    line-height: 1.4;
  }
}
</style>
