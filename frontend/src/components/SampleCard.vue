<template>
  <div class="sample-card" :class="urgencyClass">
    <div class="card-head">
      <span class="talent-name">{{ card.talentName || '未命名达人' }}</span>
      <span v-if="urgencyLevel === 'danger'" class="urgency-dot danger" title="即将超时" />
      <span v-else-if="urgencyLevel === 'warning'" class="urgency-dot warning" title="接近超时" />
    </div>

    <div class="card-body">
      <div class="card-field">
        <span class="field-label">商品</span>
        <span class="field-value">{{ card.productName || '-' }}</span>
      </div>
      <div class="card-field">
        <span class="field-label">渠道</span>
        <span class="field-value">{{ card.channelUserName || '-' }}</span>
      </div>
      <div v-if="card.trackingNo" class="card-field">
        <span class="field-label">快递</span>
        <span class="field-value tracking">{{ card.trackingNo }}</span>
      </div>
      <div v-if="card.rejectReason" class="card-field reject">
        <span class="field-label">拒绝</span>
        <span class="field-value">{{ card.rejectReason }}</span>
      </div>
    </div>

    <div class="card-footer">
      <span class="time-elapsed" :class="urgencyClass">
        <svg class="clock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
          <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
        </svg>
        {{ timeElapsedText }}
      </span>
      <span class="card-quantity">x{{ card.quantity || 1 }}</span>
    </div>

    <div v-if="hasActions" class="card-actions">
      <n-button v-if="card.status === 'PENDING_AUDIT'" size="tiny" type="primary" quaternary @click.stop="$emit('approve', card)">
        通过
      </n-button>
      <n-button v-if="card.status === 'PENDING_AUDIT'" size="tiny" type="error" quaternary @click.stop="$emit('reject', card)">
        拒绝
      </n-button>
      <n-button v-if="card.status === 'PENDING_SHIP'" size="tiny" type="info" quaternary @click.stop="$emit('ship', card)">
        发货
      </n-button>
      <n-button v-if="card.status === 'SHIPPED'" size="tiny" type="success" quaternary @click.stop="$emit('sign', card)">
        签收
      </n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface SampleBoardCard {
  id: string
  requestNo: string
  talentName: string
  productId: string
  productName: string
  quantity: number
  channelUserName: string
  trackingNo: string
  rejectReason: string
  remark: string
  status: string
  createTime: string
  stateEnterTime: string
}

type UrgencyLevel = 'normal' | 'warning' | 'danger'

const props = defineProps<{
  card: SampleBoardCard
}>()

defineEmits<{
  approve: [card: SampleBoardCard]
  reject: [card: SampleBoardCard]
  ship: [card: SampleBoardCard]
  sign: [card: SampleBoardCard]
}>()

const TIMEOUT_HOURS: Record<string, number> = {
  PENDING_AUDIT: 24,
  PENDING_SHIP: 48,
  SHIPPED: 72,
  PENDING_TASK: 168
}

function hoursSince(dateStr: string | null | undefined): number {
  if (!dateStr) return 0
  const diff = Date.now() - new Date(dateStr).getTime()
  return diff / (1000 * 60 * 60)
}

const urgencyLevel = computed<UrgencyLevel>(() => {
  const status = props.card.status
  const limit = TIMEOUT_HOURS[status]
  if (!limit) return 'normal'

  const elapsed = hoursSince(props.card.stateEnterTime)
  if (elapsed >= limit) return 'danger'
  if (elapsed >= limit * 0.75) return 'warning'
  return 'normal'
})

const urgencyClass = computed(() => `urgency-${urgencyLevel.value}`)

const hasActions = computed(() => {
  return ['PENDING_AUDIT', 'PENDING_SHIP', 'SHIPPED'].includes(props.card.status)
})

const timeElapsedText = computed(() => {
  const enterTime = props.card.stateEnterTime
  if (!enterTime) return '-'

  const elapsed = hoursSince(enterTime)
  if (elapsed < 1) return `${Math.round(elapsed * 60)}分钟`
  if (elapsed < 24) return `${Math.round(elapsed)}小时`
  return `${Math.round(elapsed / 24)}天`
})
</script>

<style scoped>
.sample-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px;
  cursor: default;
  transition: box-shadow var(--transition-fast), border-color var(--transition-fast);
  position: relative;
}

.sample-card:hover {
  box-shadow: var(--shadow-card-hover);
  border-color: var(--border-color-light);
}

.sample-card.urgency-warning {
  border-left: 3px solid var(--color-warning);
}

.sample-card.urgency-danger {
  border-left: 3px solid var(--color-danger);
  background: var(--color-danger-light);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.talent-name {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.urgency-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.urgency-dot.warning {
  background: var(--color-warning);
}

.urgency-dot.danger {
  background: var(--color-danger);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.card-field {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: var(--text-sm);
}

.field-label {
  color: var(--text-muted);
  flex-shrink: 0;
  min-width: 28px;
}

.field-value {
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-value.tracking {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
}

.card-field.reject .field-value {
  color: var(--color-danger);
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--text-xs);
}

.time-elapsed {
  color: var(--text-muted);
}

.time-elapsed.urgency-warning {
  color: var(--color-warning);
  font-weight: 600;
}

.time-elapsed.urgency-danger {
  color: var(--color-danger);
  font-weight: 600;
}

.clock-icon {
  margin-right: 2px;
}

.card-quantity {
  color: var(--text-muted);
  font-weight: 600;
}

.card-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color-light);
}
</style>
