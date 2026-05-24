<template>
  <n-card title="复制讲解模板" data-testid="promotion-template-editor">
    <n-alert type="info" :bordered="false" style="margin-bottom: 12px">
      可用占位符：{productName}、{commissionRate}、{shortLink}、{pickSource}
    </n-alert>
    <n-input
      v-model:value="templateText"
      type="textarea"
      :rows="10"
      :disabled="!editable"
      placeholder="编辑 promotion.copy_brief_template / copy_template"
      data-testid="promotion-template-input"
    />
    <n-divider>实时预览</n-divider>
    <pre class="preview" data-testid="promotion-template-preview">{{ previewText }}</pre>
  </n-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  modelValue?: string
  editable?: boolean
}>(), {
  modelValue: '',
  editable: false
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const templateText = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value)
})

const previewText = computed(() =>
  (props.modelValue || '')
    .replaceAll('{productName}', '示例商品')
    .replaceAll('{商品名称}', '示例商品')
    .replaceAll('{commissionRate}', '25%')
    .replaceAll('{佣金率}', '25%')
    .replaceAll('{shortLink}', 'https://v.douyin.com/example')
    .replaceAll('{短链接}', 'https://v.douyin.com/example')
    .replaceAll('{pickSource}', '示例来源')
    .replaceAll('{来源}', '示例来源')
)
</script>

<style scoped>
.preview {
  margin: 0;
  padding: 12px;
  background: var(--surface-muted, #f8fafc);
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
