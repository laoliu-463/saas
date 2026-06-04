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

const props = defineProps<{ filters: OrderListFilters; timeField: OrderTimeField; dateRange: [number, number] | null }>()
const emit = defineEmits<{ (e: 'row-count', count: number): void; (e: 'export'): void }>()
const message = useMessage()
const authStore = useAuthStore()
const canExport = computed(() => authStore.isAdmin || authStore.isLeader)
const loading = ref(false)
const rows = ref<any[]>([])
const pagination = reactive(createPaginationState())

const fmtDT = (v: unknown) => {
  if (!v) return ''
  const s = String(v)
  return /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/.test(s) ? s.replace('T', ' ').slice(0, 19) : s
}
const copy = (t: string) => { navigator.clipboard.writeText(t).then(() => message.success('已复制'), () => message.error('复制失败')) }
const fv = (row: any, keys: string[]) => { for (const k of keys) { const v = row?.[k]; if (v != null && v !== '') return v } return null }
const fmtRate = (v: unknown) => {
  if (v == null || v === '') return '-'
  if (typeof v === 'string' && v.trim().endsWith('%')) return v.trim()
  const n = Number(v); if (!Number.isFinite(n) || n <= 0) return '-'
  if (n < 1) return `${Math.round(n * 10000) / 100}%`
  if (n >= 100) return `${Math.round(n) / 100}%`
  return `${n}%`
}

/* ── 列渲染 ── */
const rOrderId = (r: any) => {
  const id = String(fv(r, ['orderId', 'order_id']) || '-')
  return h('div', { class: 'od-cell' }, [h('button', { type: 'button', class: 'od-id', onClick: () => copy(id) }, id)])
}

const rActivity = (r: any) => {
  const name = fv(r, ['activityName']); const aid = fv(r, ['activityId']); const ct = fv(r, ['contentTypeText'])
  return h('div', { class: 'od-cell' }, [
    name ? h('div', { class: 'od-main', title: String(name) }, String(name)) : null,
    aid ? h('div', { class: 'od-muted' }, `ID：${aid}`) : null,
    ct ? h(NTag, { size: 'small', type: ct === '直播' ? 'warning' : 'info', round: true, bordered: false, style: 'align-self:flex-start;margin-top:2px' }, { default: () => String(ct) }) : null,
    !name && !aid ? h('span', { class: 'od-empty' }, '-') : null
  ])
}

const rProduct = (r: any) => {
  const img = fv(r, ['productImage', 'productPic'])
  const name = fv(r, ['productName', 'productTitle']) || '-'
  const pid = fv(r, ['productId']) || '-'
  const shop = fv(r, ['partnerName', 'shopName']) || '-'
  const qty = fv(r, ['productQuantity', 'itemNum'])
  const cr = fv(r, ['commissionRate']); const sr = fv(r, ['serviceFeeRate'])
  return h('div', { class: 'od-product' }, [
    img ? h('img', { class: 'od-img', src: String(img) }) : h('div', { class: 'od-img od-img-ph' }),
    h('div', { class: 'od-pcontent' }, [
      h('div', { class: 'od-ptitle', title: String(name) }, String(name)),
      h('div', { class: 'od-line' }, `商品ID：${pid}`),
      h('div', { class: 'od-line' }, `店铺：${shop}`),
      h('div', { class: 'od-line' }, `数量 x ${qty ?? '-'}`),
      h('div', { class: 'od-line' }, `佣金率：${fmtRate(cr)}`),
      h('div', { class: 'od-line' }, `服务费率：${fmtRate(sr)}`)
    ])
  ])
}

const rPartner = (r: any) => {
  const n = fv(r, ['partnerName', 'shopName']); const id = fv(r, ['partnerId', 'shopId'])
  return h('div', { class: 'od-cell' }, [
    n ? h('div', { class: 'od-main' }, String(n)) : null,
    id ? h('div', { class: 'od-muted' }, String(id)) : null,
    !n ? h('span', { class: 'od-empty' }, '-') : null
  ])
}

const rTalent = (r: any) => {
  const tn = fv(r, ['talentName']); const did = fv(r, ['talentDouyinId']); const vid = fv(r, ['videoId', 'awemeId'])
  return h('div', { class: 'od-cell' }, [
    tn ? h('div', { style: 'display:flex;align-items:center;gap:4px;flex-wrap:wrap' }, [
      h('span', { class: 'od-main' }, String(tn)),
      h(NTag, { size: 'small', type: 'warning', round: true, bordered: false }, { default: () => '达人' }),
      h('span', { class: 'od-dy' }, '抖')
    ]) : null,
    did && String(did) !== '-' ? h('div', { class: 'od-muted' }, String(did)) : null,
    vid && String(vid) !== '-' ? h('div', { class: 'od-muted' }, ['出单视频: ', h('span', { class: 'od-vid' }, String(vid))]) : null,
    !tn ? h('span', { class: 'od-empty' }, '-') : null
  ])
}

