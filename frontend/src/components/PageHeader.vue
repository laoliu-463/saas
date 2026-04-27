<template>
  <div class="page-header">
    <div class="page-copy">
      <div class="title-row">
        <h1 class="page-title">{{ title }}</h1>
        <div v-if="$slots.meta" class="page-meta">
          <slot name="meta" />
        </div>
      </div>
      <p v-if="description" class="page-description">{{ description }}</p>
    </div>

    <div v-if="$slots.actions" class="page-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-copy {
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.25;
  font-weight: 700;
  color: var(--text-primary);
}

.page-description {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.page-meta,
.page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
  }

  .page-actions {
    width: 100%;
  }
}
</style>
