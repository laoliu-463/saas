<template>
  <div class="sample-index">
    <div class="sample-toolbar">
      <n-space>
        <n-button v-if="canApply" type="primary" size="small" @click="showCreateModal = true">新建寄样申请</n-button>
        <n-button size="small" :loading="loading" @click="fetchData">刷新寄样</n-button>
      </n-space>
    </div>

    <div class="sample-table-card">
      <n-tabs v-model:value="activeTab" type="line" animated @update:value="handleTabChange">
        <n-tab-pane v-for="tab in tabList" :key="tab.value" :name="tab.value" :tab="tab.label">
          <n-data-table
            remote
            :columns="columns"
            :data="data"
            :loading="loading"
            :pagination="pagination"
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
          />
        </n-tab-pane>
      </n-tabs>
    </div>

    <SampleCreateModal v-model:show="showCreateModal" @success="handleCreateSuccess" />
    <SampleDetail v-model:show="showDetail" :sample-id="currentSampleId" @refresh="fetchData" />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, NTag, useDialog, useMessage } from 'naive-ui'
import { actionSample, deleteSample, getSamplePage } from '../../api/sample'
import { mockShipSample, mockSignSample } from '../../api/mock'
import { ROLE_CODES } from '../../constants/rbac'
import { getSampleStatusText, getSampleStatusType } from '../../constants/sampleStatus'
import { useAuthStore } from '../../stores/auth'
import { isMockEnv } from '../../utils/env'
import SampleCreateModal from './components/SampleCreateModal.vue'
import SampleDetail from './SampleDetail.vue'

const message = useMessage()
const dialog = useDialog()
const authStore = useAuthStore()

const canApply =
  authStore.isAdmin ||
  authStore.roleCodes.includes(ROLE_CODES.CHANNEL_LEADER) ||
  authStore.roleCodes.includes(ROLE_CODES.CHANNEL_STAFF)

const canAudit = authStore.isAdmin || authStore.isLeader

const loading = ref(false)
const activeTab = ref('PENDING_AUDIT')
const data = ref<any[]>([])
const showDetail = ref(false)
const showCreateModal = ref(false)
const currentSampleId = ref('')

const tabList = [
  { label: '待审核', value: 'PENDING_AUDIT' },
  { label: '待发货', value: 'PENDING_SHIP' },
  { label: '快递中', value: 'SHIPPED' },
  { label: '待交作业', value: 'PENDING_TASK' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' }
]

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
})

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function openDetail(row: any) {
  currentSampleId.value = row.id
  showDetail.value = true
}

async function handleMockLogistics(id: string, type: 'ship' | 'sign') {
  try {
    if (type === 'ship') {
      await mockShipSample(id)
      message.success('模拟发货成功')
    } else {
      await mockSignSample(id)
      message.success('模拟签收成功')
    }
    await fetchData()
  } catch (error: any) {
    message.error(error?.message || '操作失败')
  }
}

async function handleApprove(id: string) {
  try {
    await actionSample(id, { action: 'APPROVED' })
    message.success('审核通过，寄样单已进入待发货')
    await fetchData()
  } catch (error: any) {
    message.error(error?.message || '审核通过失败')
  }
}

function handleReject(id: string) {
  let reason = ''
  dialog.warning({
    title: '拒绝寄样申请',
    content: () =>
      h('div', { style: 'display: flex; flex-direction: column; gap: 8px;' }, [
        h('div', null, '请填写拒绝原因'),
        h('input', {
          style: 'padding: 8px 10px; border: 1px solid #d9d9d9; border-radius: 6px;',
          placeholder: '例如：商品不符合当前合作计划',
          onInput: (event: any) => {
            reason = event?.target?.value || ''
          }
        })
      ]),
    positiveText: '确认拒绝',
    negativeText: '取消',
    positiveButtonProps: { type: 'error' },
    async onPositiveClick() {
      if (!reason.trim()) {
        message.warning('请先填写拒绝原因')
        throw new Error('missing reason')
      }
      try {
        await actionSample(id, { action: 'REJECTED', reason: reason.trim() })
        message.success('寄样单已拒绝')
        await fetchData()
      } catch (error: any) {
        message.error(error?.message || '拒绝操作失败')
        throw error
      }
    }
  })
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getSamplePage({
      page: pagination.page,
      size: pagination.pageSize,
      status: activeTab.value
    })
    const responseData = res?.data || res
    data.value = Array.isArray(responseData?.records) ? responseData.records : []
    pagination.itemCount = Number(responseData?.total || 0)
  } catch (error: any) {
    data.value = []
    pagination.itemCount = 0
    message.error(error?.message || '获取寄样列表失败')
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  pagination.page = 1
  fetchData()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchData()
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  fetchData()
}

