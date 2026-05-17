<template>
  <div class="dashboard" data-testid="data-dashboard-page">
    <PageHeader
      :title="pageTitle"
      :description="pageDesc"
    >
      <template #actions>
        <n-radio-group v-model:value="timeField" size="small" data-testid="dashboard-time-field" @update:value="loadMetrics">
          <n-radio-button value="createTime">按创建时间</n-radio-button>
          <n-radio-button value="settleTime">按结算时间</n-radio-button>
        </n-radio-group>
        <n-button type="primary" size="small" data-testid="dashboard-orders-link" @click="$router.push('/data/orders')">
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
      <div class="metric-cards" data-testid="dashboard-metric-cards">
        <div class="metric-card" data-testid="dashboard-metric-orders">
          <div class="metric-icon orders">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <path d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ metricLabels.orders }}</div>
            <div class="metric-value">{{ displayOrderCount }}</div>
            <div class="metric-trend" :class="ordersTrendClass">{{ ordersTrendText }}</div>
          </div>
        </div>

        <div class="metric-card" data-testid="dashboard-metric-amount">
          <div class="metric-icon amount">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ metricLabels.amount }}</div>
            <div class="metric-value">¥{{ displayGmv }}</div>
            <div class="metric-trend" :class="amountTrendClass">{{ amountTrendText }}</div>
          </div>
        </div>

        <div class="metric-card" data-testid="dashboard-metric-fee">
          <div class="metric-icon fee">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <circle cx="12" cy="12" r="10"/><path d="M16 8h-4a2 2 0 000 4h2a2 2 0 010 4h-5"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ metricLabels.fee }}</div>
            <div class="metric-value">¥{{ metrics.serviceFee || '0.00' }}</div>
            <div class="metric-trend neutral">{{ metricScopeText }}</div>
          </div>
        </div>

        <div class="metric-card" data-testid="dashboard-metric-profit">
          <div class="metric-icon profit">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ metricLabels.profit }}</div>
            <div class="metric-value">¥{{ metrics.grossProfit || '0.00' }}</div>
            <div class="metric-trend neutral">{{ metricScopeText }}</div>
          </div>
        </div>
      </div>

      <!-- 近 7 日真实趋势 -->
      <div v-if="trendPoints.length" class="trend-section" data-testid="dashboard-real-trend">
        <div class="trend-header">
          <h3 class="section-title">近 7 日真实趋势</h3>
          <span class="trend-scope">{{ timeScopeDescription }}</span>
        </div>

        <div class="trend-summary-list">
          <div v-for="item in trendSummaryItems" :key="item.label" class="trend-summary-item">
            <span class="trend-summary-label">{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div
          ref="trendChartRef"
          class="trend-chart"
          data-testid="dashboard-real-trend-chart"
          aria-label="近 7 日订单数与 GMV 趋势图"
        ></div>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer
])

import PageHeader from '../../components/PageHeader.vue'
import { getMetrics } from '../../api/data'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES } from '../../constants/rbac'

const message = useMessage()
const authStore = useAuthStore()
const loading = ref(false)
const initialized = ref(false)
const metrics = ref<any>({})
const timeField = ref<'settleTime' | 'createTime'>('createTime')
const trendChartRef = ref<HTMLDivElement | null>(null)
type TrendChartInstance = {
  setOption: (option: Record<string, unknown>, notMerge?: boolean) => void
  resize: () => void
  dispose: () => void
}

let trendChart: TrendChartInstance | null = null
const showSkeleton = computed(() => loading.value && !initialized.value)
const INITIAL_SKELETON_MIN_MS = 0
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

interface TrendPoint {
  date: string
  dateLabel: string
  orderCount: number
  gmv: number
  gmvText: string
}

const toNumber = (value: unknown) => {
  const parsed = Number(value ?? 0)
  return Number.isFinite(parsed) ? parsed : 0
}

const formatAmount = (value: number) => toNumber(value).toFixed(2)

const displayOrderCount = computed(() => toNumber(metrics.value?.todayOrderCount ?? metrics.value?.totalOrders))

const displayGmv = computed(() => formatAmount(toNumber(metrics.value?.todayGmv ?? metrics.value?.totalAmount)))

const timeScopeLabel = computed(() => timeField.value === 'createTime' ? '按创建时间统计' : '按结算时间统计')

const metricScopeText = computed(() => (
  timeField.value === 'createTime' ? '统计已同步真实订单' : '仅统计已结算订单'
))

const timeScopeDescription = computed(() => (
  timeField.value === 'createTime'
    ? '默认按订单创建时间统计，适合运营日报和 real-pre 回流复核'
    : '按结算时间统计，仅纳入已结算订单，适合收益复核'
))

const trendPoints = computed<TrendPoint[]>(() => {
  const rows = Array.isArray(metrics.value?.trend7d) ? metrics.value.trend7d : []
  return rows.map((row: any) => {
    const date = String(row?.date || '')
    const gmv = toNumber(row?.gmv ?? row?.orderAmount ?? row?.amount)
    return {
      date,
      dateLabel: date ? date.slice(5) : '-',
      orderCount: toNumber(row?.orderCount),
      gmv,
      gmvText: formatAmount(gmv)
    }
  })
})

const latestTrendPoint = computed(() => {
  const rows = trendPoints.value
  return rows.length ? rows[rows.length - 1] : null
})

