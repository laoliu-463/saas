<!--
  Data Dashboard — 数据看板页（真实归因双轨指标中心）
  用途：展示双轨指标（创建时间轨 vs 结算时间轨）、4 项核心指标卡片、
        近 7 日 ECharts 趋势图（柱状+折线）、业绩双轨汇总、收入分拆标签组。
  双轨说明：
    - 创建时间轨（createTime）：覆盖已同步订单，适合运营日报与 real-pre 回流复核。
    - 结算时间轨（settleTime）：仅纳入已结算订单，适合收益复核。
  角色适配：
    - ADMIN / BIZ_LEADER / CHANNEL_LEADER：团队维度指标（今日订单数 / GMV / 提成）
    - BIZ_STAFF：个人维度"我的订单数 / 我的订单总额"
    - CHANNEL_STAFF：个人维度"我的推广订单数 / 我的订单总额"
  数据来源：
    - getMetrics API → resolveDualTrackMetrics 解包双轨数据
    - getPerformanceSummary API → 业绩域双轨汇总
-->
<template>
  <div class="dashboard app-page" data-testid="data-dashboard-page">
    <!-- 页面标题栏：根据角色动态生成标题 + 双轨切换 + 查看明细入口 -->
    <PageHeader
      :title="pageTitle"
      :description="pageDesc"
    >
      <template #actions>
        <!-- 双轨口径切换：createTime（创建时间） / settleTime（结算时间） -->
        <n-radio-group v-model:value="timeField" size="small" data-testid="dashboard-time-field">
          <n-radio-button value="createTime">按创建时间</n-radio-button>
          <n-radio-button value="settleTime">按结算时间</n-radio-button>
        </n-radio-group>
        <n-button type="primary" size="small" data-testid="dashboard-orders-link" @click="goToOrderDetails">
          查看完整明细
        </n-button>
      </template>
    </PageHeader>

    <!-- 双轨概览条：展示创建轨与结算轨各自的核心数据，点击切换当前激活轨道 -->
    <div
      v-if="initialized"
      class="dual-track-bar app-summary-bar"
      data-testid="dashboard-dual-track"
      role="tablist"
      aria-label="订单时间双轨口径"
    >
      <!-- 创建时间轨：覆盖已同步订单，适合运营日报 -->
      <button
        type="button"
        class="dual-track-item"
        :class="{ active: timeField === 'createTime' }"
        data-testid="dashboard-dual-track-create"
        role="tab"
        :aria-selected="timeField === 'createTime'"
        @click="switchTimeField('createTime')"
      >
        <span class="dual-track-title">创建时间轨</span>
        <span class="dual-track-desc">运营日报 · 已同步订单</span>
        <span class="dual-track-metrics">
          <strong>{{ formatTrackOrders(dualTrackCreate) }}</strong>
          <span class="dual-track-sep">·</span>
          <strong>¥{{ formatTrackGmv(dualTrackCreate) }}</strong>
        </span>
      </button>
      <!-- 结算时间轨：仅纳入已结算订单，适合收益复核 -->
      <button
        type="button"
        class="dual-track-item"
        :class="{ active: timeField === 'settleTime' }"
        data-testid="dashboard-dual-track-settle"
        role="tab"
        :aria-selected="timeField === 'settleTime'"
        @click="switchTimeField('settleTime')"
      >
        <span class="dual-track-title">结算时间轨</span>
        <span class="dual-track-desc">收益复核 · 仅已结算订单</span>
        <span class="dual-track-metrics">
          <strong>{{ formatTrackOrders(dualTrackSettle) }}</strong>
          <span class="dual-track-sep">·</span>
          <strong>¥{{ formatTrackGmv(dualTrackSettle) }}</strong>
        </span>
      </button>
      <!-- 双轨差额提示：当创建轨有单但结算轨无单时，提示订单尚未结算 -->
      <div v-if="dualTrackGapHint" class="dual-track-hint" data-testid="dashboard-dual-track-hint">
        {{ dualTrackGapHint }}
      </div>
    </div>

    <!-- 骨架屏：首次加载中且未初始化时展示占位骨架 -->
    <div v-if="showSkeleton" class="loading-state" aria-live="polite">
      <div class="loading-copy">加载中...</div>
      <div class="metric-cards">
        <div v-for="item in 4" :key="item" class="metric-card skeleton-card">
          <n-skeleton height="88px" :sharp="false" />
        </div>
      </div>
      <div class="breakdown-section app-section-panel">
        <n-skeleton text :repeat="2" />
      </div>
      <div class="quick-links">
        <n-skeleton height="56px" :sharp="false" />
      </div>
    </div>

    <!-- 主指标卡片 -->
    <n-spin :show="delayedLoading && initialized">
      <template v-if="!showSkeleton">
      <!-- 当前轨道标识条：显示当前选择的轨道名称及统计口径说明 -->
      <div class="metric-scope-banner" data-testid="dashboard-active-time-scope">
        <span class="metric-scope-badge">{{ activeTrackBadge }}</span>
        <span class="metric-scope-copy">{{ timeScopeDescription }}</span>
      </div>

      <!-- 空数据提示：real-pre 环境无数据时引导用户完成抖店授权 -->
      <n-alert
        v-if="showEmptyDataHint"
        type="warning"
        :bordered="false"
        class="dashboard-empty-hint"
        data-testid="dashboard-empty-data-hint"
      >
        {{ emptyDataHint }}
      </n-alert>

      <!-- 核心指标卡片组：4 列网格，每张卡片含图标 + 数值 + 较昨日趋势，点击跳转订单明细 -->
      <div class="metric-cards" data-testid="dashboard-metric-cards">
        <!-- 指标卡片 1：订单数（较昨日趋势为绿/红） -->
        <div
          class="metric-card app-metric-card clickable"
          data-testid="dashboard-metric-orders"
          role="button"
          tabindex="0"
          @click="goToOrderDetails"
          @keydown.enter="goToOrderDetails"
        >
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

        <!-- 指标卡片 2：GMV / 订单总额 -->
        <div
          class="metric-card app-metric-card clickable"
          data-testid="dashboard-metric-amount"
          role="button"
          tabindex="0"
          @click="goToOrderDetails"
          @keydown.enter="goToOrderDetails"
        >
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

        <!-- 指标卡片 3：服务费净收 -->
        <div
          class="metric-card app-metric-card clickable"
          data-testid="dashboard-metric-fee"
          role="button"
          tabindex="0"
          @click="goToOrderDetails"
          @keydown.enter="goToOrderDetails"
        >
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

        <!-- 指标卡片 4：提成指标（按角色展示合计 / 招商 / 渠道提成） -->
        <div
          class="metric-card app-metric-card clickable"
          data-testid="dashboard-metric-profit"
          role="button"
          tabindex="0"
          @click="goToOrderDetails"
          @keydown.enter="goToOrderDetails"
        >
          <div class="metric-icon profit">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">{{ metricLabels.profit }}</div>
            <div class="metric-value">¥{{ displayCommissionMetric }}</div>
            <div class="metric-trend neutral">{{ metricScopeText }}</div>
          </div>
        </div>
      </div>

      <!-- 近 7 日真实趋势图：ECharts 柱状图（订单数）+ 折线图（GMV），含汇总数据卡片 -->
      <div v-if="trendPoints.length" class="trend-section app-section-panel" data-testid="dashboard-real-trend">
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

      <!-- 业绩域双轨汇总：展示 performance_records 表的预估轨和结算轨订单数及订单额 -->
      <div v-if="performanceSummary" class="breakdown-section app-section-panel" data-testid="dashboard-performance-summary">
        <h3 class="section-title">业绩双轨汇总</h3>
        <div class="breakdown-tags">
          <div class="breakdown-item">
            <span class="breakdown-label">预估轨订单数</span>
            <span class="breakdown-value">{{ performanceSummary.estimate?.orderCount ?? 0 }}</span>
          </div>
          <div class="breakdown-item">
            <span class="breakdown-label">预估轨订单额</span>
            <span class="breakdown-value accent">¥{{ centToYuan(performanceSummary.estimate?.orderAmount) }}</span>
          </div>
          <div class="breakdown-item">
            <span class="breakdown-label">结算轨订单数</span>
            <span class="breakdown-value">{{ performanceSummary.effective?.orderCount ?? 0 }}</span>
          </div>
          <div class="breakdown-item">
            <span class="breakdown-label">结算轨订单额</span>
            <span class="breakdown-value accent">¥{{ centToYuan(performanceSummary.effective?.orderAmount) }}</span>
          </div>
        </div>
      </div>

      <!-- 经营指标矩阵：按业务要求同时展示成交/预估轨与结算轨 -->
      <div class="business-metrics-section app-section-panel" data-testid="dashboard-business-metrics">
        <h3 class="section-title">经营指标</h3>
        <div class="business-metrics-grid">
          <div
            v-for="row in businessMetricRows"
            :key="row.label"
            class="business-metric-row"
          >
            <span class="business-metric-label">{{ row.label }}</span>
            <span class="business-metric-value primary">{{ row.primaryLabel }}：{{ row.primaryValue }}</span>
            <span class="business-metric-value">结算：{{ row.settleValue }}</span>
          </div>
        </div>
      </div>

      <!-- 业绩分拆标签组 -->
      <div class="breakdown-section app-section-panel">
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
        <n-card size="small" class="quick-link-card app-panel" @click="goToOrderDetails">
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
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import { getMetrics } from '../../api/data'
import { centToYuan, getPerformanceSummary, type PerformanceSummary } from '../../api/performance'
import { useRuntimeEnvironment } from '../../composables/useRuntimeEnvironment'
import { resolveDualTrackMetrics } from './dashboard-metrics'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES } from '../../constants/rbac'
import { useDelayedFlag } from '../../utils/delayedFlag'
import { handleApiFailure } from '../../utils/requestError'

