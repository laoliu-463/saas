<template>
  <n-modal :show="show" preset="card" title="订单详情" style="width: 920px" @update:show="closeModal">
    <n-spin :show="loading">
      <div v-if="detail" class="detail-body">
        <n-alert
          v-if="detail.attributionStatus !== 'ATTRIBUTED'"
          type="warning"
          title="当前订单待排查"
        >
          原因：{{ getAttributionReasonText(detail.diagnosis?.reasonCode || detail.attributionRemark) }}
          <br />
          建议：{{ getAttributionReasonSuggestion(detail.diagnosis?.reasonCode || detail.attributionRemark) }}
        </n-alert>

        <section class="detail-section">
          <h3 class="section-title">订单基础信息</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="订单号">{{ detail.orderId || '-' }}</n-descriptions-item>
            <n-descriptions-item label="订单状态">{{ detail.orderStatusText || '-' }}</n-descriptions-item>
            <n-descriptions-item label="下单时间">{{ formatDateTime(detail.time?.createTime) }}</n-descriptions-item>
            <n-descriptions-item label="结算时间">{{ formatDateTime(detail.time?.settleTime) }}</n-descriptions-item>
            <n-descriptions-item label="同步时间">{{ formatDateTime(detail.time?.syncTime) }}</n-descriptions-item>
            <n-descriptions-item label="订单金额">{{ formatMoney(detail.amount?.orderAmount) }}</n-descriptions-item>
            <n-descriptions-item label="服务费">{{ formatMoney(detail.amount?.serviceFee) }}</n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">归因结果</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="归因状态">
              <StatusTag scene="attribution" :status="detail.attributionStatus" />
            </n-descriptions-item>
            <n-descriptions-item label="归因方式">
              {{ detail.attributionStatus === 'ATTRIBUTED' ? 'pick_source_mapping' : '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="渠道">{{ detail.channel?.channelName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="渠道 ID">{{ detail.channel?.channelUserId || '-' }}</n-descriptions-item>
            <n-descriptions-item label="pick_source">{{ detail.pickSource || '-' }}</n-descriptions-item>
            <n-descriptions-item v-if="detail.attributionStatus !== 'ATTRIBUTED'" label="未归因原因">
              {{ getAttributionReasonText(detail.diagnosis?.reasonCode || detail.attributionRemark) }}
            </n-descriptions-item>
            <n-descriptions-item v-if="detail.attributionStatus !== 'ATTRIBUTED'" label="处理建议" :span="2">
              {{ getAttributionReasonSuggestion(detail.diagnosis?.reasonCode || detail.attributionRemark) }}
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
            <n-descriptions-item label="招商负责人">{{ detail.product?.colonelName || '-' }}</n-descriptions-item>
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
        <n-button @click="closeModal">关闭</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
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
    message.error(error?.message || '订单详情加载失败，请稍后重试')
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

.section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.break-all {
  word-break: break-all;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
