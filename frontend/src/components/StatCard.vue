<template>
  <div class="stat-card app-metric-card" :class="{ clickable: !!$attrs.onClick }">
    <div class="stat-icon" :class="iconClass">
      <slot name="icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
        </svg>
      </slot>
    </div>
    <div class="stat-body">
      <div class="stat-label">{{ label }}</div>
      <div class="stat-value">{{ value }}</div>
      <div v-if="trend" class="stat-trend" :class="trend > 0 ? 'up' : 'down'">
        {{ trend > 0 ? '↑' : '↓' }} {{ Math.abs(trend) }}%
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  trend?: number
  iconClass?: 'primary' | 'info' | 'success' | 'warning' | 'danger'
}>()
</script>

<style scoped>
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon.primary { background: var(--color-primary-light); color: var(--color-primary); }
.stat-icon.info { background: var(--color-info-light); color: var(--color-info); }
.stat-icon.success { background: var(--color-success-light); color: var(--color-success); }
.stat-icon.warning { background: var(--color-warning-light); color: var(--color-warning); }
.stat-icon.danger { background: var(--color-danger-light); color: var(--color-danger); }

.stat-body { flex: 1; min-width: 0; }

.stat-label {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.stat-value {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
  line-height: 1.2;
}

.stat-trend {
  font-size: var(--text-xs);
  font-weight: 500;
}

.stat-trend.up { color: var(--color-success); }
.stat-trend.down { color: var(--color-danger); }
</style>
