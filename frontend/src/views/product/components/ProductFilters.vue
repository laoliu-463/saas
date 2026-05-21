<template>
  <div class="product-toolbar app-toolbar">
    <n-space vertical :size="12">
      <n-space wrap :size="12" align="center">
        <n-select
          :value="selectedProduct"
          :options="productOptions"
          :loading="productOptionsLoading"
          filterable
          remote
          clearable
          placeholder="搜索商品名称 / ID"
          style="width: 260px"
          @update:value="$emit('update:selectedProduct', $event)"
          @search="$emit('search', $event)"
        />
        <n-input
          :value="filters.shopKeyword"
          clearable
          placeholder="店铺 / 合作方"
          style="width: 160px"
          @update:value="updateFilter('shopKeyword', $event)"
        />
        <n-select
          :value="filters.categoryName"
          :options="categoryNameOptions"
          placeholder="抖音类目"
          clearable
          filterable
          tag
          style="width: 150px"
          @update:value="updateFilter('categoryName', $event)"
        />
        <n-select
          :value="filters.systemTag"
          :options="systemTagOptions"
          placeholder="系统标签"
          clearable
          style="width: 140px"
          @update:value="updateFilter('systemTag', $event)"
        />
        <n-select
          :value="filters.commission"
          :options="commissionOptions"
          placeholder="佣金区间"
          clearable
          style="width: 130px"
          @update:value="updateFilter('commission', $event)"
        />
        <n-select
          :value="filters.salesRange"
          :options="salesRangeOptions"
          placeholder="近30天销量"
          clearable
          style="width: 170px"
          @update:value="updateFilter('salesRange', $event)"
        />
        <n-button type="primary" :loading="loading" @click="$emit('search-click')">查询</n-button>
        <n-button @click="$emit('reset')">重置</n-button>
      </n-space>

      <n-space wrap :size="12" align="center">
        <n-select
          :value="filters.allianceStatus"
          :options="allianceStatusOptions"
          placeholder="联盟推广状态"
          clearable
          style="width: 150px"
          @update:value="updateFilter('allianceStatus', $event)"
        />
        <n-select
          :value="filters.promotionLink"
          :options="promotionLinkOptions"
          placeholder="转链状态"
          clearable
          style="width: 140px"
          @update:value="updateFilter('promotionLink', $event)"
        />
        <n-select
          :value="filters.hasSample"
          :options="yesNoOptions"
          placeholder="支持寄样"
          clearable
          style="width: 120px"
          @update:value="updateFilter('hasSample', $event)"
        />
        <template v-if="mode === 'manage'">
          <n-select
            v-if="showAssigneeFilter"
            :value="filters.assignee"
            :options="assigneeOptions"
            placeholder="招商归属"
            clearable
            style="width: 150px"
            @update:value="updateFilter('assignee', $event)"
          />
          <n-select
            :value="filters.decision"
            :options="decisionOptions"
            placeholder="推进判断"
            clearable
            style="width: 140px"
            @update:value="updateFilter('decision', $event)"
          />
          <n-select
            :value="status"
            :options="bizStatusOptions"
            placeholder="业务状态"
            clearable
            style="width: 150px"
            @update:value="$emit('update:status', $event)"
          />
        </template>
        <template v-else-if="mode === 'library'">
          <n-select
            :value="libraryStatus"
            :options="libraryShelfOptions"
            placeholder="上架状态"
            clearable
            style="width: 130px"
            @update:value="$emit('update:libraryStatus', $event)"
          />
        </template>
      </n-space>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import type { ProductFilterState } from '../product-filters'
import {
  allianceStatusOptions,
  assigneeOptions,
  bizStatusOptions,
  categoryNameOptions,
  commissionOptions,
  decisionOptions,
  promotionLinkOptions,
  salesRangeOptions,
  systemTagOptions,
  yesNoOptions
} from '../product-filters'

const props = withDefaults(defineProps<{
  filters: ProductFilterState
  selectedProduct: string | null
  status: string | null
  libraryStatus?: number | null
  productOptions: { label: string; value: string }[]
  productOptionsLoading: boolean
  loading: boolean
  showAssigneeFilter?: boolean
  /** manage=活动/推进池；library=共享商品库 */
  mode?: 'manage' | 'library'
}>(), {
  showAssigneeFilter: true,
  mode: 'manage',
  libraryStatus: null
})

const emit = defineEmits<{
  'update:filters': [value: ProductFilterState]
  'update:selectedProduct': [value: string | null]
  'update:status': [value: string | null]
  'update:libraryStatus': [value: number | null]
  search: [keyword: string]
  'search-click': []
  reset: []
}>()

function updateFilter<K extends keyof ProductFilterState>(key: K, value: ProductFilterState[K]) {
  emit('update:filters', { ...props.filters, [key]: value })
}

const libraryShelfOptions = [
  { label: '上架', value: 1 },
  { label: '下架', value: 0 }
]
</script>
