<template>
  <div class="selected-filter-row" data-testid="product-selected-filters">
    <div class="filter-row-label">已选条件</div>
    <div class="selected-filter-content">
      <template v-if="selectedFilters.length">
        <button
          v-for="item in selectedFilters"
          :key="item.id"
          type="button"
          class="selected-chip"
          data-testid="selected-filter-chip"
          @click="removeFilter(item)"
        >
          <span>{{ item.label }}：{{ item.text }}</span>
          <span class="selected-chip-close" aria-hidden="true">×</span>
        </button>
        <button type="button" class="clear-button" data-testid="selected-filter-clear" @click="$emit('clear')">
          清空
        </button>
      </template>
      <span v-else class="selected-empty">暂未选择筛选条件</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ProductFilterState } from '../product-filters'
import {
  commissionOptions,
  promotionLinkOptions,
  salesRangeOptions,
  serviceFeeOptions
} from '../product-filters'

type SelectedFilter =
  | { id: string; type: 'array'; key: 'categories' | 'productTags'; label: string; text: string; value: string }
  | { id: string; type: 'field'; key: keyof ProductFilterState; label: string; text: string }
  | { id: string; type: 'libraryStatus'; label: string; text: string }

const props = defineProps<{
  filters: ProductFilterState
  libraryStatus: number | null
}>()

const emit = defineEmits<{
  'update:filters': [value: ProductFilterState]
  'update:libraryStatus': [value: number | null]
  clear: []
}>()

const libraryStatusLabel: Record<number, string> = {
  0: '待审核',
  1: '推广中'
}

const booleanLabels: Array<{ key: keyof ProductFilterState; label: string }> = [
  { key: 'materialDownload', label: '素材下载' },
  { key: 'exclusivePrice', label: '专属价' },
  { key: 'productChain', label: '商品链组' },
  { key: 'handCard', label: '手卡' },
  { key: 'doubleCommission', label: '双佣金' },
  { key: 'notInLibrary', label: '仅未加入货盘' },
  { key: 'dedup', label: '选品去重' }
]

const enabledOptionLabels: Array<{ key: keyof ProductFilterState; label: string }> = [
  { key: 'supportsAds', label: '投流' },
  { key: 'hasSample', label: '寄样' },
  { key: 'published', label: '发布' },
  { key: 'listed', label: '挂车' },
  { key: 'freeSample', label: '免费寄样' }
]

function findLabel(options: { label: string; value: string }[], value: string | null) {
  if (!value) return ''
  return options.find((option) => option.value === value)?.label || value
}

const selectedFilters = computed<SelectedFilter[]>(() => {
  const result: SelectedFilter[] = []
  const addArray = (key: 'categories' | 'productTags', label: string) => {
    props.filters[key].forEach((value) => {
      result.push({ id: `${key}:${value}`, type: 'array', key, label, text: value, value })
    })
  }
  const addField = (key: keyof ProductFilterState, label: string, text: string) => {
    if (text) result.push({ id: `${String(key)}:${text}`, type: 'field', key, label, text })
  }

  addArray('categories', '商品类目')
  addArray('productTags', '商品标签')
  addField('commission', '佣金区间', findLabel(commissionOptions, props.filters.commission))
  addField('serviceFee', '服务费率', findLabel(serviceFeeOptions, props.filters.serviceFee))
  addField('salesRange', '近30天销量', findLabel(salesRangeOptions, props.filters.salesRange))
  addField('promotionLink', '转链状态', findLabel(promotionLinkOptions, props.filters.promotionLink))
  if (props.libraryStatus !== null) {
    result.push({
      id: `libraryStatus:${props.libraryStatus}`,
      type: 'libraryStatus',
      label: '推广状态',
      text: libraryStatusLabel[props.libraryStatus] || String(props.libraryStatus)
    })
  }
  enabledOptionLabels.forEach((item) => {
    if (props.filters[item.key] === '1') {
      result.push({ id: `${String(item.key)}:1`, type: 'field', key: item.key, label: item.label, text: '是' })
    }
  })
  booleanLabels.forEach((item) => {
    if (props.filters[item.key] === true) {
      result.push({ id: `${String(item.key)}:true`, type: 'field', key: item.key, label: item.label, text: '是' })
    }
  })
  addField('productId', '商品ID', props.filters.productId || '')
  addField('shopKeyword', '店铺名称', props.filters.shopKeyword || '')
  addField('colonelName', '团长名称', props.filters.colonelName || '')
  return result
})

function removeFilter(item: SelectedFilter) {
  if (item.type === 'libraryStatus') {
    emit('update:libraryStatus', null)
    return
  }
  if (item.type === 'array') {
    emit('update:filters', {
      ...props.filters,
      [item.key]: props.filters[item.key].filter((value) => value !== item.value)
    })
    return
  }
  const currentValue = props.filters[item.key]
  emit('update:filters', {
    ...props.filters,
    [item.key]: typeof currentValue === 'boolean' ? false : null
  } as ProductFilterState)
}
</script>

<style scoped>
.selected-filter-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 14px;
  align-items: flex-start;
  padding: 12px 0 4px;
}

.filter-row-label {
  color: #6b1f2b;
  font-size: 13px;
  font-weight: 700;
  line-height: 30px;
  white-space: nowrap;
}

.selected-filter-content {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 30px;
  min-width: 0;
  align-items: center;
}

.selected-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  min-height: 28px;
  padding: 0 8px 0 10px;
  border: 1px solid rgba(232, 69, 85, 0.22);
  border-radius: 6px;
  background: #fff;
  color: #cf3344;
  cursor: pointer;
  font-size: 12px;
}

.selected-chip span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-chip-close {
  color: #e84555;
  font-size: 15px;
  line-height: 1;
}

.clear-button {
  min-height: 28px;
  padding: 0 8px;
  border: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 12px;
}

.clear-button:hover {
  color: #cf3344;
}

.selected-empty {
  color: #94a3b8;
  font-size: 13px;
  line-height: 30px;
}

@media (max-width: 720px) {
  .selected-filter-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .filter-row-label {
    line-height: 1.4;
  }
}
</style>
