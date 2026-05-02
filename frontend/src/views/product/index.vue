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

    <n-alert v-if="resolvedActivityId" type="info" style="margin-bottom: var(--spacing-md);">
      当前正在查看活动下的商品，可直接做选品、转链和寄样前置准备。
      <template #action>
        <n-button size="small" @click="$router.push('/product/activity')">返回活动列表</n-button>
      </template>
    </n-alert>

    <ProductFilters
      :filters="filters"
      :selected-product="selectedProduct"
      :status="status"
      :product-options="productOptions"
      :product-options-loading="productOptionsLoading"
      :loading="loading"
      @update:filters="filters = $event"
      @update:selected-product="selectedProduct = $event"
      @update:status="status = $event"
      @search="handleProductSearch"
      @search-click="refreshProducts"
      @reset="resetFilters"
    />

    <n-spin :show="loading">
      <div v-if="products.length" class="product-grid">
        <ProductCard
          v-for="item in products"
          :key="item.productId"
          :product="item"
          :expanded="expandedProductId === item.productId"
          :can-audit="canDo('audit')"
          :can-assign="canDo('assign')"
          @toggle="expandedProductId = $event"
          @detail="openDetail"
          @audit="openDialog('audit', $event)"
          @assign="openDialog('assign', $event)"
          @copy-link="copyPromotionLink"
          @show-logs="openDialog('logs', $event)"
        />
      </div>

      <PageEmpty
        v-else-if="!loading"
        title="暂无商品数据"
        description="可先前往 /dev/test 初始化演示数据，或切换到有商品的活动后再查看。"
      >
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="36" height="36">
            <path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/>
          </svg>
        </template>
      </PageEmpty>
    </n-spin>

    <div v-if="products.length" class="load-more">
      <n-button v-if="hasMore" :loading="loadingMore" secondary @click="loadMore">加载更多</n-button>
      <span v-else class="no-more">已加载全部商品</span>
    </div>

    <ProductDetail
      v-model:show="showDetail"
      :product-id="currentRow?.productId ?? null"
      :activity-id="resolvedActivityId || null"
      :refresh-key="detailRefreshKey"
      @action="handleDetailAction"
    />
    <ProductAuditDialog
      v-model:show="dialogs.audit"
      :product-id="currentRow?.productId ?? null"
      :activity-id="resolvedActivityId || null"
      @success="(payload: any) => handleActionSuccess('audit', payload)"
    />
    <ProductAssignDialog
      v-model:show="dialogs.assign"
      :product-id="currentRow?.productId ?? null"
      :activity-id="resolvedActivityId || null"
      @success="(payload: any) => handleActionSuccess('assign', payload)"
    />
    <ProductOperationLogDrawer
      v-model:show="dialogs.logs"
      :product-id="currentRow?.productId ?? null"
      :activity-id="resolvedActivityId || null"
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

import ProductFilters from './components/ProductFilters.vue'
import ProductCard from './components/ProductCard.vue'
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
const expandedProductId = ref<string | null>(null)
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
  assignee: null as string | null,
  decision: null as string | null
})

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

const parsePercent = (value?: string) => {
  if (!value) return 0
  return Number(String(value).replace('%', '').trim()) || 0
}

const parsePrice = (value?: string) => {
  if (!value) return 0
  return Number(String(value).replace(/[^\d.]/g, '')) || 0
}

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

const matchDecision = (item: any, decision: string | null) => {
  if (!decision) return true
  if (decision === 'NONE') return !item.latestDecisionLevel
  return item.latestDecisionLevel === decision
}

const applyFilters = (items: any[]) =>
  items.filter((item) => {
    if (status.value && item.bizStatus !== status.value) return false
    if (!matchCategory(item, filters.value.category)) return false
    if (!matchCommission(item, filters.value.commission)) return false
    if (!matchSample(item, filters.value.hasSample)) return false
    if (!matchAssignee(item, filters.value.assignee)) return false
    if (!matchDecision(item, filters.value.decision)) return false
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
  } catch (error: any) {
    message.warning(error?.response?.data?.msg || '商品搜索暂不可用')
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
  filters.value = { category: null, commission: null, hasSample: null, assignee: null, decision: null }
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
  const existingLink = item?.promotion?.link || item?.promotionLink
  if (existingLink) {
    try {
      await navigator.clipboard.writeText(existingLink)
      message.success('推广链接已复制')
    } catch {
      message.warning('推广链接已生成，但浏览器未允许写入剪贴板，请手动复制')
    }
    return
  }
  promotionLoadingIds.value = new Set(promotionLoadingIds.value).add(productId)
  try {
    const res: any = await convertActivityProductLink(activityId, productId, { scene: 'PRODUCT_LIBRARY' })
    const data = res?.data || {}
    const link = data.promoteLink || data.promotionUrl || data.shortLink
    if (!link) {
      message.warning('推广链接生成成功，但没有返回可复制的链接地址')
      detailRefreshKey.value += 1
      return
    }
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
    try {
      await navigator.clipboard.writeText(link)
      message.success('推广链接已复制，pick_source 已生成，后续订单将归因到当前渠道')
    } catch {
      message.warning('推广链接已生成，但浏览器未允许写入剪贴板，请手动复制')
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

onMounted(async () => {
  try {
    await ensureActivityId()
    await Promise.all([loadProductOptions(''), refreshProducts()])
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '页面初始化失败')
  }
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
  padding: var(--spacing-xl);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--spacing-md);
  align-items: start;
}

.load-more {
  text-align: center;
  padding: var(--spacing-xl) 0;
}

.no-more {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.badge-active { background: var(--color-success); }
.badge-review { background: var(--color-warning); }
.badge-ended { background: var(--color-danger); }
.badge-default { background: var(--text-tertiary); }
</style>
