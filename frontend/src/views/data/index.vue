<template>
  <div class="dashboard">
    <PageHeader
      :title="pageTitle"
      :description="pageDesc"
    >
      <template #actions>
        <n-radio-group v-model:value="timeField" size="small" @update:value="loadMetrics">
          <n-radio-button value="settleTime">按结算时间</n-radio-button>
          <n-radio-button value="createTime">按创建时间</n-radio-button>
        </n-radio-group>
        <n-button type="primary" size="small" @click="$router.push('/data/orders')">
          查看完整明细
        </n-button>
      </template>
    </PageHeader>

    <div v-if="showSkeleton" class="loading-state" aria-live="polite">
      <div class="loading-copy">加载中...</div>
      <div class="metric-cards">
        <div v-for="item in 4" :key="item" class="metric-card skeleton-card">
          <n-skeleton height="88px" :sharp="false" />
        </div>
      </div>
      <div class="breakdown-section">
        <n-skeleton text :repeat="2" />
      </div>
      <div class="quick-links">
        <n-skeleton height="56px" :sharp="false" />
      </div>
    </div>

    <!-- 主指标卡片 -->
    <n-spin :show="loading && initialized">
      <template v-if="!showSkeleton">
      <div class="metric-cards">
        <div class="metric-card">
          <div class="metric-icon orders">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <path d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ metricLabels.orders }}</div>
            <div class="metric-value">{{ metrics.totalOrders ?? 0 }}</div>
            <div class="metric-trend up">较上周 +12.5%</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon amount">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ metricLabels.amount }}</div>
            <div class="metric-value">¥{{ metrics.totalAmount || '0.00' }}</div>
            <div class="metric-trend up">较上周 +8.3%</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon fee">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <circle cx="12" cy="12" r="10"/><path d="M16 8h-4a2 2 0 000 4h2a2 2 0 010 4h-5"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ metricLabels.fee }}</div>
            <div class="metric-value">¥{{ metrics.serviceFee || '0.00' }}</div>
            <div class="metric-trend up">较上周 +5.7%</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon profit">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ metricLabels.profit }}</div>
            <div class="metric-value">¥{{ metrics.grossProfit || '0.00' }}</div>
            <div class="metric-trend down">较上周 -2.1%</div>
          </div>
        </div>
      </div>

      <!-- 业绩分拆标签组 -->
      <div class="breakdown-section">
        <h3 class="section-title">{{ isChannelStaffOnly ? '我的收益分拆' : '收入分拆' }}</h3>
        <div class="breakdown-tags">
          <template v-if="isChannelStaffOnly">
            <div class="breakdown-item">
              <span class="breakdown-label">服务费收入</span>
              <span class="breakdown-value accent">¥{{ metrics.serviceFeeIncome || '0.00' }}</span>
            </div>
            <div class="breakdown-item">
              <span class="breakdown-label">渠道提成</span>
              <span class="breakdown-value accent">¥{{ metrics.channelCommission || '0.00' }}</span>
            </div>
          </template>
          <template v-else>
            <div class="breakdown-item">
              <span class="breakdown-label">招商+渠道提成</span>
              <span class="breakdown-value accent">¥{{ metrics.commission || '0.00' }}</span>
            </div>
            <div class="breakdown-item">
              <span class="breakdown-label">服务费收入</span>
              <span class="breakdown-value">¥{{ metrics.serviceFeeIncome || '0.00' }}</span>
            </div>
            <div class="breakdown-item">
              <span class="breakdown-label">技术服务费</span>
              <span class="breakdown-value">¥{{ metrics.techServiceFee || '0.00' }}</span>
            </div>
            <div class="breakdown-item">
              <span class="breakdown-label">达人分佣</span>
              <span class="breakdown-value">¥{{ metrics.talentCommission || '0.00' }}</span>
            </div>
            <div class="breakdown-item">
              <span class="breakdown-label">招商提成</span>
              <span class="breakdown-value">¥{{ metrics.bizCommission || '0.00' }}</span>
            </div>
            <div class="breakdown-item">
              <span class="breakdown-label">渠道提成</span>
              <span class="breakdown-value">¥{{ metrics.channelCommission || '0.00' }}</span>
            </div>
          </template>
        </div>
      </div>

      <!-- 快速入口 -->
      <div class="quick-links">
        <n-card size="small" class="quick-link-card" @click="$router.push('/data/orders')">
          <div class="quick-link-content">
            <span class="quick-link-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <path d="M18 20V10M12 20V4M6 20v-6"/>
              </svg>
            </span>
            <span class="quick-link-text">订单明细管理</span>
            <span class="quick-link-arrow">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
            </span>
          </div>
        </n-card>
      </div>
      </template>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import { getMetrics } from '../../api/data'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES } from '../../constants/rbac'

