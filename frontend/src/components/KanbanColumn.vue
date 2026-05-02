<template>
  <div class="kanban-column" :class="`col-${status}`">
    <div class="column-header">
      <div class="header-left">
        <span class="status-dot" :style="{ background: dotColor }" />
        <span class="column-title">{{ title }}</span>
      </div>
      <n-badge :value="cards.length" :max="99" type="info" />
    </div>

    <div class="column-body">
      <template v-if="cards.length">
        <slot v-for="card in cards" :key="card.id" :card="card" />
      </template>
      <div v-else class="empty-state">
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
          <path d="M20 6L9 17l-5-5"/>
        </svg>
        <span class="empty-text">暂无数据</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  status: string
  title: string
  dotColor: string
  cards: any[]
}>()
</script>

<style scoped>
.kanban-column {
  display: flex;
  flex-direction: column;
  min-width: 260px;
  max-width: 340px;
  flex: 1;
  background: var(--bg-page);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color-light);
  overflow: hidden;
}

.column-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color-light);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.column-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}

.column-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 200px;
  max-height: calc(100vh - 240px);
}

.column-body::-webkit-scrollbar {
  width: 4px;
}

.column-body::-webkit-scrollbar-track {
  background: transparent;
}

.column-body::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 2px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 16px;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 24px;
  opacity: 0.4;
}

.empty-text {
  font-size: var(--text-sm);
}
</style>
