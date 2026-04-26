<template>
  <div class="orders-page">
    <n-space vertical :size="16">
      <n-grid responsive="screen" cols="1 s:2 m:3 l:6" :x-gap="16" :y-gap="16">
        <n-gi v-for="item in statCards" :key="item.key">
          <n-card size="small" hoverable class="stat-card">
            <n-statistic :label="item.label" :value="item.value" />
            <div v-if="item.hint" class="stat-hint">{{ item.hint }}</div>
          </n-card>
        </n-gi>
      </n-grid>

      <n-card title="订单回流工作台" :segmented="{ content: true }">
        <template #header-extra>
          <n-button :loading="loading" @click="refreshAll">刷新结果</n-button>
        </template>

        <n-card size="small" class="filter-card" :bordered="false">
          <div class="sync-hint">
            订单同步、归因和异常落库由后端自动处理，前端这里只展示最新处理结果。
          </div>

          <n-grid responsive="screen" cols="1 s:2 l:4" :x-gap="12" :y-gap="12">
            <n-gi>
              <n-form-item label="归因状态" label-placement="top">
                <n-select v-model:value="filters.attributionStatus" :options="attributionStatusOptions" clearable />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="未归因原因" label-placement="top">
                <n-select v-model:value="filters.unattributedReason" :options="reasonOptions" clearable />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="订单状态" label-placement="top">
                <n-select v-model:value="filters.orderStatus" :options="orderStatusOptions" clearable />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="商品" label-placement="top">
                <n-select
                  v-model:value="filters.productId"
                  :options="productOptions"
                  filterable
                  clearable
                  placeholder="请选择商品"
                />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="渠道" label-placement="top">
                <n-select
                  v-model:value="filters.channelKeyword"
                  :options="channelOptions"
                  filterable
                  clearable
                  placeholder="请选择渠道"
                />
              </n-form-item>
            </n-gi>
            <n-gi>
              <n-form-item label="招商" label-placement="top">
                <n-select
                  v-model:value="filters.colonelKeyword"
                  :options="colonelOptions"
                  filterable
                  clearable
                  placeholder="请选择招商"
                />
              </n-form-item>
            </n-gi>
            <n-gi :span="2">
              <n-form-item label="下单时间" label-placement="top">
                <n-date-picker v-model:value="filters.createRange" type="datetimerange" clearable style="width: 100%" />
              </n-form-item>
            </n-gi>
          </n-grid>

          <n-space justify="end">
            <n-button @click="resetFilters">重置筛选</n-button>
            <n-button type="primary" @click="applyFilters">查询</n-button>
          </n-space>
        </n-card>

        <n-tabs v-model:value="activeTab" type="line" animated>
          <n-tab-pane name="unattributed" tab="待排查订单">
            <n-data-table
              remote
              :columns="columns"
              :data="orders"
              :loading="loading"
              :pagination="pagination"
              @update:page="handlePageChange"
              @update:page-size="handlePageSizeChange"
            />
          </n-tab-pane>
          <n-tab-pane name="attributed" tab="已确认业绩">
            <n-data-table
              remote
              :columns="columns"
              :data="orders"
              :loading="loading"
              :pagination="pagination"
              @update:page="handlePageChange"
              @update:page-size="handlePageSizeChange"
            />
          </n-tab-pane>
          <n-tab-pane name="all" tab="全部订单">
            <n-data-table
              remote
              :columns="columns"
              :data="orders"
              :loading="loading"
              :pagination="pagination"
              @update:page="handlePageChange"
              @update:page-size="handlePageSizeChange"
            />
          </n-tab-pane>
        </n-tabs>
      </n-card>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NTag, useMessage } from 'naive-ui'
import { getOrderFilterOptions, getOrderStats, getOrders, getUnattributedOrders } from '../../api/order'
import { getAttributionReasonSuggestion, getAttributionReasonText, getAttributionStatusText } from '../../constants/orderAttribution'

type OrderTab = 'unattributed' | 'attributed' | 'all'

const DEFAULT_TAB: OrderTab = 'unattributed'
const message = useMessage()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const orders = ref<any[]>([])
const stats = ref<any>({})
const activeTab = ref<OrderTab>(DEFAULT_TAB)
const filters = reactive({
  attributionStatus: null as string | null,
  unattributedReason: null as string | null,
  orderStatus: null as string | null,
  productId: '',
  channelKeyword: '',
  colonelKeyword: '',
  createRange: null as [number, number] | null
})
type FilterOption = { label: string; value: string }
const filterOptionsLoading = ref(false)
const attributionStatusOptions = ref<FilterOption[]>([])
const reasonOptions = ref<FilterOption[]>([])
const orderStatusOptions = ref<FilterOption[]>([])
const productOptions = ref<{ label: string; value: string }[]>([])
const channelOptions = ref<{ label: string; value: string }[]>([])
const colonelOptions = ref<{ label: string; value: string }[]>([])

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  prefix: (info: any) => `共 ${info.itemCount} 条`
})


