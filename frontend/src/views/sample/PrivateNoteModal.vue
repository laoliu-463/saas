<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="备注"
    :style="{ width: '520px', maxWidth: 'calc(100vw - 32px)' }"
    :mask-closable="!saving"
  >
    <n-alert type="info" :bordered="false">私有备注，仅自己可见。</n-alert>
    <n-input
      v-model:value="content"
      type="textarea"
      :rows="5"
      :disabled="loading"
      maxlength="200"
      show-count
      placeholder="请输入备注"
      data-testid="private-note-content"
    />
    <template #footer>
      <n-space justify="end">
        <n-button :disabled="saving" @click="visible = false">取消</n-button>
        <n-button
          type="primary"
          :loading="saving"
          :disabled="!loadedSuccessfully || loading || saving"
          data-testid="private-note-save"
          @click="save"
        >保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { getSamplePrivateNote, saveSamplePrivateNote } from '../../api/sample'
import type { SamplePrivateNote } from '../../types'
import { notifyApiFailure } from '../../utils/requestError'

const props = defineProps<{ sampleId: string }>()
const visible = defineModel<boolean>('show', { default: false })
const message = useMessage()
const content = ref('')
const loading = ref(false)
const loadedSuccessfully = ref(false)
const saving = ref(false)
let sessionGeneration = 0

const reset = () => {
  sessionGeneration += 1
  content.value = ''
  loading.value = false
  loadedSuccessfully.value = false
  saving.value = false
}

const isCurrentSession = (generation: number, sampleId: string) => (
  generation === sessionGeneration && visible.value && props.sampleId === sampleId
)

const load = async () => {
  if (!props.sampleId) return
  const sampleId = props.sampleId
  const generation = ++sessionGeneration
  content.value = ''
  loading.value = true
  loadedSuccessfully.value = false
  try {
    const response: any = await getSamplePrivateNote(sampleId)
    if (!isCurrentSession(generation, sampleId)) return
    const result = (response?.data || response) as SamplePrivateNote
    content.value = result?.content || ''
    loadedSuccessfully.value = true
  } catch (error) {
    if (!isCurrentSession(generation, sampleId)) return
    notifyApiFailure(error, message, { fallbackMessage: '加载备注失败' })
  } finally {
    if (isCurrentSession(generation, sampleId)) loading.value = false
  }
}

const save = async () => {
  if (!loadedSuccessfully.value || loading.value || saving.value) return
  if (content.value.length > 200) {
    message.error('备注不能超过200字')
    return
  }
  const sampleId = props.sampleId
  const generation = sessionGeneration
  const noteContent = content.value
  saving.value = true
  try {
    await saveSamplePrivateNote(sampleId, { content: noteContent })
    if (!isCurrentSession(generation, sampleId)) return
    message.success('备注已保存')
    visible.value = false
  } catch (error) {
    if (!isCurrentSession(generation, sampleId)) return
    notifyApiFailure(error, message, { fallbackMessage: '保存备注失败' })
  } finally {
    if (isCurrentSession(generation, sampleId)) saving.value = false
  }
}

watch(
  () => [visible.value, props.sampleId] as const,
  ([show]) => {
    if (show) load()
    else reset()
  },
  { immediate: true }
)
</script>

<style scoped>
:deep(.n-alert) { margin-bottom: 12px; }
</style>
