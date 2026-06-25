<script setup lang="ts">
/**
 * @component CurrentActivityBanner
 * @description 商品管理页顶部活动状态条。
 *
 * 用于 [PRODUCT-FIX-001]：让用户一眼看到当前页面数据归属哪个活动。
 *
 * 状态：
 * - ready     : "当前活动: XXX (ID)"
 * - empty     : "请先选择活动"
 * - forbidden : "您没有访问该活动的权限"
 * - loading   : "正在加载活动信息…"
 */
import { computed } from 'vue'

type Status = 'ready' | 'empty' | 'forbidden' | 'loading'

const props = defineProps<{
  status: Status
  activityId?: string | null
  activityName?: string | null
}>()

const message = computed(() => {
  switch (props.status) {
    case 'ready':
      return `当前活动: ${props.activityName || props.activityId}`
    case 'empty':
      return '请先选择活动'
    case 'forbidden':
      return '您没有访问该活动的权限'
    case 'loading':
      return '正在加载活动信息…'
    default:
      return ''
  }
})

const tone = computed(() => {
  switch (props.status) {
    case 'ready':
      return 'info'
    case 'empty':
      return 'warning'
    case 'forbidden':
      return 'error'
    case 'loading':
      return 'info'
    default:
      return 'info'
  }
})

const testId = computed(() => `product-manage-activity-banner-${props.status}`)
</script>

<template>
  <n-alert
    v-if="status"
    :type="tone"
    :title="message"
    :data-testid="testId"
    show-icon
    style="margin-bottom: 12px"
  />
</template>