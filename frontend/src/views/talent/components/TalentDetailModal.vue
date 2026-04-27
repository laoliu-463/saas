<template>
  <n-modal :show="show" preset="card" title="达人详情" style="width: 960px" @update:show="closeModal">
    <n-spin :show="loading">
      <div v-if="detail" class="detail-body">
        <section class="detail-section">
          <h3 class="section-title">基础资料</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="昵称">{{ detail.talent?.nickname || '-' }}</n-descriptions-item>
            <n-descriptions-item label="抖音号">{{ detail.talent?.douyinNo || '-' }}</n-descriptions-item>
            <n-descriptions-item label="UID">{{ detail.talent?.uid || detail.talent?.douyinUid || '-' }}</n-descriptions-item>
            <n-descriptions-item label="sec_uid">{{ detail.talent?.secUid || '-' }}</n-descriptions-item>
            <n-descriptions-item label="粉丝数">{{ formatFans(detail.talent?.fansCount) }}</n-descriptions-item>
            <n-descriptions-item label="获赞数">{{ formatFans(detail.talent?.likesCount) }}</n-descriptions-item>
            <n-descriptions-item label="作品数">{{ detail.talent?.worksCount ?? '-' }}</n-descriptions-item>
            <n-descriptions-item label="IP 属地">{{ detail.talent?.ipLocation || '-' }}</n-descriptions-item>
            <n-descriptions-item label="达人等级">{{ detail.talent?.level || '-' }}</n-descriptions-item>
            <n-descriptions-item label="近 30 天销售额">{{ formatMoney(detail.talent?.monthlySales) }}</n-descriptions-item>
            <n-descriptions-item label="联系方式">{{ detail.talent?.contactPhone || '-' }}</n-descriptions-item>
            <n-descriptions-item label="备注" :span="2">{{ detail.talent?.remark || '-' }}</n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">归属信息</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="当前状态">
              <n-tag :type="detail.claim?.poolStatus === 'PRIVATE' ? 'success' : 'warning'">
                {{ detail.claim?.poolStatus === 'PRIVATE' ? '私海' : '公海' }}
              </n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="认领人">{{ detail.claim?.ownerName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="认领时间">{{ formatDateTime(detail.claim?.claimedAt) }}</n-descriptions-item>
            <n-descriptions-item label="保护期截止">{{ formatDateTime(detail.claim?.protectedUntil) }}</n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <div class="section-head">
            <h3 class="section-title">寄样记录</h3>
            <span class="section-meta">{{ detail.samples?.length || 0 }} 条</span>
          </div>
          <n-data-table :columns="sampleColumns" :data="detail.samples || []" :pagination="false" />
        </section>

        <section class="detail-section">
          <div class="section-head">
            <h3 class="section-title">订单产出</h3>
            <span class="section-meta">{{ detail.orders?.length || 0 }} 条</span>
          </div>
          <n-data-table :columns="orderColumns" :data="detail.orders || []" :pagination="false" />
        </section>

        <section class="detail-section">
          <h3 class="section-title">跟进记录</h3>
          <n-empty description="暂无跟进记录，后续可在 CRM P1 增强中补齐。" />
        </section>
      </div>
      <n-empty v-else description="暂无达人详情" />
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
import { getTalentById, type TalentDetailResponse } from '../../../api/talent'

const props = defineProps<{ show: boolean; talentId: string }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()
const message = useMessage()
const loading = ref(false)
const detail = ref<TalentDetailResponse | null>(null)

function closeModal() {
  emit('update:show', false)
}

function formatFans(value?: number | null) {
  if (value === null || value === undefined) return '-'
  if (value >= 100000000) return `${(value / 100000000).toFixed(1)}亿`
  if (value >= 10000) return `${(value / 10000).toFixed(1)}万`
  return String(value)
}

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return `¥${(Number(value) / 100).toFixed(2)}`
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

const sampleColumns = [
  { title: '寄样单 ID', key: 'sampleRequestId', width: 180 },
  { title: '商品名称', key: 'productName', minWidth: 180 },
  { title: '状态', key: 'statusText', width: 100 },
  {
    title: '申请时间',
    key: 'createTime',
    width: 180,
    render: (row: any) => formatDateTime(row.createTime)
  },
  {
    title: '完成时间',
    key: 'completeTime',
    width: 180,
    render: (row: any) => formatDateTime(row.completeTime)
  }
]

const orderColumns = [
  { title: '订单号', key: 'orderId', width: 180 },
  { title: '商品名称', key: 'productName', minWidth: 180 },
  {
    title: '订单金额',
    key: 'orderAmount',
    width: 120,
    render: (row: any) => formatMoney(row.orderAmount)
  },
  {
    title: '服务费',
    key: 'serviceFee',
    width: 120,
    render: (row: any) => formatMoney(row.serviceFee)
  },
  { title: '归因渠道', key: 'channelName', width: 140 },
  {
    title: '订单时间',
    key: 'createTime',
    width: 180,
    render: (row: any) => formatDateTime(row.createTime)
  }
]

async function loadDetail() {
  if (!props.talentId) {
    detail.value = null
    return
  }
  loading.value = true
  try {
    detail.value = await getTalentById(props.talentId)
  } catch (error: any) {
    detail.value = null
    message.error(error?.message || '获取达人详情失败')
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

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.section-meta {
  font-size: 12px;
  color: var(--text-secondary);
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