const message = useMessage()
const router = useRouter()
const authStore = useAuthStore()
const { usesRealDouyinUpstream, environmentLabel } = useRuntimeEnvironment()
const loading = ref(false)
const delayedLoading = useDelayedFlag(loading, 200)
const initialized = ref(false)
const metricsCreate = ref<Record<string, any>>({})
const metricsSettle = ref<Record<string, any>>({})
const performanceSummary = ref<PerformanceSummary | null>(null)
const timeField = ref<'settleTime' | 'createTime'>('createTime')
const metrics = computed(() => (
  timeField.value === 'settleTime' ? metricsSettle.value : metricsCreate.value
))
const dualTrackCreate = computed(() => metricsCreate.value)
const dualTrackSettle = computed(() => metricsSettle.value)
const trendChartRef = ref<HTMLDivElement | null>(null)
type TrendChartInstance = {
  setOption: (option: Record<string, unknown>, notMerge?: boolean) => void
  resize: () => void
  dispose: () => void
}
type EchartsCore = {
  init: (el: HTMLDivElement) => TrendChartInstance
  use: (extensions: unknown[]) => void
}

let trendChart: TrendChartInstance | null = null
let echartsCorePromise: Promise<EchartsCore> | null = null
const showSkeleton = computed(() => delayedLoading.value && !initialized.value)
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
  if (isChannelStaffOnly.value) {
    return '按创建时间轨看回流订单，按结算时间轨复核服务费与提成；两轨今日订单数可对照查看。'
  }
  if (isBizStaffOnly.value) {
    return '按创建时间轨看负责商品订单回流，按结算时间轨复核招商提成；两轨今日订单数可对照查看。'
  }
  return '订单域采用创建时间 / 结算时间双轨统计：创建轨覆盖已同步订单，结算轨仅纳入已结算订单，便于运营日报与收益复核。'
})

