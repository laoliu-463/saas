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
      :scroll-x="3150"
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

const formatDateTime = (value: unknown) => {
  if (value === null || value === undefined || value === '') return ''
  const text = String(value)
  if (/^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/.test(text)) {
    return text.replace('T', ' ').slice(0, 19)
  }
  return text
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

const formatRate = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'string' && value.trim().endsWith('%')) return value.trim()
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return '-'
  if (num > 0 && num < 1) return `${Math.round(num * 10000) / 100}%`
  if (num >= 100) return `${Math.round(num) / 100}%`
  return `${num}%`
}

const formatMoney = (value: unknown) => {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'string' && value.trim() === '') return '-'
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return `¥${num.toFixed(2)}`
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
  return h('div', { class: 'od-cell' }, [
    h('button', {
      type: 'button',
      class: 'od-id',
      title: '复制订单ID',
      onClick: () => copyToClipboard(orderId)
    }, orderId),
    orderTypeText ? h('div', { class: 'od-muted' }, `订单类型：${orderTypeText}`) : null,
    contentTypeText
      ? h(NTag, { size: 'small', type: 'error', round: true, bordered: false, class: 'od-content-tag' }, { default: () => String(contentTypeText) })
      : null
  ])
}

const renderActivityInfo = (row: any) => {
  const activityName = firstDisplayValue(row, ['activityName', 'activity_name'])
  const activityId = firstDisplayValue(row, ['activityId', 'activity_id'])
  return h('div', { class: 'od-cell' }, [
    activityName ? h('div', { class: 'od-main', title: String(activityName) }, String(activityName)) : null,
    activityId ? h('div', { class: 'od-muted' }, `ID：${activityId}`) : null,
    !activityName && !activityId ? h('span', { class: 'od-empty' }, '-') : null
  ])
}

const renderProductInfo = (row: any) => {
  const product = getProductInfo(row)
  const image = product.image
    ? h('img', { class: 'od-img order-detail-product-image', src: String(product.image), alt: String(product.name) })
    : h('div', { class: 'od-img od-img-ph order-detail-product-image' })

  return h('div', { class: 'od-product' }, [
    image,
    h('div', { class: 'od-pcontent' }, [
      h('div', { class: 'od-ptitle', title: String(product.name) }, String(product.name)),
      h('div', { class: 'od-line' }, `商品ID：${product.id}`),
      h('div', { class: 'od-line' }, `店铺：${product.shop}`),
      h('div', { class: 'od-line' }, `商品数量：${product.quantity != null ? product.quantity : '-'}`),
      h('div', { class: 'od-line' }, `佣金率：${formatRate(product.commissionRate)}`),
      h('div', { class: 'od-line' }, `服务费率：${formatRate(product.serviceFeeRate)}`)
    ])
  ])
}

const renderPartnerInfo = (row: any) => {
  const shopName = firstDisplayValue(row, ['partnerName', 'partner_name', 'shopName', 'shop_name', 'storeName', 'merchantName', 'merchant_name'])
  const colonelName = firstDisplayValue(row, ['colonelName', 'colonel_name', 'colonelUserName', 'colonel_user_name'])
  return h('div', { class: 'od-cell' }, [
    shopName ? h('div', { class: 'od-line' }, [h('span', { class: 'od-label' }, '商家:'), h('span', String(shopName))]) : null,
    colonelName ? h('div', { class: 'od-line' }, [h('span', { class: 'od-label' }, '团长:'), h('span', String(colonelName))]) : null,
    !shopName && !colonelName ? h('span', { class: 'od-empty' }, '-') : null
  ])
}

const renderTalentInfo = (row: any) => {
  const talentName = firstDisplayValue(row, ['talentName', 'talent_name', 'talentNickname', 'authorName'])
  const talentIdentity = firstDisplayValue(row, ['talentDouyinId', 'talent_douyin_id', 'talentId', 'talent_id', 'talentUid', 'authorId', 'authorBuyinId'])
  const videoId = firstDisplayValue(row, ['videoId', 'video_id', 'awemeId', 'aweme_id', 'itemId', 'item_id'])
  return h('div', { class: 'od-cell' }, [
    talentName
      ? h('div', { class: 'od-talent-line' }, [
          h('span', { class: 'od-main' }, String(talentName)),
          h(NTag, { size: 'small', type: 'warning', round: true, bordered: false }, { default: () => '达人' }),
          h('span', { class: 'od-dy', 'aria-hidden': 'true' }, '抖')
        ])
      : null,
    talentIdentity ? h('div', { class: 'od-muted' }, String(talentIdentity)) : null,
    videoId ? h('div', { class: 'od-video-line' }, [h('span', '出单视频:'), h('span', { class: 'od-vid' }, String(videoId))]) : null,
    !talentName && !talentIdentity && !videoId ? h('span', { class: 'od-empty' }, '-') : null
  ])
}

