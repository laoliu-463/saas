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
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: any) => row.orderId"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <OrderDetailModal v-model:show="showDetail" :order-id="activeOrderId" />
  </div>
</template>

<script setup lang="ts">
import { h, computed, onMounted, reactive, ref, watch } from 'vue'
import { NButton, NSpace, NTag, NText, useMessage } from 'naive-ui'
import { useRoute } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import OrderDetailModal from './components/OrderDetailModal.vue'
import { getOrders, getOrderStats, syncOrders } from '../../api/order'
import { getAttributionReasonText } from '../../constants/orderAttribution'
import { createPaginationState, normalizePageSize } from '../../utils/pagination'

const message = useMessage()
const route = useRoute()
const loading = ref(false)
const syncLoading = ref(false)
const data = ref([])
const stats = ref<{ totalOrders?: number; attributedOrders?: number; unattributedOrders?: number; partialOrders?: number } | null>(null)
const showDetail = ref(false)
const activeOrderId = ref('')

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
  attributionStatus: null,
  dateRange: null as [number, number] | null,
  timeField: 'createTime',
  dashboardDiagnosis: ''
})

const pagination = reactive(createPaginationState())

let fetchVersion = 0

const applyRouteFilters = () => {
  filters.orderId = typeof route.query.orderId === 'string' ? route.query.orderId : ''
  filters.activityId = typeof route.query.activityId === 'string' ? route.query.activityId : ''
  filters.productId = typeof route.query.productId === 'string' ? route.query.productId : ''
  filters.timeField = typeof route.query.timeField === 'string' ? route.query.timeField : 'createTime'
  filters.dashboardDiagnosis = typeof route.query.dashboardDiagnosis === 'string' ? route.query.dashboardDiagnosis : ''
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
  { title: '商品信息', key: 'productTitle', minWidth: 200, ellipsis: true },
  { title: '订单金额', key: 'orderAmount', width: 100, render: (row: any) => `¥${row.orderAmount || 0}` },
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
      message.error('加载订单列表失败')
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
    setTimeout(fetchData, 1000)
  } catch (err: any) {
    message.error('同步失败')
  } finally {
    syncLoading.value = false
  }
}

const resetFilters = () => {
  filters.orderId = ''
  filters.activityId = ''
  filters.productId = ''
  filters.attributionStatus = null
  filters.dateRange = null
  filters.timeField = 'createTime'
  filters.dashboardDiagnosis = ''
  stats.value = null
  pagination.page = 1
  fetchData()
}

watch(
  () => [route.query.orderId, route.query.activityId, route.query.productId, route.query.timeField, route.query.dashboardDiagnosis],
  () => {
    applyRouteFilters()
    pagination.page = 1
    fetchData()
  }
)

onMounted(() => {
  applyRouteFilters()
  fetchData()
})
</script>

<style scoped>
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
