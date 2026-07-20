<!--
  Dashboard Overview — 业务概览页（旧版遗留仪表盘）
  用途：展示 GMV、未归因订单、归因成功率、服务费收入等核心指标卡片，
        近 7 日订单归因趋势柱状图，团队/个人表现排名，以及按角色过滤的快捷入口。
  角色适配：
    - ADMIN / BIZ_LEADER / CHANNEL_LEADER：团队维度概览 + 招商团队表现榜
    - BIZ_STAFF：个人维度"我的业绩概览"，仅看自身负责商品
    - CHANNEL_STAFF：个人维度"我的业绩概览"，仅看自身达人
  数据来源：getSummary API → applySummary 兼容多格式后端响应
-->
<template>
  <div class="dashboard-page app-page" data-testid="dashboard-overview-page">
    <!-- 页面标题和描述，根据角色动态生成 -->
    <PageHeader :title="dashboardTitle" :description="dashboardDesc" />

    <!-- 加载骨架屏：统计数据卡片 + 趋势/排名面板 -->
    <div v-if="showSkeleton" class="dashboard-skeleton" data-testid="dashboard-skeleton">
      <n-grid :cols="4" :x-gap="16" :y-gap="16" class="stats-row">
        <n-gi v-for="item in 4" :key="item">
          <n-card :bordered="false" class="stat-card app-panel skeleton-card">
            <n-skeleton text :repeat="2" />
            <n-skeleton height="24px" :sharp="false" />
          </n-card>
        </n-gi>
      </n-grid>
      <n-grid :cols="2" :x-gap="16" :y-gap="16">
        <n-gi v-for="item in 2" :key="item">
          <n-card :bordered="false" class="panel-card app-panel skeleton-card">
            <n-skeleton text :repeat="3" />
            <n-skeleton height="160px" :sharp="false" />
          </n-card>
        </n-gi>
      </n-grid>
    </div>

    <template v-else-if="initialized">
      <!-- 核心指标卡片行：4 列布局，每张卡片含 label + value + 趋势箭头 -->
      <n-grid :cols="4" :x-gap="16" :y-gap="16" class="stats-row" data-testid="dashboard-stat-cards">
        <n-gi v-for="stat in stats" :key="stat.label">
          <n-card :bordered="false" class="stat-card app-panel">
            <n-statistic :label="stat.label">
              {{ stat.value }}
            </n-statistic>
            <div class="stat-footer">
              <!-- 趋势箭头：上升绿色、下降红色 -->
              <span v-if="stat.trend !== null" class="stat-trend" :class="stat.trend >= 0 ? 'up' : 'down'">
                {{ stat.trend >= 0 ? '↑' : '↓' }} {{ Math.abs(stat.trend) }}%
              </span>
              <span class="stat-period">{{ stat.trend !== null ? '较昨日' : '真实归因口径' }}</span>
            </div>
          </n-card>
        </n-gi>
      </n-grid>

      <!-- 下方面板区域：左侧趋势图 + 右侧排名/快捷入口 -->
      <n-grid :cols="2" :x-gap="16" :y-gap="16">
        <n-gi>
          <!-- 近 7 日订单归因趋势：纯 CSS 柱状图，非 ECharts -->
          <n-card title="近 7 日订单归因趋势" :bordered="false" class="panel-card app-panel">
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
          <!-- 右侧面板：排名列表（招商团队表现榜/我的重点产出）+ 快捷入口 -->
          <n-card :bordered="false" class="panel-card app-panel">
            <template #header>
              <span>{{ rankingTitle }}</span>
            </template>
            <!-- 排名列表：前三名使用 primary 色标签高亮 -->
            <n-list hoverable clickable>
              <n-list-item v-for="(item, index) in ranking" :key="item.name">
                <template #prefix>
                  <n-tag :type="index < 3 ? 'primary' : 'default'" size="small" round>{{ index + 1 }}</n-tag>
                </template>
                <n-thing :title="item.name" :description="`完成归因业绩: ¥${item.amount}`" />
              </n-list-item>
            </n-list>

            <!-- 快捷入口区域：按角色过滤后展示，点击跳转对应页面 -->
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import { getSummary } from '../../api/dashboard'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES, hasOnlyCanonicalRole } from '../../constants/rbac'
import { PERMISSION_CODES, hasPermission } from '../../constants/permissions'
import { useDelayedFlag } from '../../utils/delayedFlag'

/** 指标卡片数据项 */
interface StatItem {
  label: string
  value: string
  trend: number | null
}

