<template>
  <div class="dashboard-page">
    <PageHeader title="业务概览" description="实时掌握商品推广进度、订单归因率及招商业绩分布。" />

    <n-grid :cols="4" :x-gap="16" :y-gap="16" class="stats-row">
      <n-gi v-for="stat in stats" :key="stat.label">
        <n-card :bordered="false" class="stat-card">
          <n-statistic :label="stat.label">
            {{ stat.value }}
          </n-statistic>
          <div class="stat-footer">
            <span v-if="stat.trend !== null" class="stat-trend" :class="stat.trend >= 0 ? 'up' : 'down'">
              {{ stat.trend >= 0 ? '↑' : '↓' }} {{ Math.abs(stat.trend) }}%
            </span>
            <span class="stat-period">较昨日</span>
          </div>
        </n-card>
      </n-gi>
    </n-grid>

    <n-grid :cols="2" :x-gap="16" :y-gap="16">
      <n-gi>
        <n-card title="近 7 日订单归因趋势" :bordered="false" class="panel-card">
          <div v-if="trendData.length" class="trend-chart">
            <div v-for="item in trendData" :key="item.date" class="trend-col">
              <div class="trend-bar-track">
                <div
                  class="trend-bar-fill"
                  :style="{ height: barHeight(item.orders) }"
                ></div>
              </div>
              <div class="trend-label">{{ item.dateLabel }}</div>
              <div class="trend-value">{{ item.orders }}</div>
            </div>
          </div>
          <n-empty v-else description="暂无趋势数据" class="trend-empty" />
        </n-card>
      </n-gi>
      <n-gi>
        <n-card :bordered="false" class="panel-card">
          <template #header>
            <span>招商团队表现榜</span>
          </template>
          <n-list hoverable clickable>
            <n-list-item v-for="(item, index) in ranking" :key="item.name">
              <template #prefix>
                <n-tag :type="index < 3 ? 'primary' : 'default'" size="small" round>{{ index + 1 }}</n-tag>
              </template>
              <n-thing :title="item.name" :description="`完成归因业绩: ¥${item.amount}`" />
            </n-list-item>
          </n-list>

          <n-divider />
          <div class="section-label">快捷入口</div>
          <n-space :size="8" wrap>
            <n-button
              v-for="entry in quickEntries"
              :key="entry.path"
              quaternary
              type="primary"
              size="small"
              @click="$router.push(entry.path)"
            >
              {{ entry.label }}
            </n-button>
          </n-space>
        </n-card>
      </n-gi>
    </n-grid>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { getSummary } from '../../api/dashboard'

interface StatItem {
  label: string
  value: string
  trend: number | null
}

interface TrendItem {
  date: string
  dateLabel: string
  orders: number
}

interface RankingItem {
  name: string
  amount: string
}

const stats = ref<StatItem[]>([
  { label: '今日 GMV', value: '-', trend: null },
  { label: '待处理订单', value: '-', trend: null },
  { label: '归因成功率', value: '-', trend: null },
  { label: '活跃达人数', value: '-', trend: null }
])

const trendData = ref<TrendItem[]>([])
const ranking = ref<RankingItem[]>([])

const quickEntries = [
  { label: '订单归因', path: '/orders' },
  { label: '商品库', path: '/product' },
  { label: '达人 CRM', path: '/talent' },
  { label: '数据看板', path: '/data' }
]

const maxOrders = computed(() => {
  if (!trendData.value.length) return 1
  return Math.max(...trendData.value.map((d) => d.orders), 1)
})

const barHeight = (orders: number) => {
  return `${Math.max((orders / maxOrders.value) * 100, 4)}%`
}

const applySummary = (data: any) => {
  if (!data) return

  if (Array.isArray(data.stats) && data.stats.length) {
    // 后端返回标准 stats 数组格式
    stats.value = data.stats.map((s: any) => ({
      label: String(s.label || ''),
      value: String(s.value || '-'),
      trend: typeof s.trend === 'number' ? s.trend : null
    }))
  } else {
    // 兼容后端实际返回的 Summary 字段格式
    const gmv = typeof data.orderAmount === 'number'
      ? `¥${(data.orderAmount / 100).toLocaleString('zh-CN', { maximumFractionDigits: 0 })}`
      : '-'
    const unattributed = data.unattributedOrderCount != null ? String(data.unattributedOrderCount) : '-'
    const rate = typeof data.attributionRate === 'number'
      ? `${(data.attributionRate * 100).toFixed(1)}%`
      : '-'
    stats.value = [
      { label: '今日 GMV', value: gmv, trend: null },
      { label: '待处理订单', value: unattributed, trend: null },
      { label: '归因成功率', value: rate, trend: null },
      { label: '活跃达人数', value: '-', trend: null }
    ]
  }

  if (Array.isArray(data.trend) && data.trend.length) {
    trendData.value = data.trend.map((t: any) => ({
      date: String(t.date || ''),
      dateLabel: String(t.dateLabel || t.date || ''),
      orders: Number(t.orders || t.count || 0)
    }))
  }

  if (Array.isArray(data.ranking) && data.ranking.length) {
    ranking.value = data.ranking.map((r: any) => ({
      name: String(r.name || ''),
      amount: String(r.amount || '0')
    }))
  } else if (Array.isArray(data.colonelPerformance) && data.colonelPerformance.length) {
    // 兼容 colonelPerformance 字段
    ranking.value = data.colonelPerformance.map((r: any) => ({
      name: String(r.colonelUserName || r.name || ''),
      amount: String(r.orderAmount != null ? (r.orderAmount / 100).toFixed(0) : '0')
    }))
  } else if (Array.isArray(data.channelPerformance) && data.channelPerformance.length) {
    // 兼容 channelPerformance 字段
    ranking.value = data.channelPerformance.map((r: any) => ({
      name: String(r.channelUserName || r.name || ''),
      amount: String(r.orderAmount != null ? (r.orderAmount / 100).toFixed(0) : '0')
    }))
  }
}

const fetchSummary = async () => {
  try {
    const res: any = await getSummary()
    applySummary(res?.data)
  } catch {
    // 降级：保持默认空状态
  }
}

onMounted(fetchSummary)
</script>

<style scoped>
.dashboard-page {
  padding: var(--spacing-xl);
}

.stats-row {
  margin-bottom: var(--spacing-lg);
}

.stat-card {
  border-radius: var(--radius-lg);
  transition: transform 0.3s, box-shadow 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md);
}

.stat-footer {
  margin-top: var(--spacing-sm);
  font-size: var(--text-xs);
}

.stat-trend {
  font-weight: 600;
  margin-right: 4px;
}

.stat-trend.up {
  color: var(--color-success);
}

.stat-trend.down {
  color: var(--color-danger);
}

.stat-period {
  color: var(--text-tertiary);
}

.panel-card {
  border-radius: var(--radius-lg);
}

/* Trend Chart */
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 200px;
  padding: var(--spacing-md) 0;
}

.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.trend-bar-track {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.trend-bar-fill {
  width: 60%;
  max-width: 32px;
  background: var(--color-primary);
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  transition: height 0.4s ease;
  min-height: 4px;
}

.trend-label {
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.trend-value {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-primary);
}

.trend-empty {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
  margin-bottom: var(--spacing-sm);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
</style>
