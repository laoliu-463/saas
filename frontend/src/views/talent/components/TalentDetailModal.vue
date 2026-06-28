<template>
  <n-modal :show="show" preset="card" title="达人详情" :style="{ width: MODAL_WIDTH.xxl }" @update:show="closeModal">
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
            <n-descriptions-item v-if="!isChannelStaffOnly" label="主页链接">{{ detail.talent?.profileUrl || '-' }}</n-descriptions-item>
            <n-descriptions-item label="获赞数">{{ formatFans(detail.talent?.likesCount) }}</n-descriptions-item>
            <n-descriptions-item label="作品数">{{ detail.talent?.worksCount ?? '-' }}</n-descriptions-item>
            <n-descriptions-item label="达人等级">{{ detail.talent?.level || detail.talent?.talentLevel || '-' }}</n-descriptions-item>
            <n-descriptions-item label="数据来源">{{ dataSourceLabel }}</n-descriptions-item>
            <n-descriptions-item label="同步状态">{{ detail.talent?.syncStatus || '-' }}</n-descriptions-item>
            <n-descriptions-item v-if="detail.talent?.syncErrorMessage" label="采集说明" :span="2">
              <n-text type="warning">{{ detail.talent.syncErrorMessage }}</n-text>
            </n-descriptions-item>
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
            <h3 class="section-title">协作信息</h3>
            <n-space v-if="canEditCollaboration" :size="8">
              <n-button size="small" secondary data-testid="talent-edit-tags" @click="startTagEdit">编辑标签</n-button>
              <n-button size="small" secondary data-testid="talent-edit-shipping" @click="startAddressEdit">编辑地址</n-button>
            </n-space>
          </div>
          <div class="collab-grid">
            <div class="collab-panel">
              <div class="collab-label">经营标签</div>
              <n-space v-if="talentTags.length" :size="8" wrap>
                <n-tag v-for="tag in talentTags" :key="tag" size="small" type="info">{{ tag }}</n-tag>
              </n-space>
              <span v-else class="empty-value">-</span>
              <div v-if="tagEditing" class="edit-stack">
                <n-select
                  v-if="presetTags.length > 0"
                  v-model:value="selectedPresetTags"
                  multiple
                  filterable
                  :max-tag-count="3"
                  :options="presetTagOptions"
                  placeholder="从预设标签库选择，最多 3 个"
                  data-testid="talent-preset-tags-select"
                />
                <div v-else class="tag-input-grid">
                  <n-input
                    v-for="index in 3"
                    :key="index"
                    v-model:value="tagDraft[index - 1]"
                    size="small"
                    :maxlength="24"
                    :placeholder="`标签 ${index}`"
                  />
                </div>
                <n-space :size="8">
                  <n-button
                    size="small"
                    type="primary"
                    :loading="savingTags"
                    data-testid="talent-save-tags"
                    @click="handleSaveTags"
                  >
                    保存标签
                  </n-button>
                  <n-button size="small" :disabled="savingTags" @click="tagEditing = false">取消</n-button>
                </n-space>
              </div>
            </div>
            <div class="collab-panel">
              <div class="collab-label">默认收货地址</div>
              <div class="address-lines">
                <div><span>收货人</span><strong>{{ shippingRecipientName || '-' }}</strong></div>
                <div><span>手机号</span><strong>{{ shippingRecipientPhone || '-' }}</strong></div>
                <div><span>地址</span><strong>{{ shippingRecipientAddress || '-' }}</strong></div>
              </div>
              <div v-if="addressEditing" class="edit-stack">
                <n-input v-model:value="addressDraft.recipientName" size="small" :maxlength="100" placeholder="收货人" />
                <n-input v-model:value="addressDraft.recipientPhone" size="small" :maxlength="32" placeholder="手机号" />
                <n-input
                  v-model:value="addressDraft.recipientAddress"
                  type="textarea"
                  size="small"
                  :autosize="{ minRows: 2, maxRows: 3 }"
                  :maxlength="512"
                  placeholder="地址"
                />
                <n-space :size="8">
                  <n-button
                    size="small"
                    type="primary"
                    :loading="savingAddress"
                    data-testid="talent-save-shipping"
                    @click="handleSaveShipping"
                  >
                    保存地址
                  </n-button>
                  <n-button size="small" :disabled="savingAddress" @click="addressEditing = false">取消</n-button>
                </n-space>
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
        <n-button secondary :loading="refreshing" data-testid="talent-detail-refresh" @click="handleRefresh">
          刷新达人信息
        </n-button>
        <n-button v-if="canApplySample && detail" type="primary" secondary @click="handleApplySample">快速寄样</n-button>
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
import {
  getTalentById,
  getPresetTalentTags,
  syncTalentProfile,
  updateTalentShippingAddress,
  updateTalentTags,
  type TalentDetailResponse
} from '../../../api/talent'
import { useAuthStore } from '../../../stores/auth'
import { resolveSafeAvatarUrl } from '../../../utils/media'
import { buildTalentSampleContext } from '../../sample/sample-context'
import {
  canApplySampleFromTalentByRole,
  formatDateTime,
  formatFans,
  formatMoney,
  getPoolLabel,
  getPoolTagType,
  isChannelStaffOnlyTalentRole
} from '../constants'