const previousTrendPoint = computed(() => {
  const rows = trendPoints.value
  return rows.length > 1 ? rows[rows.length - 2] : null
})

const formatSignedCount = (delta: number) => `${delta > 0 ? '+' : ''}${Math.trunc(delta)} 单`
const formatSignedAmount = (delta: number) => `${delta > 0 ? '+' : ''}${formatAmount(delta)} 元`

const buildDeltaText = (
  current: number,
  previous: number | null,
  formatter: (delta: number) => string
) => {
  if (previous === null) return `${timeScopeLabel.value} · 暂无昨日对比`
  const delta = current - previous
  if (delta === 0) return '较昨日持平'
  if (previous === 0) return `较昨日 ${formatter(delta)}`
  const percent = (delta / Math.abs(previous)) * 100
  return `较昨日 ${formatter(delta)} (${delta > 0 ? '+' : ''}${percent.toFixed(1)}%)`
}

const buildTrendClass = (current: number, previous: number | null) => {
  if (previous === null) return 'neutral'
  const delta = current - previous
  if (delta > 0) return 'up'
  if (delta < 0) return 'down'
  return 'flat'
}

const ordersTrendText = computed(() => buildDeltaText(
  latestTrendPoint.value?.orderCount ?? 0,
  previousTrendPoint.value?.orderCount ?? null,
  formatSignedCount
))

const amountTrendText = computed(() => buildDeltaText(
  latestTrendPoint.value?.gmv ?? 0,
  previousTrendPoint.value?.gmv ?? null,
  formatSignedAmount
))

const ordersTrendClass = computed(() => buildTrendClass(
  latestTrendPoint.value?.orderCount ?? 0,
  previousTrendPoint.value?.orderCount ?? null
))

const amountTrendClass = computed(() => buildTrendClass(
  latestTrendPoint.value?.gmv ?? 0,
  previousTrendPoint.value?.gmv ?? null
))

const trendTotalOrders = computed(() => trendPoints.value.reduce((total, item) => total + item.orderCount, 0))

const trendTotalGmv = computed(() => trendPoints.value.reduce((total, item) => total + item.gmv, 0))

const peakTrendPoint = computed(() => (
  trendPoints.value.reduce<TrendPoint | null>((peak, item) => {
    if (!peak || item.orderCount > peak.orderCount) return item
    return peak
  }, null)
))

const trendSummaryItems = computed(() => [
  { label: '7 日订单', value: `${trendTotalOrders.value} 单` },
  { label: '7 日 GMV', value: `¥${formatAmount(trendTotalGmv.value)}` },
  {
    label: '峰值日期',
    value: peakTrendPoint.value
      ? `${peakTrendPoint.value.dateLabel} · ${peakTrendPoint.value.orderCount} 单`
      : '-'
  }
])

const resizeTrendChart = () => {
  trendChart?.resize()
}

const renderTrendChart = async () => {
  await nextTick()
  if (!trendChartRef.value || !trendPoints.value.length) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value) as unknown as TrendChartInstance
  }

  trendChart.setOption({
    color: ['#2563EB', '#16A34A'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      valueFormatter: (value: number | string) => String(value)
    },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748B' }
    },
    grid: {
      top: 42,
      right: 56,
      bottom: 28,
      left: 48
    },
    xAxis: {
      type: 'category',
      data: trendPoints.value.map((item) => item.dateLabel),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#CBD5E1' } },
      axisLabel: { color: '#64748B' }
    },
    yAxis: [
      {
        type: 'value',
        name: '订单',
        minInterval: 1,
        axisLabel: { color: '#64748B' },
        splitLine: { lineStyle: { color: '#E2E8F0' } }
      },
      {
        type: 'value',
        name: 'GMV',
        axisLabel: {
          color: '#64748B',
          formatter: (value: number) => `¥${value}`
        },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '订单数',
        type: 'bar',
        data: trendPoints.value.map((item) => item.orderCount),
        barMaxWidth: 28,
        itemStyle: { borderRadius: [4, 4, 0, 0] }
      },
      {
        name: 'GMV',
        type: 'line',
        yAxisIndex: 1,
        data: trendPoints.value.map((item) => Number(item.gmv.toFixed(2))),
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: 3 }
      }
    ]
  }, true)
}

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
    void renderTrendChart()
  }
}

onMounted(() => {
  loadMetrics()
  window.addEventListener('resize', resizeTrendChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeTrendChart)
  trendChart?.dispose()
  trendChart = null
})

watch(trendPoints, () => {
  renderTrendChart()
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

.metric-trend.flat,
.metric-trend.neutral {
  color: var(--text-muted);
}

/* ---- 近 7 日趋势 ---- */
.trend-section {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 20px 24px;
  box-shadow: var(--shadow-card);
  margin-bottom: var(--spacing-md);
}

.trend-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.trend-scope {
  max-width: 520px;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
  text-align: right;
}

.trend-summary-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.trend-summary-item {
  min-width: 0;
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-page);
}

.trend-summary-label {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.trend-summary-item strong {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.trend-chart {
  width: 100%;
  height: 320px;
  min-height: 320px;
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

@media (max-width: 768px) {
  .trend-header {
    display: grid;
  }

  .trend-scope {
    text-align: left;
  }

  .trend-summary-list {
    grid-template-columns: 1fr;
  }

  .trend-chart {
    height: 300px;
    min-height: 300px;
  }
}
</style>
