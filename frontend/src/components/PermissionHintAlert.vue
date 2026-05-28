<!--
  PermissionHintAlert - 权限不足提示组件

  用途：当用户权限不足以访问某功能时，显示一个警告级别的全局提示条。
  支持两种数据来源：外部通过 modelValue prop 传入，或从全局权限提示 store 读取。

  Props:
    - modelValue: 自定义提示文案，可选。优先级高于全局 store 中的提示。

  数据流：优先使用 prop 传入的值，为空时回退到 globalPermissionHint 全局状态。
  当 hint 为空时不渲染任何内容（v-if 控制）。

  使用场景：页面头部、列表区域等权限受限时的全局提示。
-->
<template>
  <!-- 仅有提示内容时才渲染警告提示条 -->
  <n-alert
    v-if="hint"
    type="warning"
    :bordered="false"
    class="permission-hint-alert"
    data-testid="global-permission-hint"
  >
    {{ hint }}
  </n-alert>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { globalPermissionHint } from '../stores/permissionHint'

const props = defineProps<{ /** 自定义提示文案，可选，优先级高于全局 store */ modelValue?: string }>()

/**
 * 最终显示的提示文案
 * 优先使用 prop 传入值，为空时回退到全局权限提示 store 中的值
 */
const hint = computed(() => props.modelValue ?? globalPermissionHint.value)
</script>

<style scoped>
/* 提示条底部间距，使用 CSS 变量方便统一调整 */
.permission-hint-alert {
  margin-bottom: var(--content-gap, 12px);
}
</style>
