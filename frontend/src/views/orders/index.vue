<template>
  <div class="orders-page app-page" data-testid="orders-page">
    <PageHeader
      title="订单归因"
      description="自动回流抖店结算订单，精准识别推广渠道、达人业绩与招商归属。"
    >
      <template #actions>
        <n-button :loading="syncLoading" type="primary" secondary data-testid="orders-sync" @click="handleSync">同步最新订单</n-button>
        <n-button type="primary" data-testid="orders-search-submit" @click="fetchData">查询</n-button>
      </template>
    </PageHeader>

    <div v-if="summaryReady" class="attribution-summary app-summary-bar">
      <n-space :size="16" align="center">
        <span class="summary-label">归因概览</span>
        <n-tag type="success" size="small" round>已归因: {{ attributionSummary.attributed }} 单 ({{ attributionSummary.attributedPercent }}%)</n-tag>
        <n-tag type="error" size="small" round>待排查: {{ attributionSummary.unattributed }} 单</n-tag>
        <n-tag type="info" size="small" round>部分归因: {{ attributionSummary.partial }} 单</n-tag>
      </n-space>
    </div>

    <div class="toolbar app-toolbar">
      <n-space wrap>
        <n-input v-model:value="filters.orderId" placeholder="订单 ID" style="width: 200px" />
        <n-input v-model:value="filters.productId" placeholder="商品 ID" style="width: 180px" />
        <n-select
          v-model:value="filters.channelKeyword"
          :options="channelOptions"
          :loading="channelOptionsLoading"
          filterable
          remote
          clearable
          placeholder="渠道负责人"
          style="width: 180px"
          @search="handleChannelSearch"
        />
        <n-select
          v-model:value="filters.colonelKeyword"
          :options="recruiterOptions"
          :loading="recruiterOptionsLoading"
          filterable
          remote
          clearable
          placeholder="招商组长"
          style="width: 180px"
          @search="handleRecruiterSearch"
        />
        <n-select
          v-model:value="filters.recruiterDeptIds"
          :options="recruiterDeptOptions"
          multiple
          filterable
          clearable
          max-tag-count="responsive"
          placeholder="招商部门"
          style="min-width: 200px"
          data-testid="orders-recruiter-dept-filter"
        />
        <n-select
          v-model:value="filters.channelDeptIds"
          :options="channelDeptOptions"
          multiple
          filterable
          clearable
          max-tag-count="responsive"
          placeholder="渠道部门"
          style="min-width: 200px"
          data-testid="orders-channel-dept-filter"
        />
        <n-select
          v-model:value="filters.attributionStatus"
          :options="[
            { label: '已归因', value: 'ATTRIBUTED' },
            { label: '待排查', value: 'UNATTRIBUTED' },
            { label: '部分归因', value: 'PARTIAL' }
          ]"
          placeholder="归因状态"
          clearable
          style="width: 140px"
        />
        <n-date-picker v-model:value="filters.dateRange" type="daterange" clearable style="width: 280px" />
        <n-button @click="resetFilters">重置</n-button>
      </n-space>
    </div>

    <n-card :bordered="false" class="main-card app-panel">
      <n-data-table
        remote
        data-testid="orders-table"
        :columns="columns"
        :data="data"
        :loading="tableLoading"
        :pagination="pagination"
        :scroll-x="1600"
        :row-key="(row: any) => row.orderId"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <OrderDetailModal v-model:show="showDetail" :order-id="activeOrderId" />
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { h, computed, onMounted, reactive, ref, watch } from 'vue'
import { NButton, NSpace, NTag, NText, useMessage } from 'naive-ui'
import { useRoute } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import OrderDetailModal from './components/OrderDetailModal.vue'
import { getOrders, getOrderFilterOptions, getOrderStats, syncOrders } from '../../api/order'
import { getAttributionReasonText } from '../../constants/orderAttribution'
import { createPaginationState, normalizePageSize } from '../../utils/pagination'
import { useDelayedFlag } from '../../utils/delayedFlag'
import { useDebouncedFn } from '../../utils/debounce'
import { loadOrderChannelOptions, loadOrderRecruiterOptions } from './order-user-filter-options'

