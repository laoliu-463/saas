<template>
  <div class="order-list" data-testid="data-orders-page">
    <n-card title="订单明细" :bordered="false">
      <n-space style="margin-bottom: 16px">
        <n-radio-group v-model:value="timeField" size="small" @update:value="handleTimeFieldChange">
          <n-radio-button value="createTime">按创建时间</n-radio-button>
          <n-radio-button value="settleTime">按结算时间</n-radio-button>
        </n-radio-group>
        <n-date-picker
          v-model:value="dateRange"
          type="daterange"
          clearable
          placeholder="选择日期范围"
          style="width: 250px"
        />
        <n-select
          v-model:value="searchParams.status"
          :options="statusOptions"
          placeholder="状态筛选"
          style="width: 150px"
          clearable
        />
        <n-input
          v-model:value="searchParams.orderId"
          placeholder="订单号筛选"
          style="width: 220px"
          clearable
        />
        <n-input
          v-model:value="searchParams.talentId"
          placeholder="达人ID筛选"
          style="width: 220px"
          clearable
        />
        <n-input
          v-model:value="searchParams.merchantId"
          placeholder="商家ID筛选"
          style="width: 220px"
          clearable
        />
        <n-button type="primary" data-testid="data-orders-search-submit" @click="fetchData">查询</n-button>
        <n-button ghost type="primary" @click="fetchData">刷新订单</n-button>
        <n-button v-if="canExport" type="info" data-testid="data-orders-export" @click="handleExport">导出 CSV</n-button>
      </n-space>

      <n-data-table
        remote
        data-testid="data-orders-table"
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: any) => row.id"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <n-modal v-model:show="showDetail" preset="card" title="订单详情" style="width: 600px">
      <n-descriptions v-if="currentOrder" bordered :column="1">
        <n-descriptions-item label="订单号">{{ currentOrder.id }}</n-descriptions-item>
        <n-descriptions-item label="商品信息">{{ currentOrder.productName || '未知商品' }}</n-descriptions-item>
        <n-descriptions-item label="达人信息">{{ currentOrder.talentName || '未知达人' }}</n-descriptions-item>
        <n-descriptions-item label="金额明细">
          <div>总价：¥{{ currentOrder.amount || '0.00' }}</div>
          <div style="color: gray; font-size: 12px">
            商品价：¥{{ currentOrder.goodsPrice || currentOrder.amount || '0.00' }} |
            佣金：¥{{ currentOrder.commission || '0.00' }} |
            运费：¥{{ currentOrder.freight || '0.00' }}
          </div>
        </n-descriptions-item>
        <n-descriptions-item label="收件人信息">
          <n-text v-if="hasReceiverInfo(currentOrder)">
            {{ displayText(currentOrder.receiverName) }}
            <template v-if="currentOrder.receiverPhone">（{{ currentOrder.receiverPhone }}）</template>
            <template v-if="currentOrder.receiverAddress"> - {{ currentOrder.receiverAddress }}</template>
          </n-text>
          <n-text v-else depth="3">上游当前未返回收件人姓名或地址</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="联系电话">{{ displayText(currentOrder.receiverPhone, '上游未返回联系电话') }}</n-descriptions-item>
        <n-descriptions-item label="快递信息">
          <n-text v-if="hasExpressInfo(currentOrder)">
            {{ displayText(currentOrder.expressCompany) }}
            <template v-if="currentOrder.expressNo"> - {{ currentOrder.expressNo }}</template>
          </n-text>
          <n-text v-else depth="3">暂无真实物流信息</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="归因来源">
          <n-tag :type="getAttributionTagType(currentOrder.attributionSource)" size="small">
            {{ getAttributionLabel(currentOrder.attributionSource) }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="创建时间">{{ displayText(currentOrder.createTime) }}</n-descriptions-item>
        <n-descriptions-item label="结算时间">{{ currentOrder.settleTime || '未结算或上游未返回' }}</n-descriptions-item>
      </n-descriptions>
      <template #footer>
        <n-button @click="showDetail = false">关闭</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NTag, NText, useMessage } from 'naive-ui'
import { exportOrders, getOrderPage } from '../../api/data'
import { useAuthStore } from '../../stores/auth'
import { createPaginationState, normalizePageSize } from '../../utils/pagination'

const authStore = useAuthStore()
const message = useMessage()
const loading = ref(false)

const data = ref<any[]>([])
const pagination = reactive(createPaginationState())

const buildTodayRange = (): [number, number] => {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  return [start.getTime(), end.getTime()]
}

const dateRange = ref<[number, number] | null>(buildTodayRange())
const timeField = ref<'createTime' | 'settleTime'>('createTime')

const searchParams = reactive({
  orderId: '',
  status: null as string | null,
  talentId: '',
  merchantId: ''
})

const showDetail = ref(false)
const currentOrder = ref<any>(null)
const canExport = computed(() => authStore.isAdmin || authStore.isLeader)
const activeTimeTitle = computed(() => timeField.value === 'settleTime' ? '结算时间' : '创建时间')

