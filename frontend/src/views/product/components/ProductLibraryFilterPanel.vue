<template>
  <section class="library-filter-panel" data-testid="product-library-filter-panel">
    <ProductCategoryFilter
      :model-value="filters.categories"
      :options="categoryOptions"
      @update:model-value="updateFilter('categories', $event)"
    />
    <ProductTagFilter
      title="货盘标签"
      test-id="goods-tag-filter"
      :model-value="filters.goodsTags"
      :options="goodsTagOptions"
      @update:model-value="updateFilter('goodsTags', $event)"
    />
    <ProductTagFilter
      title="商品标签"
      test-id="product-tag-filter"
      :model-value="filters.productTags"
      :options="productTagOptions"
      @update:model-value="updateFilter('productTags', $event)"
    />
    <ProductDemandFilter
      :filters="filters"
      :library-status="libraryStatus"
      @update:filters="$emit('update:filters', $event)"
      @update:library-status="$emit('update:libraryStatus', $event)"
    />
    <ProductOtherFilter
      :filters="filters"
      @update:filters="$emit('update:filters', $event)"
    />
    <ProductSelectedFilters
      :filters="filters"
      :library-status="libraryStatus"
      @update:filters="$emit('update:filters', $event)"
      @update:library-status="$emit('update:libraryStatus', $event)"
      @clear="$emit('reset')"
    />
    <ProductBasicSearchBar
      :filters="filters"
      :loading="loading"
      @update:filters="$emit('update:filters', $event)"
      @search-click="$emit('search-click')"
      @reset="$emit('reset')"
    />
  </section>
</template>

<script setup lang="ts">
import type { ProductFilterState } from '../product-filters'
import { goodsTagOptions, productTagOptions } from '../product-filters'
import ProductBasicSearchBar from './ProductBasicSearchBar.vue'
import ProductCategoryFilter from './ProductCategoryFilter.vue'
import ProductDemandFilter from './ProductDemandFilter.vue'
import ProductOtherFilter from './ProductOtherFilter.vue'
import ProductSelectedFilters from './ProductSelectedFilters.vue'
import ProductTagFilter from './ProductTagFilter.vue'

const props = defineProps<{
  filters: ProductFilterState
  libraryStatus: number | null
  loading: boolean
  categoryOptions: { label: string; value: string }[]
}>()

const emit = defineEmits<{
  'update:filters': [value: ProductFilterState]
  'update:libraryStatus': [value: number | null]
  'search-click': []
  reset: []
}>()

function updateFilter<K extends keyof ProductFilterState>(key: K, value: ProductFilterState[K]) {
  emit('update:filters', { ...props.filters, [key]: value })
}
</script>

<style scoped>
.library-filter-panel {
  margin-bottom: var(--content-gap);
  padding: 14px 18px 16px;
  border: 1px solid rgba(232, 69, 85, 0.16);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 246, 248, 0.98) 0%, rgba(255, 250, 251, 0.98) 100%);
  box-shadow: 0 8px 24px rgba(232, 69, 85, 0.06);
}

@media (max-width: 640px) {
  .library-filter-panel {
    padding: 12px;
  }
}
</style>
