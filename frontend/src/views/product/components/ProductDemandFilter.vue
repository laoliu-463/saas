<template>
  <div class="filter-row" data-testid="product-demand-filter">
    <div class="filter-row-label">按需检索</div>
    <div class="demand-groups">
      <div v-for="group in demandGroups" :key="group.key" class="demand-group">
        <span class="demand-label">{{ group.label }}</span>
        <div class="filter-options">
          <button
            type="button"
            class="filter-chip"
            :class="{ active: !filters[group.key] }"
            :data-testid="`demand-${group.key}-all`"
            @click="updateFilter(group.key, null)"
          >
            全部
          </button>
          <button
            v-for="option in group.options"
            :key="option.value"
            type="button"
            class="filter-chip"
            :class="{ active: filters[group.key] === option.value }"
            :data-testid="`demand-${group.key}-option`"
            :data-filter-value="option.value"
            @click="toggleFilter(group.key, option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <div class="demand-group">
        <span class="demand-label">推广状态</span>
        <div class="filter-options">
          <button
            type="button"
            class="filter-chip"
            :class="{ active: libraryStatus === null }"
            data-testid="demand-library-status-all"
            @click="$emit('update:libraryStatus', null)"
          >
            全部
          </button>
          <button
            v-for="option in libraryShelfOptions"
            :key="option.value"
            type="button"
            class="filter-chip"
            :class="{ active: libraryStatus === option.value }"
            data-testid="demand-library-status-option"
            :data-filter-value="option.value"
            @click="$emit('update:libraryStatus', libraryStatus === option.value ? null : option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ProductFilterState } from '../product-filters'
import {
  commissionOptions,
  promotionLinkOptions,
  salesRangeOptions,
  serviceFeeOptions
} from '../product-filters'

type DemandFilterKey = 'commission' | 'serviceFee' | 'salesRange' | 'promotionLink'

const props = defineProps<{
  filters: ProductFilterState
  libraryStatus: number | null
}>()

const emit = defineEmits<{
  'update:filters': [value: ProductFilterState]
  'update:libraryStatus': [value: number | null]
}>()

const demandGroups: Array<{
  key: DemandFilterKey
  label: string
  options: { label: string; value: string }[]
}> = [
  { key: 'commission', label: '佣金区间', options: commissionOptions },
  { key: 'serviceFee', label: '服务费率', options: serviceFeeOptions },
  { key: 'salesRange', label: '近30天销量', options: salesRangeOptions },
  { key: 'promotionLink', label: '转链状态', options: promotionLinkOptions }
]

const libraryShelfOptions = [
  { label: '推广中', value: 1 },
  { label: '待审核', value: 0 }
]

function updateFilter(key: DemandFilterKey, value: string | null) {
  emit('update:filters', { ...props.filters, [key]: value })
}

function toggleFilter(key: DemandFilterKey, value: string) {
  updateFilter(key, props.filters[key] === value ? null : value)
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

.demand-groups {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.demand-group {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 10px;
  align-items: flex-start;
}

.demand-label {
  color: #7f2a36;
  font-size: 13px;
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
  .filter-row,
  .demand-group {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .filter-row-label,
  .demand-label {
    line-height: 1.4;
  }
}
</style>
