<template>
  <n-modal :show="show" preset="card" title="订单详情" :style="{ width: MODAL_WIDTH.xl }" @update:show="closeModal">
    <n-spin :show="loading">
      <div v-if="detail" class="detail-body">
        <n-alert
          :type="caseSummary.type"
          :title="caseSummary.title"
        >
          <div class="case-summary-content">
            <div>{{ caseSummary.description }}</div>
            <ul v-if="caseSummary.actions.length" class="case-summary-actions">
              <li v-for="action in caseSummary.actions" :key="action">{{ action }}</li>
            </ul>
          </div>
        </n-alert>

        <section class="detail-section">
          <h3 class="section-title">排查路径</h3>
          <div class="trace-grid">
            <div
              v-for="step in traceSteps"
              :key="step.key"
              class="trace-card"
              :class="step.tone"
            >
              <div class="trace-header">
                <span class="trace-step">{{ step.step }}</span>
                <span class="trace-title">{{ step.title }}</span>
              </div>
              <div class="trace-status">{{ step.status }}</div>
              <div class="trace-desc">{{ step.description }}</div>
            </div>
          </div>
        </section>

        <section class="detail-section">
          <h3 class="section-title">订单基础信息</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="订单号">{{ detail.orderId || '-' }}</n-descriptions-item>
            <n-descriptions-item label="订单状态">{{ detail.orderStatusText || '-' }}</n-descriptions-item>
            <n-descriptions-item label="下单时间">{{ formatDateTime(detail.time?.createTime) }}</n-descriptions-item>
            <n-descriptions-item label="结算时间">{{ formatDateTime(detail.time?.settleTime) }}</n-descriptions-item>
            <n-descriptions-item label="同步时间" :span="2">{{ formatDateTime(detail.time?.syncTime) }}</n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">费用信息</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="订单金额">{{ formatMoney(detail.amount?.orderAmount) }}</n-descriptions-item>
            <n-descriptions-item label="结算金额">{{ formatMoney(detail.amount?.settleAmount) }}</n-descriptions-item>
            <n-descriptions-item label="预估服务费">{{ formatMoney(detail.amount?.estimateServiceFee) }}</n-descriptions-item>
            <n-descriptions-item label="结算服务费">{{ formatMoney(detail.amount?.effectiveServiceFee) }}</n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">归因结果</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="归因状态">
              <StatusTag scene="attribution" :status="detail.attributionStatus" />
            </n-descriptions-item>
            <n-descriptions-item label="归因方式">
              {{ resolveAttributionMethod(detail) }}
            </n-descriptions-item>
            <n-descriptions-item label="渠道">{{ detail.channel?.channelName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="渠道 ID">{{ detail.channel?.channelUserId || '-' }}</n-descriptions-item>
            <n-descriptions-item label="pick_source">{{ detail.pickSource || '-' }}</n-descriptions-item>
            <n-descriptions-item v-if="detail.attributionStatus !== 'ATTRIBUTED'" label="未归因原因">
              {{ resolveReasonText(detail) }}
            </n-descriptions-item>
            <n-descriptions-item v-if="detail.attributionStatus !== 'ATTRIBUTED'" label="处理建议" :span="2">
              {{ resolveReasonSuggestion(detail) }}
            </n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">推广链路</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="pick_source">{{ detail.promotion?.pickSource || '-' }}</n-descriptions-item>
            <n-descriptions-item label="匹配结果">
              <n-tag :type="detail.promotion?.matched ? 'success' : 'warning'">
                {{ detail.promotion?.matched ? '已匹配推广链接' : '未匹配到系统推广链接' }}
              </n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="promotionUrl" :span="2">
              <span class="break-all">{{ detail.promotion?.promotionUrl || '-' }}</span>
            </n-descriptions-item>
            <n-descriptions-item label="mappingId">{{ detail.promotion?.mappingId || '-' }}</n-descriptions-item>
            <n-descriptions-item label="生成时间">{{ formatDateTime(detail.promotion?.createdAt) }}</n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">商品 / 活动 / 招商</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="商品 ID">{{ detail.product?.productId || '-' }}</n-descriptions-item>
            <n-descriptions-item label="商品名称">{{ detail.product?.productName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="活动 ID">{{ detail.product?.activityId || '-' }}</n-descriptions-item>
            <n-descriptions-item label="活动名称">{{ detail.product?.activityName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="招商组长">{{ detail.product?.colonelName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="招商 ID">{{ detail.product?.colonelUserId || '-' }}</n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">达人信息</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="达人 UID">{{ detail.talent?.talentUid || '-' }}</n-descriptions-item>
            <n-descriptions-item label="达人昵称">{{ detail.talent?.talentName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="author_id / talent_uid" :span="2">
              {{ [detail.talent?.authorId, detail.talent?.talentUid].filter(Boolean).join(' / ') || '暂无达人匹配信息' }}
            </n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">寄样关联</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="是否关联寄样单">
              {{ detail.sample?.matched ? '是' : '否' }}
            </n-descriptions-item>
            <n-descriptions-item label="寄样单 ID">{{ detail.sample?.sampleRequestId || '-' }}</n-descriptions-item>
            <n-descriptions-item label="寄样状态">{{ detail.sample?.sampleStatusText || '-' }}</n-descriptions-item>
            <n-descriptions-item label="完成说明">
              {{ detail.sample?.completedByOrderRule ? '该订单已命中寄样交作业规则' : '-' }}
            </n-descriptions-item>
          </n-descriptions>
        </section>
      </div>

      <n-empty v-else description="暂无订单详情" />
    </n-spin>

    <template #footer>
      <div class="footer-actions">
        <n-button
          :loading="loading"
          :disabled="!orderId"
          data-testid="order-detail-refresh"
          @click="loadDetail"
        >
          刷新
        </n-button>
        <n-button @click="closeModal">关闭</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../../utils/requestError'
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { MODAL_WIDTH } from '../../../constants/ui'
import { getOrderDetail, type OrderDetail } from '../../../api/order'
import StatusTag from '../../../components/StatusTag.vue'
import {
  getAttributionReasonSuggestion,
  getAttributionReasonText
} from '../../../constants/orderAttribution'

const props = defineProps<{
  show: boolean
  orderId: string
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
}>()

const message = useMessage()
const loading = ref(false)
const detail = ref<OrderDetail | null>(null)

// 后端权威优先：模板优先展示 detail.diagnosis.reasonText / suggestion（已由 OrderQueryService 翻译）。
// 仅在后端字段缺失时 fallback 到前端 constants/orderAttribution 的 map（兼容旧契约）。
function resolveReasonText(d: OrderDetail | null) {
  if (!d) return '-'
  const backend = d.diagnosis?.reasonText
  if (backend && backend.trim()) return backend
  return getAttributionReasonText(d.diagnosis?.reasonCode || d.attributionRemark)
}

function resolveReasonSuggestion(d: OrderDetail | null) {
  if (!d) return '-'
  const backend = d.diagnosis?.suggestion
  if (backend && backend.trim()) return backend
  return getAttributionReasonSuggestion(d.diagnosis?.reasonCode || d.attributionRemark)
}

// 归因方式：已归因 + 命中推广映射 → 推广映射归因；其余场景显示 "-"。
// 后续后端补 attributionMethod 字段后，此处改为直接读后端字段。
function resolveAttributionMethod(d: OrderDetail | null) {
  if (!d || d.attributionStatus !== 'ATTRIBUTED') return '-'
  if (d.promotion?.mappingId) return '推广映射归因'
  return '-'
}

const caseSummary = computed(() => {
  if (!detail.value) {
    return {
      type: 'info' as const,
      title: '订单详情',
      description: '-',
      actions: [] as string[]
    }
  }

  if (detail.value.attributionStatus === 'ATTRIBUTED') {
    return {
      type: 'success' as const,
      title: '当前订单已完成归因',
      description: `系统已把这笔订单归到 ${detail.value.channel?.channelName || '对应渠道'}，可继续结合商品、达人和寄样信息做结果复盘。`,
      actions: [
        '先确认 pick_source、推广链接和渠道负责人是否与预期一致',
        '再核对商品招商组长、达人信息和寄样记录，判断本次成交是自然转化还是有明确前置动作支撑'
      ]
    }
  }

  return {
    type: 'warning' as const,
    title: '当前订单待排查',
    description: `原因：${resolveReasonText(detail.value)}。建议先顺着下方排查路径定位卡点。`,
    actions: [
      resolveReasonSuggestion(detail.value),
      '优先核对订单是否携带 pick_source，再确认系统内是否存在对应推广映射',
      '如果链路字段都正常，再回看商品活动绑定、招商组长和达人使用链路是否一致'
    ]
  }
})

const traceSteps = computed(() => {
  if (!detail.value) return []

  const hasPickSource = Boolean(detail.value.pickSource || detail.value.promotion?.pickSource)
  const matchedPromotion = Boolean(detail.value.promotion?.matched)
  const attributed = detail.value.attributionStatus === 'ATTRIBUTED'
  const hasProduct = Boolean(detail.value.product?.productId)
  const hasChannel = Boolean(detail.value.channel?.channelUserId || detail.value.channel?.channelName)

  return [
    {
      key: 'sync',
      step: '01',
      title: '订单回流',
      status: detail.value.time?.syncTime ? '已回流到系统' : '等待确认同步',
      description: detail.value.time?.syncTime
        ? `最近同步时间：${formatDateTime(detail.value.time?.syncTime)}`
        : '先确认这笔订单是否已经完成回流同步。',
      tone: detail.value.time?.syncTime ? 'success' : 'warning'
    },
    {
      key: 'pick-source',
      step: '02',
      title: '推广参数识别',
      status: hasPickSource ? '已识别 pick_source' : '缺少推广参数',
      description: hasPickSource
        ? `当前 pick_source：${detail.value.pickSource || detail.value.promotion?.pickSource}`
        : '订单里没有有效 pick_source，通常无法继续做渠道归因。',
      tone: hasPickSource ? 'success' : 'error'
    },
    {
      key: 'mapping',
      step: '03',
      title: '系统推广映射',
      status: matchedPromotion ? '已匹配系统推广链接' : '未匹配到推广映射',
      description: matchedPromotion
        ? `mappingId：${detail.value.promotion?.mappingId || '-'}`
        : '请回看商品是否通过系统转链，以及映射是否成功落库。',
      tone: matchedPromotion ? 'success' : hasPickSource ? 'warning' : 'default'
    },
    {
      key: 'attribution',
      step: '04',
      title: '业务归因结果',
      status: attributed ? '已归到渠道负责人' : '归因尚未完成',
      description: attributed
        ? `渠道：${detail.value.channel?.channelName || '-'}；招商：${detail.value.product?.colonelName || '-'}`
        : hasProduct || hasChannel
          ? '商品、活动或负责人信息仍需继续补齐后再判断归因结果。'
          : '当前还缺少商品或渠道负责人关联信息，建议优先补主链路。 ',
      tone: attributed ? 'success' : 'warning'
    }
  ]
})

function closeModal() {
  emit('update:show', false)
}

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return `¥${(Number(value) / 100).toFixed(2)}`
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function loadDetail() {
  if (!props.orderId) {
    detail.value = null
    return
  }
  loading.value = true
  try {
    detail.value = await getOrderDetail(props.orderId)
  } catch (error: any) {
    detail.value = null
    notifyApiFailure(error, message, { fallbackMessage: '订单详情加载失败，请稍后重试' })
  } finally {
    loading.value = false
  }
}

watch(
  () => props.show,
  (show) => {
    if (show) {
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

.case-summary-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  line-height: 1.7;
}

.case-summary-actions {
  margin: 0;
  padding-left: 18px;
  color: var(--text-secondary);
}

.trace-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.trace-card {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 14px;
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trace-card.success {
  border-color: rgba(24, 160, 88, 0.28);
  background: rgba(24, 160, 88, 0.06);
}

.trace-card.warning {
  border-color: rgba(240, 160, 32, 0.28);
  background: rgba(240, 160, 32, 0.06);
}

.trace-card.error {
  border-color: rgba(208, 48, 80, 0.28);
  background: rgba(208, 48, 80, 0.06);
}

.trace-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.trace-step {
  min-width: 32px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.trace-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.trace-status {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.trace-desc {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  line-height: 1.6;
}

.section-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
}

.break-all {
  word-break: break-all;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .trace-grid {
    grid-template-columns: 1fr;
  }
}
</style>