const metricLabels = computed(() => {
  const trackSuffix = timeField.value === 'createTime' ? '（创建轨）' : '（结算轨）'
  const amountSuffix = metrics.value?.amountTrack === 'effective' ? '·结算金额' : metrics.value?.amountTrack === 'estimate' ? '·预估金额' : ''
  if (isChannelStaffOnly.value) {
    return {
      orders: `我的推广订单数${trackSuffix}`,
      amount: `我的订单总额${trackSuffix}`,
      fee: `我的服务费净收${amountSuffix}`,
      profit: `我的渠道提成${amountSuffix}`
    }
  }
  if (isBizStaffOnly.value) {
    return {
      orders: `我的订单数${trackSuffix}`,
      amount: `我的订单总额${trackSuffix}`,
      fee: `我的服务费净收${amountSuffix}`,
      profit: `我的招商提成${amountSuffix}`
    }
  }
  return {
    orders: `今日订单数${trackSuffix}`,
    amount: `今日 GMV${trackSuffix}`,
    fee: `今日服务费净收${amountSuffix}`,
    profit: `今日提成${amountSuffix}`
  }
})

const activeTrackBadge = computed(() => (
  timeField.value === 'createTime' ? '当前：创建时间轨' : '当前：结算时间轨'
))

const formatTrackOrders = (track: Record<string, any>) => {
  const count = toNumber(track?.todayOrderCount ?? track?.totalOrders)
  return `${Math.trunc(count)} 单`
}

