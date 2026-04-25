<template>
  <div class="product-card">
    <!-- 商品图片 -->
    <div class="card-image">
      <img
        v-if="imageUrl"
        :src="imageUrl"
        :alt="title"
        @error="(e: Event) => ((e.target as HTMLImageElement).style.display = 'none')"
      />
      <div class="card-image-fallback" v-if="!imageUrl || imageFailed">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="32" height="32">
          <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/>
        </svg>
      </div>
      <!-- 状态角标 -->
      <span v-if="badge" class="card-badge" :class="badgeClass">{{ badge }}</span>
    </div>

    <!-- 商品信息 -->
    <div class="card-body">
      <h4 class="card-title" :title="title">{{ title }}</h4>
      <div class="card-meta">
        <span v-if="productId" class="card-id">ID: {{ productId }}</span>
        <span v-if="subtitle" class="card-subtitle">{{ subtitle }}</span>
      </div>

      <!-- 指标行 -->
      <div v-if="metrics.length" class="card-metrics">
        <div v-for="m in metrics" :key="m.label" class="metric-item">
          <span class="metric-label">{{ m.label }}</span>
          <span class="metric-val" :class="{ primary: m.highlight }">{{ m.value }}</span>
        </div>
      </div>

      <!-- 底部操作 -->
      <div class="card-footer">
        <div class="card-stats">
          <slot name="stats" />
        </div>
        <div class="card-actions">
          <slot name="actions" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  imageUrl?: string
  title: string
  productId?: string
  subtitle?: string
  badge?: string
  badgeClass?: string
  metrics: { label: string; value: string; highlight?: boolean }[]
}>()

const imageFailed = ref(false)

watch(() => props.imageUrl, () => {
  imageFailed.value = false
})
</script>

<style scoped>
.product-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--transition-normal), transform var(--transition-normal);
}

.product-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.card-image {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background: linear-gradient(135deg, #f8f9fb 0%, #eef0f4 100%);
  overflow: hidden;
}

.card-image img {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  object-fit: cover;
}

.card-image-fallback {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.card-badge {
  position: absolute;
  top: 8px; left: 8px;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 600;
  color: white;
}

.card-body {
  padding: 14px 16px;
}

.card-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 6px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.card-id {
  font-family: var(--font-mono);
}

.card-metrics {
  display: flex;
  gap: 16px;
  padding: 10px 0;
  border-top: 1px solid var(--border-color-light);
  border-bottom: 1px solid var(--border-color-light);
  margin-bottom: 10px;
}

.metric-item {
  flex: 1;
  text-align: center;
}

.metric-label {
  display: block;
  font-size: var(--text-xs);
  color: var(--text-muted);
  margin-bottom: 2px;
}

.metric-val {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}

.metric-val.primary {
  color: var(--color-primary);
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-stats {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.card-actions {
  display: flex;
  gap: 2px;
}
</style>