const statCards = computed(() => [
  { key: 'total', label: '总订单数', value: stats.value.totalOrders || 0 },
  { key: 'attributed', label: '已确认业绩数', value: stats.value.attributedOrders || 0 },
  { key: 'unattributed', label: '待排查异常数', value: stats.value.unattributedOrders || 0 },
  { key: 'sync-failed', label: '同步失败数', value: stats.value.syncFailedOrders || 0 },
  {
    key: 'success-rate',
    label: '归因成功率',
    value: buildAttributionRate(stats.value.attributedOrders, stats.value.totalOrders)
  },
  {
    key: 'last-sync',
    label: '最近同步时间',
    value: formatDateTimeText(stats.value.lastSyncTime),
    hint: stats.value.lastSyncTime ? '' : '暂无同步记录'
  }
])

const columns = [
  {
    title: '订单号',
    key: 'orderId',
    width: 200,
    render(row: any) {
      return row.orderId || row.externalOrderId || row.id || '-'
    }
  },
  {
    title: '商品 ID / 名称',
    key: 'productInfo',
    width: 260,
    render(row: any) {
      const title = row.productTitle || row.productName || '-'
      return `${row.productId || '-'} / ${title}`
    }
  },
  {
    title: '订单金额',
    key: 'orderAmount',
    width: 120,
    render(row: any) {
      return formatMoney(row.orderAmount)
    }
  },
  {
    title: '服务费',
    key: 'serviceFee',
    width: 120,
    render(row: any) {
      return formatMoney(row.settleColonelCommission)
    }
  },
  {
    title: 'pick_source',
    key: 'pickSource',
    width: 180,
    ellipsis: true,
    render(row: any) {
      return row.pickSource || '-'
    }
  },
  {
    title: '渠道归属',
    key: 'channelUserName',
    width: 140,
    render(row: any) {
      return row.channelUserName || row.channelUserId || '-'
    }
  },
  {
    title: '招商归属',
    key: 'colonelUserName',
    width: 140,
    render(row: any) {
      return row.colonelUserName || '-'
    }
  },
  {
    title: '归因状态',
    key: 'attributionStatus',
    width: 120,
    render(row: any) {
      const status = row.attributionStatus
      const type = status === 'ATTRIBUTED' ? 'success' : 'warning'
      return h(NTag, { type }, { default: () => getAttributionStatusText(status) })
    }
  },
  {
    title: '诊断结果',
    key: 'unattributedReason',
    width: 320,
    ellipsis: true,
    render(row: any) {
      if (row.attributionStatus === 'ATTRIBUTED') return '-'
      const reason = row.unattributedReason || row.attributionRemark
      return h('div', [
        h('div', { style: 'font-weight: 500; color: #d03050;' }, getAttributionReasonText(reason)),
        h('div', { style: 'font-size: 12px; color: #666; margin-top: 4px;' }, `建议：${getAttributionReasonSuggestion(reason)}`)
      ])
    }
  },
  {
    title: '下单时间',
    key: 'createTime',
    width: 180,
    render(row: any) {
      return formatDateTimeText(row.createTime)
    }
  },
  {
    title: '结算时间',
    key: 'settleTime',
    width: 180,
    render(row: any) {
      return formatDateTimeText(row.settleTime)
    }
  },
  {
    title: '同步时间',
    key: 'updateTime',
    width: 180,
    render(row: any) {
      return formatDateTimeText(row.updateTime)
    }
  }
]

function formatMoney(value: number | null | undefined) {
  if (value === null || value === undefined) return '-'
  return `¥${(Number(value) / 100).toFixed(2)}`
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function formatDateTimeText(value: string | number | Date | null | undefined) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function formatDateTime(timestamp: number) {
  return formatDateTimeText(timestamp)
}

function buildAttributionRate(attributed = 0, total = 0) {
  if (!total) return '0.0%'
  return `${((Number(attributed) / Number(total)) * 100).toFixed(1)}%`
}

function currentAttributionStatus() {
  if (activeTab.value === 'unattributed') return 'UNATTRIBUTED'
  if (activeTab.value === 'attributed') return 'ATTRIBUTED'
  return filters.attributionStatus || undefined
}

function buildQueryParams() {
  const [startTime, endTime] = filters.createRange || []
  return {
    page: pagination.page,
    pageSize: pagination.pageSize,
    attributionStatus: currentAttributionStatus(),
    unattributedReason: filters.unattributedReason || undefined,
    productId: filters.productId.trim() || undefined,
    channelKeyword: filters.channelKeyword.trim() || undefined,
    colonelKeyword: filters.colonelKeyword.trim() || undefined,
    orderStatus: filters.orderStatus || undefined,
    startTime: startTime ? formatDateTime(startTime) : undefined,
    endTime: endTime ? formatDateTime(endTime) : undefined
  }
}

async function loadFilterOptions() {
  filterOptionsLoading.value = true
  try {
    const res: any = await getOrderFilterOptions()
    const data = res?.data || {}
    orderStatusOptions.value = data.orderStatuses || []
    attributionStatusOptions.value = data.attributionStatuses || []
    reasonOptions.value = data.unattributedReasons || []
    productOptions.value = data.products || []
    channelOptions.value = data.channels || []
    colonelOptions.value = data.colonels || []
  } finally {
    filterOptionsLoading.value = false
  }
}

async function loadStats() {
  const params: any = buildQueryParams()
  if (activeTab.value === 'all') {
    delete params.attributionStatus
  }
  const res: any = await getOrderStats(params)
  stats.value = res.data || {}
}

async function loadOrders() {
  loading.value = true
  try {
    const params = buildQueryParams()
    const res: any =
      activeTab.value === 'unattributed' ? await getUnattributedOrders(params) : await getOrders(params)
    orders.value = res.data?.records || []
    pagination.itemCount = res.data?.total || 0
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '鍔犺浇澶辫触')
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadOrders(), loadStats()])
}

