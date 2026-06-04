<template>
  <section class="table-panel app-panel app-table-shell">
    <div class="table-toolbar">
      <n-button v-if="canExport" type="primary" secondary data-testid="data-order-detail-export" @click="$emit('export')">
        导 出
      </n-button>
    </div>

    <n-data-table
      data-testid="data-order-detail-table"
      :columns="columns"
      :data="rows"
      :loading="loading"
      :row-key="(row: any) => row.orderId || row.orderCreateTime"
      :scroll-x="2200"
      :pagination="pagination"
      remote
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { NTag, useMessage } from 'naive-ui'
import { getOrderDetailPage } from '../../api/data'
import { buildOrderDetailPageParams, type OrderListFilters, type OrderTimeField } from './order-list-query'
import { createPaginationState } from '../../utils/pagination'
import { notifyApiFailure } from '../../utils/requestError'
import { useAuthStore } from '../../stores/auth'

const props = defineProps<{
  filters: OrderListFilters
  timeField: OrderTimeField
  dateRange: [number, number] | null
}>()

const emit = defineEmits<{
  (e: 'row-count', count: number): void
  (e: 'export'): void
}>()

const message = useMessage()
const authStore = useAuthStore()
const canExport = computed(() => authStore.isAdmin || authStore.isLeader)
const loading = ref(false)
const rows = ref<any[]>([])
const pagination = reactive(createPaginationState())

/* ── 格式化辅助 ── */

const formatDateTime = (v: unknown) => {
  if (v === null || v === undefined || v === '') return ''
  const value = String(v)
  if (/^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/.test(value)) {
    return value.replace('T', ' ').slice(0, 19)
  }
  return value
}

const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text).then(
    () => message.success('已复制'),
    () => message.error('复制失败')
  )
}

const firstDisplayValue = (row: any, keys: string[]) => {
  for (const key of keys) {
    const value = row?.[key]
    if (value !== null && value !== undefined && value !== '') return value
  }
  return null
}

/** 佣金率 / 服务费率兼容小数、整数百分比、基点和已带百分号的字符串。 */
const formatRate = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'string' && value.trim().endsWith('%')) return value.trim()
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return '-'
  if (num > 0 && num < 1) return `${Math.round(num * 10000) / 100}%`
  if (num >= 100) return `${Math.round(num) / 100}%`
  return `${num}%`
}

const getProductInfo = (row: any) => ({
  image: firstDisplayValue(row, ['productImage', 'product_image', 'productPic', 'product_pic', 'goodsImage', 'imageUrl', 'cover']),
  name: firstDisplayValue(row, ['productName', 'product_name', 'productTitle', 'product_title', 'goodsName', 'goodsTitle', 'title']) || '-',
  id: firstDisplayValue(row, ['productId', 'product_id', 'goodsId', 'goods_id', 'itemId']) || '-',
  shop: firstDisplayValue(row, ['shopName', 'shop_name', 'partnerName', 'partner_name', 'storeName', 'merchantName', 'merchant_name']) || '-',
  quantity: firstDisplayValue(row, ['productQuantity', 'product_quantity', 'quantity', 'goodsNum', 'goods_num', 'itemNum', 'item_num', 'itemCount']),
  commissionRate: firstDisplayValue(row, ['commissionRate', 'commission_rate', 'cosRatio', 'cos_ratio', 'activityCosRatio', 'activity_cos_ratio']),
  serviceFeeRate: firstDisplayValue(row, ['serviceFeeRate', 'service_fee_rate', 'serviceRate', 'service_rate', 'adServiceRatio', 'ad_service_ratio'])
})

const renderOrderId = (row: any) => {
  const orderId = String(firstDisplayValue(row, ['orderId', 'order_id']) || '-')
  const orderTypeText = firstDisplayValue(row, ['orderTypeText', 'order_type_text'])
  const contentTypeText = firstDisplayValue(row, ['contentTypeText', 'content_type_text'])
  return h('div', { class: 'order-detail-id-cell' }, [
    h(
      'button',
      {
        type: 'button',
        class: 'order-detail-id',
        title: '复制订单ID',
        onClick: () => copyToClipboard(orderId)
      },
      orderId
    ),
    orderTypeText ? h('div', { class: 'order-detail-subline' }, `订单类型：${orderTypeText}`) : null,
    contentTypeText
      ? h(NTag, { size: 'small', type: 'error', round: true, bordered: false, class: 'order-detail-content-tag' }, { default: () => String(contentTypeText) })
      : null
  ])
}

const renderActivityInfo = (row: any) => {
  const activityName = firstDisplayValue(row, ['activityName', 'activity_name'])
  const activityId = firstDisplayValue(row, ['activityId', 'activity_id'])
  return h('div', { class: 'order-detail-stack' }, [
    activityName ? h('div', { class: 'order-detail-main', title: String(activityName) }, String(activityName)) : null,
    activityId ? h('div', { class: 'order-detail-muted' }, `ID：${activityId}`) : null,
    !activityName && !activityId ? h('span', { class: 'order-detail-empty' }, '-') : null
  ])
}