const formatTrackGmv = (track: Record<string, any>) => (
  formatAmount(toNumber(track?.todayGmv ?? track?.totalAmount))
)

const dualTrackGapHint = computed(() => {
  const createOrders = toNumber(dualTrackCreate.value?.todayOrderCount ?? dualTrackCreate.value?.totalOrders)
  const settleOrders = toNumber(dualTrackSettle.value?.todayOrderCount ?? dualTrackSettle.value?.totalOrders)
  if (createOrders > 0 && settleOrders === 0) {
    return '今日已有创建轨订单，但结算轨为 0：多为订单尚未结算，服务费与提成请以结算时间轨在结算后复核。'
  }
  if (createOrders > settleOrders && settleOrders > 0) {
    const gap = Math.trunc(createOrders - settleOrders)
    return `今日创建轨较结算轨多 ${gap} 单，差额通常为待结算或未进入结算窗口的订单。`
  }
  return ''
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

const formatMoney = (value: number) => `¥${formatAmount(value)}`

const metricAmount = (track: Record<string, any>, key: string) => formatMoney(toNumber(track?.[key]))

const serviceFeeExpense = (track: Record<string, any>) => {
  const explicit = toNumber(track?.serviceFeeExpense)
  if (explicit > 0) return formatMoney(explicit)
  return formatMoney(toNumber(track?.commission) || toNumber(track?.bizCommission) + toNumber(track?.channelCommission))
}

const businessMetricRows = computed(() => {
  const createTrack = dualTrackCreate.value
  const settleTrack = dualTrackSettle.value
  const createOrders = Math.trunc(toNumber(createTrack?.totalOrders ?? createTrack?.todayOrderCount))
  const settleOrders = Math.trunc(toNumber(settleTrack?.totalOrders ?? settleTrack?.todayOrderCount))

  return [
    {
      label: '总订单数',
      primaryLabel: '成交',
      primaryValue: String(createOrders),
      settleValue: String(settleOrders)
    },
    {
      label: '订单额',
      primaryLabel: '成交',
      primaryValue: formatMoney(toNumber(createTrack?.totalAmount ?? createTrack?.todayGmv)),
      settleValue: formatMoney(toNumber(settleTrack?.totalAmount ?? settleTrack?.todayGmv))
    },
    {
      label: '服务费收入',
      primaryLabel: '预估',
      primaryValue: metricAmount(createTrack, 'serviceFeeIncome'),
      settleValue: metricAmount(settleTrack, 'serviceFeeIncome')
    },
    {
      label: '技术服务费',
      primaryLabel: '预估',
      primaryValue: metricAmount(createTrack, 'techServiceFee'),
      settleValue: metricAmount(settleTrack, 'techServiceFee')
    },
    {
      label: '服务费支出',
      primaryLabel: '预估',
      primaryValue: serviceFeeExpense(createTrack),
      settleValue: serviceFeeExpense(settleTrack)
    },
    {
      label: '服务费收益',
      primaryLabel: '预估',
      primaryValue: metricAmount(createTrack, 'serviceFee'),
      settleValue: metricAmount(settleTrack, 'serviceFee')
    },
    {
      label: '招商提成',
      primaryLabel: '预估',
      primaryValue: metricAmount(createTrack, 'bizCommission'),
      settleValue: metricAmount(settleTrack, 'bizCommission')
    },
    {
      label: '渠道提成',
      primaryLabel: '预估',
      primaryValue: metricAmount(createTrack, 'channelCommission'),
      settleValue: metricAmount(settleTrack, 'channelCommission')
    },
    {
      label: '毛利',
      primaryLabel: '预估',
      primaryValue: metricAmount(createTrack, 'grossProfit'),
      settleValue: metricAmount(settleTrack, 'grossProfit')
    }
  ]
})

const displayOrderCount = computed(() => toNumber(metrics.value?.todayOrderCount ?? metrics.value?.totalOrders))

const displayGmv = computed(() => formatAmount(toNumber(metrics.value?.todayGmv ?? metrics.value?.totalAmount)))

const displayCommissionMetric = computed(() => {
  if (isChannelStaffOnly.value) {
    return formatAmount(toNumber(metrics.value?.channelCommission))
  }
  if (isBizStaffOnly.value) {
    return formatAmount(toNumber(metrics.value?.bizCommission))
  }
  return formatAmount(toNumber(metrics.value?.commission))
})

const timeScopeLabel = computed(() => timeField.value === 'createTime' ? '按创建时间统计' : '按结算时间统计')

const metricScopeText = computed(() => {
  if (timeField.value === 'createTime') {
    return '服务费 / 提成按当前创建轨订单字段汇总'
  }
  const settleOrders = toNumber(metricsSettle.value?.todayOrderCount ?? metricsSettle.value?.totalOrders)
  return settleOrders > 0
    ? '收益类指标与结算轨订单一致'
    : '结算轨今日无单，收益类指标为 0'
})

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

const metricsAreEmpty = computed(() => {
  const createOrders = toNumber(dualTrackCreate.value?.todayOrderCount ?? dualTrackCreate.value?.totalOrders)
  const settleOrders = toNumber(dualTrackSettle.value?.todayOrderCount ?? dualTrackSettle.value?.totalOrders)
  return createOrders === 0 && settleOrders === 0 && trendTotalOrders.value === 0
})

const showEmptyDataHint = computed(() => initialized.value && !loading.value && metricsAreEmpty.value)

const emptyDataHint = computed(() => {
  if (usesRealDouyinUpstream.value) {
    const label = environmentLabel.value || 'REAL-PRE'
    return `当前 ${label} 环境尚未回流订单（常见原因：抖店 Token 未授权或尚未执行订单同步）。请先到「系统 → 抖店联调」完成授权并同步订单，然后刷新本页。`
  }
  return '当前环境暂无订单数据。若预期应有数据，请确认测试种子是否已写入，或检查订单同步任务是否正常运行。'
})

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

const loadEchartsCore = async () => {
  if (!echartsCorePromise) {
    echartsCorePromise = Promise.all([
      import('echarts/core'),
      import('echarts/charts'),
      import('echarts/components'),
      import('echarts/renderers')
    ]).then(([core, charts, components, renderers]) => {
      core.use([
        charts.BarChart,
        charts.LineChart,
        components.GridComponent,
        components.LegendComponent,
        components.TooltipComponent,
        renderers.CanvasRenderer
      ])
      return core as unknown as EchartsCore
    })
  }

  return echartsCorePromise
}

const renderTrendChart = async () => {
  await nextTick()
  if (!trendChartRef.value || !trendPoints.value.length) return
  if (!trendChart) {
    const echarts = await loadEchartsCore()
    if (!trendChartRef.value) return
    trendChart = echarts.init(trendChartRef.value)
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

const buildMetricsParams = (field: 'createTime' | 'settleTime') => {
  const params: Record<string, string> = { timeField: field }
  if (isChannelStaffOnly.value) params.scope = 'personal'
  return params
}

const switchTimeField = (field: 'createTime' | 'settleTime') => {
  if (timeField.value === field) return
  timeField.value = field
  void renderTrendChart()
}

const goToOrderDetails = () => {
  router.push({
    path: '/data/orders',
    query: { timeField: timeField.value }
  })
}

const formatLocalDateTime = (date: Date) => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const buildSummaryParams = (): import('../../api/performance').PerformanceListParams => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const tomorrow = new Date(today)
  tomorrow.setDate(tomorrow.getDate() + 1)
  return {
    timeFilterType: timeField.value === 'settleTime' ? 'settle' : 'pay',
    timeStart: formatLocalDateTime(today),
    timeEnd: formatLocalDateTime(tomorrow)
  }
}

const loadMetrics = async () => {
  loading.value = true

  const summaryPromise = getPerformanceSummary(buildSummaryParams())
    .then((summaryRes: any) => {
      performanceSummary.value = summaryRes?.data || summaryRes || null
    })
    .catch(() => {
      performanceSummary.value = null
    })

  try {
    const metricsRes = await getMetrics(buildMetricsParams('createTime'))
    const { estimate, settle } = resolveDualTrackMetrics(metricsRes)
    metricsCreate.value = estimate
    metricsSettle.value = settle
  } catch (error: any) {
    handleApiFailure(error, {
      permissionFallback: '当前角色无权查看数据看板',
      onFallback: (msg) => message.warning(msg),
      fallbackMessage: '获取数据看板指标异常'
    })
  } finally {
    initialized.value = true
    loading.value = false
    void renderTrendChart()
  }
  void summaryPromise
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

watch(timeField, () => {
  void renderTrendChart()
  void getPerformanceSummary(buildSummaryParams())
    .then((summaryRes: any) => {
      performanceSummary.value = summaryRes?.data || summaryRes || null
    })
    .catch(() => {
      performanceSummary.value = null
    })
})
</script>

<style scoped>
.dashboard {
  max-width: var(--page-max-width);
}

:deep(.n-page-header-extra) {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.dual-track-bar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: var(--spacing-md);
  padding: 12px;
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
}

.dual-track-item {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-page);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--transition-normal), box-shadow var(--transition-normal);
}

.dual-track-item:hover {
  border-color: var(--color-primary-border);
}

.dual-track-item.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary-light);
  background: var(--color-primary-light);
}

