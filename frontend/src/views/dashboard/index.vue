<template>
  <div class="dashboard-page">
    <n-space vertical :size="16">
      <n-space justify="space-between" align="center">
        <n-radio-group v-model:value="preset" size="small" @update:value="handlePresetChange">
          <n-radio-button value="today">今日</n-radio-button>
          <n-radio-button value="last7">近 7 天</n-radio-button>
          <n-radio-button value="month">本月</n-radio-button>
          <n-radio-button value="custom">自定义</n-radio-button>
        </n-radio-group>
        <n-space align="center">
          <n-date-picker
            v-model:value="range"
            type="datetimerange"
            clearable
            :disabled="preset !== 'custom'"
            @update:value="handleCustomRangeChange"
          />
          <n-button ghost type="primary" @click="loadSummary">刷新看板</n-button>
        </n-space>
      </n-space>

      <n-card title="归因业务概览" :segmented="{ content: true }">
        <n-grid responsive="screen" cols="1 s:2 m:3 l:6" :x-gap="16" :y-gap="16">
          <n-gi v-for="card in summaryCards" :key="card.key">
            <n-card hoverable size="small" class="summary-card" @click="card.action?.()">
              <n-statistic :label="card.label" :value="card.value" />
              <div v-if="card.hint" class="summary-hint">{{ card.hint }}</div>
            </n-card>
          </n-gi>
        </n-grid>
      </n-card>

      <n-card title="异常提醒" :segmented="{ content: true }">
        <n-empty v-if="!summary.unattributedReasons?.length" description="当前没有待排查异常" />
        <n-space v-else vertical :size="12">
          <div class="alert-head">当前有 {{ summary.unattributedOrderCount || 0 }} 条订单待排查</div>
          <n-space vertical :size="8">
            <div
              v-for="item in summary.unattributedReasons"
              :key="item.reason"
              class="reason-row"
              @click="jumpToOrders({ tab: 'unattributed', attributionStatus: 'UNATTRIBUTED', unattributedReason: item.reason })"
            >
              <span>{{ getAttributionReasonText(item.reason) }}</span>
              <n-tag type="warning" size="small">{{ item.count }} 条</n-tag>
            </div>
          </n-space>
        </n-space>
      </n-card>

      <n-grid responsive="screen" cols="1 l:2" :x-gap="16" :y-gap="16">
        <n-gi>
          <n-card title="渠道业绩排行 (Top 10)" :segmented="{ content: true }">
            <n-data-table :columns="channelColumns" :data="summary.channelPerformance || []" :pagination="false" max-height="400" />
          </n-card>
        </n-gi>
        <n-gi>
          <n-card title="招商业绩排行 (Top 10)" :segmented="{ content: true }">
            <n-data-table :columns="colonelColumns" :data="summary.colonelPerformance || []" :pagination="false" max-height="400" />
          </n-card>
        </n-gi>
      </n-grid>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { getSummary } from '../../api/dashboard'
import { getAttributionReasonText } from '../../constants/orderAttribution'

const message = useMessage()
const router = useRouter()
const summary = ref<any>({})
const preset = ref<'today' | 'last7' | 'month' | 'custom'>('last7')
const range = ref<[number, number] | null>(null)

const summaryCards = computed(() => [
  { key: 'total', label: '总订单数', value: summary.value.orderCount || 0, action: () => jumpToOrders({ tab: 'all' }) },
  {
    key: 'attributed',
    label: '已确认业绩数',
    value: summary.value.attributedOrderCount || 0,
    action: () => jumpToOrders({ tab: 'attributed', attributionStatus: 'ATTRIBUTED' })
  },
  {
    key: 'unattributed',
    label: '待排查订单数',
    value: summary.value.unattributedOrderCount || 0,
    action: () => jumpToOrders({ tab: 'unattributed', attributionStatus: 'UNATTRIBUTED' })
  },
  { key: 'gmv', label: '总 GMV (元)', value: formatMoney(summary.value.orderAmount) },
  {
    key: 'serviceFee',
    label: '服务费收入 (元)',
    value: formatMoney(summary.value.serviceFee),
    action: () => jumpToOrders({ tab: 'all' })
  },
  {
    key: 'rate',
    label: '归因成功率',
    value: `${((summary.value.attributionRate || 0) * 100).toFixed(1)}%`,
    hint: summary.value.unattributedOrderCount ? '点击待排查卡片查看异常' : '当前归因表现稳定'
  }
])

const channelColumns = [
  { title: '渠道负责人', key: 'channelUserName', render: (row: any) => row.channelUserName || '-' },
  { title: '订单量', key: 'orderCount', sorter: 'default' },
  { title: 'GMV', key: 'orderAmount', render: (row: any) => formatMoney(row.orderAmount) },
  { title: '预计服务费', key: 'serviceFee', render: (row: any) => formatMoney(row.serviceFee) }
]

const colonelColumns = [
  { title: '招商负责人', key: 'colonelUserName', render: (row: any) => row.colonelUserName || '-' },
  { title: '订单量', key: 'orderCount', sorter: 'default' },
  { title: 'GMV', key: 'orderAmount', render: (row: any) => formatMoney(row.orderAmount) },
  { title: '预计服务费', key: 'serviceFee', render: (row: any) => formatMoney(row.serviceFee) }
]

function formatMoney(value: number | null | undefined) {
  if (value === null || value === undefined) return '0.00'
  return (Number(value) / 100).toFixed(2)
}

function formatDateTime(timestamp: number) {
  const date = new Date(timestamp)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function getPresetRange(value: 'today' | 'last7' | 'month' | 'custom') {
  const now = new Date()
  if (value === 'today') {
    return [new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime(), now.getTime()] as [number, number]
  }
  if (value === 'month') {
    return [new Date(now.getFullYear(), now.getMonth(), 1).getTime(), now.getTime()] as [number, number]
  }
  return [now.getTime() - 7 * 24 * 60 * 60 * 1000, now.getTime()] as [number, number]
}

function jumpToOrders(query: Record<string, string> = {}) {
  router.push({ path: '/orders', query })
}

async function loadSummary() {
  try {
    const params = range.value ? { startTime: formatDateTime(range.value[0]), endTime: formatDateTime(range.value[1]) } : undefined
    const res: any = await getSummary(params)
    summary.value = res.data || {}
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '汇总数据加载失败')
  }
}

function handlePresetChange(value: 'today' | 'last7' | 'month' | 'custom') {
  if (value !== 'custom') {
    range.value = getPresetRange(value)
    loadSummary()
  }
}

function handleCustomRangeChange(value: [number, number] | null) {
  if (preset.value === 'custom' && value) {
    loadSummary()
  }
}

onMounted(() => {
  range.value = getPresetRange(preset.value)
  loadSummary()
})
</script>

<style scoped>
.dashboard-page {
  padding: 16px;
}

.summary-card {
  cursor: pointer;
}

.summary-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.alert-head {
  font-size: 14px;
  font-weight: 600;
}

.reason-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
</style>
