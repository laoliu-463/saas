<!-- 商品规格选择器：基于 NSelect 封装的 SKU 规格下拉选择组件 -->
<template>
  <!-- 规格下拉选择器，支持搜索、清除、加载状态 -->
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
/**
 * 商品规格选择器组件（ProductSpecSelector）
 *
 * 功能：将商品 SKU 列表渲染为可选下拉项，支持双向绑定选中值，
 * 选中时向外传递完整的 SKU 对象。
 *
 * 使用场景：
 * - 寄样申请表单中选择商品规格
 * - 活动商品 SKU 选择
 * - 任何需要从商品 SKU 列表中选择的场景
 */
import { computed } from 'vue'

/** 商品 SKU 选项类型定义 */
type ProductSkuOption = { skuId?: string; skuName?: string; priceText?: string }

const props = withDefaults(
  defineProps<{
    /** 当前选中的规格值（支持 v-model 双向绑定） */
    modelValue?: string | null
    /** SKU 数据列表，每项至少包含 skuName 和可选的 skuId、priceText */
    skus?: ProductSkuOption[]
    /** 是否显示加载中状态 */
    loading?: boolean
    /** 下拉框占位文本 */
    placeholder?: string
    /** 用作选项 value 的字段名，'skuName' 或 'skuId'，默认 'skuName' */
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
  /** v-model 双向绑定更新事件 */
  'update:modelValue': [value: string]
  /** 选中 SKU 时触发，传递完整的 SKU 对象（未选中为 null） */
  select: [sku: ProductSkuOption | null]
}>()

/**
 * 将 SKU 列表转换为 NSelect 选项格式
 * - 过滤掉没有名称的 SKU
 * - label 格式为 "规格名（价格）" 或仅 "规格名"
 * - value 根据 valueField 取 skuName 或 skuId
 */
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

/**
 * 处理下拉选择值变更
 * - 标准化输入值后触发 v-model 更新
 * - 在 SKU 列表中查找匹配项，通过 select 事件传递完整 SKU 对象
 * @param value 选中的规格值
 */
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
