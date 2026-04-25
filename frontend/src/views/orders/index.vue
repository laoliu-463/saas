<template>
  <div class="orders-page">
    <n-card title="订单归因工作台" :segmented="{ content: true }">
      <template #header-extra>
        <n-space>
          <n-date-picker
            v-model:value="range"
            type="datetimerange"
            clearable
            placeholder="选择同步时间范围"
          />
          <n-button type="primary" :loading="syncing" @click="handleSync">
            同步并归因
          </n-button>
          <n-button :loading="loading" @click="loadOrders">
            刷新订单
          </n-button>
        </n-space>
      </template>

      <n-alert v-if="syncResult" type="success" style="margin-bottom: 16px" closable>
        <div class="sync-summary">
          <span>同步完成</span>
          <span>拉取 {{ syncResult.totalFetched || 0 }} 条</span>
          <span class="summary-strong">新增 {{ syncResult.created || 0 }} 条</span>
          <span class="summary-strong">更新 {{ syncResult.updated || 0 }} 条</span>
          <span>已归因 {{ syncResult.attributed || 0 }} 条</span>
          <span>未归因 {{ syncResult.unattributed || 0 }} 条</span>
        </div>
        <div class="sync-hint">
          列表按最近更新时间倒序显示，刚同步完成的订单会排在前面。
        </div>
      </n-alert>

      <n-tabs v-model:value="activeTab" type="line" animated>
        <n-tab-pane name="all" tab="全部订单">
          <n-data-table
            remote
            :columns="columns"
            :data="orders"
            :loading="loading"
            :pagination="pagination"
            @update:page="handlePageChange"
          />
        </n-tab-pane>
        <n-tab-pane name="unattributed" tab="未归因订单">
          <n-data-table
            remote
            :columns="columns"
            :data="orders"
            :loading="loading"
            :pagination="pagination"
            @update:page="handlePageChange"
          />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref, watch } from 'vue'
import { NTag, useMessage } from 'naive-ui'
import { getOrders, getUnattributedOrders, syncOrders } from '../../api/order'

const message = useMessage()

const loading = ref(false)
const syncing = ref(false)
const orders = ref<any[]>([])
const syncResult = ref<any>(null)
const range = ref<[number, number] | null>(null)
const activeTab = ref('all')

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  prefix: (info: any) => `共 ${info.itemCount} 条`
})

const attributionStatusMap: Record<string, string> = {
  ATTRIBUTED: '已归因',
  UNATTRIBUTED: '未归因',
  PARTIAL: '部分归因',
  FAILED: '归因失败'
}

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
    title: '商品',
    key: 'productTitle',
    width: 220,
    ellipsis: true,
    render(row: any) {
      return row.productTitle || row.productName || row.productId || '-'
    }
  },
  {
    title: '渠道负责人',
    key: 'channelUserName',
    width: 120,
    render(row: any) {
      return row.channelUserName || '-'
    }
  },
  {
    title: '招商负责人',
    key: 'colonelUserName',
    width: 120,
    render(row: any) {
      return row.colonelUserName || '-'
    }
  },
  {
    title: '订单金额',
    key: 'orderAmount',
    width: 110,
    render(row: any) {
      return formatMoney(row.orderAmount)
    }
  },
  {
    title: '归因状态',
    key: 'attributionStatus',
    width: 120,
    render(row: any) {
      const status = row.attributionStatus
      const type = status === 'ATTRIBUTED' ? 'success' : 'warning'
      return h(NTag, { type }, { default: () => attributionStatusMap[status] || status || '-' })
    }
  },
  {
    title: '归因备注',
    key: 'attributionRemark',
    width: 220,
    ellipsis: true,
    render(row: any) {
      return row.attributionRemark || '-'
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
    title: '最近更新',
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
  const year = date.getFullYear()
  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())
  const hour = pad(date.getHours())
  const minute = pad(date.getMinutes())
  const second = pad(date.getSeconds())
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

function getDefaultRange() {
  const end = Date.now()
  const start = end - 30 * 24 * 60 * 60 * 1000
  return [start, end] as [number, number]
}

function formatDateTime(timestamp: number) {
  return formatDateTimeText(timestamp)
}

async function handleSync() {
  if (!range.value) {
    message.warning('请选择同步时间范围')
    return
  }
  try {
    syncing.value = true
    const res: any = await syncOrders(
      formatDateTime(range.value[0]),
      formatDateTime(range.value[1])
    )
    syncResult.value = res.data
    const created = res.data?.created || 0
    const updated = res.data?.updated || 0
    message.success(`同步完成：新增 ${created} 条，更新 ${updated} 条`)
    pagination.page = 1
    await loadOrders()
  } catch (error: any) {
    message.error(error?.message || '同步失败')
  } finally {
    syncing.value = false
  }
}

async function loadOrders() {
  try {
    loading.value = true
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize
    }
    const res: any =
      activeTab.value === 'all'
        ? await getOrders(params)
        : await getUnattributedOrders(params)

    orders.value = res.data?.records || []
    pagination.itemCount = res.data?.total || 0
  } catch (error: any) {
    message.error(error?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  pagination.page = page
  loadOrders()
}

watch(activeTab, () => {
  pagination.page = 1
  loadOrders()
})

onMounted(() => {
  range.value = getDefaultRange()
  loadOrders()
})
</script>

<style scoped>
.orders-page {
  padding: 16px;
}

.sync-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.summary-strong {
  font-weight: 600;
}

.sync-hint {
  margin-top: 8px;
  font-size: 12px;
  opacity: 0.85;
}
</style>
