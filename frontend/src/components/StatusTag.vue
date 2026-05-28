<!--
  StatusTag - 状态标签组件

  用途：根据业务场景和状态码展示对应的彩色标签，统一全站状态展示风格。
  支持三种业务场景（scene）：业绩归因(attribution)、寄样(sample)、商品(product)。

  Props:
    - scene: 业务场景，必填。决定使用哪一套状态映射表。
    - status: 状态码字符串，可为空。用于在映射表中查找对应的标签配置。
    - label: 自定义显示文本，可选。优先级高于映射表中的 label。

  使用场景：订单列表、寄样管理、商品库等页面中的状态列展示。
-->
<template>
  <!-- Naive UI 的 Tag 组件，圆角小尺寸，颜色由 resolvedType 计算属性决定 -->
  <n-tag :type="resolvedType" size="small" round>
    {{ resolvedLabel }}
  </n-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/** 业务场景类型：业绩归因 / 寄样 / 商品 */
type Scene = 'attribution' | 'sample' | 'product'
/** Naive UI Tag 组件支持的颜色类型 */
type TagType = 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error'

const props = defineProps<{
  /** 状态码，如 'ATTRIBUTED'、'PENDING_AUDIT' 等，可为空 */
  status?: string | null
  /** 业务场景，决定使用哪一套状态映射表 */
  scene: Scene
  /** 自定义标签文本，优先级高于映射表中的 label */
  label?: string
}>()

/**
 * 三大业务场景的状态映射表
 * 每个场景定义了状态码 -> { 标签文本, 颜色类型 } 的映射关系
 * - attribution: 业绩归因状态（已确认、待排查、部分归因、同步失败）
 * - sample: 寄样流程状态（待审核 -> 待发货 -> 快递中 -> 待交作业 -> 已完成/已拒绝/已关闭）
 * - product: 商品状态（待审核、审核通过、已拒绝、已绑定、已分配、已转链等）
 */
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

/** 根据 scene 和 status 从映射表中查找对应的配置项，未命中返回 null */
const resolvedConfig = computed(() => statusMaps[props.scene][String(props.status || '')] || null)
/** 最终的标签颜色类型，未命中时降级为 'default' */
const resolvedType = computed<TagType>(() => resolvedConfig.value?.type || 'default')
/** 最终的标签文本：优先使用自定义 label，其次映射表 label，再降级为原始 status，最后显示 '-' */
const resolvedLabel = computed(() => props.label || resolvedConfig.value?.label || props.status || '-')
</script>
