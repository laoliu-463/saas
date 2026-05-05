<template>
  <div class="operation-log-list">
    <div class="toolbar">
      <n-space wrap :size="10">
        <n-input v-model:value="filters.username" placeholder="操作人" clearable style="width: 160px" />
        <n-input v-model:value="filters.module" placeholder="模块" clearable style="width: 160px" />
        <n-input v-model:value="filters.action" placeholder="动作" clearable style="width: 180px" />
        <n-select
          v-model:value="filters.requestMethod"
          :options="methodOptions"
          placeholder="请求方法"
          clearable
          style="width: 140px"
        />
        <n-date-picker
          v-model:value="dateRange"
          type="daterange"
          clearable
          style="width: 260px"
        />
        <n-button type="primary" size="small" @click="handleSearch">查询</n-button>
        <n-button size="small" @click="handleReset">重置</n-button>
      </n-space>
    </div>

    <div class="table-card">
      <n-data-table
        remote
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        size="small"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import { NTag, useMessage } from 'naive-ui'
import { getOperationLogPage } from '../../api/sys'

const message = useMessage()
const loading = ref(false)
const data = ref<any[]>([])
const dateRange = ref<[number, number] | null>(null)

const filters = reactive({
  username: '',
  module: '',
  action: '',
  requestMethod: null as string | null
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50, 100]
})

const methodOptions = [
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'PATCH', value: 'PATCH' },
  { label: 'DELETE', value: 'DELETE' }
]

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

const requestMethodTagType = (method?: string) => {
  switch (method) {
    case 'POST':
      return 'success'
    case 'PUT':
      return 'info'
    case 'PATCH':
      return 'warning'
    case 'DELETE':
      return 'error'
    default:
      return 'default'
  }
}

const responseCodeTagType = (code?: string) => {
  if (!code) return 'default'
  if (code.startsWith('2')) return 'success'
  if (code.startsWith('4')) return 'warning'
  if (code.startsWith('5')) return 'error'
  return 'default'
}

const columns: DataTableColumns<any> = [
  { title: '时间', key: 'createTime', width: 180, render: (row) => formatDateTime(row.createTime) },
  { title: '操作人', key: 'username', width: 120, ellipsis: { tooltip: true } },
  { title: '模块', key: 'module', width: 140, ellipsis: { tooltip: true } },
  { title: '动作', key: 'action', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '目标', key: 'targetName', minWidth: 180, ellipsis: { tooltip: true } },
  {
    title: '请求',
    key: 'requestMethod',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { type: requestMethodTagType(row.requestMethod), size: 'small', round: true },
        { default: () => row.requestMethod || '-' }
      )
  },
  {
    title: '状态',
    key: 'responseCode',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { type: responseCodeTagType(row.responseCode), size: 'small', round: true },
        { default: () => row.responseCode || '-' }
      )
  },
  { title: '耗时(ms)', key: 'durationMs', width: 100 },
  { title: '错误信息', key: 'errorMessage', minWidth: 220, ellipsis: { tooltip: true } }
]

const buildParams = () => {
  let startDate
  let endDate
  if (dateRange.value) {
    startDate = new Date(dateRange.value[0]).toISOString().split('T')[0]
    endDate = new Date(dateRange.value[1]).toISOString().split('T')[0]
  }
  return {
    page: pagination.page,
    size: pagination.pageSize,
    username: filters.username || undefined,
    module: filters.module || undefined,
    action: filters.action || undefined,
    requestMethod: filters.requestMethod || undefined,
    startDate,
    endDate
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await getOperationLogPage(buildParams())
    const responseData = res?.data || res
    data.value = responseData?.records || []
    pagination.itemCount = responseData?.total || 0
  } catch (error: any) {
    data.value = []
    pagination.itemCount = 0
    message.error(error?.message || '获取操作日志失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchData()
}

const handleReset = () => {
  filters.username = ''
  filters.module = ''
  filters.action = ''
  filters.requestMethod = null
  dateRange.value = null
  pagination.page = 1
  fetchData()
}

const handlePageChange = (page: number) => {
  pagination.page = page
  fetchData()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize
  pagination.page = 1
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.operation-log-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar,
.table-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px;
  box-shadow: var(--shadow-card);
}
</style>
