<template>
  <div class="product-manage-filters app-toolbar" data-testid="product-manage-filters">
    <div class="filter-grid">
      <n-input
        :value="props.filters.productId"
        clearable
        placeholder="商品ID"
        data-testid="filter-product-id"
        @update:value="updateFilter('productId', $event)"
        @keyup.enter="$emit('search-click')"
      />
      <n-input
        :value="props.filters.productName"
        clearable
        placeholder="商品名称"
        data-testid="filter-product-name"
        @update:value="updateFilter('productName', $event)"
        @keyup.enter="$emit('search-click')"
      />
      <n-select
        :value="props.filters.recruitActivityId"
        :options="props.assignedActivityOptions"
        :loading="props.assignedActivityOptionsLoading"
        filterable
        clearable
        placeholder="活动信息"
        data-testid="filter-assigned-activity"
        @update:value="updateAssignedActivity"
      />
      <n-select
        v-if="props.showAssigneeFilter"
        :value="props.filters.assignee"
        :options="assigneeOptions"
        clearable
        placeholder="招商"
        data-testid="filter-assignee"
        @update:value="updateFilter('assignee', $event)"
      />
      <n-button type="primary" :loading="props.loading" data-testid="filter-search" @click="$emit('search-click')">搜索</n-button>
      <n-button data-testid="filter-reset" @click="$emit('reset')">重置</n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  assigneeOptions,
  type ProductFilterState
} from '../product-filters'

const props = withDefaults(defineProps<{
  filters: ProductFilterState
  loading: boolean
  showAssigneeFilter?: boolean
  assignedActivityOptions?: { label: string; value: string }[]
  assignedActivityOptionsLoading?: boolean
}>(), {
  showAssigneeFilter: true,
  assignedActivityOptions: () => [],
  assignedActivityOptionsLoading: false
})

const emit = defineEmits<{
  'update:filters': [value: ProductFilterState]
  'search-click': []
  reset: []
}>()

function updateFilter<K extends keyof ProductFilterState>(key: K, value: ProductFilterState[K]) {
  emit('update:filters', { ...props.filters, [key]: value })
}

/**
 * 三联赋值:招商活动 dropdown 选中时同步写 recruitActivityId / activityId / recruitActivityName。
 * - recruitActivityId: 当前 T1 页面 (activity 维度) 选定的活动;
 * - activityId: T2 商品库 / T1 library 模式的"硬筛选"活动;与 recruitActivityId 一致时写同一值,
 *   避免用户在 T1 选定活动后跳到商品库看不到对应数据;
 * - recruitActivityName: 后端 `matchesRecruitActivityFilter` 用作名称模糊匹配;从 option.label
 *   反解 `${name} (${value})` 取 name 部分;value=null 时三字段统一清空。
 */
function updateAssignedActivity(value: string | null) {
  const matchedOption = value
    ? props.assignedActivityOptions.find((option) => option.value === value)
    : null
  const matchedName = matchedOption
    ? matchedOption.label.replace(/\s*\([^)]*\)\s*$/, '').trim()
    : ''
  emit('update:filters', {
    ...props.filters,
    recruitActivityId: value,
    activityId: value,
    recruitActivityName: matchedName || null
  })
}
</script>

<style scoped>
.product-manage-filters {
  margin-bottom: 12px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr)) auto auto;
  gap: 10px;
  align-items: center;
}

@media (max-width: 1100px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