const props = defineProps<{ show: boolean; talentId: string }>()
const emit = defineEmits<{
  'update:show': [value: boolean]
  applySample: [query: Record<string, string>]
}>()

const message = useMessage()
const authStore = useAuthStore()
const loading = ref(false)
const refreshing = ref(false)
const tagEditing = ref(false)
const addressEditing = ref(false)
const savingTags = ref(false)
const savingAddress = ref(false)
const tagDraft = ref(['', '', ''])
const presetTags = ref<string[]>([])
const selectedPresetTags = ref<string[]>([])
const presetTagOptions = computed(() => presetTags.value.map(tag => ({ label: tag, value: tag })))
const addressDraft = ref({
  recipientName: '',
  recipientPhone: '',
  recipientAddress: ''
})
const detail = ref<TalentDetailResponse | null>(null)
const isChannelStaffOnly = computed(() => isChannelStaffOnlyTalentRole(authStore.roleCodes, authStore.isAdmin))
const canApplySample = computed(() => canApplySampleFromTalentByRole(authStore.roleCodes, authStore.isAdmin))
const canEditCollaboration = computed(() => canApplySample.value)

const talentTags = computed(() =>
  Array.isArray(detail.value?.talent?.tags)
    ? detail.value.talent.tags.map(tag => String(tag || '').trim()).filter(Boolean).slice(0, 3)
    : []
)
const shippingRecipientName = computed(() => detail.value?.claim?.recipientName || detail.value?.talent?.shippingRecipientName || '')
const shippingRecipientPhone = computed(() => detail.value?.claim?.recipientPhone || detail.value?.talent?.shippingRecipientPhone || '')
const shippingRecipientAddress = computed(() => detail.value?.claim?.recipientAddress || detail.value?.talent?.shippingRecipientAddress || '')

const resolvedPoolStatus = computed(() => {
  if (detail.value?.talent?.blacklisted) return 'BLACKLIST'
  if (Number(detail.value?.claim?.activeClaimCount || 0) > 0) return 'PRIVATE'
  return detail.value?.claim?.poolStatus || 'PUBLIC'
})

const poolLabel = computed(() => getPoolLabel(resolvedPoolStatus.value))
const poolTagType = computed(() => getPoolTagType(resolvedPoolStatus.value))

const dataSourceLabel = computed(() => {
  const source = detail.value?.talent?.dataSource
  if (!source) return '-'
  const map: Record<string, string> = {
    API: '官方 API',
    CRAWLER: '公开页爬虫',
    public_web: '公开页爬虫',
    manual: '手动补录',
    MANUAL: '手动补录',
    MOCK: 'Mock 数据',
    configurable_http: '可配置 HTTP',
    stub: '内部同步'
  }
  return map[source] || source
})

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

function normalizeTags(values: string[]) {
  const unique: string[] = []
  for (const value of values) {
    const text = String(value || '').trim()
    if (!text || unique.includes(text)) {
      continue
    }
    unique.push(text)
    if (unique.length >= 3) {
      break
    }
  }
  return unique
}

function startTagEdit() {
  selectedPresetTags.value = [...talentTags.value]
  const next = [...talentTags.value]
  while (next.length < 3) {
    next.push('')
  }
  tagDraft.value = next.slice(0, 3)
  tagEditing.value = true
}

function startAddressEdit() {
  addressDraft.value = {
    recipientName: shippingRecipientName.value,
    recipientPhone: shippingRecipientPhone.value,
    recipientAddress: shippingRecipientAddress.value
  }
  addressEditing.value = true
}