const renderProductInfo = (row: any) => {
  const product = getProductInfo(row)
  const image = product.image
    ? h('img', { class: 'order-detail-product-image', src: String(product.image), alt: String(product.name) })
    : h('div', { class: 'order-detail-product-image order-detail-product-image--placeholder' })

  return h('div', { class: 'order-detail-product-cell' }, [
    image,
    h('div', { class: 'order-detail-product-content' }, [
      h('div', { class: 'order-detail-product-title', title: String(product.name) }, String(product.name)),
      h('div', { class: 'order-detail-product-line' }, `商品ID：${product.id}`),
      h('div', { class: 'order-detail-product-line' }, `店铺：${product.shop}`),
      h('div', { class: 'order-detail-product-line' }, `商品数量：${product.quantity != null ? product.quantity : '-'}`),
      h('div', { class: 'order-detail-product-line' }, `佣金率：${formatRate(product.commissionRate)}`),
      h('div', { class: 'order-detail-product-line' }, `服务费率：${formatRate(product.serviceFeeRate)}`)
    ])
  ])
}

const renderPartnerInfo = (row: any) => {
  const shopName = firstDisplayValue(row, ['partnerName', 'partner_name', 'shopName', 'shop_name', 'storeName', 'merchantName', 'merchant_name'])
  const colonelName = firstDisplayValue(row, ['colonelName', 'colonel_name', 'colonelUserName', 'colonel_user_name', 'recruiterName', 'recruiter_name'])
  return h('div', { class: 'order-detail-stack' }, [
    shopName ? h('div', { class: 'order-detail-line' }, [h('span', { class: 'order-detail-label' }, '商家:'), h('span', String(shopName))]) : null,
    colonelName ? h('div', { class: 'order-detail-line' }, [h('span', { class: 'order-detail-label' }, '团长:'), h('span', String(colonelName))]) : null,
    !shopName && !colonelName ? h('span', { class: 'order-detail-empty' }, '-') : null
  ])
}

const renderPromoterInfo = (row: any) => {
  const talentName = firstDisplayValue(row, ['talentName', 'talent_name', 'talentNickname', 'authorName'])
  const talentIdentity = firstDisplayValue(row, ['talentDouyinId', 'talent_douyin_id', 'talentId', 'talent_id', 'talentUid', 'authorId', 'authorBuyinId'])
  const videoId = firstDisplayValue(row, ['videoId', 'video_id', 'awemeId', 'aweme_id', 'itemId', 'item_id'])
  return h('div', { class: 'order-detail-talent-cell' }, [
    talentName
      ? h('div', { class: 'order-detail-talent-name' }, [
          h('span', String(talentName)),
          h(NTag, { size: 'small', type: 'warning', round: true, bordered: false, class: 'order-detail-talent-tag' }, { default: () => '达人' }),
          h('span', { class: 'order-detail-douyin-icon', 'aria-hidden': 'true' }, '抖')
        ])
      : null,
    talentIdentity ? h('div', { class: 'order-detail-muted' }, String(talentIdentity)) : null,
    videoId ? h('div', { class: 'order-detail-video-line' }, [h('span', '出单视频:'), h('span', { class: 'order-detail-video-id' }, String(videoId))]) : null,
    !talentName && !talentIdentity && !videoId ? h('span', { class: 'order-detail-empty' }, '-') : null
  ])
}

const renderMediaInfo = (row: any) => {
  const mediaName = firstDisplayValue(row, ['channelName', 'channel_name', 'channelUserName', 'channel_user_name'])
  const mediaGroup = firstDisplayValue(row, ['channelDeptName', 'channel_dept_name', 'mediaGroupName', 'media_group_name'])
  return h('div', { class: 'order-detail-stack', 'data-testid': 'data-order-detail-media' }, [
    mediaName ? h('div', { class: 'order-detail-main' }, String(mediaName)) : h('span', { class: 'order-detail-empty' }, '-'),
    mediaGroup ? h('div', { class: 'order-detail-muted' }, String(mediaGroup)) : null
  ])
}

const renderOrderTime = (row: any) => {
  const lines = [
    ['付款:', formatDateTime(firstDisplayValue(row, ['payTime', 'pay_time', 'createTime', 'create_time', 'orderCreateTime', 'order_create_time']))],
    ['收货:', formatDateTime(firstDisplayValue(row, ['deliveryTime', 'delivery_time', 'receiveTime', 'receive_time']))],
    ['结算:', formatDateTime(firstDisplayValue(row, ['settleTime', 'settle_time']))],
    ['失效:', formatDateTime(firstDisplayValue(row, ['expireTime', 'expire_time', 'invalidTime', 'invalid_time']))]
  ]
  return h('div', { class: 'order-detail-time-cell' }, lines.map(([label, value]) =>
    h('div', { class: 'order-detail-time-line' }, [
      h('span', { class: 'order-detail-time-label' }, label),
      h('span', { class: 'order-detail-time-value' }, value)
    ])
  ))
}

/* ── 列定义：按上游订单明细截图布局渲染 ── */