.dual-track-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.dual-track-desc {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.dual-track-metrics {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  font-size: var(--text-base);
  color: var(--text-primary);
}

.dual-track-metrics strong {
  font-weight: 700;
}

.dual-track-sep {
  color: var(--text-muted);
}

.dual-track-hint {
  grid-column: 1 / -1;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--bg-page);
  border: 1px dashed var(--border-color);
}

.metric-scope-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: var(--spacing-md);
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  background: var(--bg-page);
}

.dashboard-empty-hint {
  margin-bottom: var(--spacing-md);
}

.metric-scope-badge {
  flex-shrink: 0;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-primary);
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-primary-light);
}

.metric-scope-copy {
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
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
  margin-bottom: var(--content-gap);
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
  margin-bottom: var(--content-gap);
}

.business-metrics-section {
  margin-bottom: var(--content-gap);
}

.business-metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
}

.business-metric-row {
  display: grid;
  grid-template-columns: minmax(84px, 1fr) auto auto;
  gap: 10px;
  align-items: center;
  min-height: 44px;
  padding: 9px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-page);
}

.business-metric-label {
  min-width: 0;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.business-metric-value {
  white-space: nowrap;
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.business-metric-value.primary {
  color: var(--color-primary);
  font-weight: 600;
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
  .dual-track-bar {
    grid-template-columns: 1fr;
  }

  .trend-header {
    display: grid;
  }

  .trend-scope {
    text-align: left;
  }

  .trend-summary-list {
    grid-template-columns: 1fr;
  }

  .business-metric-row {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }

  .trend-chart {
    height: 300px;
    min-height: 300px;
  }
}
</style>
