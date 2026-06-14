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
        :scroll-x="2320"
        :row-key="(row: any) => row.orderId"
        :row-selection="{ type: 'selection' }"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <OrderDetailModal v-if="showDetail" v-model:show="showDetail" :order-id="activeOrderId" />
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { h, computed, onMounted, reactive, ref, watch } from 'vue'
import { NButton, NSpace, NTag, useMessage } from 'naive-ui'
import { useRoute } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import OrderDetailModal from './components/OrderDetailModal.vue'
import { getOrders, getOrderFilterOptions, getOrderStats, syncOrders } from '../../api/order'
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

/**
 * 格式化佣金率 / 服务费率。
 * 后端若返回 0.07（小数）则乘 100 展示 7%；若返回 7（整数百分比）则直接展示 7%。
 * 空值显示 '-'。
 */
function formatRate(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'string' && value.trim().endsWith('%')) return value.trim()
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  let pct: number
  if (num > 0 && num < 1) {
    // 小数形式（如 0.07 → 7%）
    pct = Math.round(num * 10000) / 100
  } else if (num >= 100) {
    // 基点形式（如 500 → 5%，1400 → 14%）
    pct = Math.round(num) / 100
  } else {
    pct = num
  }
  return `${pct}%`
}

function firstDisplayValue(row: any, keys: string[]) {
  for (const key of keys) {
    const value = row?.[key]
    if (value !== null && value !== undefined && value !== '') return value
  }
  return null
}

function getProductInfo(row: any) {
  return {
    image: firstDisplayValue(row, ['productImage', 'product_image', 'goodsImage', 'imageUrl', 'productPic', 'product_pic', 'cover']),
    name: firstDisplayValue(row, ['productTitle', 'product_title', 'productName', 'product_name', 'goodsName', 'goodsTitle', 'itemTitle', 'title']) || '-',
    id: firstDisplayValue(row, ['productId', 'product_id', 'goodsId', 'goods_id', 'itemId']) || '-',
    shop: firstDisplayValue(row, ['shopName', 'shop_name', 'storeName', 'partnerName', 'partner_name', 'merchantName', 'merchant_name']) || '-',
    quantity: firstDisplayValue(row, ['productQuantity', 'product_quantity', 'quantity', 'goodsNum', 'goods_num', 'itemNum', 'item_num', 'itemCount']),
    commissionRate: firstDisplayValue(row, ['commissionRate', 'commission_rate', 'cosRatio', 'cos_ratio', 'activityCosRatio', 'activity_cos_ratio']),
    serviceFeeRate: firstDisplayValue(row, ['serviceFeeRate', 'service_fee_rate', 'serviceRate', 'service_rate', 'adServiceRatio', 'ad_service_ratio'])
  }
}

function getChannelInfo(row: any) {
  return {
    id: firstDisplayValue(row, [
      'channelId',
      'channel_id',
      'channelUserId',
      'channel_user_id'
    ]) || '',
    name: firstDisplayValue(row, [
      'channelName',
      'channel_name',
      'channelUserName',
      'channel_user_name'
    ]) || '-'
  }
}

/**
 * 渲染商品信息列：左图右文布局（96px 图片 + 右侧详情文本）。
 * 严格按照用户截图样式展示：图片顶部对齐、标题红色省略、元信息灰字紧凑排列。
 */
function renderProductInfo(row: any) {
  const product = getProductInfo(row)

  const imageNode = product.image
    ? h('img', { class: 'order-product-image', src: product.image, alt: product.name })
    : h('div', { class: 'order-product-image order-product-image--placeholder' })

  const contentNode = h('div', { class: 'order-product-content' }, [
    h('div', { class: 'order-product-title', title: product.name }, product.name),
    h('div', { class: 'order-product-line' }, `商品ID：${product.id}`),
    h('div', { class: 'order-product-line' }, `店铺：${product.shop}`),
    h('div', { class: 'order-product-line' }, `商品数量：${product.quantity != null ? product.quantity : '-'}`),
    h('div', { class: 'order-product-line' }, `佣金率：${formatRate(product.commissionRate)}`),
    h('div', { class: 'order-product-line' }, `服务费率：${formatRate(product.serviceFeeRate)}`)
  ])

  return h('div', { class: 'order-product-cell' }, [imageNode, contentNode])
}

/** 格式化日期时间，null/undefined 返回空字符串 */
function formatTime(value: unknown): string {
  if (value === null || value === undefined || value === '') return ''
  const s = String(value)
  // 已经是 YYYY-MM-DD HH:mm:ss 格式 (19字符)
  if (/^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/.test(s)) {
    return s.replace('T', ' ').slice(0, 19)
  }
  return s
}