const columns = computed(() => {
  const cols: any[] = [
    { type: 'selection' },

    {
      title: '订单ID',
      key: 'orderId',
      width: 220,
      fixed: 'left' as const,
      render: renderOrderId
    },
    {
      title: '活动信息',
      key: 'activity',
      width: 240,
      render: renderActivityInfo
    },
    {
      title: '商品信息',
      key: 'product',
      width: 470,
      minWidth: 470,
      render: renderProductInfo
    },
    {
      title: '合作方信息',
      key: 'partner',
      width: 220,
      render: renderPartnerInfo
    },
    {
      title: '推广者',
      key: 'talent',
      width: 260,
      render: renderPromoterInfo
    },
    {
      title: '媒介',
      key: 'channel',
      width: 150,
      render: renderMediaInfo
    },
    {
      title: '订单时间',
      key: 'orderTime',
      width: 280,
      render: renderOrderTime
    }
  ]

  return cols
})

/* ── 数据请求 ── */

const fetchDetailData = async () => {
  loading.value = true
  try {
    const res = await getOrderDetailPage(
      buildOrderDetailPageParams({
        filters: props.filters,
        timeField: props.timeField,
        dateRange: props.dateRange,
        page: pagination.page,
        pageSize: pagination.pageSize
      })
    )
    const payload: any = res?.data || res
    const records = payload?.records || payload?.data?.records || []
    const total = payload?.total ?? payload?.data?.total ?? 0
    rows.value = Array.isArray(records) ? records : []
    pagination.itemCount = Number(total) || 0
    emit('row-count', pagination.itemCount)
  } catch (error: any) {
    rows.value = []
    pagination.itemCount = 0
    emit('row-count', 0)
    notifyApiFailure(error, message, { fallbackMessage: '获取订单明细失败' })
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  pagination.page = page
  void fetchDetailData()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize
  pagination.page = 1
  void fetchDetailData()
}

defineExpose({
  refresh: fetchDetailData,
  hasData: () => rows.value.length > 0
})

onMounted(() => {
  void fetchDetailData()
})

watch(
  () => [props.filters, props.timeField, props.dateRange],
  () => {
    pagination.page = 1
    void fetchDetailData()
  },
  { deep: true }
)
</script>

<style scoped>
.table-panel {
  border-radius: 8px;
  padding: 20px 0 0;
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 14px;
  margin: 0 18px 12px;
}

:deep(.n-data-table-th) {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

:deep(.n-data-table-td) {
  color: #242934;
  vertical-align: top;
  padding: 14px 10px !important;
}

:deep(.order-detail-id-cell),
:deep(.order-detail-stack),
:deep(.order-detail-talent-cell),
:deep(.order-detail-time-cell) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  line-height: 1.5;
}

:deep(.order-detail-id) {
  padding: 0;
  border: 0;
  background: transparent;
  color: #242934;
  font: inherit;
  line-height: 20px;
  text-align: left;
  word-break: break-all;
  cursor: pointer;
}

:deep(.order-detail-id:hover) {
  color: var(--primary-color, #2563eb);
}

:deep(.order-detail-subline),
:deep(.order-detail-muted),
:deep(.order-detail-line),
:deep(.order-detail-time-line),
:deep(.order-detail-product-line),
:deep(.order-detail-video-line) {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

:deep(.order-detail-content-tag) {
  align-self: flex-start;
  margin-top: 2px;
}

:deep(.order-detail-main) {
  max-width: 100%;
  overflow: hidden;
  color: #242934;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.order-detail-empty) {
  color: #9ca3af;
}

:deep(.order-detail-product-cell) {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  width: 100%;
  min-width: 450px;
  box-sizing: border-box;
}

:deep(.order-detail-product-image) {
  display: block;
  width: 104px;
  height: 104px;
  flex: 0 0 104px;
  border-radius: 2px;
  background: #f3f4f6;
  object-fit: cover;
}

:deep(.order-detail-product-image--placeholder) {
  border: 1px solid #e5e7eb;
}

:deep(.order-detail-product-content) {
  min-width: 0;
  flex: 1;
}

:deep(.order-detail-product-title) {
  max-width: 300px;
  overflow: hidden;
  color: #ff2f2f;
  font-size: 14px;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.order-detail-label) {
  margin-right: 4px;
  color: #6b7280;
}

:deep(.order-detail-talent-name) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: #242934;
  font-weight: 500;
}

:deep(.order-detail-talent-tag) {
  flex-shrink: 0;
}

:deep(.order-detail-douyin-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: #111827;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  box-shadow: inset 3px 0 0 #23f4ee, inset -3px 0 0 #ff2d55;
}

:deep(.order-detail-video-line) {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

:deep(.order-detail-video-id) {
  color: #ff2f2f;
  word-break: break-all;
}

:deep(.order-detail-time-line) {
  display: flex;
  gap: 6px;
  min-height: 20px;
}

:deep(.order-detail-time-label) {
  width: 40px;
  flex: 0 0 40px;
  color: #6b7280;
}

:deep(.order-detail-time-value) {
  color: #111827;
  white-space: nowrap;
}
</style>
