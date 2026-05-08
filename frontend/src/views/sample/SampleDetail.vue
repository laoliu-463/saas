<template>
  <n-modal :show="effectiveShow" preset="card" title="寄样详情" style="width: 760px" @update:show="closeDetail">
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
            <n-descriptions-item v-if="!isChannelStaffOnly" label="渠道名称">{{ detail.channelUserName || '-' }}</n-descriptions-item>
            <n-descriptions-item :label="isChannelStaffOnly ? '审核负责人' : '招商负责人'">{{ detail.colonelUserName || '-' }}</n-descriptions-item>
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

        <section v-if="showEligibilitySection" class="detail-section">
          <h3 class="section-title">资格校验</h3>
          <n-grid :cols="24" :x-gap="12">
            <n-gi :span="24">
              <n-alert
                :type="detail.eligibilityCheck?.passed ? 'success' : 'warning'"
                :title="detail.eligibilityCheck?.passed ? '提交时满足默认寄样标准' : '提交时未满足默认寄样标准'"
              >
                <template v-if="detail.eligibilityCheck?.passed">
                  系统已记录该达人在提交申请时满足默认寄样标准。
                </template>
                <template v-else>
                  <div v-if="detail.applyReason" class="eligibility-reason">
                    申请原因：{{ detail.applyReason }}
                  </div>
                  <div v-if="detail.eligibilityCheck?.reasons?.length" class="eligibility-reason">
                    未达标项：{{ detail.eligibilityCheck.reasons.join('；') }}
                  </div>
                </template>
              </n-alert>
            </n-gi>
            <n-gi :span="24">
              <n-descriptions bordered :column="2">
                <n-descriptions-item label="标准销售额">
                  {{ formatSales(detail.requirementSnapshot?.min30DaySales) }}
                </n-descriptions-item>
                <n-descriptions-item label="当前销售额">
                  {{ formatSales(detail.requirementSnapshot?.actual30DaySales) }}
                </n-descriptions-item>
                <n-descriptions-item label="标准等级">
                  {{ detail.requirementSnapshot?.minLevel || '-' }}
                </n-descriptions-item>
                <n-descriptions-item label="当前等级">
                  {{ detail.requirementSnapshot?.actualLevel || '-' }}
                </n-descriptions-item>
              </n-descriptions>
            </n-gi>
          </n-grid>
        </section>

        <section class="detail-section">
          <h3 class="section-title">{{ isChannelStaffOnly ? '当前处理状态' : '可执行操作' }}</h3>
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
            <n-button v-if="canShip && detail.status === 'SHIPPED'" type="success" @click="handleAction('SIGNED')">
              签收
            </n-button>
            <n-tag v-if="detail.status === 'PENDING_TASK'" type="warning" round>等待订单完成交作业</n-tag>
            <n-tag v-if="isChannelStaffOnly && detail.status === 'PENDING_AUDIT'" type="info" round>等待招商审核</n-tag>
            <n-tag v-if="isChannelStaffOnly && detail.status === 'PENDING_SHIP'" type="warning" round>等待运营发货</n-tag>
            <n-tag v-if="isChannelStaffOnly && detail.status === 'SHIPPED'" type="info" round>已发货，等待签收</n-tag>
          </n-space>
        </section>

        <section v-if="!isChannelStaffOnly" class="detail-section">
          <h3 class="section-title">操作日志</h3>
          <div v-if="statusLogs.length" class="log-timeline">
            <div v-for="log in statusLogs" :key="log.id" class="log-item">
              <div class="log-dot" />
              <div class="log-content">
                <div class="log-action">
                  <span v-if="log.fromStatus">{{ statusLabel(log.fromStatus) }}</span>
                  <span v-if="log.fromStatus" class="log-arrow">→</span>
                  <span class="log-target">{{ statusLabel(log.toStatus) }}</span>
                </div>
                <div class="log-meta">
                  <span>{{ log.operatorName || '-' }}</span>
                  <span class="log-sep">·</span>
                  <span>{{ formatDateTime(log.operateTime) }}</span>
                </div>
                <div v-if="log.remark" class="log-remark">{{ log.remark }}</div>
              </div>
            </div>
          </div>
          <n-empty v-else description="暂无操作日志" />
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
import { useRoute, useRouter } from 'vue-router'
import { NInput, useDialog, useMessage } from 'naive-ui'
import { actionSample, getSampleById, getSampleStatusLogs } from '../../api/sample'
import StatusTag from '../../components/StatusTag.vue'
import { ROLE_CODES } from '../../constants/rbac'
import { useAuthStore } from '../../stores/auth'
import type { SampleItem } from '../../types'

const props = withDefaults(defineProps<{ show?: boolean; sampleId?: string }>(), {
  show: true,
  sampleId: ''
})
const emit = defineEmits(['update:show', 'refresh'])

const message = useMessage()
const dialog = useDialog()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const actionLoading = ref(false)
const detail = ref<SampleItem | null>(null)
const statusLogs = ref<any[]>([])
const shippingDraft = ref('')
const shippingError = ref('')

const routeSampleId = computed(() => {
  const value = route.params.id
  return Array.isArray(value) ? value[0] : value
})
const isUuid = (value?: string) => /^[0-9a-fA-F-]{36}$/.test(value || '')
const isRouteMode = computed(() => Boolean(routeSampleId.value))
const effectiveShow = computed(() => isRouteMode.value || props.show)
const effectiveSampleId = computed(() => routeSampleId.value || props.sampleId)