async function doDeleteSample(id: string) {
  try {
    await deleteSample(id)
    message.success('寄样单删除成功')
    await fetchData()
  } catch (error: any) {
    message.error(error?.message || '删除失败')
  }
}

function handleCreateSuccess() {
  activeTab.value = 'PENDING_AUDIT'
  pagination.page = 1
  fetchData()
}

const columns = [
  { title: '寄样单 ID', key: 'id', width: 240 },
  { title: '寄样编号', key: 'requestNo', width: 180, render: (row: any) => row.requestNo || '-' },
  { title: '商品名称', key: 'productName', minWidth: 180, render: (row: any) => row.productName || '-' },
  { title: '达人昵称', key: 'talentName', minWidth: 140, render: (row: any) => row.talentName || '-' },
  { title: '渠道名称', key: 'channelUserName', minWidth: 150, render: (row: any) => row.channelUserName || '-' },
  { title: '招商负责人', key: 'colonelUserName', minWidth: 150, render: (row: any) => row.colonelUserName || '-' },
  {
    title: '当前状态',
    key: 'status',
    width: 120,
    render: (row: any) => h(NTag, { type: getSampleStatusType(row.status) }, { default: () => getSampleStatusText(row.status) })
  },
  { title: '物流单号', key: 'trackingNo', minWidth: 180, render: (row: any) => row.trackingNo || '-' },
  { title: '申请时间', key: 'createTime', width: 180, render: (row: any) => formatDateTime(row.createTime) },
  { title: '更新时间', key: 'updateTime', width: 180, render: (row: any) => formatDateTime(row.updateTime || row.createTime) },
  { title: '完成时间', key: 'completeTime', width: 180, render: (row: any) => formatDateTime(row.completeTime) },
  {
    title: '操作',
    key: 'actions',
    width: 360,
    render(row: any) {
      const actions = [
        h(
          NButton,
          { size: 'small', type: 'info', onClick: () => openDetail(row) },
          { default: () => '详情' }
        )
      ]

      if (canAudit && row.status === 'PENDING_AUDIT') {
        actions.push(
          h(NButton, { size: 'small', type: 'success', onClick: () => handleApprove(row.id) }, { default: () => '审核通过' }),
          h(NButton, { size: 'small', type: 'error', ghost: true, onClick: () => handleReject(row.id) }, { default: () => '拒绝' })
        )
      }

      if (isMockEnv && row.status === 'PENDING_SHIP') {
        actions.push(
          h(
            NButton,
            { size: 'small', type: 'primary', ghost: true, onClick: () => handleMockLogistics(row.id, 'ship') },
            { default: () => '模拟发货' }
          )
        )
      }

      if (isMockEnv && row.status === 'SHIPPED') {
        actions.push(
          h(
            NButton,
            { size: 'small', type: 'success', ghost: true, onClick: () => handleMockLogistics(row.id, 'sign') },
            { default: () => '模拟签收' }
          )
        )
      }

      if (row.status === 'PENDING_TASK') {
        actions.push(h(NTag, { type: 'info', bordered: false }, { default: () => '等待出单' }))
      }

      if (row.status === 'FINISHED') {
        actions.push(h(NTag, { type: 'success', bordered: false }, { default: () => '已完成' }))
      }

      if (row.status === 'REJECTED') {
        actions.push(
          h(
            NButton,
            {
              size: 'small',
              quaternary: true,
              onClick: () => message.info(row.rejectReason || '未填写拒绝原因')
            },
            { default: () => '查看拒绝原因' }
          )
        )
      }

      if (row.status === 'CLOSED') {
        actions.push(h(NTag, { bordered: false }, { default: () => '已关闭' }))
      }

      if (row.status === 'PENDING_AUDIT' || row.status === 'REJECTED') {
        actions.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => doDeleteSample(row.id) },
            {
              trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
              default: () => '确认删除这条寄样记录？'
            }
          )
        )
      }

      return h('div', { style: 'display: flex; gap: 8px; flex-wrap: wrap;' }, actions)
    }
  }
]

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    fetchData()
  }
}

onMounted(() => {
  fetchData()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.sample-index {
  max-width: 100%;
}

.sample-toolbar {
  margin-bottom: var(--spacing-md);
}

.sample-table-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 4px 16px 16px;
  box-shadow: var(--shadow-card);
}
</style>
