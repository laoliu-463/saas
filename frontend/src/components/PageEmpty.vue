<template>
  <div class="page-empty">
    <div class="empty-icon">{{ icon }}</div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="description" class="empty-description">{{ description }}</div>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  icon?: string
}>()
</script>

<style scoped>
.page-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 220px;
  padding: 32px 20px;
  text-align: center;
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 36px;
  line-height: 1;
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
