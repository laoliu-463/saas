<template>
  <section class="table-panel app-panel app-table-shell">
    <div class="table-toolbar">
      <n-popover trigger="click" placement="bottom-end">
        <template #trigger>
          <n-button quaternary>⚙ 自定义表头</n-button>
        </template>
        <n-checkbox-group v-model:value="visibleColumnKeys" class="column-picker">
          <n-checkbox
            v-for="column in detailConfigurableColumns"
            :key="column.key"
            :value="column.key"
          >
            {{ column.title }}
          </n-checkbox>
        </n-checkbox-group>
      </n-popover>
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
      :scroll-x="2800"
      :pagination="pagination"
      remote
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { NImage, NTag, NText, NTooltip, useMessage } from 'naive-ui'
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

const statusTextMap: Record<string, string> = {
  ORDERED: '待结算',
  SHIPPED: '待结算',
  FINISHED: '已结算',
  CANCELLED: '已失效',
  REFUNDED: '已退款'
}

const statusTagType = (text: string) => {
  if (text === '已结算') return 'success'
  if (text === '待结算') return 'warning'
  if (text === '已退款' || text === '已失效') return 'error'
  return 'info'
}

const resolveStatusText = (row: any) => {
  const raw = row.orderStatusText
  if (raw && statusTextMap[String(raw)]) return statusTextMap[String(raw)]
  if (raw && ['待结算', '已结算', '已退款', '已失效'].includes(String(raw))) return String(raw)
  const code = Number(row.orderStatus)
  if (code === 1 || code === 2) return '待结算'
  if (code === 3) return '已结算'
  if (code === 4) return '已失效'
  if (code === 5) return '已退款'
  return '-'
}

const fmtMoney = (v: unknown) => {
  if (v == null) return '-'
  const n = Number(v)
  return Number.isFinite(n) ? `¥${n.toFixed(2)}` : '-'
}

const fmtTrackMoney = (estimate: unknown, effective: unknown) => {
  const est = fmtMoney(estimate)
  const eff = fmtMoney(effective)
  return { est, eff }
}

const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text).then(
    () => message.success('已复制'),
    () => message.error('复制失败')
  )
}

const hasVisibleColumn = (key: string) => visibleColumnKeys.value.includes(key)

const detailConfigurableColumns = [
  { title: '活动信息', key: 'activity' },
  { title: '商品信息', key: 'product' },
  { title: '合作方信息', key: 'partner' },
  { title: '推广者', key: 'talent' },
  { title: '渠道', key: 'channel' },
  { title: '招商', key: 'recruiter' },
  { title: '订单状态', key: 'status' },
  { title: '订单额', key: 'orderAmount' },
  { title: '服务费收入', key: 'serviceFee' },
  { title: '技术服务费', key: 'techServiceFee' },
  { title: '服务费支出', key: 'serviceFeeExpense' },
  { title: '服务费收益', key: 'serviceFeeProfit' },
  { title: '招商提成', key: 'recruiterCommission' },
  { title: '渠道提成', key: 'channelCommission' },
  { title: '订单时间', key: 'orderTime' }
]

const visibleColumnKeys = ref(detailConfigurableColumns.map((c) => c.key))

const renderDualMoney = (estimate: unknown, effective: unknown) => {
  const { est, eff } = fmtTrackMoney(estimate, effective)
  return h('div', { class: 'table-multi-line' }, [
    h('div', `预估：${est}`),
    h(NText, { depth: 3 }, { default: () => `结算：${eff}` })
  ])
}