const renderChannelInfo = (row: any) => {
  const channelName = firstDisplayValue(row, ['channelName', 'channel_name', 'channelUserName', 'channel_user_name', 'mediaName', 'media_name'])
  const channelGroup = firstDisplayValue(row, ['channelDeptName', 'channel_dept_name', 'channelGroupName', 'channel_group_name', 'mediaGroupName', 'media_group_name'])
  return h('div', { class: 'od-cell', 'data-testid': 'data-order-detail-channel' }, [
    channelName ? h('div', { class: 'od-main' }, String(channelName)) : h('span', { class: 'od-empty' }, '-'),
    channelGroup ? h('div', { class: 'od-muted' }, String(channelGroup)) : null
  ])
}

const renderRecruiterInfo = (row: any) => {
  const recruiterName = firstDisplayValue(row, ['recruiterName', 'recruiter_name', 'colonelName', 'colonel_name', 'colonelUserName', 'colonel_user_name'])
  return h('div', { class: 'od-cell' }, [
    recruiterName ? h('div', { class: 'od-main' }, String(recruiterName)) : h('span', { class: 'od-empty' }, '-')
  ])
}

const renderOrderStatus = (row: any) => {
  const statusText = firstDisplayValue(row, ['orderStatusText', 'order_status_text', 'settleStatusText', 'settle_status_text'])
  const statusCode = firstDisplayValue(row, ['orderStatus', 'order_status', 'status'])
  return h('div', { class: 'od-cell od-status-cell' }, [
    statusText ? h('div', { class: 'od-main' }, String(statusText)) : null,
    !statusText && statusCode !== null ? h('div', { class: 'od-main' }, String(statusCode)) : null,
    !statusText && statusCode === null ? h('span', { class: 'od-empty' }, '-') : null
  ])
}

const renderMoneyLines = (row: any, lines: Array<[string, string[]]>) =>
  h('div', { class: 'od-cell od-money-cell' }, lines.map(([label, keys]) =>
    h('div', { class: 'od-money-line' }, [
      h('span', { class: 'od-money-label' }, label),
      h('span', { class: 'od-money-value' }, formatMoney(firstDisplayValue(row, keys)))
    ])
  ))

const renderOrderAmount = (row: any) => renderMoneyLines(row, [
  ['付款:', ['payAmount', 'pay_amount', 'actualAmount', 'actual_amount', 'orderAmount', 'order_amount']],
  ['结算:', ['settleAmount', 'settle_amount']]
])

const renderServiceFeeIncome = (row: any) => renderMoneyLines(row, [
  ['预估:', ['estimateServiceFee', 'estimate_service_fee', 'serviceFeeIncome', 'service_fee_income']],
  ['结算:', ['effectiveServiceFee', 'effective_service_fee', 'settleServiceFee', 'settle_service_fee']]
])

const renderTechServiceFee = (row: any) => renderMoneyLines(row, [
  ['预估:', ['estimateTechServiceFee', 'estimate_tech_service_fee', 'estimateTechnicalServiceFee', 'estimate_technical_service_fee', 'technicalServiceFee', 'technical_service_fee']],
  ['结算:', ['effectiveTechServiceFee', 'effective_tech_service_fee', 'effectiveTechnicalServiceFee', 'effective_technical_service_fee']]
])

const renderServiceFeeExpense = (row: any) => renderMoneyLines(row, [
  ['预估:', ['estimateServiceFeeExpense', 'estimate_service_fee_expense', 'serviceFeeCost', 'service_fee_cost', 'serviceFeeExpense', 'service_fee_expense', 'talentCommission', 'talent_commission']],
  ['结算:', ['effectiveServiceFeeExpense', 'effective_service_fee_expense', 'settleServiceFeeExpense', 'settle_service_fee_expense']]
])

const renderServiceFeeProfit = (row: any) => renderMoneyLines(row, [
  ['预估:', ['estimateServiceProfit', 'estimate_service_profit', 'serviceFeeProfit', 'service_fee_profit']],
  ['结算:', ['effectiveServiceProfit', 'effective_service_profit', 'settleServiceFeeProfit', 'settle_service_fee_profit']]
])

const renderRecruiterCommission = (row: any) => renderMoneyLines(row, [
  ['预估:', ['estimateRecruiterCommission', 'estimate_recruiter_commission', 'recruiterCommission', 'recruiter_commission']],
  ['结算:', ['effectiveRecruiterCommission', 'effective_recruiter_commission', 'settleRecruiterCommission', 'settle_recruiter_commission']]
])

const renderChannelCommission = (row: any) => renderMoneyLines(row, [
  ['预估:', ['estimateChannelCommission', 'estimate_channel_commission', 'channelCommission', 'channel_commission']],
  ['结算:', ['effectiveChannelCommission', 'effective_channel_commission', 'settleChannelCommission', 'settle_channel_commission']]
])