const message = useMessage()
const route = useRoute()
const loading = ref(false)
const tableLoading = useDelayedFlag(loading, 200)
const syncLoading = ref(false)
const data = ref([])
const stats = ref<{ totalOrders?: number; attributedOrders?: number; unattributedOrders?: number; partialOrders?: number; lastSyncTime?: string | null } | null>(null)
const showDetail = ref(false)
const activeOrderId = ref('')
const channelOptions = ref<{ label: string; value: string }[]>([])
const recruiterOptions = ref<{ label: string; value: string }[]>([])
const channelOptionsLoading = ref(false)
const recruiterOptionsLoading = ref(false)

const attributionSummary = computed(() => {
  const total = Number(stats.value?.totalOrders || 0)
  const attributed = Number(stats.value?.attributedOrders || 0)
  const unattributed = Number(stats.value?.unattributedOrders || 0)
  const partial = Number(stats.value?.partialOrders ?? Math.max(total - attributed - unattributed, 0))
  return {
    attributed,
    unattributed,
    partial,
    attributedPercent: total ? Math.round((attributed / total) * 100) : 0
  }
})

const summaryReady = computed(() => Number(stats.value?.totalOrders || 0) > 0)

const filters = reactive({
  orderId: '',
  activityId: '',
  productId: '',
  channelKeyword: null as string | null,
  colonelKeyword: null as string | null,
  recruiterDeptIds: [] as string[],
  channelDeptIds: [] as string[],
  attributionStatus: null,
  dateRange: null as [number, number] | null,
  timeField: 'createTime',
  dashboardDiagnosis: ''
})

const recruiterDeptOptions = ref<{ label: string; value: string }[]>([])
const channelDeptOptions = ref<{ label: string; value: string }[]>([])

const pagination = reactive(createPaginationState())

let fetchVersion = 0

const applyRouteFilters = () => {
  filters.orderId = typeof route.query.orderId === 'string' ? route.query.orderId : ''
  filters.activityId = typeof route.query.activityId === 'string' ? route.query.activityId : ''
  filters.productId = typeof route.query.productId === 'string' ? route.query.productId : ''
  filters.channelKeyword = typeof route.query.channelKeyword === 'string' ? route.query.channelKeyword : null
  filters.colonelKeyword = typeof route.query.colonelKeyword === 'string' ? route.query.colonelKeyword : null
  filters.timeField = typeof route.query.timeField === 'string' ? route.query.timeField : 'createTime'
  filters.dashboardDiagnosis = typeof route.query.dashboardDiagnosis === 'string' ? route.query.dashboardDiagnosis : ''
}

const fetchChannelOptions = async (keyword: string) => {
  channelOptionsLoading.value = true
  try {
    channelOptions.value = await loadOrderChannelOptions(keyword)
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '加载渠道负责人失败' })
  } finally {
    channelOptionsLoading.value = false
  }
}

const fetchRecruiterOptions = async (keyword: string) => {
  recruiterOptionsLoading.value = true
  try {
    recruiterOptions.value = await loadOrderRecruiterOptions(keyword)
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '加载招商组长失败' })
  } finally {
    recruiterOptionsLoading.value = false
  }
}

const handleChannelSearch = useDebouncedFn((keyword: string) => {
  void fetchChannelOptions(keyword)
}, 250)

const handleRecruiterSearch = useDebouncedFn((keyword: string) => {
  void fetchRecruiterOptions(keyword)
}, 250)

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return `¥${(Number(value) / 100).toFixed(2)}`
}

/**
 * 格式化佣金率 / 服务费率。
 * 后端若返回 0.07（小数）则乘 100 展示 7%；若返回 7（整数百分比）则直接展示 7%。
 * 空值显示 '-'。
 */
function formatRate(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  // 如果已经是 "10%" 形式字符串，直接返回
  if (typeof value === 'string' && value.endsWith('%')) return value
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  // 如果值 <= 1 且不为 0，认为是小数形式（如 0.07 → 7%）
  const pct = (num > 0 && num < 1) ? Math.round(num * 10000) / 100 : num
  return `${pct}%`
}