function handlePageChange(page: number) {
  pagination.page = page
  syncRouteQuery()
  refreshAll()
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  syncRouteQuery()
  refreshAll()
}

function applyFilters() {
  pagination.page = 1
  syncRouteQuery()
  refreshAll()
}

function resetFilters() {
  filters.attributionStatus = activeTab.value === 'all' ? null : currentAttributionStatus() || null
  filters.unattributedReason = null
  filters.orderStatus = null
  filters.productId = ''
  filters.channelKeyword = ''
  filters.colonelKeyword = ''
  filters.createRange = null
  pagination.page = 1
  pagination.pageSize = 10
  syncRouteQuery()
  refreshAll()
}

function syncRouteQuery() {
  router.replace({
    path: route.path,
    query: {
      tab: activeTab.value,
      page: pagination.page > 1 ? String(pagination.page) : undefined,
      pageSize: pagination.pageSize !== 10 ? String(pagination.pageSize) : undefined,
      attributionStatus: filters.attributionStatus || undefined,
      unattributedReason: filters.unattributedReason || undefined,
      orderStatus: filters.orderStatus || undefined,
      productId: filters.productId || undefined,
      channelKeyword: filters.channelKeyword || undefined,
      colonelKeyword: filters.colonelKeyword || undefined
    }
  })
}

function initFiltersFromRoute() {
  const tab = typeof route.query.tab === 'string' ? route.query.tab : DEFAULT_TAB
  activeTab.value = tab === 'attributed' || tab === 'all' ? tab : 'unattributed'

  const attr = typeof route.query.attributionStatus === 'string' ? route.query.attributionStatus : null
  const reason = typeof route.query.unattributedReason === 'string' ? route.query.unattributedReason : null
  const orderStatus = typeof route.query.orderStatus === 'string' ? route.query.orderStatus : null
  const page = typeof route.query.page === 'string' ? Number(route.query.page) : 1
  const pageSize = typeof route.query.pageSize === 'string' ? Number(route.query.pageSize) : 10

  filters.attributionStatus = attr
  filters.unattributedReason = reason
  filters.orderStatus = orderStatus
  filters.productId = typeof route.query.productId === 'string' ? route.query.productId : ''
  filters.channelKeyword = typeof route.query.channelKeyword === 'string' ? route.query.channelKeyword : ''
  filters.colonelKeyword = typeof route.query.colonelKeyword === 'string' ? route.query.colonelKeyword : ''
  pagination.page = Number.isFinite(page) && page > 0 ? page : 1
  pagination.pageSize = Number.isFinite(pageSize) && pageSize > 0 ? pageSize : 10

  if (activeTab.value === 'unattributed') {
    filters.attributionStatus = 'UNATTRIBUTED'
  } else if (activeTab.value === 'attributed') {
    filters.attributionStatus = 'ATTRIBUTED'
  }
}

watch(activeTab, () => {
  if (activeTab.value === 'unattributed') {
    filters.attributionStatus = 'UNATTRIBUTED'
  } else if (activeTab.value === 'attributed') {
    filters.attributionStatus = 'ATTRIBUTED'
  } else {
    filters.attributionStatus = null
  }
  pagination.page = 1
  syncRouteQuery()
  refreshAll()
})

watch(
  () => route.query,
  () => {
    initFiltersFromRoute()
  }
)

onMounted(() => {
  initFiltersFromRoute()
  loadFilterOptions()
  refreshAll()
})
</script>

<style scoped>
.orders-page {
  padding: 16px;
}

.stat-card {
  min-height: 108px;
}

.stat-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.filter-card {
  margin-bottom: 16px;
  background: #f8fafc;
}

.sync-hint {
  margin: 0 0 16px;
  color: #666;
  font-size: 13px;
}
</style>

