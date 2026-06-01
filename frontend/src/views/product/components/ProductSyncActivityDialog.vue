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
  confirm: [activityId: string]
  refreshOptions: []
}>()

const selectedActivityId = ref('')

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

const updateShow = (value: boolean) => {
  emit('update:show', value)
}

const submit = () => {
  if (!canSubmit.value) return
  emit('confirm', selectedActivityId.value)
}

watch(() => props.show, (show) => {
  if (show) resetSelection()
}, { immediate: true })

watch(() => props.activityOptions, () => {
  if (!props.show) return
  if (selectedActivityId.value && props.activityOptions.some((option) => option.value === selectedActivityId.value)) return
  resetSelection()
})
</script>
