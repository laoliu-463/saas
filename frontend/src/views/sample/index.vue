<template>
  <div class="sample-page">
    <PageHeader title="寄样台" description="跟踪寄样申请、审核、发货、签收和交作业完成状态。">
      <template #actions>
        <n-button :loading="loading" type="primary" @click="fetchData">刷新列表</n-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <n-space>
        <n-input v-model:value="filters.keyword" placeholder="搜达人 / 商品" style="width: 240px" />
        <n-select
          v-model:value="filters.status"
          :options="statusOptions"
          placeholder="寄样状态"
          clearable
          style="width: 160px"
        />
        <n-button type="primary" @click="fetchData">搜索</n-button>
      </n-space>
    </div>

    <n-card :bordered="false" class="main-card">
      <n-data-table
        remote
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: any) => row.id"
        @update:page="handlePageChange"
      />
    </n-card>

    <n-modal v-model:show="showLogistics" preset="dialog" title="填写物流单号">
      <n-form-item label="快递公司" required>
        <n-select
          v-model:value="logisticsForm.company"
          :options="[
            { label: '顺丰速运', value: 'SF' },
            { label: '中通快递', value: 'ZTO' },
            { label: '圆通速递', value: 'YTO' }
          ]"
        />
      </n-form-item>
      <n-form-item label="快递单号" required>
        <n-input v-model:value="logisticsForm.no" placeholder="输入真实单号或测试单号" />
      </n-form-item>
      <template #action>
        <n-button @click="showLogistics = false">取消</n-button>
        <n-button type="primary" :loading="submitLoading" @click="submitLogistics">确认发货</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NSpace, useMessage } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import request from '../../utils/request'
import { testShipSample, testSignSample } from '../../api/test'

const message = useMessage()
const loading = ref(false)
const submitLoading = ref(false)
const showLogistics = ref(false)
const data = ref([])
const currentId = ref<string | null>(null)

const filters = reactive({
  keyword: '',
  status: null
})

const logisticsForm = reactive({
  company: 'SF',
  no: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
})

const statusOptions = [
  { label: '待审核', value: 'PENDING_AUDIT' },
  { label: '待发货', value: 'PENDING_SHIP' },
  { label: '快递中', value: 'SHIPPED' },
  { label: '待交作业', value: 'PENDING_TASK' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已拒绝', value: 'REJECTED' }
]

const columns = [
  { title: '申请时间', key: 'createTime', width: 160 },
  { title: '商品名称', key: 'productName', minWidth: 180, render: (row: any) => row.productName || '-' },
  { title: '达人名称', key: 'talentName', width: 140 },
  {
    title: '状态',
    key: 'status',
    width: 120,
    render: (row: any) => h(StatusTag, { status: row.status, scene: 'sample' })
  },
  {
    title: '物流单号',
    key: 'trackingNo',
    width: 180,
    render: (row: any) => (row.trackingNo ? `${row.logisticsCompany || ''} ${row.trackingNo}` : '-')
  },
  {
    title: '操作',
    key: 'actions',
    width: 240,
    fixed: 'right',
    render: (row: any) => {
      return h(NSpace, null, {
        default: () => [
          row.status === 'PENDING_AUDIT' && h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => handleAudit(row, 'APPROVED') }, { default: () => '通过' }),
          row.status === 'PENDING_AUDIT' && h(NButton, { size: 'small', quaternary: true, type: 'error', onClick: () => handleAudit(row, 'REJECTED') }, { default: () => '拒绝' }),
          row.status === 'PENDING_SHIP' && h(NButton, { size: 'small', quaternary: true, type: 'info', onClick: () => openLogistics(row) }, { default: () => '填写单号' }),
          row.status === 'SHIPPED' && h(NButton, { size: 'small', quaternary: true, type: 'success', onClick: () => handleSign(row) }, { default: () => '模拟签收' })
        ]
      })
    }
  }
]

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/samples', {
      params: {
        page: pagination.page,
        size: pagination.pageSize,
        keyword: filters.keyword || undefined,
        status: filters.status || undefined
      }
    })
    data.value = res.data.records || []
    pagination.itemCount = res.data.total || 0
  } catch (err: any) {
    message.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  pagination.page = page
  fetchData()
}

const handleAudit = async (row: any, status: string) => {
  try {
    await request.put(`/samples/${row.id}/status`, { status })
    message.success('操作成功')
    fetchData()
  } catch (err: any) {
    message.error('操作失败')
  }
}

const openLogistics = (row: any) => {
  currentId.value = row.id
  logisticsForm.no = `TEST${Date.now()}`
  showLogistics.value = true
}

const submitLogistics = async () => {
  if (!currentId.value) return
  submitLoading.value = true
  try {
    await testShipSample(currentId.value)
    message.success('已模拟发货')
    showLogistics.value = false
    fetchData()
  } catch (err: any) {
    message.error('发货失败')
  } finally {
    submitLoading.value = false
  }
}

const handleSign = async (row: any) => {
  try {
    await testSignSample(row.id)
    message.success('已模拟签收')
    fetchData()
  } catch (err: any) {
    message.error('操作失败')
  }
}

onMounted(fetchData)
</script>

<style scoped>
.sample-page { padding: 24px; }
.filter-bar { margin-bottom: 16px; background: #fff; padding: 16px; border-radius: 8px; }
.main-card { border-radius: 8px; }
</style>
