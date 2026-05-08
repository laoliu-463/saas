<template>
  <n-modal :show="show" preset="card" title="达人详情" style="width: 1040px" @update:show="closeModal">
    <n-spin :show="loading">
      <div v-if="detail" class="detail-body">
        <section class="hero-card">
          <div class="hero-main">
            <n-avatar round :size="72" :src="resolveSafeAvatarUrl(detail.talent?.avatarUrl)">
              {{ (detail.talent?.nickname || '达').slice(0, 1) }}
            </n-avatar>
            <div class="hero-copy">
              <div class="hero-title">{{ detail.talent?.nickname || '未命名达人' }}</div>
              <div class="hero-sub">抖音号：{{ detail.talent?.douyinNo || '-' }} · UID：{{ detail.talent?.uid || detail.talent?.douyinUid || '-' }}</div>
              <n-space :size="8" wrap>
                <n-tag :type="poolTagType">{{ poolLabel }}</n-tag>
                <n-tag type="info">{{ detail.talent?.mainCategory || '未分类' }}</n-tag>
                <n-tag type="default">{{ detail.talent?.ipLocation || '未知地区' }}</n-tag>
              </n-space>
            </div>
          </div>
          <div class="hero-metrics">
            <div class="metric-block">
              <span class="metric-label">粉丝数</span>
              <strong>{{ formatFans(detail.talent?.fansCount) }}</strong>
            </div>
            <div class="metric-block">
              <span class="metric-label">订单数</span>
              <strong>{{ detail.talent?.orderCount ?? detail.orders?.length ?? 0 }}</strong>
            </div>
            <div class="metric-block">
              <span class="metric-label">服务费贡献</span>
              <strong>{{ formatMoney(detail.talent?.serviceFeeContribution) }}</strong>
            </div>
          </div>
        </section>

        <section class="detail-section">
          <h3 class="section-title">基础资料</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item v-if="!isChannelStaffOnly" label="sec_uid">{{ detail.talent?.secUid || '-' }}</n-descriptions-item>
            <n-descriptions-item v-if="!isChannelStaffOnly" label="主页链接">{{ detail.talent?.profileUrl || '-' }}</n-descriptions-item>
            <n-descriptions-item label="获赞数">{{ formatFans(detail.talent?.likesCount) }}</n-descriptions-item>
            <n-descriptions-item label="作品数">{{ detail.talent?.worksCount ?? '-' }}</n-descriptions-item>
            <n-descriptions-item label="达人等级">{{ detail.talent?.level || '-' }}</n-descriptions-item>
            <n-descriptions-item label="联系方式">{{ detail.talent?.contactPhone || '-' }}</n-descriptions-item>
            <n-descriptions-item label="备注" :span="2">{{ detail.talent?.remark || '-' }}</n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">经营信息</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="近 30 天销售额">{{ formatMoney(detail.talent?.monthlySales) }}</n-descriptions-item>
            <n-descriptions-item label="直播销售区间">{{ detail.talent?.liveSalesBand || '-' }}</n-descriptions-item>
            <n-descriptions-item label="寄样次数">{{ detail.talent?.sampleCount ?? detail.samples?.length ?? 0 }}</n-descriptions-item>
            <n-descriptions-item label="视频销售区间">{{ detail.talent?.videoSalesBand || '-' }}</n-descriptions-item>
            <n-descriptions-item label="直播观看区间">{{ detail.talent?.liveViewBand || '-' }}</n-descriptions-item>
            <n-descriptions-item label="直播 GPM">{{ detail.talent?.liveGpmBand || '-' }}</n-descriptions-item>
            <n-descriptions-item label="黑名单状态">{{ detail.talent?.blacklisted ? '已拉黑' : '正常' }}</n-descriptions-item>
            <n-descriptions-item label="黑名单原因">{{ detail.talent?.blacklistReason || '-' }}</n-descriptions-item>
          </n-descriptions>
        </section>

        <section class="detail-section">
          <h3 class="section-title">{{ isChannelStaffOnly ? '合作信息' : '归属信息' }}</h3>
          <n-descriptions bordered :column="2">
            <n-descriptions-item label="当前状态">
              <n-tag :type="poolTagType">{{ poolLabel }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item :label="isChannelStaffOnly ? '当前归属' : '认领人'">{{ detail.claim?.ownerName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="认领时间">{{ formatDateTime(detail.claim?.claimedAt) }}</n-descriptions-item>
            <n-descriptions-item label="保护期截止">{{ formatDateTime(detail.claim?.protectedUntil) }}</n-descriptions-item>
            <n-descriptions-item v-if="!isChannelStaffOnly" label="有效认领人数">{{ detail.claim?.activeClaimCount ?? 0 }}</n-descriptions-item>
          </n-descriptions>
          <div v-if="!isChannelStaffOnly && detail.claim?.activeClaimOwners?.length" class="claim-owner-list">
            <div class="claim-owner-title">当前有效认领人</div>
            <div
              v-for="owner in detail.claim.activeClaimOwners"
              :key="`${owner.userId || 'unknown'}-${owner.claimedAt || ''}`"
              class="claim-owner-item"
            >
              <div class="claim-owner-name">{{ owner.ownerName || '-' }}</div>
              <div class="claim-owner-meta">
                认领时间：{{ formatDateTime(owner.claimedAt) }} · 保护期：{{ formatDateTime(owner.protectedUntil) }}
              </div>
            </div>
          </div>
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
      </div>
      <n-empty v-else description="暂无达人详情" />
    </n-spin>

    <template #footer>
      <div class="footer-actions">
        <n-button secondary :loading="refreshing" @click="handleRefresh">刷新达人信息</n-button>
        <n-button @click="closeModal">关闭</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { getTalentById, refreshTalent, type TalentDetailResponse } from '../../../api/talent'
import { useAuthStore } from '../../../stores/auth'
import { ROLE_CODES } from '../../../constants/rbac'
import { resolveSafeAvatarUrl } from '../../../utils/media'
import { formatDateTime, formatFans, formatMoney, getPoolLabel, getPoolTagType } from '../constants'

const props = defineProps<{ show: boolean; talentId: string }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()

const message = useMessage()
const authStore = useAuthStore()
const loading = ref(false)
const refreshing = ref(false)
const detail = ref<TalentDetailResponse | null>(null)
const isChannelStaffOnly = computed(() => {
  const roles = authStore.roleCodes
  return roles.includes(ROLE_CODES.CHANNEL_STAFF)
    && !roles.includes(ROLE_CODES.CHANNEL_LEADER)
    && !authStore.isAdmin
})

const resolvedPoolStatus = computed(() => {
  if (detail.value?.talent?.blacklisted) return 'BLACKLIST'
  if (Number(detail.value?.claim?.activeClaimCount || 0) > 0) return 'PRIVATE'
  return detail.value?.claim?.poolStatus || 'PUBLIC'
})

const poolLabel = computed(() => getPoolLabel(resolvedPoolStatus.value))
const poolTagType = computed(() => getPoolTagType(resolvedPoolStatus.value))

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

const orderColumns = computed(() => [
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
  ...(!isChannelStaffOnly.value ? [{ title: '归因渠道', key: 'channelName', width: 140 }] : []),
  {
    title: '订单时间',
    key: 'createTime',
    width: 180,
    render: (row: any) => formatDateTime(row.createTime)
  }
])

function closeModal() {
  emit('update:show', false)
}

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
    message.error(error?.msg || error?.message || '获取达人详情失败')
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  if (!props.talentId) return
  refreshing.value = true
  try {
    await refreshTalent(props.talentId)
    message.success('已刷新达人信息')
    await loadDetail()
  } catch (error: any) {
    message.error(error?.msg || error?.message || '刷新达人信息失败')
  } finally {
    refreshing.value = false
  }
}

watch(
  () => [props.show, props.talentId],
  ([show]) => {
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

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
}

.hero-main {
  display: flex;
  align-items: center;
  gap: 16px;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hero-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.hero-sub {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(88px, 1fr));
  gap: 16px;
  min-width: 360px;
}

.metric-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 8px;
}

.metric-label {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.claim-owner-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.02);
}

.claim-owner-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.claim-owner-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.claim-owner-name {
  font-size: var(--text-sm);
  font-weight: 500;
}

.claim-owner-meta {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
}

.section-meta {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 960px) {
  .hero-card {
    flex-direction: column;
  }

  .hero-metrics {
    min-width: 0;
  }
}
</style>
