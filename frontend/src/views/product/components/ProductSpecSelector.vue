<template>
  <n-select
    :value="modelValue"
    :options="specOptions"
    :loading="loading"
    filterable
    clearable
    :placeholder="placeholder"
    data-testid="product-spec-selector"
    @update:value="handleUpdateValue"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'

type ProductSkuOption = { skuId?: string; skuName?: string; priceText?: string }

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    skus?: ProductSkuOption[]
    loading?: boolean
    placeholder?: string
    valueField?: 'skuName' | 'skuId'
  }>(),
  {
    modelValue: '',
    skus: () => [],
    loading: false,
    placeholder: '选择商品规格',
    valueField: 'skuName'
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  select: [sku: ProductSkuOption | null]
}>()

const specOptions = computed(() =>
  (props.skus || [])
    .map((sku) => {
      const name = String(sku?.skuName || '').trim()
      if (!name) return null
      const price = String(sku?.priceText || '').trim()
      const value = String(props.valueField === 'skuId' ? sku?.skuId || '' : name).trim()
      if (!value) return null
      return {
        label: price ? `${name}（${price}）` : name,
        value
      }
    })
    .filter(Boolean) as Array<{ label: string; value: string }>
)

const handleUpdateValue = (value: string | null) => {
  const normalized = String(value || '').trim()
  emit('update:modelValue', normalized)
  const selected = (props.skus || []).find((sku) => {
    const optionValue = String(props.valueField === 'skuId' ? sku?.skuId || '' : sku?.skuName || '').trim()
    return optionValue === normalized
  })
  emit('select', selected || null)
}
</script>
