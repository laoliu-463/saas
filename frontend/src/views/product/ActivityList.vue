<template>
  <div class="activity-page">
    <PageHeader
      title="团长活动"
      description="同步并查看抖音官方报名的团长活动，作为商品引入的基础。"
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
        <n-button type="primary" @click="fetchData">查询</n-button>
        <n-button @click="resetFilters">重置</n-button>
      </n-space>
    </div>

    <n-alert type="warning" style="margin-bottom: 16px;">
      当前为 Test 测试环境，活动数据来自后端 Test 服务。联调 Real 环境时将实时请求抖店开放平台。
    </n-alert>

    <n-card :bordered="false" class="main-card">
      <n-data-table
        remote
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

const message = useMessage()
const router = useRouter()
const loading = ref(false)
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
        onClick: () => {
          router.push(`/product/activity/${row.activityId}`)
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

onMounted(fetchData)
</script>

<style scoped>
.activity-page { padding: 24px; }
.toolbar { margin-bottom: 16px; background: var(--bg-card); padding: 16px; border-radius: var(--radius-md); }
.main-card { border-radius: var(--radius-md); }
</style>