const renderGrossProfit = (row: any) => renderMoneyLines(row, [
  ['预估:', ['estimateGrossProfit', 'estimate_gross_profit', 'grossProfit', 'gross_profit']],
  ['结算:', ['effectiveGrossProfit', 'effective_gross_profit', 'settleGrossProfit', 'settle_gross_profit']]
])

const renderOrderTime = (row: any) => {
  const lines = [
    ['付款:', formatDateTime(firstDisplayValue(row, ['payTime', 'pay_time', 'createTime', 'create_time', 'orderCreateTime', 'order_create_time']))],
    ['收货:', formatDateTime(firstDisplayValue(row, ['deliveryTime', 'delivery_time', 'receiveTime', 'receive_time']))],
    ['结算:', formatDateTime(firstDisplayValue(row, ['settleTime', 'settle_time']))],
    ['失效:', formatDateTime(firstDisplayValue(row, ['expireTime', 'expire_time', 'invalidTime', 'invalid_time']))]
  ]
  return h('div', { class: 'od-cell od-time-cell' }, lines.map(([label, value]) =>
    h('div', { class: 'od-time-line' }, [
      h('span', { class: 'od-time-label' }, label),
      h('span', { class: 'od-time-value' }, value || '-')
    ])
  ))
}

const columns = computed(() => [
  { title: '订单ID', key: 'orderId', width: 180, fixed: 'left' as const, render: renderOrderId },
  { title: '活动信息', key: 'activity', width: 190, render: renderActivityInfo },
  { title: '商品信息', key: 'product', width: 420, render: renderProductInfo },
  { title: '合作方信息', key: 'partner', width: 180, render: renderPartnerInfo },
  { title: '推广者', key: 'talent', width: 210, render: renderTalentInfo },
  { title: '渠道', key: 'channel', width: 140, render: renderChannelInfo },
  { title: '招商', key: 'recruiter', width: 140, render: renderRecruiterInfo },
  { title: '订单状态', key: 'orderStatus', width: 120, render: renderOrderStatus },
  { title: '订单额', key: 'amount', width: 150, render: renderOrderAmount },
  { title: '服务费收入', key: 'serviceFeeIncome', width: 150, render: renderServiceFeeIncome },
  { title: '技术服务费', key: 'techServiceFee', width: 150, render: renderTechServiceFee },
  { title: '服务费支出', key: 'serviceFeeExpense', width: 150, render: renderServiceFeeExpense },
  { title: '服务费收益', key: 'serviceFeeProfit', width: 150, render: renderServiceFeeProfit },
  { title: '招商提成', key: 'recruiterCommission', width: 150, render: renderRecruiterCommission },
  { title: '渠道提成', key: 'channelCommission', width: 150, render: renderChannelCommission },
  { title: '毛利', key: 'grossProfit', width: 150, render: renderGrossProfit },
  { title: '订单时间', key: 'orderTime', width: 250, render: renderOrderTime }
])

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
  padding: 12px 10px !important;
}

:deep(.od-cell) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  line-height: 1.5;
}

:deep(.od-main) {
  max-width: 100%;
  overflow: hidden;
  color: #242934;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.od-muted),
:deep(.od-line),
:deep(.od-video-line),
:deep(.od-time-line) {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

:deep(.od-empty) {
  color: #9ca3af;
}

:deep(.od-label) {
  margin-right: 4px;
  color: #6b7280;
}

:deep(.od-content-tag) {
  align-self: flex-start;
  margin-top: 2px;
}

:deep(.od-id) {
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

:deep(.od-id:hover) {
  color: var(--primary-color, #2563eb);
}

:deep(.od-product) {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

:deep(.od-img) {
  display: block;
  width: 80px;
  height: 80px;
  flex: 0 0 80px;
  border-radius: 4px;
  background: #f3f4f6;
  object-fit: cover;
}

:deep(.od-img-ph) {
  border: 1px solid #e5e7eb;
}

:deep(.od-pcontent) {
  min-width: 0;
  flex: 1;
}

:deep(.od-ptitle) {
  max-width: 280px;
  overflow: hidden;
  color: #ff2f2f;
  font-size: 14px;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.od-talent-line) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

:deep(.od-dy) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  background: #111827;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  box-shadow: inset 2px 0 0 #23f4ee, inset -2px 0 0 #ff2d55;
}

:deep(.od-video-line) {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

:deep(.od-vid) {
  color: #ff2f2f;
  word-break: break-all;
}

:deep(.od-time-line) {
  display: flex;
  gap: 6px;
  min-height: 20px;
}

:deep(.od-money-line) {
  display: flex;
  gap: 4px;
  min-height: 20px;
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

:deep(.od-money-label) {
  width: 40px;
  flex: 0 0 40px;
  color: #6b7280;
}

:deep(.od-money-value) {
  color: #111827;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

:deep(.od-time-label) {
  width: 40px;
  flex: 0 0 40px;
  color: #6b7280;
}

:deep(.od-time-value) {
  color: #111827;
  white-space: nowrap;
}
</style>
