<template>
  <div class="orders-page">
    <PageHeader
      title="订单归因"
      description="自动回流抖店结算订单，精准识别推广渠道、达人业绩与招商归属。"
    >
      <template #actions>
        <n-button :loading="syncLoading" type="primary" secondary @click="handleSync">同步最新订单</n-button>
        <n-button type="primary" @click="fetchData">查询</n-button>
      </template>
    </PageHeader>

    <div class="toolbar">
      <n-space wrap>
        <n-input v-model:value="filters.orderId" placeholder="订单 ID" style="width: 200px" />
        <n-input v-model:value="filters.productId" placeholder="商品 ID" style="width: 180px" />
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

    <n-card :bordered="false" class="main-card">
      <n-data-table
        remote
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: any) => row.orderId"
        @update:page="handlePageChange"
      />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NSpace, NTag, useMessage } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import request from '../../utils/request'

const message = useMessage()
const loading = ref(false)
const syncLoading = ref(false)
const data = ref([])

const filters = reactive({
  orderId: '',
  productId: '',
  attributionStatus: null,
  dateRange: null as [number, number] | null
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})

const columns = [
  { title: '订单号/结算时间', key: 'orderInfo', width: 220, render: (row: any) => h('div', null, [
    h('div', { style: 'font-weight: 600' }, row.orderId),
    h('div', { style: 'font-size: 12px; color: #999' }, row.settleTime || '-')
  ]) },
  { title: '商品信息', key: 'productTitle', minWidth: 200, ellipsis: true },
  { title: '订单金额', key: 'orderAmount', width: 100, render: (row: any) => `¥${row.orderAmount || 0}` },
  {
    title: '归因状态',
    key: 'attributionStatus',
    width: 120,
    render: (row: any) => {
      const status = row.attributionStatus || 'UNATTRIBUTED'
      const type = status === 'ATTRIBUTED' ? 'success' : (status === 'UNATTRIBUTED' ? 'error' : 'info')
      const labels: Record<string, string> = { ATTRIBUTED: '已归因', UNATTRIBUTED: '待排查', PARTIAL: '部分归因' }
      return h(NTag, { type, size: 'small' }, { default: () => labels[status] || status })
    }
  },
  { title: '渠道负责人', key: 'channelUserName', width: 120, render: (row: any) => row.channelUserName || '-' },
  { title: '归因标识 (pick_source)', key: 'pickSource', width: 180, ellipsis: true },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    fixed: 'right',
    render: () => {
      return h(NButton, { size: 'small', quaternary: true, onClick: () => message.info('详情功能开发中') }, { default: () => '查看详情' })
    }
  }
]

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/orders', {
      params: {
        page: pagination.page,
        size: pagination.pageSize,
        orderId: filters.orderId || undefined,
        productId: filters.productId || undefined,
        attributionStatus: filters.attributionStatus || undefined
      }
    })
    data.value = res.data.records || []
    pagination.itemCount = res.data.total || 0
  } catch (err: any) {
    message.error('加载订单列表失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  pagination.page = page
  fetchData()
}

const handleSync = async () => {
  syncLoading.value = true
  try {
    await request.post('/orders/sync')
    message.success('已触发同步，订单回流中...')
    setTimeout(fetchData, 1000)
  } catch (err: any) {
    message.error('同步失败')
  } finally {
    syncLoading.value = false
  }
}

const resetFilters = () => {
  filters.orderId = ''
  filters.productId = ''
  filters.attributionStatus = null
  filters.dateRange = null
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.orders-page { padding: 24px; }
.toolbar { margin-bottom: 16px; background: #fff; padding: 16px; border-radius: 8px; }
.main-card { border-radius: 8px; }
</style>
