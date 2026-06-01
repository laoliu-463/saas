<template>
  <n-card :bordered="false" class="main-card app-panel app-table-shell">
    <slot name="batch-toolbar" />
    <n-data-table
      v-if="rows.length"
      data-testid="product-table"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :row-key="rowKey"
      :checked-row-keys="checkedRowKeys"
      :single-line="false"
      :scroll-x="scrollX"
      :scrollbar-props="{ trigger: 'none' }"
      @update:checked-row-keys="(keys: Array<string | number>) => $emit('update:checkedRowKeys', keys.map((k) => String(k)))"
    />

    <PageEmpty v-else-if="!loading" title="暂无商品数据" :description="emptyDescription" class="table-empty">
      <template #icon>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="36" height="36">
          <path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
        </svg>
      </template>
    </PageEmpty>

    <div v-if="rows.length" class="load-more">
      <n-button v-if="hasMore" :loading="loadingMore" secondary @click="$emit('loadMore')">加载更多</n-button>
      <span v-else class="no-more">已加载全部商品</span>
    </div>
  </n-card>
</template>

<script setup lang="ts">
import PageEmpty from '../../../components/PageEmpty.vue'

withDefaults(defineProps<{
  rows: Record<string, unknown>[]
  columns: any[]
  loading: boolean
  loadingMore: boolean
  hasMore: boolean
  checkedRowKeys: string[]
  scrollX: number
  emptyDescription: string
}>(), {
  rows: () => [],
  columns: () => [],
  checkedRowKeys: () => []
})

defineEmits<{
  'update:checkedRowKeys': [value: string[]]
  loadMore: []
}>()

const rowKey = (row: Record<string, unknown>) => String(row.productId || '')
</script>