function resetCollaborationEditing() {
  tagEditing.value = false
  addressEditing.value = false
  savingTags.value = false
  savingAddress.value = false
}

async function loadPresetTags() {
  try {
    const tags = await getPresetTalentTags()
    presetTags.value = Array.isArray(tags) ? tags.filter(Boolean) : []
  } catch {
    presetTags.value = []
  }
}

async function loadDetail() {
  if (!props.talentId) {
    detail.value = null
    return
  }
  loading.value = true
  try {
    detail.value = await getTalentById(props.talentId)
    resetCollaborationEditing()
  } catch (error: any) {
    detail.value = null
    notifyApiFailure(error, message, { fallbackMessage: '获取达人详情失败' })
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  if (!props.talentId) return
  const previous = detail.value?.talent ? { ...detail.value.talent } : null
  refreshing.value = true
  try {
    const syncResult = await syncTalentProfile(props.talentId, true)
    await loadDetail()
    if (syncResult?.success) {
      message.success('已刷新达人资料')
      return
    }
    const reason = syncResult?.syncErrorMessage || syncResult?.syncErrorCode || '采集未成功'
    message.warning(`刷新未完成：${reason}（已保留原有资料）`)
    if (!detail.value?.talent?.nickname && previous?.nickname) {
      detail.value = detail.value || {}
      detail.value.talent = { ...previous, ...detail.value?.talent }
    }
  } catch (error: any) {
    if (previous && detail.value?.talent) {
      detail.value.talent = { ...previous, ...detail.value.talent }
    }
    notifyApiFailure(error, message, { fallbackMessage: '刷新达人信息失败' })
  } finally {
    refreshing.value = false
  }
}

async function handleSaveTags() {
  if (!props.talentId || !detail.value?.talent) return
  savingTags.value = true
  try {
    const payload = presetTags.value.length > 0 ? selectedPresetTags.value : tagDraft.value
    const saved = await updateTalentTags(props.talentId, normalizeTags(payload))
    detail.value.talent.tags = Array.isArray(saved) ? saved : []
    tagEditing.value = false
    message.success('已保存达人标签')
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '保存达人标签失败' })
  } finally {
    savingTags.value = false
  }
}

async function handleSaveShipping() {
  if (!props.talentId || !detail.value?.talent) return
  savingAddress.value = true
  try {
    const saved: any = await updateTalentShippingAddress(props.talentId, {
      recipientName: addressDraft.value.recipientName,
      recipientPhone: addressDraft.value.recipientPhone,
      recipientAddress: addressDraft.value.recipientAddress
    })
    const savedName = saved?.shippingRecipientName ?? addressDraft.value.recipientName
    const savedPhone = saved?.shippingRecipientPhone ?? addressDraft.value.recipientPhone
    const savedAddress = saved?.shippingRecipientAddress ?? addressDraft.value.recipientAddress
    detail.value.talent.shippingRecipientName = savedName
    detail.value.talent.shippingRecipientPhone = savedPhone
    detail.value.talent.shippingRecipientAddress = savedAddress
    detail.value.claim = detail.value.claim || {}
    detail.value.claim.recipientName = savedName
    detail.value.claim.recipientPhone = savedPhone
    detail.value.claim.recipientAddress = savedAddress
    addressEditing.value = false
    message.success('已保存默认收货地址')
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '保存默认收货地址失败' })
  } finally {
    savingAddress.value = false
  }
}

function handleApplySample() {
  const context = buildTalentSampleContext(detail.value)
  if (!context.query.talentId) {
    message.warning('达人信息不完整，暂不可快速寄样')
    return
  }
  emit('applySample', context.query)
}

watch(
  () => [props.show, props.talentId],
  ([show]) => {
    if (show) {
      loadPresetTags()
      loadDetail()
      return
    }
    detail.value = null
    resetCollaborationEditing()
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

.collab-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.35fr);
  gap: 12px;
}

.collab-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.02);
}

.collab-label {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.empty-value {
  color: var(--text-secondary);
}

.edit-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tag-input-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.address-lines {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.address-lines div {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  font-size: var(--text-sm);
}

.address-lines span {
  color: var(--text-secondary);
}

.address-lines strong {
  min-width: 0;
  font-weight: 500;
  color: var(--text-primary);
  word-break: break-word;
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

  .collab-grid,
  .tag-input-grid {
    grid-template-columns: 1fr;
  }
}
</style>