/**
 * 渲染商品信息列：左图右文布局（96px 图片 + 右侧详情文本）。
 * 严格按照用户截图样式展示：图片顶部对齐、标题红色省略、元信息灰字紧凑排列。
 */
function renderProductInfo(row: any) {
  const image = row.productImage || row.productPic || row.cover || null
  const name = row.productTitle || row.productName || '-'
  const id = row.productId || '-'
  const shop = row.shopName || '-'
  const qty = row.quantity ?? row.productQuantity ?? row.goodsNum ?? row.itemNum ?? null
  const commRate = row.commissionRate ?? row.commission_rate ?? row.cosRatio ?? null
  const svcRate = row.serviceFeeRate ?? row.service_fee_rate ?? row.serviceRate ?? null

  const imageNode = image
    ? h('img', { class: 'order-product-image', src: image, alt: name })
    : h('div', { class: 'order-product-image order-product-image--placeholder' })

  const contentNode = h('div', { class: 'order-product-content' }, [
    h('div', { class: 'order-product-title', title: name }, name),
    h('div', { class: 'order-product-line' }, `商品ID：${id}`),
    h('div', { class: 'order-product-line' }, `店铺：${shop}`),
    h('div', { class: 'order-product-line' }, `商品数量：${qty != null ? qty : '-'}`),
    h('div', { class: 'order-product-line' }, `佣金率：${formatRate(commRate)}`),
    h('div', { class: 'order-product-line' }, `服务费率：${formatRate(svcRate)}`)
  ])

  return h('div', { class: 'order-product-cell' }, [imageNode, contentNode])
}

function getDiagnosticSummary(row: any) {
  const status = row.attributionStatus || 'UNATTRIBUTED'
  const reason = row.unattributedReason || row.attributionRemark

  if (status === 'ATTRIBUTED') {
    return {
      tag: '可复盘',
      type: 'success' as const,
      text: row.pickSource ? `已通过 ${row.pickSource} 完成归因` : '已完成渠道归因'
    }
  }

  if (reason === 'NO_PICK_SOURCE' || !row.pickSource) {
    return {
      tag: '先查推广参数',
      type: 'error' as const,
      text: '订单未携带 pick_source'
    }
  }

  if (reason === 'MAPPING_NOT_FOUND') {
    return {
      tag: '先查转链映射',
      type: 'warning' as const,
      text: `未找到 ${row.pickSource} 对应映射`
    }
  }

  return {
    tag: '待排查',
    type: 'warning' as const,
    text: getAttributionReasonText(reason) || '等待补充排查原因'
  }
}

const columns = [
  { title: '订单号/结算时间', key: 'orderInfo', width: 220, render: (row: any) => h('div', { 'data-testid': 'order-row' }, [
    h('div', { style: 'font-weight: 600' }, row.orderId),
    h('div', { style: 'font-size: var(--text-xs); color: var(--text-tertiary)' }, row.settleTime || '-')
  ]) },
  { title: '商品信息', key: 'productInfo', width: 430, minWidth: 430, render: (row: any) => renderProductInfo(row) },
  { title: '订单金额', key: 'orderAmount', width: 100, render: (row: any) => formatMoney(row.orderAmount) },
  {
    title: '归因状态',
    key: 'attributionStatus',
    width: 120,
    render: (row: any) => {
      const status = row.attributionStatus || 'UNATTRIBUTED'
      const type = status === 'ATTRIBUTED' ? 'success' : (status === 'UNATTRIBUTED' ? 'error' : 'info')
      const labels: Record<string, string> = { ATTRIBUTED: '已归因', UNATTRIBUTED: '待排查', PARTIAL: '部分归因' }
      return h(NTag, { type, size: 'small', 'data-testid': 'order-attribution-status' }, { default: () => labels[status] || status })
    }
  },
  {
    title: '排查摘要',
    key: 'diagnosticSummary',
    minWidth: 220,
    render: (row: any) => {
      const summary = getDiagnosticSummary(row)
      return h('div', { class: 'diagnostic-summary' }, [
        h(NTag, { type: summary.type, size: 'small', round: true }, { default: () => summary.tag }),
        h(NText, { depth: 3, class: 'diagnostic-summary-text' }, { default: () => summary.text })
      ])
    }
  },
  { title: '渠道负责人', key: 'channelUserName', width: 120, render: (row: any) => h('span', { 'data-testid': 'order-channel' }, row.channelUserName || '-') },
  { title: '归因标识 (pick_source)', key: 'pickSource', width: 180, ellipsis: true },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    fixed: 'right',
    render: (row: any) => {
      return h(
        NButton,
        {
          size: 'small',
          quaternary: true,
          'data-testid': 'order-detail-button',
          onClick: () => openDetail(row)
        },
        { default: () => '查看详情' }
      )
    }
  }
]