const columns = computed(() => {
  const cols: any[] = [
    {
      title: '订单ID',
      key: 'orderId',
      width: 200,
      fixed: 'left' as const,
      render: (row: any) =>
        h(
          'span',
          {
            class: 'order-id-cell',
            style: 'cursor:pointer;text-decoration:underline dotted',
            onClick: () => copyToClipboard(row.orderId || '')
          },
          row.orderId || '-'
        )
    }
  ]

  if (hasVisibleColumn('activity')) {
    cols.push({
      title: '活动信息',
      key: 'activity',
      width: 180,
      render: (row: any) =>
        h('div', { class: 'table-multi-line' }, [
          h('div', row.activityName || '未归属活动'),
          h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => row.activityId || '' })
        ])
    })
  }

  if (hasVisibleColumn('product')) {
    cols.push({
      title: '商品信息',
      key: 'product',
      width: 220,
      render: (row: any) =>
        h('div', { style: 'display:flex;gap:8px;align-items:flex-start' }, [
          row.productImage
            ? h(NImage, { src: row.productImage, width: 40, height: 40, objectFit: 'cover', previewDisabled: true, fallbackSrc: '' })
            : h('div', { style: 'width:40px;height:40px;background:#f5f5f5;border-radius:4px;flex-shrink:0' }),
          h('div', { class: 'table-multi-line', style: 'min-width:0' }, [
            h(
              NTooltip,
              { trigger: 'hover' },
              {
                trigger: () =>
                  h('div', { class: 'text-ellipsis', style: 'max-width:140px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap' }, row.productName || '-'),
                default: () => row.productName || '-'
              }
            ),
            h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => row.productId || '' })
          ])
        ])
    })
  }

  if (hasVisibleColumn('partner')) {
    cols.push({
      title: '合作方信息',
      key: 'partner',
      width: 160,
      render: (row: any) =>
        h('div', { class: 'table-multi-line' }, [
          h('div', row.partnerName || '-'),
          h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => row.partnerId || '' })
        ])
    })
  }

  if (hasVisibleColumn('talent')) {
    cols.push({
      title: '推广者',
      key: 'talent',
      width: 160,
      render: (row: any) =>
        h('div', { class: 'table-multi-line' }, [
          h('div', row.talentName || '-'),
          h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => row.talentId || '' })
        ])
    })
  }

  if (hasVisibleColumn('channel')) {
    cols.push({
      title: '渠道',
      key: 'channel',
      width: 120,
      render: (row: any) => row.channelName || '未归因'
    })
  }

  if (hasVisibleColumn('recruiter')) {
    cols.push({
      title: '招商',
      key: 'recruiter',
      width: 120,
      render: (row: any) => row.recruiterName || '未归因'
    })
  }

  if (hasVisibleColumn('status')) {
    cols.push({
      title: '订单状态',
      key: 'status',
      width: 110,
      render: (row: any) => {
        const text = resolveStatusText(row)
        return h(NTag, { type: statusTagType(text), size: 'small', bordered: false }, { default: () => text })
      }
    })
  }

  if (hasVisibleColumn('orderAmount')) {
    cols.push({
      title: '订单额',
      key: 'orderAmount',
      width: 180,
      sorter: (a: any, b: any) => Number(a.payAmount || 0) - Number(b.payAmount || 0),
      render: (row: any) =>
        h('div', { class: 'table-multi-line' }, [
          h('div', `支付：${fmtMoney(row.payAmount)}`),
          h(NText, { depth: 3 }, { default: () => `结算：${fmtMoney(row.settleAmount)}` })
        ])
    })
  }

  if (hasVisibleColumn('serviceFee')) {
    cols.push({
      title: '服务费收入',
      key: 'serviceFee',
      width: 180,
      render: (row: any) => renderDualMoney(row.estimateServiceFee, row.effectiveServiceFee)
    })
  }

  if (hasVisibleColumn('techServiceFee')) {
    cols.push({
      title: '技术服务费',
      key: 'techServiceFee',
      width: 180,
      render: (row: any) => renderDualMoney(row.estimateTechServiceFee, row.effectiveTechServiceFee)
    })
  }

  if (hasVisibleColumn('serviceFeeExpense')) {
    cols.push({
      title: '服务费支出',
      key: 'serviceFeeExpense',
      width: 180,
      render: (row: any) => renderDualMoney(row.estimateServiceFeeExpense, row.effectiveServiceFeeExpense)
    })
  }

  if (hasVisibleColumn('serviceFeeProfit')) {
    cols.push({
      title: '服务费收益',
      key: 'serviceFeeProfit',
      width: 180,
      render: (row: any) => renderDualMoney(row.estimateServiceProfit, row.effectiveServiceProfit)
    })
  }

  if (hasVisibleColumn('recruiterCommission')) {
    cols.push({
      title: '招商提成',
      key: 'recruiterCommission',
      width: 180,
      render: (row: any) => renderDualMoney(row.estimateRecruiterCommission, row.effectiveRecruiterCommission)
    })
  }

  if (hasVisibleColumn('channelCommission')) {
    cols.push({
      title: '渠道提成',
      key: 'channelCommission',
      width: 180,
      render: (row: any) => renderDualMoney(row.estimateChannelCommission, row.effectiveChannelCommission)
    })
  }

  if (hasVisibleColumn('orderTime')) {
    cols.push({
      title: '订单时间',
      key: 'orderTime',
      width: 180,
      render: (row: any) =>
        h(
          NTooltip,
          { trigger: 'hover' },
          {
            trigger: () => h('div', formatDateTime(row.payTime || row.orderCreateTime)),
            default: () =>
              h('div', { class: 'table-multi-line' }, [
                h('div', `付款：${formatDateTime(row.payTime)}`),
                h('div', `结算：${formatDateTime(row.settleTime)}`),
                h('div', `创建：${formatDateTime(row.orderCreateTime)}`)
              ])
          }
        )
    })
  }

  return cols
})

const formatDateTime = (v: unknown) => {
  if (!v) return '-'
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 19)
  return '-'
}

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
  padding: 36px 36px 12px;
}

.table-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;
}

.column-picker {
  display: grid;
  gap: 8px;
  min-width: 150px;
}

.table-multi-line {
  display: grid;
  gap: 4px;
  line-height: 1.5;
}

:deep(.n-data-table-th) {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

:deep(.n-data-table-td) {
  color: #242934;
  vertical-align: top;
}

.order-id-cell:hover {
  color: var(--n-text-color);
}
</style>
