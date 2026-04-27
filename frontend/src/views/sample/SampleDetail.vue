<template>
  <n-modal :show="show" preset="card" title="寄样详情" style="width: 760px" @update:show="closeDetail">
    <n-spin :show="loading || actionLoading">
      <div v-if="detail" class="detail-body">
        <section class="detail-section">
          <h3 class="section-title">状态流程</h3>
          <div class="status-flow">
            <div v-for="step in flowSteps" :key="step.key" class="flow-step" :class="step.state">
              <div class="flow-dot" />
              <div class="flow-label">{{ step.label }}</div>
            </div>
          </div>
          <n-alert v-if="detail.status === 'FINISHED' && detail.completeTime" type="success" title="该寄样单已完成">
            完成时间：{{ formatDateTime(detail.completeTime) }}
          </n-alert>
          <n-alert v-if="detail.status === 'REJECTED' && detail.rejectReason" type="error" title="该寄样申请已拒绝">
            原因：{{ detail.rejectReason }}
          </n-alert>
        </section>

        <section class="detail-section">
          <h3 class="section-title">基础信息</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="寄样单 ID">{{ detail.id || '-' }}</n-descriptions-item>
            <n-descriptions-item label="寄样编号">{{ detail.requestNo || '-' }}</n-descriptions-item>
            <n-descriptions-item label="当前状态">
              <StatusTag scene="sample" :status="detail.status" />
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
            <n-descriptions-item v-if="detail.closeReason" label="关闭原因" :span="2">
              {{ detail.closeReason }}
            </n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">可执行操作</h3>
          <n-space>
            <n-button v-if="canAudit && detail.status === 'PENDING_AUDIT'" type="success" @click="handleAction('APPROVED')">
              审核通过
            </n-button>
            <n-button v-if="canAudit && detail.status === 'PENDING_AUDIT'" type="error" @click="handleAction('REJECTED')">
              拒绝
            </n-button>
            <n-button v-if="canShip && detail.status === 'PENDING_SHIP'" type="primary" @click="handleAction('SHIPPED')">
              发货
            </n-button>
            <n-tag v-if="detail.status === 'PENDING_TASK'" type="warning" round>等待订单完成交作业</n-tag>
          </n-space>
        </section>
      </div>
      <n-empty v-else description="暂无寄样数据" />
    </n-spin>

    <template #footer>
      <div class="footer-actions">
        <n-button @click="closeDetail">关闭</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { NInput, useDialog, useMessage } from 'naive-ui'
import { actionSample, getSampleById } from '../../api/sample'
import StatusTag from '../../components/StatusTag.vue'
import { ROLE_CODES } from '../../constants/rbac'
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

const stepOrder = ['PENDING_AUDIT', 'PENDING_SHIP', 'SHIPPED', 'PENDING_TASK', 'FINISHED']

const flowSteps = computed(() => {
  const currentIndex = stepOrder.indexOf(detail.value?.status)
  return stepOrder.map((key, index) => ({
    key,
    label:
      {
        PENDING_AUDIT: '待审核',
        PENDING_SHIP: '待发货',
        SHIPPED: '快递中',
        PENDING_TASK: '待交作业',
        FINISHED: '已完成'
      }[key] || key,
    state:
      detail.value?.status === 'REJECTED' || detail.value?.status === 'CLOSED'
        ? 'muted'
        : index < currentIndex
          ? 'done'
          : index === currentIndex
            ? 'active'
            : 'pending'
  }))
})

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
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.status-flow {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

.flow-step {
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  text-align: center;
}

.flow-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin: 0 auto 8px;
  background: var(--border-color);
}

.flow-step.done .flow-dot,
.flow-step.active .flow-dot {
  background: var(--color-success);
}

.flow-step.active {
  border-color: rgba(7, 193, 96, 0.4);
  background: rgba(7, 193, 96, 0.05);
}

.flow-step.muted .flow-dot {
  background: var(--text-muted);
}

.flow-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.remark-box {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
