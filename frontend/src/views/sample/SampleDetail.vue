<template>
  <n-modal :show="show" preset="card" title="寄样单详情" style="width: 760px" @update:show="closeDetail">
    <n-spin :show="loading || actionLoading">
      <div v-if="detail">
        <n-descriptions bordered :column="2">
          <n-descriptions-item label="寄样单 ID">{{ detail.id || '-' }}</n-descriptions-item>
          <n-descriptions-item label="寄样编号">{{ detail.requestNo || '-' }}</n-descriptions-item>
          <n-descriptions-item label="当前状态">
            <n-tag :type="getSampleStatusType(detail.status)">{{ getSampleStatusText(detail.status) }}</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="渠道名称">{{ detail.channelUserName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="招商负责人">{{ detail.colonelUserName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="商品名称">{{ detail.productName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="达人昵称">{{ detail.talentName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="物流单号">{{ detail.trackingNo || '-' }}</n-descriptions-item>
          <n-descriptions-item label="申请时间">{{ formatDateTime(detail.createTime) }}</n-descriptions-item>
          <n-descriptions-item label="更新时间">{{ formatDateTime(detail.updateTime) }}</n-descriptions-item>
          <n-descriptions-item label="完成时间">{{ formatDateTime(detail.completeTime) }}</n-descriptions-item>
          <n-descriptions-item label="申请说明" :span="2">
            <pre class="remark-box">{{ detail.remark || '-' }}</pre>
          </n-descriptions-item>
          <n-descriptions-item v-if="detail.rejectReason" label="拒绝原因" :span="2">
            {{ detail.rejectReason }}
          </n-descriptions-item>
          <n-descriptions-item v-if="detail.closeReason" label="关闭原因" :span="2">
            {{ detail.closeReason }}
          </n-descriptions-item>
        </n-descriptions>

        <n-divider />
        <n-space>
          <n-button v-if="canAudit && detail.status === 'PENDING_AUDIT'" type="success" @click="handleAction('APPROVED')">
            审核通过
          </n-button>
          <n-button v-if="canAudit && detail.status === 'PENDING_AUDIT'" type="error" @click="handleAction('REJECTED')">
            拒绝
          </n-button>
          <n-button v-if="canShip && detail.status === 'PENDING_SHIP'" type="info" @click="handleAction('SHIPPED')">
            发货
          </n-button>
        </n-space>
      </div>
      <n-empty v-else description="暂无寄样数据" />
    </n-spin>

    <template #footer>
      <div style="display: flex; justify-content: flex-end">
        <n-button @click="closeDetail">关闭</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { h, ref, watch } from 'vue'
import { NInput, useDialog, useMessage } from 'naive-ui'
import { actionSample, getSampleById } from '../../api/sample'
import { ROLE_CODES } from '../../constants/rbac'
import { getSampleStatusText, getSampleStatusType } from '../../constants/sampleStatus'
import { useAuthStore } from '../../stores/auth'

const props = defineProps<{ show: boolean; sampleId: string }>()
const emit = defineEmits(['update:show', 'refresh'])

const message = useMessage()
const dialog = useDialog()
const authStore = useAuthStore()

const loading = ref(false)
const actionLoading = ref(false)
const detail = ref<any>(null)

const canAudit = authStore.isAdmin || authStore.isLeader
const canShip = canAudit || authStore.roleCodes.includes(ROLE_CODES.OPS_STAFF)

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function closeDetail() {
  emit('update:show', false)
}

async function loadDetail() {
  loading.value = true
  try {
    const res = await getSampleById(props.sampleId)
    detail.value = res?.data || res
  } catch (error: any) {
    detail.value = null
    message.error(error?.message || '无法获取寄样详情')
  } finally {
    loading.value = false
  }
}

async function doActionSample(payload: any) {
  actionLoading.value = true
  try {
    await actionSample(props.sampleId, payload)
    message.success('状态流转成功')
    emit('refresh')
    await loadDetail()
  } catch (error: any) {
    message.error(error?.message || '操作失败')
  } finally {
    actionLoading.value = false
  }
}

function askReason() {
  return new Promise<string>((resolve) => {
    let value = ''
    dialog.warning({
      title: '拒绝原因',
      content: () =>
        h(NInput, {
          type: 'textarea',
          placeholder: '请输入拒绝原因',
          onUpdateValue: (nextValue) => {
            value = nextValue
          }
        }),
      positiveText: '确认拒绝',
      negativeText: '取消',
      positiveButtonProps: { type: 'error' },
      onPositiveClick: () => resolve(value || ''),
      onNegativeClick: () => resolve('')
    })
  })
}

function askTrackingNo() {
  return new Promise<string>((resolve) => {
    let value = ''
    dialog.warning({
      title: '填写物流单号',
      content: () =>
        h('div', [
          h('div', { style: 'margin-bottom: 8px;' }, '物流单号：'),
          h(NInput, {
            placeholder: 'SF1234567890',
            onUpdateValue: (nextValue) => {
              value = nextValue
            }
          })
        ]),
      positiveText: '确认发货',
      negativeText: '取消',
      onPositiveClick: () => resolve(value || ''),
      onNegativeClick: () => resolve('')
    })
  })
}

async function handleAction(action: string) {
  if (action === 'REJECTED') {
    const reason = await askReason()
    if (!reason) return
    await doActionSample({ action, reason })
    return
  }

  if (action === 'SHIPPED') {
    const trackingNo = await askTrackingNo()
    if (!trackingNo) return
    await doActionSample({ action, trackingNo })
    return
  }

  await doActionSample({ action })
}

watch(
  () => props.show,
  (show) => {
    if (show && props.sampleId) {
      loadDetail()
      return
    }
    detail.value = null
  }
)
</script>

<style scoped>
.remark-box {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
}
</style>