const rMedia = (r: any) => { const n = fv(r, ['channelName']); return h('div', { class: 'od-cell' }, [n ? h('div', { class: 'od-main' }, String(n)) : h('span', { class: 'od-empty' }, '-')]) }

const rRecruiter = (r: any) => { const n = fv(r, ['recruiterName', 'colonelName']); return h('div', { class: 'od-cell' }, [n ? h('div', { class: 'od-main' }, String(n)) : h('span', { class: 'od-empty' }, '-')]) }

const rTime = (r: any) => {
  const pt = fmtDT(fv(r, ['payTime', 'orderCreateTime']))
  const st = String(fv(r, ['settleStatusText']) || '-')
  const sc = st === '失效' ? '#e74c3c' : st === '已结算' ? '#18a058' : '#999'
  return h('div', { class: 'od-cell' }, [
    pt ? h('div', null, pt) : null,
    h('div', { style: `font-size:13px;color:${sc};margin-top:4px` }, st)
  ])
}

const columns = computed(() => [
  { type: 'selection' },
  { title: '订单ID', key: 'orderId', width: 180, fixed: 'left' as const, render: rOrderId },
  { title: '活动信息', key: 'activity', width: 200, render: rActivity },
  { title: '商品信息', key: 'product', width: 420, render: rProduct },
  { title: '合作方信息', key: 'partner', width: 160, render: rPartner },
  { title: '推广者', key: 'talent', width: 220, render: rTalent },
  { title: '媒介', key: 'channel', width: 110, render: rMedia },
  { title: '招商', key: 'recruiter', width: 110, render: rRecruiter },
  { title: '订单时间', key: 'orderTime', width: 200, render: rTime }
])

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getOrderDetailPage(buildOrderDetailPageParams({ filters: props.filters, timeField: props.timeField, dateRange: props.dateRange, page: pagination.page, pageSize: pagination.pageSize }))
    const p: any = res?.data || res; const recs = p?.records || p?.data?.records || []; const total = p?.total ?? p?.data?.total ?? 0
    rows.value = Array.isArray(recs) ? recs : []; pagination.itemCount = Number(total) || 0; emit('row-count', pagination.itemCount)
  } catch (e: any) { rows.value = []; pagination.itemCount = 0; emit('row-count', 0); notifyApiFailure(e, message, { fallbackMessage: '获取订单明细失败' }) }
  finally { loading.value = false }
}

const handlePageChange = (p: number) => { pagination.page = p; void fetchData() }
const handlePageSizeChange = (s: number) => { pagination.pageSize = s; pagination.page = 1; void fetchData() }
defineExpose({ refresh: fetchData, hasData: () => rows.value.length > 0 })
onMounted(() => { void fetchData() })
watch(() => [props.filters, props.timeField, props.dateRange], () => { pagination.page = 1; void fetchData() }, { deep: true })
</script>

<style scoped>
.table-panel { border-radius: 8px; padding: 20px 0 0; overflow: hidden; }
.table-toolbar { display: flex; justify-content: flex-end; align-items: center; gap: 14px; margin: 0 18px 12px; }
:deep(.n-data-table-th) { color: #111827; font-size: 15px; font-weight: 700; }
:deep(.n-data-table-td) { color: #242934; vertical-align: top; padding: 12px 10px !important; }
:deep(.od-cell) { display: flex; flex-direction: column; gap: 4px; line-height: 1.5; min-width: 0; }
:deep(.od-main) { color: #242934; font-weight: 500; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
:deep(.od-muted) { color: #4b5563; font-size: 13px; }
:deep(.od-line) { color: #4b5563; font-size: 13px; line-height: 20px; }
:deep(.od-empty) { color: #9ca3af; }
:deep(.od-id) { padding: 0; border: 0; background: transparent; color: #242934; font: inherit; cursor: pointer; word-break: break-all; text-align: left; }
:deep(.od-id:hover) { color: var(--primary-color, #2563eb); }
:deep(.od-product) { display: flex; align-items: flex-start; gap: 12px; min-width: 380px; }
:deep(.od-img) { width: 80px; height: 80px; flex: 0 0 80px; border-radius: 4px; object-fit: cover; background: #f3f4f6; }
:deep(.od-img-ph) { border: 1px solid #e5e7eb; }
:deep(.od-pcontent) { min-width: 0; flex: 1; }
:deep(.od-ptitle) { max-width: 280px; overflow: hidden; color: #ff2f2f; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
:deep(.od-dy) { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 4px; background: #111827; color: #fff; font-size: 11px; font-weight: 800; box-shadow: inset 2px 0 0 #23f4ee, inset -2px 0 0 #ff2d55; }
:deep(.od-vid) { color: #ff2f2f; word-break: break-all; }
</style>
