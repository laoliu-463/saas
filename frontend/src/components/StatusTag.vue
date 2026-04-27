<template>
  <n-tag :type="resolvedType" size="small" round>
    {{ resolvedLabel }}
  </n-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Scene = 'attribution' | 'sample' | 'product'
type TagType = 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error'

const props = defineProps<{
  status?: string | null
  scene: Scene
  label?: string
}>()

const statusMaps: Record<Scene, Record<string, { label: string; type: TagType }>> = {
  attribution: {
    ATTRIBUTED: { label: '已确认业绩', type: 'success' },
    UNATTRIBUTED: { label: '待排查订单', type: 'warning' },
    PARTIAL: { label: '部分归因', type: 'info' },
    FAILED: { label: '同步失败', type: 'error' }
  },
  sample: {
    PENDING_AUDIT: { label: '待审核', type: 'info' },
    PENDING_SHIP: { label: '待发货', type: 'primary' },
    SHIPPED: { label: '快递中', type: 'info' },
    PENDING_TASK: { label: '待交作业', type: 'warning' },
    FINISHED: { label: '已完成', type: 'success' },
    REJECTED: { label: '已拒绝', type: 'error' },
    CLOSED: { label: '已关闭', type: 'default' }
  },
  product: {
    PENDING_AUDIT: { label: '待审核', type: 'info' },
    APPROVED: { label: '审核通过', type: 'success' },
    REJECTED: { label: '已拒绝', type: 'error' },
    BOUND: { label: '已绑定', type: 'default' },
    ASSIGNED: { label: '已分配', type: 'primary' },
    LINKED: { label: '已转链', type: 'success' },
    FOLLOWING: { label: '已转达人 CRM', type: 'warning' }
  }
}

const resolvedConfig = computed(() => statusMaps[props.scene][String(props.status || '')] || null)
const resolvedType = computed<TagType>(() => resolvedConfig.value?.type || 'default')
const resolvedLabel = computed(() => props.label || resolvedConfig.value?.label || props.status || '-')
</script>
