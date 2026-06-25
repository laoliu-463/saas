<template>
  <n-modal :show="show" preset="dialog" title="同步商品" style="width: 520px" @update:show="updateShow">
    <n-form label-placement="top">
      <n-form-item label="选择活动">
        <n-select
          v-model:value="selectedActivityId"
          data-testid="sync-activity-select"
          filterable
          clearable
          :loading="loading"
          :options="activityOptions"
          placeholder="请选择活动"
        />
      </n-form-item>
      <n-form-item label="同步范围">
        <n-radio-group v-model:value="selectedSyncMode" data-testid="sync-mode-radio-group">
          <n-space vertical>
            <n-radio value="FULL" data-testid="sync-mode-full">
              同步全部商品
            </n-radio>
            <n-radio value="PRIORITY_1000" data-testid="sync-mode-priority-1000">
              先同步 1000 个优先商品（待审核、推广中）
            </n-radio>
          </n-space>
        </n-radio-group>
      </n-form-item>
    </n-form>
    <template #action>
      <n-space justify="end">
        <n-button @click="updateShow(false)">取消</n-button>
        <n-button secondary :loading="loading" @click="$emit('refreshOptions')">刷新活动</n-button>
        <n-button type="primary" :disabled="!canSubmit" :loading="syncing" @click="submit">开始同步</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AssignedActivityOption } from '../assigned-activity-options'

type ProductSyncMode = 'FULL' | 'PRIORITY_1000'

interface ProductSyncActivityConfirmPayload {
  activityId: string
  syncMode: ProductSyncMode
  maxRowsPerActivity?: number
  priorityStatuses?: number[]
}

const props = withDefaults(defineProps<{
  show: boolean
  activityOptions: AssignedActivityOption[]
  defaultActivityId?: string | null
  loading?: boolean
  syncing?: boolean
}>(), {
  defaultActivityId: null,
  loading: false,
  syncing: false
})

const emit = defineEmits<{
  'update:show': [value: boolean]
  confirm: [payload: ProductSyncActivityConfirmPayload]
  refreshOptions: []
}>()

const selectedActivityId = ref('')
const selectedSyncMode = ref<ProductSyncMode>('FULL')

const canSubmit = computed(() =>
  Boolean(selectedActivityId.value) && !props.loading && !props.syncing
)

const resetSelection = () => {
  const defaultActivityId = String(props.defaultActivityId || '').trim()
  if (defaultActivityId && props.activityOptions.some((option) => option.value === defaultActivityId)) {
    selectedActivityId.value = defaultActivityId
    return
  }
  selectedActivityId.value = props.activityOptions.length === 1 ? props.activityOptions[0].value : ''
}

const resetSyncMode = () => {
  selectedSyncMode.value = 'FULL'
}

const updateShow = (value: boolean) => {
  emit('update:show', value)
}

const submit = () => {
  if (!canSubmit.value) return
  emit('confirm', {
    activityId: selectedActivityId.value,
    syncMode: selectedSyncMode.value,
    maxRowsPerActivity: selectedSyncMode.value === 'PRIORITY_1000' ? 1000 : undefined,
    priorityStatuses: selectedSyncMode.value === 'PRIORITY_1000' ? [0, 1] : undefined
  })
}

watch(() => props.show, (show) => {
  if (show) {
    resetSelection()
    resetSyncMode()
  }
}, { immediate: true })

watch(() => props.activityOptions, () => {
  if (!props.show) return
  if (selectedActivityId.value && props.activityOptions.some((option) => option.value === selectedActivityId.value)) return
  resetSelection()
})
</script>