function renderTalentInfo(row: any) {
  const talentName = firstDisplayValue(row, ['talentName', 'talent_name', 'talentNickname', 'authorName'])
  const talentId = firstDisplayValue(row, ['talentId', 'talent_id', 'talentUid', 'authorId', 'authorBuyinId'])
  const awemeId = firstDisplayValue(row, ['awemeId', 'aweme_id', 'videoId', 'video_id', 'itemId', 'item_id'])

  const nodes: any[] = []
  if (talentName) {
    nodes.push(h('div', { class: 'talent-name-line' }, [
      h('span', { class: 'talent-name' }, String(talentName)),
      talentId ? h('span', { class: 'talent-id' }, `ID: ${talentId}`) : null,
      h(NTag, { type: 'info', size: 'tiny', round: true, class: 'talent-tag' }, { default: () => '达人' }),
      h('span', { class: 'douyin-icon', innerHTML: '<svg viewBox="0 0 24 24" width="14" height="14"><path fill="currentColor" d="M16.6 5.82s.51.5 0 0A4.28 4.28 0 0 1 15.54 3h-3.09v12.04c0 .76-.42 1.65-1.65 1.65-1.03 0-1.84-.85-1.84-1.88 0-1.1.83-1.84 1.84-1.88.19 0 .41.02.57.08V9.8a5.07 5.07 0 0 0-.57-.03C7.83 9.77 6 11.57 6 14.57c0 2.79 2.05 4.63 4.8 4.63 2.76 0 4.84-1.84 4.84-4.63V8.56a7.3 7.3 0 0 0 4.36 1.43V6.9a4.28 4.28 0 0 1-3.4-1.08z"/></svg>' })
    ]))
  }
  if (awemeId) {
    nodes.push(h('div', { class: 'aweme-line' }, [
      h('span', { class: 'aweme-label' }, '出单视频:'),
      h('span', { class: 'aweme-id' }, String(awemeId))
    ]))
  }
  if (!talentName && !awemeId) {
    nodes.push(h('span', { style: 'color: #999' }, '-'))
  }
  return h('div', { class: 'talent-cell' }, nodes)
}

function renderOrderTime(row: any) {
  const payTime = formatTime(firstDisplayValue(row, ['payTime', 'pay_time', 'createTime', 'create_time']))
  const deliveryTime = formatTime(firstDisplayValue(row, ['deliveryTime', 'delivery_time']))
  const settleTime = formatTime(firstDisplayValue(row, ['settleTime', 'settle_time']))
  const expireTime = formatTime(firstDisplayValue(row, ['expireTime', 'expire_time']))

  return h('div', { class: 'order-time-cell' }, [
    h('div', { class: 'time-line' }, [h('span', { class: 'time-label' }, '付款:'), h('span', { class: 'time-value' }, payTime || '')]),
    h('div', { class: 'time-line' }, [h('span', { class: 'time-label' }, '收货:'), h('span', { class: 'time-value' }, deliveryTime || '')]),
    h('div', { class: 'time-line' }, [h('span', { class: 'time-label' }, '结算:'), h('span', { class: 'time-value' }, settleTime || '')]),
    h('div', { class: 'time-line' }, [h('span', { class: 'time-label' }, '失效:'), h('span', { class: 'time-value' }, expireTime || '')])
  ])
}

function renderPartnerInfo(row: any) {
  const shopName = firstDisplayValue(row, ['shopName', 'shop_name', 'partnerName', 'partner_name', 'storeName'])
  const colonelName = firstDisplayValue(row, ['colonelUserName', 'colonel_user_name', 'colonelName', 'colonel_name'])

  const nodes: any[] = []
  if (shopName) {
    nodes.push(h('div', { class: 'partner-line' }, [h('span', { class: 'partner-label' }, '商家:'), h('span', String(shopName))]))
  }
  if (colonelName) {
    nodes.push(h('div', { class: 'partner-line' }, [h('span', { class: 'partner-label' }, '团长:'), h('span', String(colonelName))]))
  }
  if (!shopName && !colonelName) {
    nodes.push(h('span', { style: 'color: #999' }, '-'))
  }
  return h('div', { class: 'partner-cell' }, nodes)
}

function renderActivityInfo(row: any) {
  const activityName = firstDisplayValue(row, ['activityName', 'activity_name'])
  const activityId = firstDisplayValue(row, ['activityId', 'activity_id'])

  const nodes: any[] = []
  if (activityName) {
    nodes.push(h('div', { class: 'activity-name', title: String(activityName) }, String(activityName)))
  }
  if (activityId) {
    nodes.push(h('div', { class: 'activity-id-line' }, `ID: ${activityId}`))
  }
  if (!activityName && !activityId) {
    nodes.push(h('span', { style: 'color: #999' }, '-'))
  }
  return h('div', { class: 'activity-cell' }, nodes)
}

