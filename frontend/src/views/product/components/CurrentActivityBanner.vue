<template>
  <n-alert
    :type="alertType"
    class="page-alert app-page-alert"
    data-testid="current-activity-banner"
  >
    {{ text }}
  </n-alert>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ProductManageActivityContextStatus } from '../product-page-data-source'

const props = defineProps<{
  status: ProductManageActivityContextStatus
  activityId?: string
  activityName?: string
}>()

const alertType = computed(() => {
  if (props.status === 'forbidden') return 'error'
  if (props.status === 'ready') return 'info'
  return 'warning'
})

const text = computed(() => {
  if (props.status === 'ready' && props.activityId) {
    const name = props.activityName || props.activityId
    return `当前活动: ${name} (${props.activityId})`
  }
  if (props.status === 'forbidden') {
    return '无权限查看当前活动'
  }
  if (props.status === 'loading') {
    return '正在加载活动权限'
  }
  return '请先选择活动'
})
</script>