/** 趋势图数据项（近 7 日每日订单归因数） */
interface TrendItem {
  date: string
  dateLabel: string
  orders: number
}

/** 排名数据项（团队/个人表现） */
interface RankingItem {
  name: string
  amount: string
}

/** 核心指标卡片初始值，接口返回前展示为 '-' */
const stats = ref<StatItem[]>([
  { label: '累计 GMV', value: '-', trend: null },
  { label: '未归因订单', value: '-', trend: null },
  { label: '归因成功率', value: '-', trend: null },
  { label: '服务费收入', value: '-', trend: null }
])

const trendData = ref<TrendItem[]>([])
const ranking = ref<RankingItem[]>([])
const authStore = useAuthStore()
const loading = ref(false)
const initialized = ref(false)
/** 延迟 200ms 显示 loading，避免快速请求闪屏 */
const delayedLoading = useDelayedFlag(loading, 200)
/** 骨架屏：首次加载中且尚未初始化时显示 */
const showSkeleton = computed(() => delayedLoading.value && !initialized.value)
const ROLE = ROLE_CODES
const PERMISSION = PERMISSION_CODES

/**
 * 判断当前用户是否仅为业务员（BIZ_STAFF 且不是 ADMIN/BIZ_LEADER）
 * 用于降级为"我的业绩概览"个人视角
 */
const isBizStaffOnly = computed(() => {
  return hasOnlyCanonicalRole(authStore.roleCodes, ROLE.BIZ_STAFF)
})

/**
 * 判断当前用户是否仅为渠道员（CHANNEL_STAFF 且不是 ADMIN/CHANNEL_LEADER）
 * 用于降级为"我的业绩概览"个人视角
 */
const isChannelStaffOnly = computed(() => {
  return hasOnlyCanonicalRole(authStore.roleCodes, ROLE.CHANNEL_STAFF)
})

/** 是否为仅个人视角（业务员或渠道员） */
const isPersonalOnly = computed(() => isBizStaffOnly.value || isChannelStaffOnly.value)

/** 页面标题：个人视角显示"我的业绩概览"，管理者视角显示"业务概览" */
const dashboardTitle = computed(() => isPersonalOnly.value ? '我的业绩概览' : '业务概览')

/** 页面描述：根据角色展示不同的说明文案 */
const dashboardDesc = computed(() => {
  if (isBizStaffOnly.value) return '实时掌握我负责商品的销售额、佣金及订单趋势。'
  if (isChannelStaffOnly.value) return '实时掌握我的达人带来的销售额、服务费及订单趋势。'
  return '实时掌握商品推广进度、订单归因率及团队业绩分布。'
})

/**
 * 排名标题：个人视角为"我的重点产出"，管理者视角为"招商团队表现榜"
 */
const rankingTitle = computed(() => {
  if (isChannelStaffOnly.value) return '我的重点产出'
  if (isBizStaffOnly.value) return '我的重点产出'
  return '招商团队表现榜'
})

/**
 * 快捷入口列表：按权限过滤可见项，并对个人视角重命名标签
 * 例如 CHANNEL_STAFF 看到"我的达人"而非"达人 CRM"
 */
const quickEntries = computed(() => {
  const permissions = authStore.permissionCodes
  return [
    { label: '订单归因', path: '/orders', permissions: [PERMISSION.ORDER_ACCESS] },
    { label: '商品库', path: '/product', permissions: [PERMISSION.PRODUCT_ACCESS] },
    { label: '商品管理', path: '/product/manage', permissions: [PERMISSION.PRODUCT_MANAGE_ACCESS] },
    { label: '我的商品', path: '/product/manage/products', permissions: [PERMISSION.PRODUCT_MANAGE_ACCESS] },
    { label: '达人 CRM', path: '/talent', permissions: [PERMISSION.TALENT_ACCESS] },
    { label: '数据看板', path: '/data', permissions: [PERMISSION.DATA_ACCESS] },
    { label: '合作单', path: '/sample', permissions: [PERMISSION.SAMPLE_ACCESS] }
  ]
    .filter((entry) => hasPermission(permissions, entry.permissions))
    .map((entry) => {
      if (isChannelStaffOnly.value) {
        if (entry.path === '/talent') return { ...entry, label: '我的达人' }
        if (entry.path === '/data') return { ...entry, label: '我的业绩' }
        if (entry.path === '/sample') return { ...entry, label: '合作单' }
      }
      if (isBizStaffOnly.value) {
        if (entry.path === '/data') return { ...entry, label: '我的业绩' }
        if (entry.path === '/sample') return { ...entry, label: '合作单' }
      }
      return entry
    })
})

