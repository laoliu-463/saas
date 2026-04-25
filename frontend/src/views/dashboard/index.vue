<template>
  <div class="dashboard-page">
    <n-space vertical :size="16">
      <n-card title="归因业务概览" :segmented="{ content: true }">
        <n-grid :cols="5" :x-gap="16" :y-gap="16">
          <n-gi>
            <n-statistic label="总订单数" :value="summary.orderCount || 0" />
          </n-gi>
          <n-gi>
            <n-statistic label="归因成功数" :value="summary.attributedOrderCount || 0" />
          </n-gi>
          <n-gi>
            <n-statistic label="待归因数" :value="summary.unattributedOrderCount || 0" />
          </n-gi>
          <n-gi>
            <n-statistic label="总 GMV (元)" :value="formatMoney(summary.orderAmount)" />
          </n-gi>
          <n-gi>
            <n-statistic label="服务费收入 (元)" :value="formatMoney(summary.serviceFee)" />
          </n-gi>
        </n-grid>
      </n-card>

      <n-grid :cols="2" :x-gap="16" :y-gap="16">
        <n-gi>
          <n-card title="渠道业绩排行 (Top 10)" :segmented="{ content: true }">
            <n-data-table
              :columns="channelColumns"
              :data="summary.channelPerformance || []"
              :pagination="false"
              max-height="400"
            />
          </n-card>
        </n-gi>
        <n-gi>
          <n-card title="招商业绩排行 (Top 10)" :segmented="{ content: true }">
            <n-data-table
              :columns="colonelColumns"
              :data="summary.colonelPerformance || []"
              :pagination="false"
              max-height="400"
            />
          </n-card>
        </n-gi>
      </n-grid>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { getSummary } from '../../api/dashboard'

const message = useMessage()
const summary = ref<any>({})

const channelColumns = [
  {
    title: '渠道负责人',
    key: 'channelUserName',
    render(row: any) {
      return row.channelUserName || row.channelUserId || '-'
    }
  },
  {
    title: '订单量',
    key: 'orderCount',
    sorter: 'default'
  },
  {
    title: 'GMV',
    key: 'orderAmount',
    render(row: any) {
      return formatMoney(row.orderAmount)
    }
  },
  {
    title: '预估服务费',
    key: 'serviceFee',
    render(row: any) {
      return formatMoney(row.serviceFee)
    }
  }
]

const colonelColumns = [
  {
    title: '招商负责人',
    key: 'colonelUserName',
    render(row: any) {
      return row.colonelUserName || row.colonelUserId || '-'
    }
  },
  {
    title: '订单量',
    key: 'orderCount',
    sorter: 'default'
  },
  {
    title: 'GMV',
    key: 'orderAmount',
    render(row: any) {
      return formatMoney(row.orderAmount)
    }
  },
  {
    title: '预估服务费',
    key: 'serviceFee',
    render(row: any) {
      return formatMoney(row.serviceFee)
    }
  }
]

function formatMoney(value: number) {
  if (value === null || value === undefined) return '0.00'
  return (Number(value) / 100).toFixed(2)
}

async function loadSummary() {
  try {
    const res: any = await getSummary()
    summary.value = res.data || {}
  } catch (error: any) {
    message.error(error?.message || '汇总数据加载失败')
  }
}

onMounted(loadSummary)
</script>

<style scoped>
.dashboard-page {
  padding: 16px;
}
</style>