const canAudit = authStore.isAdmin || authStore.roleCodes.includes(ROLE_CODES.BIZ_STAFF)
const canShip = authStore.isAdmin || authStore.roleCodes.includes(ROLE_CODES.OPS_STAFF)
const isChannelStaffOnly = computed(() => {
  const roles = authStore.roleCodes
  return roles.includes(ROLE_CODES.CHANNEL_STAFF)
    && !roles.includes(ROLE_CODES.CHANNEL_LEADER)
    && !authStore.isAdmin
})

const isOpsStaffOnly = computed(() => {
  const roles = authStore.roleCodes
  return roles.includes(ROLE_CODES.OPS_STAFF) && !authStore.isAdmin
})

const stepOrder = ['PENDING_AUDIT', 'PENDING_SHIP', 'SHIPPED', 'PENDING_TASK', 'FINISHED']

const flowSteps = computed(() => {
  const currentIndex = stepOrder.indexOf(detail.value?.status || '')
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

const showEligibilitySection = computed(() =>
  !isOpsStaffOnly.value && Boolean(
    detail.value?.applyReason
      || detail.value?.eligibilityCheck
      || detail.value?.requirementSnapshot
  )
)

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatSales(value?: number) {
  if (value === null || value === undefined) return '-'
  return `${(value / 100).toFixed(2)}`
}

function closeDetail() {
  if (isRouteMode.value) {
    router.push(isOpsStaffOnly.value ? '/ops/shipping' : '/sample')
    return
  }
  emit('update:show', false)
}

const STATUS_LABELS: Record<string, string> = {
  PENDING_AUDIT: '待审核',
  PENDING_SHIP: '待发货',
  SHIPPED: '快递中',
  SIGNED: '已签收',
  PENDING_TASK: '待交作业',
  FINISHED: '已完成',
  REJECTED: '已拒绝',
  CLOSED: '已关闭'
}

function statusLabel(status?: string) {
  if (!status) return '-'
  return STATUS_LABELS[status] || status
}

async function loadStatusLogs() {
  if (!effectiveSampleId.value || !isUuid(effectiveSampleId.value)) {
    statusLogs.value = []
    return
  }
  try {
    const res = await getSampleStatusLogs(effectiveSampleId.value)
    statusLogs.value = res?.data || res || []
  } catch {
    statusLogs.value = []
  }
}

async function loadDetail() {
  if (!effectiveSampleId.value) {
    detail.value = null
    return
  }
  if (!isUuid(effectiveSampleId.value)) {
    detail.value = null
    return
  }
  loading.value = true
  try {
    const res = await getSampleById(effectiveSampleId.value)
    detail.value = (res?.data || res) as SampleItem
    await loadStatusLogs()
  } catch (error: any) {
    detail.value = null
    message.error(error?.message || '无法获取寄样详情')
  } finally {
    loading.value = false
  }
}

async function doActionSample(payload: any) {
  if (!effectiveSampleId.value) return
  actionLoading.value = true
  try {
    await actionSample(effectiveSampleId.value, payload)
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
    shippingDraft.value = ''
    shippingError.value = ''
    dialog.warning({
      title: '填写物流单号',
      content: () =>
        h('div', [
          h('div', { style: 'margin-bottom: 8px;' }, '物流单号：'),
          h(NInput, {
            placeholder: 'SF1234567890',
            status: shippingError.value ? 'error' : undefined,
            value: shippingDraft.value,
            onUpdateValue: (nextValue) => {
              shippingDraft.value = nextValue
              if (shippingError.value && nextValue.trim()) {
                shippingError.value = ''
              }
            }
          }),
          shippingError.value
            ? h('div', { style: 'margin-top: 6px; color: var(--color-danger); font-size: var(--text-xs);' }, shippingError.value)
            : null
        ]),
      positiveText: '确认发货',
      negativeText: '取消',
      onPositiveClick: () => {
        const value = shippingDraft.value.trim()
        if (!value) {
          shippingError.value = '请先填写物流单号'
          return false
        }
        resolve(value)
        return true
      },
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

  if (action === 'SIGNED') {
    await doActionSample({ action })
    return
  }

  await doActionSample({ action })
}

watch(
  () => [effectiveShow.value, effectiveSampleId.value],
  ([show, sampleId]) => {
    if (show && sampleId) {
      loadDetail()
      return
    }
    detail.value = null
  },
  { immediate: true }
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
  font-size: var(--text-base);
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
  border-radius: var(--radius-md);
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
  font-size: var(--text-sm);
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

.log-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding-left: 4px;
}

.log-item {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-left: 2px solid var(--border-color);
  padding-left: 16px;
  position: relative;
}

.log-item:last-child {
  border-left-color: transparent;
}

.log-dot {
  position: absolute;
  left: -5px;
  top: 14px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary);
}

.log-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.log-action {
  font-size: var(--text-sm);
  font-weight: 500;
}

.log-arrow {
  margin: 0 4px;
  color: var(--text-muted);
}

.log-target {
  color: var(--color-primary);
}

.log-meta {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.log-sep {
  margin: 0 4px;
}

.log-remark {
  font-size: var(--text-xs);
  color: var(--text-secondary);
  padding: 4px 8px;
  background: var(--hover-color);
  border-radius: var(--radius-sm);
}
</style>