/**
 * 趋势图最大订单数（用于计算柱状图高度百分比）
 * 保证至少为 1 避免除零
 */
const maxOrders = computed(() => {
  if (!trendData.value.length) return 1
  return Math.max(...trendData.value.map((d) => d.orders), 1)
})

/** 计算柱状图高度百分比，最小 4% 防止零值柱不可见 */
const barHeight = (orders: number) => {
  return `${Math.max((orders / maxOrders.value) * 100, 4)}%`
}

/**
 * 应用概览数据到页面状态
 * 兼容后端多种响应格式：
 * 1. 标准 stats 数组格式（{stats:[], trend:[], ranking:[]}）
 * 2. Summary 字段格式（{orderAmount, serviceFee, unattributedOrderCount, attributionRate}）
 * 3. 排名兼容 colonelPerformance / channelPerformance 字段
 * @param data 接口返回的 data 层
 */
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
      ? `¥${(data.orderAmount / 100).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
      : '-'
    const serviceFee = typeof data.serviceFee === 'number'
      ? `¥${(data.serviceFee / 100).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
      : '-'
    const unattributed = data.unattributedOrderCount != null ? String(data.unattributedOrderCount) : '-'
    const rate = typeof data.attributionRate === 'number'
      ? `${(data.attributionRate * 100).toFixed(1)}%`
      : '-'
    stats.value = [
      { label: '累计 GMV', value: gmv, trend: null },
      { label: '未归因订单', value: unattributed, trend: null },
      { label: '归因成功率', value: rate, trend: null },
      { label: '服务费收入', value: serviceFee, trend: null }
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

/**
 * 拉取概览数据
 * 失败时降级为默认空状态，不阻塞页面渲染
 */
const fetchSummary = async () => {
  loading.value = true
  try {
    const res: any = await getSummary()
    applySummary(res?.data)
  } catch {
    // 降级：保持默认空状态
  } finally {
    initialized.value = true
    loading.value = false
  }
}

/** 组件挂载后立即拉取概览数据 */
onMounted(fetchSummary)
</script>

<style scoped>
/* ---- 指标卡片行 ---- */
/* 指标卡片行底部间距，与下方趋势/排名面板分隔 */
.stats-row {
  margin-bottom: var(--content-gap);
}

/* 指标卡片数值样式：大号加粗，紧凑字间距 */
.stat-card :deep(.n-statistic-value) {
  font-size: var(--text-2xl);
  font-weight: 700;
  letter-spacing: -0.02em;
}

/* 指标卡片标签样式：小号辅助色文字 */
.stat-card :deep(.n-statistic-label) {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

/* 指标卡片底部区域：包含趋势箭头和统计口径说明 */
.stat-footer {
  margin-top: var(--spacing-sm);
  font-size: var(--text-xs);
}

/* 趋势箭头文字：加粗显示 */
.stat-trend {
  font-weight: 600;
  margin-right: 4px;
}

/* 上升趋势：绿色标识正向变化 */
.stat-trend.up {
  color: var(--color-success);
}

/* 下降趋势：红色标识负向变化 */
.stat-trend.down {
  color: var(--color-danger);
}

/* 统计口径说明文字（"较昨日"/"真实归因口径"） */
.stat-period {
  color: var(--text-tertiary);
}

/* ---- 面板卡片 ---- */
/* 趋势图和排名面板的统一样式 */
.panel-card {
  border-radius: var(--radius-lg);
}

/* ---- 趋势图（纯 CSS 柱状图） ---- */
/* 柱状图容器：水平排列各列柱子，底部对齐 */
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 200px;
  padding: var(--spacing-md) 0;
}

/* 单列柱子容器：垂直排列柱体 + 日期标签 + 数值 */
.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

/* 柱体轨道：占满剩余高度，柱体从底部向上生长 */
.trend-bar-track {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

/* 柱体填充：渐变主色，带圆角顶部和平滑高度过渡 */
.trend-bar-fill {
  width: 60%;
  max-width: 32px;
  background: var(--color-primary-gradient);
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  transition: height 0.4s ease;
  min-height: 4px;
}

/* 日期标签：小号辅助色 */
.trend-label {
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-tertiary);
}

/* 订单数值：小号加粗主色 */
.trend-value {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-primary);
}

/* 趋势图空状态：居中显示占满固定高度 */
.trend-empty {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ---- 快捷入口区域 ---- */
/* 区域标签：大写小号字，灰色，与按钮群分隔 */
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
  margin-bottom: var(--spacing-sm);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
</style>
