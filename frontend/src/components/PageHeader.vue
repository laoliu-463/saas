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
  gap: var(--spacing-md);
  margin-bottom: var(--content-gap);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
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
  font-size: var(--text-2xl);
  line-height: 1.3;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

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

.page-description {
  margin: 6px 0 0 14px;
  font-size: var(--text-sm);
  line-height: 1.55;
  color: var(--text-secondary);
}

.page-meta,
.page-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

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
