<template>
  <div class="product-page">
    <PageHeader
      title="商品库"
      description="渠道选品、查看商品详情、复制推广链接，作为本地演示链路的业务入口。"
    >
      <template #actions>
        <n-button :loading="loading" type="primary" @click="refreshProducts">刷新商品</n-button>
      </template>
    </PageHeader>

    <n-alert v-if="resolvedActivityId" type="info" style="margin-bottom: 16px;">
      当前正在查看活动下的商品，可直接做选品、转链和寄样前置准备。
      <template #action>
        <n-button size="small" @click="$router.push('/product/activity')">返回活动列表</n-button>
      </template>
    </n-alert>

    <div class="product-toolbar">
      <n-space vertical :size="12">
        <n-space wrap :size="12" align="center">
          <n-select
            v-model:value="selectedProduct"
            :options="productOptions"
            :loading="productOptionsLoading"
            filterable
            remote
            clearable
            placeholder="搜索商品 / 店铺"
            style="width: 280px"
            @search="handleProductSearch"
          />
          <n-select
            v-model:value="filters.category"
            :options="categoryOptions"
            placeholder="商品标签"
            clearable
            style="width: 160px"
          />
          <n-select
            v-model:value="filters.commission"
            :options="commissionOptions"
            placeholder="佣金区间"
            clearable
            style="width: 140px"
          />
          <n-select
            v-model:value="filters.hasSample"
            :options="yesNoOptions"
            placeholder="支持寄样"
            clearable
            style="width: 120px"
          />
          <n-select
            v-model:value="filters.assignee"
            :options="assigneeOptions"
            placeholder="招商归属"
            clearable
            style="width: 160px"
          />
          <n-select
            v-model:value="status"
            :options="statusOptions"
            placeholder="业务状态"
            clearable
            style="width: 160px"
          />
          <n-button type="primary" :loading="loading" @click="refreshProducts">查询</n-button>
          <n-button @click="resetFilters">重置</n-button>
        </n-space>
      </n-space>
    </div>

    <n-spin :show="loading">
      <div v-if="products.length" class="product-grid">
        <n-popover
          v-for="item in products"
          :key="item.productId"
          trigger="hover"
          placement="right"
          :width="320"
          style="padding: 0; border-radius: 12px; overflow: hidden;"
        >
          <template #trigger>
            <div class="product-card" @click="openDetail(item)" style="cursor: pointer;">
              <div class="card-image">
                <img :src="item.cover || undefined" :alt="item.title || '商品图片'" @error="handleImageError" />
                <div v-if="!item.cover" class="card-image-fallback">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="32" height="32">
                    <rect x="3" y="3" width="18" height="18" rx="2" />
                    <circle cx="8.5" cy="8.5" r="1.5" />
                    <path d="M21 15l-5-5L5 21" />
                  </svg>
                </div>
                <span class="card-badge" :class="statusBadgeClass(item.bizStatus)">
                  {{ getBusinessStatusLabel(item) }}
                </span>
              </div>

              <div class="card-body">
                <div class="card-header">
                  <h4 class="card-title" :title="item.title || '-'">{{ item.title || '-' }}</h4>
                  <p class="card-subtitle">{{ item.shopName || '未识别店铺' }}</p>
                </div>

                <div class="card-tags">
                  <n-tag v-for="tag in cardTags(item)" :key="tag.text" :type="tag.type" size="small" bordered>
                    {{ tag.text }}
                  </n-tag>
                </div>

                <div class="card-metrics">
                  <div class="metric-item">
                    <span class="metric-label">售价</span>
                    <span class="metric-val">{{ item.priceText || '-' }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">佣金率</span>
                    <span class="metric-val primary">{{ item.activityCosRatioText || '-' }}</span>
                  </div>
                  <div class="metric-item">
                    <span class="metric-label">预估服务费</span>
                    <span class="metric-val accent">¥{{ item.estimatedServiceFee || '0.00' }}</span>
                  </div>
                </div>

                <div class="card-footer">
                  <div class="card-stats">
                    <span class="stats-active">{{ item.activityName || '活动商品' }}</span>
                  </div>
                  <div class="card-actions">
                    <n-button
                      v-if="item.bizStatus === 'PENDING_AUDIT' && canDo('audit')"
                      size="small"
                      quaternary
                      type="warning"
                      @click.stop="openDialog('audit', item)"
                    >
                      审核
                    </n-button>
                    <n-button
                      v-if="['APPROVED', 'BOUND'].includes(item.bizStatus) && canDo('assign')"
                      size="small"
                      quaternary
                      type="info"
                      @click.stop="openDialog('assign', item)"
                    >
                      分配
                    </n-button>
                    <n-button size="small" quaternary @click.stop="openDetail(item)">详情</n-button>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <div class="quick-view-panel">
            <div class="qv-header">
              <div class="qv-title">业务快照</div>
              <n-tag size="small" :type="statusBadgeClass(item.bizStatus)">{{ getStatusLabel(item.bizStatus) }}</n-tag>
            </div>
            <div class="qv-content">
              <div class="qv-section">
                <div class="section-label">经营表现 (近30天)</div>
                <n-grid :cols="2" :x-gap="12">
                  <n-gi>
                    <div class="qv-stat">
                      <div class="qv-stat-label">成交数</div>
                      <div class="qv-stat-val">{{ item.sales30d || 0 }}</div>
                    </div>
                  </n-gi>
                  <n-gi>
                    <div class="qv-stat">
                      <div class="qv-stat-label">GMV</div>
                      <div class="qv-stat-val">¥{{ item.gmv30d || '0.00' }}</div>
                    </div>
                  </n-gi>
                </n-grid>
              </div>

              <div class="qv-section">
                <div class="section-label">业务推进</div>
                <div class="qv-steps">
                  <div class="qv-step" :class="{ active: ['PENDING_AUDIT', 'APPROVED', 'ASSIGNED', 'LINKED'].includes(item.bizStatus) }">同步</div>
                  <div class="qv-step-line"></div>
                  <div class="qv-step" :class="{ active: ['APPROVED', 'ASSIGNED', 'LINKED'].includes(item.bizStatus) }">初筛</div>
                  <div class="qv-step-line"></div>
                  <div class="qv-step" :class="{ active: ['ASSIGNED', 'LINKED'].includes(item.bizStatus) }">分配</div>
                  <div class="qv-step-line"></div>
                  <div class="qv-step" :class="{ active: ['LINKED'].includes(item.bizStatus) }">就绪</div>
                </div>
              </div>

              <div class="qv-section">
                <div class="section-label">快速操作</div>
                <n-space vertical :size="8">
                  <n-button block size="small" type="primary" secondary @click.stop="copyPromotionLink(item)">
                    复制推广链接 (Pick Source)
                  </n-button>
                  <n-button block size="small" @click.stop="openDialog('logs', item)">
                    查看操作日志
                  </n-button>
                </n-space>
              </div>
            </div>
            <div class="qv-footer" @click="openDetail(item)">
              点击卡片查看全貌详情 →
            </div>
          </div>
        </n-popover>
      </div>

      <PageEmpty
        v-else-if="!loading"
        title="暂无商品数据"
        description="可先前往 /dev/test 初始化演示数据，或切换到有商品的活动后再查看。"
        icon="📦"
      />
    </n-spin>

    <div v-if="products.length" class="load-more">
      <n-button v-if="hasMore" :loading="loadingMore" @click="loadMore" secondary>加载更多</n-button>
      <span v-else class="no-more">已加载全部商品</span>
    </div>

    <ProductDetail
      v-model:show="showDetail"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      :refresh-key="detailRefreshKey"
      @action="handleDetailAction"
    />
    <ProductAuditDialog
      v-model:show="dialogs.audit"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      @success="(payload: any) => handleActionSuccess('audit', payload)"
    />
    <ProductAssignDialog
      v-model:show="dialogs.assign"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      @success="(payload: any) => handleActionSuccess('assign', payload)"
    />
    <ProductOperationLogDrawer
      v-model:show="dialogs.logs"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { useRoute } from 'vue-router'
import PageEmpty from '../../components/PageEmpty.vue'
import PageHeader from '../../components/PageHeader.vue'
import { useAuthStore } from '../../stores/auth'
import { hasAccess } from '../../constants/rbac'
import { convertActivityProductLink, getActivityProducts } from '../../api/activityProduct'
import { getColonelActivityPage } from '../../api/activity'

import ProductDetail from './ProductDetail.vue'
import ProductAuditDialog from './components/ProductAuditDialog.vue'
import ProductAssignDialog from './components/ProductAssignDialog.vue'
import ProductOperationLogDrawer from './components/ProductOperationLogDrawer.vue'

type ProductAction = 'audit' | 'assign'

const message = useMessage()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const loadingMore = ref(false)
const products = ref<any[]>([])
const nextCursor = ref('')
const hasMore = ref(false)
const promotionLoadingIds = ref<Set<string>>(new Set())
const selectedProduct = ref<string | null>(null)
const productKeyword = ref('')
const productOptions = ref<{ label: string; value: string }[]>([])
const productOptionsLoading = ref(false)
const status = ref<string | null>(null)
const fallbackActivityId = ref('')
const currentRow = ref<any | null>(null)
const showDetail = ref(false)
const detailRefreshKey = ref(0)

const dialogs = ref({
  audit: false,
  assign: false,
  logs: false
})

const filters = ref({
  category: null as string | null,
  commission: null as string | null,
  hasSample: null as string | null,
  assignee: null as string | null
})

const categoryOptions = [
  { label: '高佣爆款', value: 'high_commission' },
  { label: '适合投放', value: 'traffic' },
  { label: '新品首发', value: 'new' },
  { label: '高客单价', value: 'high_price' }
]

const commissionOptions = [
  { label: '20%以上', value: 'gt20' },
  { label: '10% - 20%', value: '10_20' },
  { label: '10%以下', value: 'lt10' }
]

const yesNoOptions = [
  { label: '是', value: '1' },
  { label: '否', value: '0' }
]

const assigneeOptions = [
  { label: '已分配负责人', value: 'assigned' },
  { label: '未分配负责人', value: 'unassigned' }
]

const statusOptions = [
  { label: '待审核', value: 'PENDING_AUDIT' },
  { label: '审核通过', value: 'APPROVED' },
  { label: '审核拒绝', value: 'REJECTED' },
  { label: '历史已绑定', value: 'BOUND' },
  { label: '已分配招商', value: 'ASSIGNED' },
  { label: '已转链', value: 'LINKED' },
  { label: '已转交达人 CRM', value: 'FOLLOWING' }
]

const forcedStatusMap: Record<ProductAction, string> = {
  audit: 'APPROVED',
  assign: 'ASSIGNED'
}

const canDo = (action: string) => {
  const roles = authStore.roleCodes
  if (roles.includes('admin')) return true
  if (action === 'audit') return hasAccess(roles, ['biz_leader', 'biz_staff'])
  if (action === 'assign') return hasAccess(roles, ['biz_leader', 'channel_leader'])
  if (action === 'promotion') return hasAccess(roles, ['channel_leader', 'channel_staff'])
  return true
}

const resolvedActivityId = computed(() => String(route.params.activityId || fallbackActivityId.value || ''))

const getStatusLabel = (statusCode?: string) => {
  const map: Record<string, string> = {
    PENDING_AUDIT: '待审核',
    APPROVED: '审核通过',
    REJECTED: '审核拒绝',
    BOUND: '历史已绑定',
    ASSIGNED: '已分配招商',
    LINKED: '已转链',
    FOLLOWING: '已转交达人 CRM'
  }
  return map[statusCode || ''] || statusCode || '未知状态'
}

const statusBadgeClass = (statusCode?: string) => {
  if (statusCode === 'PENDING_AUDIT') return 'warning'
  if (['LINKED', 'FOLLOWING'].includes(statusCode || '')) return 'success'
  if (statusCode === 'REJECTED') return 'error'
  return 'default'
}

const getBusinessStatusLabel = (item: any) => {
  if (item.activityExpired) return '活动过期'
  if (Array.isArray(item.alertTags) && item.alertTags.includes('库存不足')) return '库存不足'
  return getStatusLabel(item.bizStatus)
}

const cardTags = (item: any) => {
  const tags: Array<{ text: string; type: 'error' | 'warning' | 'info' | 'success' }> = []
  if (!item.promotion?.link && !item.promotionLink && (item.promotion?.status || item.promotionLinkStatus) === 'FAILED') {
    tags.push({ text: '链接生成失败', type: 'error' })
  }
  if (!item.hasMaterial) tags.push({ text: '缺少话术素材', type: 'warning' })
  if (!item.hasSampleRule) tags.push({ text: '暂无寄样要求', type: 'warning' })
  if (!item.assigneeName) tags.push({ text: '待分配负责人', type: 'error' })
  return tags
}

const handleImageError = (event: Event) => {
  ;(event.target as HTMLImageElement).style.display = 'none'
}

const ensureActivityId = async () => {
  if (route.params.activityId) {
    fallbackActivityId.value = ''
    return String(route.params.activityId)
  }
  if (fallbackActivityId.value) return fallbackActivityId.value

  const res: any = await getColonelActivityPage({
    page: 1,
    pageSize: 1,
    status: 0,
    searchType: 0,
    sortType: 1
  })
  const activity = res?.data?.activityList?.[0]
  if (!activity?.activityId) throw new Error('暂无可用活动')
  fallbackActivityId.value = String(activity.activityId)
  return fallbackActivityId.value
}

const normalizeItem = (item: any) => ({
  ...item,
  bizStatus: item?.bizStatus || 'PENDING_AUDIT',
  bizStatusLabel: item?.bizStatusLabel || getStatusLabel(item?.bizStatus),
  productId: String(item?.productId ?? ''),
  sales30d: item?.sales30d ?? item?.sales ?? 0,
  gmv30d: item?.gmv30d ?? '0.00',
  estimatedServiceFee: item?.estimatedServiceFee ?? item?.estimatedServiceFeeAmount ?? '0.00',
  hasMaterial: item?.hasMaterial ?? false,
  hasSampleRule: item?.hasSampleRule ?? true,
  activityExpired: Boolean(item?.activityExpired),
  systemTags: Array.isArray(item?.systemTags) ? item.systemTags : [],
  alertTags: Array.isArray(item?.alertTags) ? item.alertTags : [],
  promotion: item?.promotion || {
    status: item?.promotionLinkStatus || 'PENDING',
    statusLabel: item?.promotionLinkStatusLabel || '未生成',
    link: item?.promotionLink || null,
    generatedAt: item?.promotionLinkGeneratedAt || null,
    expireAt: item?.promotionLinkExpireAt || null,
    failReason: item?.promotionLinkFailReason || null
  }
})

const matchCategory = (item: any, category: string | null) => {
  if (!category) return true
  const tags = Array.isArray(item.systemTags) ? item.systemTags : []
  if (category === 'high_commission') return tags.includes('高佣')
  if (category === 'traffic') return tags.includes('抖音商品池')
  if (category === 'new') return (item.sales30d ?? 0) < 100
  if (category === 'high_price') return parsePrice(item.priceText) >= 300
  return true
}

const matchCommission = (item: any, commission: string | null) => {
  if (!commission) return true
  const rate = parsePercent(item.activityCosRatioText)
  if (commission === 'gt20') return rate >= 20
  if (commission === '10_20') return rate >= 10 && rate < 20
  if (commission === 'lt10') return rate < 10
  return true
}

const matchSample = (item: any, hasSample: string | null) => {
  if (!hasSample) return true
  return hasSample === '1' ? Boolean(item.hasSampleRule) : !item.hasSampleRule
}

const matchAssignee = (item: any, assignee: string | null) => {
  if (!assignee) return true
  return assignee === 'assigned' ? Boolean(item.assigneeName) : !item.assigneeName
}

const applyFilters = (items: any[]) =>
  items.filter((item) => {
    if (status.value && item.bizStatus !== status.value) return false
    if (!matchCategory(item, filters.value.category)) return false
    if (!matchCommission(item, filters.value.commission)) return false
    if (!matchSample(item, filters.value.hasSample)) return false
    if (!matchAssignee(item, filters.value.assignee)) return false
    return true
  })

const fetchProducts = async (reset: boolean) => {
  if (reset) loading.value = true
  else loadingMore.value = true

  try {
    const activityId = await ensureActivityId()
    const res: any = await getActivityProducts(activityId, {
      count: 20,
      cursor: reset ? undefined : nextCursor.value,
      productInfo: selectedProduct.value || productKeyword.value.trim() || undefined,
      retrieveMode: 1
    })

    const data = res?.data || {}
    const items = applyFilters((Array.isArray(data.items) ? data.items : []).map(normalizeItem))
    products.value = reset ? items : products.value.concat(items)
    nextCursor.value = String(data.nextCursor || '')
    hasMore.value = Boolean(data.hasMore || data.nextCursor)
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '商品查询失败')
    if (reset) {
      products.value = []
      nextCursor.value = ''
      hasMore.value = false
    }
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const buildProductOption = (item: any) => {
  const title = String(item?.title || item?.productName || '未命名商品').trim()
  const shopName = String(item?.shopName || '').trim()
  const productId = String(item?.productId || '').trim()
  return {
    label: shopName ? `${title} / ${shopName}` : title,
    value: productId
  }
}

const loadProductOptions = async (keyword: string) => {
  productOptionsLoading.value = true
  try {
    const activityId = await ensureActivityId()
    const res: any = await getActivityProducts(activityId, {
      count: 20,
      productInfo: keyword || undefined,
      retrieveMode: 1
    })
    const items = Array.isArray(res?.data?.items) ? res.data.items : []
    productOptions.value = items.map(buildProductOption).filter((item: { label: string; value: string }) => Boolean(item.value))
  } catch {
    productOptions.value = []
  } finally {
    productOptionsLoading.value = false
  }
}

const handleProductSearch = async (keyword: string) => {
  productKeyword.value = String(keyword || '').trim()
  await loadProductOptions(productKeyword.value)
}

const resetFilters = () => {
  selectedProduct.value = null
  status.value = null
  filters.value = { category: null, commission: null, hasSample: null, assignee: null }
  refreshProducts()
}

const refreshProducts = () => {
  fetchProducts(true)
}

const loadMore = () => {
  if (hasMore.value) fetchProducts(false)
}

const openDetail = (row: any) => {
  currentRow.value = { ...row }
  showDetail.value = true
}

const openDialog = (type: keyof typeof dialogs.value, row: any) => {
  currentRow.value = { ...row }
  dialogs.value[type] = true
}

const mergeProductRow = (payload?: any, fallbackAction?: ProductAction) => {
  const row = currentRow.value
  const productId = String(payload?.productId || row?.productId || '')
  if (!productId) return

  const merged = normalizeItem({
    ...row,
    ...payload,
    productId,
    bizStatus: payload?.bizStatus || forcedStatusMap[fallbackAction || 'audit'] || row?.bizStatus,
    bizStatusLabel:
      payload?.bizStatusLabel ||
      getStatusLabel(payload?.bizStatus || forcedStatusMap[fallbackAction || 'audit'] || row?.bizStatus)
  })

  products.value = products.value.map((item: any) => (String(item.productId) === productId ? { ...item, ...merged } : item))
  currentRow.value = { ...merged }
}

const handleActionSuccess = (action: ProductAction, payload?: any) => {
  mergeProductRow(payload, action)
  detailRefreshKey.value += 1
  refreshProducts()
}

const handleDetailAction = (payload: { action: string; row: any }) => {
  openDialog(payload.action as keyof typeof dialogs.value, payload.row)
}

const copyPromotionLink = async (item: any) => {
  const productId = String(item?.productId || '')
  const activityId = resolvedActivityId.value
  if (!productId || !activityId) {
    message.warning('商品信息不完整，暂时无法生成推广链接')
    return
  }
  promotionLoadingIds.value = new Set(promotionLoadingIds.value).add(productId)
  try {
    const res: any = await convertActivityProductLink(activityId, productId, { scene: 'PRODUCT_LIBRARY' })
    const data = res?.data || {}
    const link = data.promoteLink || data.promotionUrl || data.shortLink
    if (!link) {
      message.warning('推广链接生成成功，但没有返回可复制的链接地址')
      return
    }
    await navigator.clipboard.writeText(link)
    message.success('推广链接已复制，pick_source 已生成，后续订单将归因到当前渠道')
    const merged = normalizeItem({
      ...item,
      bizStatus: 'LINKED',
      bizStatusLabel: getStatusLabel('LINKED'),
      promotion: {
        status: 'READY',
        statusLabel: '已生成',
        link,
        generatedAt: new Date().toISOString(),
        expireAt: null,
        failReason: null
      },
      promotionLink: link,
      promotionLinkStatus: 'READY',
      promotionLinkStatusLabel: '已生成',
      promotionLinkFailReason: null
    })
    products.value = products.value.map((row: any) => (String(row.productId) === productId ? { ...row, ...merged } : row))
    if (String(currentRow.value?.productId || '') === productId) {
      currentRow.value = { ...currentRow.value, ...merged }
    }
    detailRefreshKey.value += 1
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '推广链接生成失败，请稍后重试')
  } finally {
    const next = new Set(promotionLoadingIds.value)
    next.delete(productId)
    promotionLoadingIds.value = next
  }
}

const parsePercent = (value?: string) => {
  if (!value) return 0
  return Number(String(value).replace('%', '').trim()) || 0
}

const parsePrice = (value?: string) => {
  if (!value) return 0
  return Number(String(value).replace(/[^\d.]/g, '')) || 0
}

onMounted(() => {
  loadProductOptions('')
  refreshProducts()
})

watch(
  () => route.params.activityId,
  () => {
    nextCursor.value = ''
    refreshProducts()
  }
)
</script>

<style scoped>
.product-page {
  max-width: 100%;
  padding: 24px;
}

.product-toolbar {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  margin-bottom: var(--spacing-md);
  box-shadow: var(--shadow-sm);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--spacing-md);
}