const statusOptions = [
  { label: '已下单', value: 'ORDERED' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已取消', value: 'CANCELLED' }
]

function getAttributionLabel(source?: string) {
  if (source === 'EXCLUSIVE_MERCHANT') return '独家招商'
  if (source === 'EXCLUSIVE_TALENT') return '独家达人'
  if (source === 'PICK_SOURCE_MAPPING') return '映射归属'
  return source || '默认归属'
}

function getAttributionTagType(source?: string): 'success' | 'warning' | 'info' | 'default' {
  if (source === 'EXCLUSIVE_MERCHANT') return 'warning'
  if (source === 'EXCLUSIVE_TALENT') return 'success'
  if (source === 'PICK_SOURCE_MAPPING') return 'info'
  return 'default'
}

const displayText = (value: unknown, fallback = '-') => {
  if (value === null || value === undefined || value === '') return fallback
  return String(value)
}

const hasReceiverInfo = (order: any) => Boolean(order?.receiverName || order?.receiverPhone || order?.receiverAddress)

const hasExpressInfo = (order: any) => Boolean(order?.expressCompany || order?.expressNo)

const activeTimeValue = (row: any) => {
  if (timeField.value === 'settleTime') return row?.settleTime || '未结算'
  return row?.createTime || '-'
}

const handleTimeFieldChange = () => {
  pagination.page = 1
  fetchData()
}

const openDetail = (row: any) => {
  currentOrder.value = row
  showDetail.value = true
}

const fetchData = async () => {
  pagination.pageSize = normalizePageSize(pagination.pageSize)
  loading.value = true
  try {
    let startDate
    let endDate
    if (dateRange.value) {
      startDate = new Date(dateRange.value[0]).toISOString().split('T')[0]
      endDate = new Date(dateRange.value[1]).toISOString().split('T')[0]
    }

    const res = await getOrderPage({
      page: pagination.page,
      size: pagination.pageSize,
      orderId: searchParams.orderId || undefined,
      status: searchParams.status,
      talentId: searchParams.talentId || undefined,
      merchantId: searchParams.merchantId || undefined,
      timeField: timeField.value,
      startDate,
      endDate
    })

    const responseData: any = res?.data || res
    if (responseData?.records && Array.isArray(responseData.records)) {
      data.value = responseData.records
      pagination.itemCount = responseData.total || 0
    } else {
      data.value = []
      pagination.itemCount = 0
    }
  } catch (error: any) {
    message.error(error?.message || '获取订单列表失败')
  } finally {
    loading.value = false
  }
}

const handleExport = async () => {
  if (!canExport.value) {
    message.warning('当前角色无权导出订单数据')
    return
  }
  if (!data.value.length) {
    message.warning('暂无数据可导出')
    return
  }
  try {
    let startDate
    let endDate
    if (dateRange.value) {
      startDate = new Date(dateRange.value[0]).toISOString().split('T')[0]
      endDate = new Date(dateRange.value[1]).toISOString().split('T')[0]
    }
    const res: any = await exportOrders({
      status: searchParams.status,
      talentId: searchParams.talentId || undefined,
      merchantId: searchParams.merchantId || undefined,
      timeField: timeField.value,
      startDate,
      endDate
    })
    const filename = `orders-${new Date().toISOString().slice(0, 10)}.csv`
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', filename)
    document.body.appendChild(link)
    link.click()
    link.parentNode?.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch {
    message.error('导出失败')
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

const columns = computed(() => [
  { title: '订单号', key: 'id' },
  { title: '商品名称', key: 'productName' },
  { title: '达人名称', key: 'talentName' },
  {
    title: '归因来源',
    key: 'attributionSource',
    render(row: any) {
      return h(
        NTag,
        { type: getAttributionTagType(row.attributionSource), size: 'small' },
        { default: () => getAttributionLabel(row.attributionSource) }
      )
    }
  },
  { title: '金额', key: 'amount' },
  {
    title: '状态',
    key: 'status',
    render(row: any) {
      const opt = statusOptions.find((item) => item.value === row.status)
      return h(
        NTag,
        { type: row.status === 'FINISHED' ? 'success' : 'default' },
        { default: () => (opt ? opt.label : row.status) }
      )
    }
  },
  {
    title: '联系电话',
    key: 'receiverPhone',
    width: 200,
    render(row: any) {
      return h(NText, { depth: row.receiverPhone ? undefined : 3 }, { default: () => displayText(row.receiverPhone, '上游未返回') })
    }
  },
  {
    title: activeTimeTitle.value,
    key: timeField.value === 'settleTime' ? 'settleTime' : 'createTime',
    render(row: any) {
      return activeTimeValue(row)
    }
  },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    fixed: 'right' as const,
    render(row: any) {
      return h(NButton, { size: 'small', onClick: () => openDetail(row) }, { default: () => '详情' })
    }
  }
])

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.order-list {
  min-height: 100%;
}
</style>
