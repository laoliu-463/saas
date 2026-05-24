<template>
  <n-select
    :value="modelValue"
    :options="specOptions"
    :loading="loading"
    filterable
    clearable
    :placeholder="placeholder"
    data-testid="product-spec-selector"
    @update:value="emit('update:modelValue', $event || '')"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    skus?: Array<{ skuId?: string; skuName?: string; priceText?: string }>
    loading?: boolean
    placeholder?: string
  }>(),
  {
    modelValue: '',
    skus: () => [],
    loading: false,
    placeholder: '选择商品规格'
  }
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const specOptions = computed(() =>
  (props.skus || [])
    .map((sku) => {
      const name = String(sku?.skuName || '').trim()
      if (!name) return null
      const price = String(sku?.priceText || '').trim()
      return {
        label: price ? `${name}（${price}）` : name,
        value: name
      }
    })
    .filter(Boolean) as Array<{ label: string; value: string }>
)
</script>