.product-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: box-shadow 0.3s ease, transform 0.3s ease;
  position: relative;
}

.product-card:hover {
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.12);
  transform: translateY(-6px);
}

.card-image {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background: linear-gradient(135deg, #f8f9fb 0%, #eef0f4 100%);
  overflow: hidden;
}

.card-image img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.product-card:hover .card-image img {
  transform: scale(1.05);
}

.card-image-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.card-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 600;
  color: #fff;
  z-index: 2;
}

.card-body {
  padding: 16px;
}

.card-header {
  margin-bottom: 10px;
}

.card-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.card-tags {
  margin-bottom: 10px;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  min-height: 28px;
}

.card-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 10px 0;
  border-top: 1px solid var(--border-color-light);
  border-bottom: 1px solid var(--border-color-light);
  margin-bottom: 10px;
}

.metric-item {
  text-align: center;
}

.metric-label {
  display: block;
  font-size: var(--text-xs);
  color: var(--text-muted);
  margin-bottom: 2px;
}

.metric-val {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}

.metric-val.primary {
  color: var(--color-primary);
}

.metric-val.accent {
  color: #f2a900;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-stats {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.stats-active {
  color: var(--color-primary);
}

.card-actions {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.quick-view-panel {
  background: #fff;
  width: 100%;
}

.qv-header {
  padding: 12px 16px;
  background: #f8fafc;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e2e8f0;
}

.qv-title {
  font-weight: 600;
  color: #1e293b;
}

.qv-content {
  padding: 16px;
}

.qv-section {
  margin-bottom: 16px;
}

.qv-section:last-child {
  margin-bottom: 0;
}

.section-label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.qv-stat {
  padding: 8px;
  background: #f1f5f9;
  border-radius: 6px;
  text-align: center;
}

.qv-stat-label {
  font-size: 11px;
  color: #94a3b8;
}

.qv-stat-val {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.qv-steps {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.qv-step {
  font-size: 11px;
  color: #cbd5e1;
  padding: 4px 8px;
  border-radius: 4px;
  background: #f8fafc;
}

.qv-step.active {
  color: #fff;
  background: #3b82f6;
}

.qv-step-line {
  flex: 1;
  height: 1px;
  background: #e2e8f0;
  margin: 0 4px;
}

.qv-footer {
  padding: 10px;
  text-align: center;
  font-size: 12px;
  color: #3b82f6;
  background: #eff6ff;
  cursor: pointer;
  border-top: 1px solid #dbeafe;
}

.qv-footer:hover {
  background: #dbeafe;
}

.load-more {
  text-align: center;
  padding: 24px 0;
}

.no-more {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

/* Badge colors */
.badge-active { background: #10b981; }
.badge-review { background: #f59e0b; }
.badge-ended { background: #ef4444; }
.badge-default { background: #64748b; }
</style>
