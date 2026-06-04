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

/* ── 格式化辅助 ── */

const formatDateTime = (v: unknown) => {
  if (!v) return '-'
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 19)
  return '-'
}

const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text).then(
    () => message.success('已复制'),
    () => message.error('复制失败')
  )
}

/** 佣金率 / 服务费率：值 > 100 视为万分比（‱），否则视为百分比（%） */
const fmtRate = (v: unknown) => {
  if (v == null) return '-'
  const n = Number(v)
  if (!Number.isFinite(n) || n <= 0) return '-'
  if (n >= 100) return `${(n / 100).toFixed(2)}%`
  return `${n}%`
}

/* ── 列定义：严格按截图 9 列 ── */

const columns = computed(() => {
  const cols: any[] = [
    { type: 'selection' },

    /* 1. 订单ID */
    {
      title: '订单ID',
      key: 'orderId',
      width: 180,
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
    },

    /* 2. 活动信息：名称 + ID + 内容类型标签 */
    {
      title: '活动信息',
      key: 'activity',
      width: 200,
      render: (row: any) =>
        h('div', { class: 'cell-multi' }, [
          h('div', { class: 'cell-main' }, row.activityName || '未归属活动'),
          h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => row.activityId || '' }),
          row.contentTypeText
            ? h(NTag, { size: 'small', type: row.contentTypeText === '直播' ? 'warning' : 'info', bordered: false, style: 'margin-top:2px;width:fit-content' }, { default: () => row.contentTypeText })
            : null
        ])
    },

    /* 3. 商品信息：图片 + 名称 + 商品ID + 店铺 + 数量 + 佣金率 + 服务费率 */
    {
      title: '商品信息',
      key: 'product',
      width: 320,
      render: (row: any) =>
        h('div', { style: 'display:flex;gap:8px;align-items:flex-start' }, [
          row.productImage
            ? h(NImage, {
                src: row.productImage,
                width: 48,
                height: 48,
                objectFit: 'cover',
                previewDisabled: true,
                fallbackSrc: '',
                style: 'border-radius:4px;flex-shrink:0'
              })
            : h('div', { style: 'width:48px;height:48px;background:#f5f5f5;border-radius:4px;flex-shrink:0' }),
          h('div', { class: 'cell-multi', style: 'min-width:0' }, [
            h(
              NTooltip,
              { trigger: 'hover' },
              {
                trigger: () =>
                  h('div', { class: 'text-ellipsis cell-main', style: 'max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap' }, row.productName || '-'),
                default: () => row.productName || '-'
              }
            ),
            h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => `ID: ${row.productId || '-'}` }),
            h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => row.partnerName || '-' }),
            h('div', { style: 'font-size:12px;color:#666;display:flex;flex-wrap:wrap;gap:4px 10px;margin-top:2px' }, [
              h('span', null, `数量 x ${row.productQuantity ?? '-'}`),
              h('span', null, `佣金率 ${fmtRate(row.commissionRate)}`),
              h('span', null, `服务费率 ${fmtRate(row.serviceFeeRate)}`)
            ])
          ])
        ])
    },

    /* 4. 合作方信息 */
    {
      title: '合作方信息',
      key: 'partner',
      width: 140,
      render: (row: any) =>
        h('div', { class: 'cell-multi' }, [
          h('div', { class: 'cell-main' }, row.partnerName || '-'),
          h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => row.partnerId || '' })
        ])
    },

    /* 5. 推广者：昵称 + "达人" 标签 + 抖音ID + 出单视频ID */
    {
      title: '推广者',
      key: 'talent',
      width: 200,
      render: (row: any) =>
        h('div', { class: 'cell-multi' }, [
          h('div', { style: 'display:flex;align-items:center;gap:4px' }, [
            h('span', { class: 'cell-main' }, row.talentName || '-'),
            h(NTag, { size: 'tiny', type: 'success', bordered: false }, { default: () => '达人' })
          ]),
          h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => row.talentDouyinId && row.talentDouyinId !== '-' ? row.talentDouyinId : '' }),
          row.videoId && row.videoId !== '-'
            ? h(NText, { depth: 3, style: 'font-size:12px' }, { default: () => `出单视频: ${row.videoId}` })
            : null
        ])
    },

    /* 6. 媒介 */
    {
      title: '媒介',
      key: 'channel',
      width: 100,
      render: (row: any) => row.channelName || '-'
    },

    /* 7. 招商 */
    {
      title: '招商',
      key: 'recruiter',
      width: 100,
      render: (row: any) => row.recruiterName || '-'
    },

    /* 8. 订单时间：付款时间 + 结算状态 */
    {
      title: '订单时间',
      key: 'orderTime',
      width: 180,
      render: (row: any) => {
        const statusText = row.settleStatusText || '-'
        const statusColor = statusText === '失效'
          ? '#e74c3c'
          : statusText === '已结算'
            ? '#18a058'
            : '#999'
        return h('div', { class: 'cell-multi' }, [
          h('div', null, formatDateTime(row.payTime)),
          h('div', { style: `font-size:12px;color:${statusColor};margin-top:2px` }, statusText)
        ])
      }
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
  padding: 36px 36px 12px;
}

.table-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;
}

.cell-multi {
  display: grid;
  gap: 4px;
  line-height: 1.5;
}

.cell-main {
  font-weight: 500;
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
