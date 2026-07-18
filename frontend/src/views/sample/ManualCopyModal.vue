<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="手动复制"
    :style="{ width: '640px', maxWidth: 'calc(100vw - 32px)' }"
  >
    <n-alert type="warning" :bordered="false">浏览器未允许写入剪贴板，请手动复制以下完整内容。</n-alert>
    <n-input :value="content" type="textarea" :rows="12" readonly data-testid="manual-copy-content" />
    <template #footer>
      <n-space justify="end">
        <n-button @click="visible = false">关闭</n-button>
        <n-button type="primary" @click="retryCopy">再次复制</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { tryCopyText } from '../../utils/clipboard'

const props = defineProps<{ content: string }>()
const visible = defineModel<boolean>('show', { default: false })
const message = useMessage()

const retryCopy = async () => {
  if (await tryCopyText(props.content)) {
    message.success('复制成功')
    visible.value = false
    return
  }
  message.warning('仍无法写入剪贴板，请手动选择并复制')
}
</script>

<style scoped>
:deep(.n-alert) { margin-bottom: 12px; }
</style>
