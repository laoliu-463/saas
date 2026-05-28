<!--
  PageHeader - 页面头部标题组件

  用途：提供统一的页面头部布局，包含标题行、描述文案和操作按钮区。
  标题前有品牌色竖条装饰，整体为左右分布布局（标题区 + 操作区）。

  Props:
    - title: 页面标题，必填。
    - description: 页面描述文案，可选。

  Slots:
    - meta: 标题行右侧的元信息区域（如统计数字、标签等）。
    - actions: 右侧操作区（如"新建"按钮、筛选器等）。

  使用场景：各业务页面顶部的统一标题栏。
-->
<template>
  <div class="page-header">
    <!-- 左侧：标题 + 描述文案区域 -->
    <div class="page-copy">
      <div class="title-row">
        <h1 class="page-title">{{ title }}</h1>
        <!-- 标题右侧的元信息插槽（如统计标签） -->
        <div v-if="$slots.meta" class="page-meta">
          <slot name="meta" />
        </div>
      </div>
      <!-- 页面描述文案，标题下方小字显示 -->
      <p v-if="description" class="page-description">{{ description }}</p>
    </div>

    <!-- 右侧操作区插槽（如新建按钮、批量操作按钮等） -->
    <div v-if="$slots.actions" class="page-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /** 页面主标题，必填 */
  title: string
  /** 页面描述/副标题文案，可选 */
  description?: string
}>()
</script>

<style scoped>
/* 页面头部：左右分布布局，底部有细分隔线 */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--content-gap);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
}

/* 标题文案容器，防止溢出 */
.page-copy {
  min-width: 0;
}

/* 标题行：标题与元信息横向排列，允许换行 */
.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

/* 页面主标题样式：大号加粗，紧凑字间距 */
.page-title {
  margin: 0;
  font-size: var(--text-2xl);
  line-height: 1.3;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

/* 标题左侧的品牌色竖条装饰，使用伪元素实现 */
.page-title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 1.1em;
  margin-right: 10px;
  vertical-align: -0.12em;
  border-radius: var(--radius-full);
  background: var(--color-primary-gradient);
}

/* 页面描述文案：小字，缩进与竖条对齐 */
.page-description {
  margin: 6px 0 0 14px;
  font-size: var(--text-sm);
  line-height: 1.55;
  color: var(--text-secondary);
}

/* 元信息区和操作区共用样式：flex 横排，允许换行 */
.page-meta,
.page-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

/* 响应式：窄屏（< 960px）时改为纵向堆叠布局 */
@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
    border-bottom: none;
    padding-bottom: 0;
  }

  .page-actions {
    width: 100%;
  }
}
</style>