const message = useMessage()
const authStore = useAuthStore()
const loading = ref(false)
const initialized = ref(false)
const metrics = ref<any>({})
const timeField = ref<'settleTime' | 'createTime'>('settleTime')
const showSkeleton = computed(() => loading.value && !initialized.value)
const INITIAL_SKELETON_MIN_MS = 1200
const ROLE = ROLE_CODES

const isBizStaffOnly = computed(() => {
  const roles = authStore.roleCodes
  return roles.includes(ROLE.BIZ_STAFF) && !roles.includes(ROLE.ADMIN) && !roles.includes(ROLE.BIZ_LEADER)
})

const isChannelStaffOnly = computed(() => {
  const roles = authStore.roleCodes
  return roles.includes(ROLE.CHANNEL_STAFF) && !roles.includes(ROLE.ADMIN) && !roles.includes(ROLE.CHANNEL_LEADER)
})

const pageTitle = computed(() => {
  if (isChannelStaffOnly.value) return '我的业绩'
  if (isBizStaffOnly.value) return '我的业绩'
  return '数据看板'
})

const pageDesc = computed(() => {
  if (isChannelStaffOnly.value) return '查看我自己的订单、服务费收益和提成预估，持续跟进达人产出。'
  if (isBizStaffOnly.value) return '查看我负责商品带来的订单、服务费收益和招商提成预估。'
  return '核心经营指标一览，快速了解订单、收入与利润趋势。'
})

const metricLabels = computed(() => {
  if (isChannelStaffOnly.value) {
    return {
      orders: '我的推广订单数',
      amount: '我的订单总额',
      fee: '我的服务费收入',
      profit: '我的渠道提成'
    }
  }
  return {
    orders: '今日订单数',
    amount: '今日订单总额',
    fee: '今日服务费净收',
    profit: '今日毛利'
  }
})

const loadMetrics = async () => {
  const startedAt = Date.now()
  loading.value = true
  try {
    const params: any = { timeField: timeField.value }
    if (isChannelStaffOnly.value) params.scope = 'personal'
    const res = await getMetrics(params)
    metrics.value = res?.data || res || {}
  } catch (error: any) {
    message.warning(error?.message || '获取指标异常')
  } finally {
    const elapsed = Date.now() - startedAt
    const remaining = INITIAL_SKELETON_MIN_MS - elapsed
    if (remaining > 0) {
      await new Promise((resolve) => setTimeout(resolve, remaining))
    }
    initialized.value = true
    loading.value = false
  }
}

onMounted(() => {
  loadMetrics()
})
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
  padding: var(--spacing-xl);
}

:deep(.n-page-header-extra) {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.loading-state {
  display: grid;
  gap: var(--spacing-md);
}

.loading-copy {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.skeleton-card :deep(.n-skeleton) {
  width: 100%;
}

/* ---- 主指标卡片 ---- */
.metric-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.metric-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 20px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--transition-normal), transform var(--transition-normal);
}

.metric-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.metric-icon.orders {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.metric-icon.amount {
  background: var(--color-info-light);
  color: var(--color-info);
}

.metric-icon.fee {
  background: var(--color-success-light);
  color: var(--color-success);
}

.metric-icon.profit {
  background: var(--color-warning-light);
  color: var(--color-warning);
}

.metric-body {
  flex: 1;
  min-width: 0;
}

.metric-label {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.metric-value {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
  line-height: 1.2;
}

.metric-trend {
  font-size: var(--text-xs);
  font-weight: 500;
}

.metric-trend.up {
  color: var(--color-success);
}

.metric-trend.down {
  color: var(--color-danger);
}

/* ---- 分拆标签 ---- */
.breakdown-section {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 20px 24px;
  box-shadow: var(--shadow-card);
  margin-bottom: var(--spacing-md);
}

.section-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 16px;
}

.breakdown-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.breakdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--bg-page);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
}

.breakdown-label {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.breakdown-value {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}

.breakdown-value.accent {
  color: var(--color-primary);
}

/* ---- 快速入口 ---- */
.quick-links {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--spacing-md);
}

.quick-link-card {
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: box-shadow var(--transition-normal);
}

.quick-link-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.quick-link-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quick-link-icon {
  font-size: var(--text-xl);
}

.quick-link-text {
  flex: 1;
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--text-primary);
}

.quick-link-arrow {
  color: var(--text-muted);
  font-size: var(--text-base);
}
</style>