function renderOrderId(row: any) {
  const orderTypeText = row.orderTypeText || ''
  const contentTypeText = row.contentTypeText || ''

  const nodes: any[] = [
    h('div', { class: 'order-id-text' }, String(row.orderId || '-'))
  ]
  if (orderTypeText) {
    nodes.push(h('div', { class: 'order-type-text' }, orderTypeText))
  }
  if (contentTypeText) {
    nodes.push(h(NTag, { type: 'error', size: 'tiny', round: true, class: 'content-type-tag' }, { default: () => contentTypeText }))
  }
  return h('div', { class: 'order-id-cell' }, nodes)
}

function openDetail(row: any) {
  const orderId = firstDisplayValue(row, ['orderId', 'order_id'])
  if (!orderId) {
    message.warning('订单 ID 缺失，无法打开详情')
    return
  }
  activeOrderId.value = String(orderId)
  showDetail.value = true
}

const columns = [
  {
    title: '订单ID',
    key: 'orderId',
    width: 210,
    fixed: 'left' as const,
    render: (row: any) => renderOrderId(row)
  },
  {
    title: '活动信息',
    key: 'activityInfo',
    width: 180,
    render: (row: any) => renderActivityInfo(row)
  },
  {
    title: '商品信息',
    key: 'productInfo',
    width: 430,
    minWidth: 430,
    render: (row: any) => renderProductInfo(row)
  },
  {
    title: '合作方信息',
    key: 'partnerInfo',
    width: 180,
    render: (row: any) => renderPartnerInfo(row)
  },
  {
    title: '推广者',
    key: 'talentInfo',
    width: 280,
    render: (row: any) => renderTalentInfo(row)
  },
  {
    title: '渠道',
    key: 'channelInfo',
    width: 120,
    render: (row: any) => {
      const ch = getChannelInfo(row)
      const name = ch.name
      return h('div', { 'data-testid': 'order-channel', class: 'channel-cell' },
        name === '-' ? h('span', { style: 'color: #999' }, '未归因') : h('span', name)
      )
    }
  },
  {
    title: '订单时间',
    key: 'orderTime',
    width: 260,
    render: (row: any) => renderOrderTime(row)
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    fixed: 'right' as const,
    render: (row: any) => h(
      NButton,
      {
        size: 'small',
        secondary: true,
        'data-testid': 'order-detail-button',
        onClick: () => openDetail(row)
      },
      { default: () => '详情' }
    )
  }
]

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
/* ---- 订单ID列 ---- */
.order-id-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.order-id-text {
  font-weight: 600;
  font-size: 13px;
  word-break: break-all;
}
.order-type-text {
  font-size: 12px;
  color: #666;
}
.content-type-tag {
  margin-top: 2px;
  align-self: flex-start;
}

/* ---- 活动信息列 ---- */
.activity-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.activity-name {
  font-weight: 500;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160px;
  cursor: default;
}
.activity-id-line {
  font-size: 12px;
  color: #888;
}

/* ---- 合作方信息列 ---- */
.partner-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.partner-line {
  font-size: 13px;
  line-height: 20px;
}
.partner-label {
  color: #888;
  margin-right: 4px;
}

/* ---- 推广者列 ---- */
.talent-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.talent-name-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.talent-name {
  font-weight: 500;
  font-size: 13px;
}
.talent-id {
  font-size: 12px;
  color: #888;
}
.talent-tag {
  flex-shrink: 0;
}
.douyin-icon {
  display: inline-flex;
  align-items: center;
  color: #333;
}
.aweme-line {
  font-size: 12px;
  line-height: 18px;
}
.aweme-label {
  color: #888;
}
.aweme-id {
  color: #ff2f2f;
  font-weight: 500;
}

/* ---- 渠道列 ---- */
.channel-cell {
  font-size: 13px;
}

/* ---- 订单时间列 ---- */
.order-time-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.time-line {
  font-size: 12px;
  line-height: 18px;
  display: flex;
  gap: 4px;
}
.time-label {
  color: #888;
  flex-shrink: 0;
  width: 36px;
}
.time-value {
  color: #333;
  white-space: nowrap;
}

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

/* ---- 行高控制 ---- */
:deep(.n-data-table-td) {
  padding: 12px 8px !important;
  vertical-align: top !important;
}
</style>
