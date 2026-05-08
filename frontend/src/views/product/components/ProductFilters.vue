<template>
  <div class="product-toolbar">
    <n-space vertical :size="12">
      <n-space wrap :size="12" align="center">
        <n-select
          :value="selectedProduct"
          :options="productOptions"
          :loading="productOptionsLoading"
          filterable
          remote
          clearable
          placeholder="搜索商品 / 店铺"
          style="width: 280px"
          @update:value="$emit('update:selectedProduct', $event)"
          @search="$emit('search', $event)"
        />
        <n-select
          :value="filters.category"
          :options="categoryOptions"
          placeholder="商品标签"
          clearable
          style="width: 160px"
          @update:value="updateFilter('category', $event)"
        />
        <n-select
          :value="filters.commission"
          :options="commissionOptions"
          placeholder="佣金区间"
          clearable
          style="width: 140px"
          @update:value="updateFilter('commission', $event)"
        />
        <n-select
          :value="filters.hasSample"
          :options="yesNoOptions"
          placeholder="支持寄样"
          clearable
          style="width: 120px"
          @update:value="updateFilter('hasSample', $event)"
        />
        <n-select
          v-if="showAssigneeFilter"
          :value="filters.assignee"
          :options="assigneeOptions"
          placeholder="招商归属"
          clearable
          style="width: 160px"
          @update:value="updateFilter('assignee', $event)"
        />
        <n-select
          :value="filters.decision"
          :options="decisionOptions"
          placeholder="推进判断"
          clearable
          style="width: 160px"
          @update:value="updateFilter('decision', $event)"
        />
        <n-select
          :value="status"
          :options="statusOptions"
          placeholder="业务状态"
          clearable
          style="width: 160px"
          @update:value="$emit('update:status', $event)"
        />
        <n-button type="primary" :loading="loading" @click="$emit('search-click')">查询</n-button>
        <n-button @click="$emit('reset')">重置</n-button>
      </n-space>
    </n-space>
  </div>
</template>

<script setup lang="ts">
interface Filters {
  category: string | null
  commission: string | null
  hasSample: string | null
  assignee: string | null
  decision: string | null
}

const props = withDefaults(defineProps<{
  filters: Filters
  selectedProduct: string | null
  status: string | null
  productOptions: { label: string; value: string }[]
  productOptionsLoading: boolean
  loading: boolean
  showAssigneeFilter?: boolean
}>(), {
  showAssigneeFilter: true
})

const emit = defineEmits<{
  'update:filters': [value: Filters]
  'update:selectedProduct': [value: string | null]
  'update:status': [value: string | null]
  search: [keyword: string]
  'search-click': []
  reset: []
}>()

function updateFilter(key: keyof Filters, value: string | null) {
  emit('update:filters', { ...props.filters, [key]: value })
}

const categoryOptions = [
  { label: '高佣爆款', value: 'high_commission' },
  { label: '适合投放', value: 'traffic' },
  { label: '新品首发', value: 'new' },
  { label: '高客单价', value: 'high_price' }
]

const commissionOptions = [
  { label: '20%以上', value: 'gt20' },
  { label: '10% - 20%', value: '10_20' },
  { label: '10%以下', value: 'lt10' }
]

const yesNoOptions = [
  { label: '是', value: '1' },
  { label: '否', value: '0' }
]

const assigneeOptions = [
  { label: '已分配负责人', value: 'assigned' },
  { label: '未分配负责人', value: 'unassigned' }
]

const decisionOptions = [
  { label: '主推', value: 'MAIN' },
  { label: '次推', value: 'SECONDARY' },
  { label: '暂缓', value: 'PAUSE' },
  { label: '放弃', value: 'DROP' },
  { label: '暂无判断', value: 'NONE' }
]

const statusOptions = [
  { label: '待审核', value: 'PENDING_AUDIT' },
  { label: '审核通过', value: 'APPROVED' },
  { label: '审核拒绝', value: 'REJECTED' },
  { label: '历史已绑定', value: 'BOUND' },
  { label: '已分配招商', value: 'ASSIGNED' },
  { label: '已转链', value: 'LINKED' },
  { label: '已转交达人 CRM', value: 'FOLLOWING' }
]
</script>

<style scoped>
.product-toolbar {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg) 20px;
  margin-bottom: var(--spacing-md);
  box-shadow: var(--shadow-sm);
}
</style>
