<template>
  <div class="activity-page" data-testid="activity-list-page">
    <PageHeader
      title="活动列表"
      description="同步并查看抖音官方报名的团长活动，点击活动可进入商品列表进行选品操作。"
    />

    <div class="toolbar">
      <n-space>
        <n-input-group>
          <n-input v-model:value="filters.activityId" placeholder="活动 ID" style="width: 200px" />
          <n-input v-model:value="filters.activityName" placeholder="活动名称" style="width: 240px" />
        </n-input-group>
        <n-select
          v-model:value="filters.status"
          :options="statusOptions"
          placeholder="活动状态"
          clearable
          style="width: 160px"
        />
        <n-button type="primary" data-testid="activity-search-submit" @click="fetchData">查询</n-button>
        <n-button @click="resetFilters">重置</n-button>
        <n-button type="info" :loading="exporting" @click="handleExport">导出 CSV</n-button>
      </n-space>
    </div>

    <n-alert type="warning" style="margin-bottom: 16px;">
      当前为 Test 测试环境，活动数据来自后端 Test 服务。联调 Real 环境时将实时请求抖店开放平台。
    </n-alert>

    <n-card :bordered="false" class="main-card">
      <n-data-table
        remote
        data-testid="activity-table"
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: any) => row.activityId"
        @update:page="handlePageChange"
      />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NTag, useMessage } from 'naive-ui'
import { useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import { getColonelActivityPage } from '../../api/activity'
import { exportActivities } from '../../api/data'

const message = useMessage()
const router = useRouter()
const loading = ref(false)
const exporting = ref(false)
const data = ref([])

const filters = reactive({
  activityId: '',
  activityName: '',
  status: null
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
})

const statusOptions = [
  { label: '未开始', value: 1 },
  { label: '进行中', value: 2 },
  { label: '报名中', value: 3 },
  { label: '推广未开始', value: 4 },
  { label: '推广中', value: 5 },
  { label: '已结束', value: 6 }
]

const columns = [
  { title: '活动 ID', key: 'activityId', width: 180 },
  { title: '活动名称', key: 'activityName', minWidth: 240 },
  {
    title: '状态',
    key: 'activityStatus',
    width: 120,
    render: (row: any) => {
      const option = statusOptions.find(o => o.value === row.activityStatus)
      const type = row.activityStatus === 2 || row.activityStatus === 5 ? 'success' : 'default'
      return h(NTag, { type, size: 'small', round: true }, { default: () => option?.label || '未知' })
    }
  },
  { title: '起止时间', key: 'timeRange', width: 320, render: (row: any) => `${row.startTime || '-'} 至 ${row.endTime || '-'}` },
  { title: '同步时间', key: 'createTime', width: 160 },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    fixed: 'right',
    render: (row: any) => {
      return h(NButton, {
        size: 'small',
        type: 'primary',
        quaternary: true,
        'data-testid': 'activity-view-products',
        onClick: () => {
          router.push(`/product/manage/${row.activityId}`)
        }
      }, { default: () => '查看商品' })
    }
  }
]

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await getColonelActivityPage({
      page: pagination.page,
      pageSize: pagination.pageSize,
      activityId: filters.activityId || undefined,
      activityName: filters.activityName || undefined,
      status: filters.status || undefined
    })
    const result = res.data || {}
    data.value = result.activityList || []
    pagination.itemCount = result.total || 0
  } catch (err: any) {
    message.error('加载活动列表失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  pagination.page = page
  fetchData()
}

const resetFilters = () => {
  filters.activityId = ''
  filters.activityName = ''
  filters.status = null
  fetchData()
}

const handleExport = async () => {
  exporting.value = true
  try {
    const res: any = await exportActivities({
      activityName: filters.activityName || undefined
    })
    const filename = `activities-${new Date().toISOString().slice(0, 10)}.csv`
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', filename)
    document.body.appendChild(link)
    link.click()
    link.parentNode?.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (err: any) {
    message.error(err?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.activity-page { padding: 24px; }
.toolbar { margin-bottom: 16px; background: var(--bg-card); padding: 16px; border-radius: var(--radius-md); }
.main-card { border-radius: var(--radius-md); }
</style>
