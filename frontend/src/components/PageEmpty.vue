<!--
  PageEmpty - 空状态占位组件

  用途：当列表无数据、搜索无结果等场景下展示统一的空状态提示。
  提供图标、标题、描述文案和操作按钮的组合展示。

  Props:
    - title: 空状态主标题，必填。如"暂无数据"、"没有搜索结果"。
    - description: 补充说明文案，可选。
    - icon: 图标字符，可选。不传时显示默认的 3D 盒子 SVG 图标。

  Slots:
    - icon: 自定义图标插槽，覆盖 icon prop 和默认 SVG。
    - action: 操作区插槽，通常放置按钮（如"创建新记录"）。

  使用场景：各页面列表为空时的统一空状态展示。
-->
<template>
  <div class="page-empty">
    <!-- 图标区域：优先使用 icon 插槽，其次使用 icon prop，最后使用默认 SVG -->
    <div class="empty-icon">
      <slot name="icon">
        <svg v-if="!icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="36" height="36">
          <path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/>
        </svg>
        <span v-else>{{ icon }}</span>
      </slot>
    </div>
    <!-- 主标题 -->
    <div class="empty-title">{{ title }}</div>
    <!-- 可选的补充描述 -->
    <div v-if="description" class="empty-description">{{ description }}</div>
    <!-- 操作区插槽，通常放置引导按钮 -->
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /** 空状态主标题，必填 */
  title: string
  /** 补充说明文案，可选 */
  description?: string
  /** 图标字符（如 emoji），可选；不传时显示默认 SVG 图标 */
  icon?: string
}>()
</script>

<style scoped>
/* 空状态容器：垂直居中布局，虚线边框 + 柔和背景色，营造空旷感 */
.page-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 220px;
  padding: 36px 24px;
  text-align: center;
  color: var(--text-secondary);
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-card-muted);
}

.empty-icon {
  font-size: 36px;
  line-height: 1;
  color: var(--text-tertiary);
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.empty-description {
  max-width: 420px;
  font-size: 14px;
  line-height: 1.6;
}

.empty-action {
  margin-top: 8px;
}
</style>
