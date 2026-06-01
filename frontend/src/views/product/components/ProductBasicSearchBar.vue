<template>
  <div class="basic-search-bar" data-testid="product-basic-search-bar">
    <n-input
      :value="filters.productId"
      clearable
      placeholder="商品ID"
      data-testid="library-basic-product-id"
      @update:value="updateFilter('productId', $event)"
      @keyup.enter="$emit('search-click')"
    />
    <n-input
      :value="filters.shopKeyword"
      clearable
      placeholder="店铺名称"
      data-testid="library-basic-shop-name"
      @update:value="updateFilter('shopKeyword', $event)"
      @keyup.enter="$emit('search-click')"
    />
    <n-input
      :value="filters.colonelName"
      clearable
      placeholder="团长名称"
      data-testid="library-basic-colonel-name"
      @update:value="updateFilter('colonelName', $event)"
      @keyup.enter="$emit('search-click')"
    />
    <n-button type="primary" :loading="loading" data-testid="library-basic-search" @click="$emit('search-click')">
      查询
    </n-button>
    <n-button data-testid="library-basic-reset" @click="$emit('reset')">重置</n-button>
  </div>
</template>

<script setup lang="ts">
import type { ProductFilterState } from '../product-filters'

const props = defineProps<{
  filters: ProductFilterState
  loading: boolean
}>()

const emit = defineEmits<{
  'update:filters': [value: ProductFilterState]
  'search-click': []
  reset: []
}>()

function updateFilter<K extends keyof ProductFilterState>(key: K, value: ProductFilterState[K]) {
  emit('update:filters', { ...props.filters, [key]: value })
}
</script>

<style scoped>
.basic-search-bar {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr)) auto auto;
  gap: 10px;
  align-items: center;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(232, 69, 85, 0.16);
}

@media (max-width: 960px) {
  .basic-search-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .basic-search-bar {
    grid-template-columns: 1fr;
  }
}
</style>