function openDetail(row: any) {
  activeOrderId.value = String(row.orderId || '')
  showDetail.value = true
}

function formatDateTime(value: number, endOfDay = false) {
  const date = new Date(value)
  if (endOfDay) {
    date.setHours(23, 59, 59, 0)
  } else {
    date.setHours(0, 0, 0, 0)
  }
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function resolveDateRange() {
  if (!filters.dateRange) {
    return {}
  }
  const [start, end] = filters.dateRange
  return {
    startTime: formatDateTime(start),
    endTime: formatDateTime(end, true)
  }
}

function buildQueryParams() {
  return {
    page: pagination.page,
    size: pagination.pageSize,
    orderId: filters.orderId || undefined,
    activityId: filters.activityId || undefined,
    productId: filters.productId || undefined,
    channelKeyword: filters.channelKeyword || undefined,
    colonelKeyword: filters.colonelKeyword || undefined,
    // 用 CSV（逗号分隔）传给后端：Spring `@RequestParam List<UUID>` 自带 CSV 解析能力，
    // 避免依赖 axios 数组序列化（axios 1.x 默认会拼 `key[]=`，与 Spring 默认 binder 不兼容）。
    recruiterDeptIds: filters.recruiterDeptIds.length ? filters.recruiterDeptIds.join(',') : undefined,
    channelDeptIds: filters.channelDeptIds.length ? filters.channelDeptIds.join(',') : undefined,
    attributionStatus: filters.attributionStatus || undefined,
    timeField: filters.timeField || undefined,
    dashboardDiagnosis: filters.dashboardDiagnosis || undefined,
    ...resolveDateRange()
  }
}

const fetchData = async () => {
  const currentFetch = ++fetchVersion
  loading.value = true
  const params = buildQueryParams()
  void getOrderStats({
    orderId: params.orderId,
    attributionStatus: params.attributionStatus,
    activityId: params.activityId,
    productId: params.productId,
    channelKeyword: params.channelKeyword,
    colonelKeyword: params.colonelKeyword,
    recruiterDeptIds: params.recruiterDeptIds,
    channelDeptIds: params.channelDeptIds,
    timeField: params.timeField,
    dashboardDiagnosis: params.dashboardDiagnosis,
    startTime: params.startTime,
    endTime: params.endTime
  })
    .then((statsRes: any) => {
      if (currentFetch === fetchVersion) {
        stats.value = statsRes.data || null
      }
    })
    .catch((err: any) => {
      if (currentFetch === fetchVersion) {
        stats.value = null
      }
      console.warn('[orders] stats load failed', err)
    })

  try {
    const listRes: any = await getOrders(params)
    if (currentFetch !== fetchVersion) {
      return
    }
    data.value = listRes.data.records || []
    pagination.itemCount = listRes.data.total || 0
  } catch (err: any) {
    if (currentFetch === fetchVersion) {
      stats.value = null
      notifyApiFailure(err, message, { fallbackMessage: '加载订单列表失败' })
    }
  } finally {
    if (currentFetch === fetchVersion) {
      loading.value = false
    }
  }
}

const handlePageChange = (page: number) => {
  pagination.page = page
  fetchData()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = normalizePageSize(pageSize)
  pagination.page = 1
  fetchData()
}

// 同步触发后轮询窗口：
//   - 间隔 2s，避免压垮 stats 接口
//   - 超时 12s 兜底强刷，防止后端长时间未推进时无限等待
//   - 命中"lastSyncTime 变化"立即拉列表，体感比固定 1s setTimeout 更准
const SYNC_POLL_INTERVAL_MS = 2000
const SYNC_POLL_TIMEOUT_MS = 12000

const handleSync = async () => {
  syncLoading.value = true
  try {
    const now = new Date()
    const start = new Date()
    start.setDate(now.getDate() - 30)
    const range = filters.dateRange
      ? {
          startTime: formatDateTime(filters.dateRange[0]),
          endTime: formatDateTime(filters.dateRange[1], true)
        }
      : {
          startTime: formatDateTime(start.getTime()),
          endTime: formatDateTime(now.getTime(), true)
        }
    await syncOrders(range.startTime, range.endTime)
    message.success('已触发同步，订单回流中...')

    const baselineSyncTime = stats.value?.lastSyncTime ?? null
    const startedAt = Date.now()
    const pollSync = async (): Promise<void> => {
      try {
        const res: any = await getOrderStats(buildQueryParams())
        const latest = res?.data?.lastSyncTime ?? null
        if (latest && latest !== baselineSyncTime) {
          await fetchData()
          return
        }
      } catch (err) {
        // 轮询自身失败不打扰用户，超时分支兜底强刷
        console.warn('[orders] poll stats during sync failed', err)
      }
      if (Date.now() - startedAt < SYNC_POLL_TIMEOUT_MS) {
        setTimeout(pollSync, SYNC_POLL_INTERVAL_MS)
      } else {
        await fetchData()
      }
    }
    setTimeout(pollSync, SYNC_POLL_INTERVAL_MS)
  } catch (err: any) {
    notifyApiFailure(err, message, { fallbackMessage: '同步失败' })
  } finally {
    syncLoading.value = false
  }
}

const resetFilters = () => {
  filters.orderId = ''
  filters.activityId = ''
  filters.productId = ''
  filters.channelKeyword = null
  filters.colonelKeyword = null
  filters.recruiterDeptIds = []
  filters.channelDeptIds = []
  filters.attributionStatus = null
  filters.dateRange = null
  filters.timeField = 'createTime'
  filters.dashboardDiagnosis = ''
  stats.value = null
  pagination.page = 1
  fetchData()
}

watch(
  () => [
    route.query.orderId,
    route.query.activityId,
    route.query.productId,
    route.query.channelKeyword,
    route.query.colonelKeyword,
    route.query.timeField,
    route.query.dashboardDiagnosis
  ],
  () => {
    applyRouteFilters()
    pagination.page = 1
    fetchData()
  }
)

async function fetchDeptFilterOptions() {
  try {
    const res: any = await getOrderFilterOptions()
    const payload = res?.data || {}
    const map = (list: any): { label: string; value: string }[] => Array.isArray(list)
      ? list
          .filter((item: any) => item && item.value)
          .map((item: any) => ({ label: String(item.label || item.value), value: String(item.value) }))
      : []
    recruiterDeptOptions.value = map(payload.recruiterDepartments)
    channelDeptOptions.value = map(payload.channelDepartments)
  } catch (err) {
    // 部门下拉失败不阻塞页面主流程，仅提示一次
    notifyApiFailure(err as any, message, { fallbackMessage: '加载部门筛选项失败' })
  }
}

onMounted(() => {
  applyRouteFilters()
  void fetchChannelOptions('')
  void fetchRecruiterOptions('')
  void fetchDeptFilterOptions()
  fetchData()
})
</script>

<style scoped>
/* ---- 商品信息列 ---- */
.order-product-cell {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  min-width: 420px;
  max-width: 520px;
  box-sizing: border-box;
}

.order-product-image {
  width: 96px;
  height: 96px;
  flex: 0 0 96px;
  object-fit: cover;
  border-radius: 2px;
  background: #f5f5f5;
  display: block;
}

.order-product-image--placeholder {
  background: #f0f0f0;
}

.order-product-content {
  min-width: 0;
  flex: 1;
  line-height: 1.55;
  padding-top: 0;
}

.order-product-title {
  color: #ff2f2f;
  font-size: 14px;
  line-height: 20px;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: default;
}

.order-product-line {
  color: #555;
  font-size: 14px;
  line-height: 22px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary-label {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
}
.diagnostic-summary {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

.diagnostic-summary-text {
  font-size: var(--text-xs);
  line-height: 1.5;
  word-break: break-all;
}
</style>
