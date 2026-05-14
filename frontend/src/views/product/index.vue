<template>
  <div class="product-page" :data-testid="isActivityProductMode ? 'activity-product-page' : 'product-manage-page'">
    <PageHeader :title="pageTitle" :description="pageDescription">
      <template #actions>
        <n-button :loading="loading" type="primary" data-testid="product-manage-refresh" @click="refreshProducts">
          刷新商品
        </n-button>
      </template>
    </PageHeader>

    <n-alert v-if="hasExplicitActivityRoute" type="info" class="page-alert">
      当前正在查看活动下的商品列表，可直接做审核、转链和寄样前置准备。
      <template #action>
        <n-button size="small" @click="router.push('/product/manage')">返回活动列表</n-button>
      </template>
    </n-alert>

    <ProductFilters
      :filters="filters"
      :selected-product="selectedProduct"
      :status="status"
      :product-options="productOptions"
      :product-options-loading="productOptionsLoading"
      :loading="loading"
      :show-assignee-filter="!authStore.roleCodes.includes('biz_staff') || authStore.roleCodes.includes('biz_leader') || authStore.isAdmin"
      @update:filters="filters = $event"
      @update:selected-product="selectedProduct = $event"
      @update:status="status = $event"
      @search="handleProductSearch"
      @search-click="refreshProducts"
      @reset="resetFilters"
    />

    <n-card :bordered="false" class="main-card">
      <n-data-table
        v-if="products.length"
        data-testid="product-table"
        :columns="columns"
        :data="products"
        :loading="loading"
        :row-key="(row: any) => row.productId"
        :single-line="false"
      />

      <PageEmpty v-else-if="!loading" title="暂无商品数据" :description="emptyDescription" class="table-empty">
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="36" height="36">
            <path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
          </svg>
        </template>
      </PageEmpty>

      <div v-if="products.length" class="load-more">
        <n-button v-if="hasMore" :loading="loadingMore" secondary @click="loadMore">加载更多</n-button>
        <span v-else class="no-more">已加载全部商品</span>
      </div>
    </n-card>

    <ProductDetail
      v-model:show="showDetail"
      :product-id="currentRow?.productId ?? null"
      :activity-id="detailActivityId"
      :pick-mode="isPickLibraryMode"
      :library-mode="isSharedLibraryMode"
      :can-put-into-library="canDo('libraryEntry')"
      :refresh-key="detailRefreshKey"
      @action="handleDetailAction"
    />
    <ProductAuditDialog
      v-model:show="dialogs.audit"
      :product-id="currentRow?.productId ?? null"
      :activity-id="detailActivityId"
      @success="(payload: any) => handleActionSuccess('audit', payload)"
    />
    <ProductAssignDialog
      v-model:show="dialogs.assign"
      :product-id="currentRow?.productId ?? null"
      :activity-id="detailActivityId"
      @success="(payload: any) => handleActionSuccess('assign', payload)"
    />
    <ProductOperationLogDrawer
      v-model:show="dialogs.logs"
      :product-id="currentRow?.productId ?? null"
      :activity-id="detailActivityId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import PageEmpty from '../../components/PageEmpty.vue'
import PageHeader from '../../components/PageHeader.vue'
import { useAuthStore } from '../../stores/auth'
import { hasAccess } from '../../constants/rbac'
import { convertActivityProductLink, getActivityProducts, putActivityProductIntoLibrary } from '../../api/activityProduct'
import { getColonelActivityPage } from '../../api/activity'
import { getProducts, getProductPickPage } from '../../api/product'
import ProductFilters from './components/ProductFilters.vue'
import ProductDetail from './ProductDetail.vue'
import ProductAuditDialog from './components/ProductAuditDialog.vue'
import ProductAssignDialog from './components/ProductAssignDialog.vue'
import ProductOperationLogDrawer from './components/ProductOperationLogDrawer.vue'

type ProductAction = 'audit' | 'assign'

const message = useMessage()
const route = useRoute()
const router = useRouter()
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
  if (action === 'audit') return hasAccess(roles, ['biz_staff'])
  if (action === 'assign') return hasAccess(roles, ['biz_leader'])
  if (action === 'promotion') return hasAccess(roles, ['channel_leader', 'channel_staff'])
  if (action === 'libraryEntry') return false
  if (action === 'decision') return false
  return true
}

const resolvedActivityId = computed(() => String(route.params.activityId || fallbackActivityId.value || ''))
const hasExplicitActivityRoute = computed(() => Boolean(route.params.activityId))
const isSharedLibraryMode = computed(() => route.path === '/product')
const isActivityProductMode = computed(() => hasExplicitActivityRoute.value)
const isPickLibraryMode = computed(() => route.path === '/product/manage/products' || (!isSharedLibraryMode.value && !isActivityProductMode.value))

const pageTitle = computed(() => {
  if (isActivityProductMode.value) return '商品列表'
  if (route.path === '/product/manage/products') return '我负责的商品'
  return '商品库'
})

const pageDescription = computed(() => {
  if (isActivityProductMode.value) return '查看活动下的商品列表，支持审核、转链和寄样前置准备。'
  if (route.path === '/product/manage/products') return '管理我负责的商品，补全推广资料并完成审核。'
  if (isSharedLibraryMode.value) return '沉淀完成的共享商品库，对全员可见，可直接复用历史商品结果。'
  return '支持候选商品浏览、审核、转链和入库沉淀，作为商品协同的统一工作台。'
})

const emptyDescription = computed(() => {
  if (isSharedLibraryMode.value) return '当前还没有加入商品库的商品，可先在商品管理里进入活动并将商品沉淀到商品库。'
  return '当前暂无我负责的待审商品，如有疑问请联系组长分配。'
})

const detailActivityId = computed(() => {
  const rowActivityId = currentRow.value?.sourceActivityId || currentRow.value?.activityId
  return String(rowActivityId || resolvedActivityId.value || '') || null
})

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

const normalizeText = (value?: string | number | null) => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  return text === 'null' || text === 'undefined' ? '' : text
}

const formatPercent = (value?: string | number | null) => {
  const text = normalizeText(value)
  if (!text) return '-'
  return text.includes('%') ? text : `${text}%`
}

const formatMoney = (value?: string | number | null, prefix = '¥') => {
  const text = normalizeText(value)
  if (!text) return '-'
  return text.startsWith(prefix) ? text : `${prefix}${text}`
}

const normalizeImageUrl = (value?: string | null) => {
  const text = normalizeText(value)
  if (!text) return ''
  if (text.startsWith('//')) return `https:${text}`
  return text
}

const resolveProductImage = (item: any) => {
  const candidates = [
    item?.cover,
    item?.imageUrl,
    item?.imgUrl,
    item?.coverUrl,
    item?.picUrl,
    item?.mainPic,
    Array.isArray(item?.pics) ? item.pics[0] : null
  ]
  for (const candidate of candidates) {
    const normalized = normalizeImageUrl(candidate)
    if (normalized) return normalized
  }
  return ''
}

const getAuditSupplement = (item: any) => item?.auditSupplement || {}

const getPromotionMaterialPack = (item: any) => item?.promotionMaterialPack || {}

const getSampleThresholdText = (item: any) => {
  const supplement = getAuditSupplement(item)
  const parts = []
  if (supplement?.sampleThresholdSales !== undefined) parts.push(`近30天销量额>=${supplement.sampleThresholdSales}`)
  if (supplement?.sampleThresholdLevel !== undefined) parts.push(`达人带货等级>=LV${supplement.sampleThresholdLevel}`)
  if (supplement?.sampleThresholdRemark) parts.push(normalizeText(supplement.sampleThresholdRemark))
  return parts.join('\n') || '未设置寄样门槛'
}

const getCooperationLines = (item: any) => {
  const supplement = getAuditSupplement(item)
  const materialPack = getPromotionMaterialPack(item)
  const supportAds =
    supplement?.supportsAds === undefined || supplement?.supportsAds === null
      ? '未设置'
      : supplement.supportsAds
        ? '支持'
        : '不支持'
  return [
    getSampleThresholdText(item),
    supplement?.participationRequirements || materialPack?.outreachScript || '未补充合作要求',
    `投放支持：${supportAds}`,
    `人工设置标签：${Array.isArray(item.systemTags) && item.systemTags.length ? '开' : '关'}`,
    `特殊提报比例：${normalizeText(supplement?.specialCommissionRatio) ? formatPercent(supplement.specialCommissionRatio) : '关'}`
  ]
}

const getActivityLines = (item: any) => [
  `活动名称：${normalizeText(item.activityName) || (isActivityProductMode.value ? '当前活动商品' : '未绑定活动')}`,
  `活动ID：${normalizeText(item.sourceActivityId || item.activityId || resolvedActivityId.value) || '-'}`,
  `推广时间：${normalizeText(item.promotionStartTime || item.startTime) || '-'}${normalizeText(item.promotionEndTime || item.endTime) ? ` ~ ${normalizeText(item.promotionEndTime || item.endTime)}` : ''}`
]

const getCommissionLines = (item: any) => {
  const common = normalizeText(item.commonCommissionRateText || item.normalCommissionText || item.activityCosRatioText)
  const daily = normalizeText(item.dailyCommissionRateText || item.activityCosRatioText)
  const campaign = normalizeText(item.campaignCommissionRateText || item.deliveryCommissionRateText || item.putCommissionRateText)
  return [
    `普通：${common || '-'}`,
    `日常：${daily || '-'}`,
    `投放期：${campaign || '-'}`
  ]
}

const getServiceFeeLines = (item: any) => {
  const normal = normalizeText(item.serviceFeeRateText || item.serviceFeeRate)
  const daily = normalizeText(item.dailyServiceFeeRateText || item.dayServiceFeeRate || item.estimatedServiceFee)
  const campaign = normalizeText(item.campaignServiceFeeRateText || item.putServiceFeeRate)
  return [
    normal ? `普通：${formatPercent(normal)}` : '',
    `日常：${daily ? (String(daily).includes('%') ? formatPercent(daily) : formatMoney(daily)) : '-'}`,
    `投放期：${campaign ? formatPercent(campaign) : '-'}`
  ].filter(Boolean)
}

const hasDoubleCommission = (item: any) =>
  Boolean(item?.doubleCommission || item?.dualCommission || item?.serviceFeeMode === 'DOUBLE' || item?.promotionMode === 'DOUBLE')

const ensureActivityId = async () => {
  if (isSharedLibraryMode.value) return ''
  if (isPickLibraryMode.value) return ''
  if (route.params.activityId) {
    fallbackActivityId.value = ''
    return String(route.params.activityId)
  }
  if (fallbackActivityId.value) return fallbackActivityId.value

  try {
    const res: any = await getColonelActivityPage({
      page: 1,
      pageSize: 1,
      status: 0,
      searchType: 0,
      sortType: 1
    })
    const activity = res?.data?.activityList?.[0]
    if (activity?.activityId) {
      fallbackActivityId.value = String(activity.activityId)
      return fallbackActivityId.value
    }
  } catch {
    // Token 未配置时保持空态或走本地候选数据兜底
  }
  return ''
}

const normalizeItem = (item: any) => ({
  ...item,
  cover: resolveProductImage(item),
  bizStatus: item?.bizStatus || 'PENDING_AUDIT',
  bizStatusLabel: item?.bizStatusLabel || getStatusLabel(item?.bizStatus),
  productId: String(item?.productId ?? ''),
  activityId: item?.activityId ? String(item.activityId) : '',
  sourceActivityId: item?.sourceActivityId ? String(item.sourceActivityId) : item?.activityId ? String(item.activityId) : '',
  sales30d: item?.sales30d ?? item?.sales ?? 0,
  gmv30d: item?.gmv30d ?? '0.00',
  estimatedServiceFee: item?.estimatedServiceFee ?? item?.estimatedServiceFeeAmount ?? '0.00',
  hasMaterial: item?.hasMaterial ?? false,
  hasSampleRule: item?.hasSampleRule ?? true,
  activityExpired: Boolean(item?.activityExpired),
  selectedToLibrary: Boolean(item?.selectedToLibrary || item?.libraryVisible),
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

const parsePercent = (value?: string) => Number(String(value || '').replace('%', '').trim()) || 0
const parsePrice = (value?: string) => Number(String(value || '').replace(/[^\d.]/g, '')) || 0

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
    if (isSharedLibraryMode.value) {
      const page = reset ? 1 : Math.floor(products.value.length / 20) + 1
      const res: any = await getProducts({
        page,
        size: 20,
        keyword: selectedProduct.value || productKeyword.value.trim() || undefined
      })
      const data = res?.data || {}
      const records = Array.isArray(data.records) ? data.records : []
      const items = applyFilters(records.map((p: any) => normalizeItem({
        ...p,
        title: p.title || p.name || '未命名商品',
        productId: String(p.productId || '')
      })))
      products.value = reset ? items : products.value.concat(items)
      const currentPage = Number(data.page || page || 1)
      const pageSize = Number(data.size || 20)
      const total = Number(data.total || 0)
      hasMore.value = currentPage * pageSize < total
      nextCursor.value = ''
      return
    }

    const activityId = await ensureActivityId()
    if (!isPickLibraryMode.value && activityId) {
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
      return
    }

    const page = reset ? 1 : Math.floor(products.value.length / 20) + 1
    const res: any = await getProductPickPage({ page, size: 20 })
    const data = res?.data || {}
    const records = Array.isArray(data.records) ? data.records : []
    const items = applyFilters(records.map((p: any) => normalizeItem({
      ...p,
      title: p.title || p.name || '未命名商品',
      productId: String(p.productId || ''),
      bizStatus: p.bizStatus || 'PENDING_AUDIT',
      hasSampleRule: p.hasSampleRule ?? true,
      hasMaterial: p.hasMaterial ?? false,
      systemTags: Array.isArray(p.systemTags) ? p.systemTags : [],
      alertTags: Array.isArray(p.alertTags) ? p.alertTags : []
    })))
    products.value = reset ? items : products.value.concat(items)
    const currentPage = Number(data.page || page || 1)
    const pageSize = Number(data.size || 20)
    const total = Number(data.total || 0)
    hasMore.value = currentPage * pageSize < total
    nextCursor.value = ''
  } catch (error: any) {
    if (hasExplicitActivityRoute.value) {
      products.value = []
      nextCursor.value = ''
      hasMore.value = false
    } else {
      message.error(error?.response?.data?.msg || error?.message || '商品查询失败')
      if (reset) {
        products.value = []
        nextCursor.value = ''
        hasMore.value = false
      }
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
    if (isSharedLibraryMode.value) {
      const res: any = await getProducts({ size: 20, keyword: keyword || undefined })
      const records = Array.isArray(res?.data?.records) ? res.data.records : []
      productOptions.value = records
        .map((p: any) => buildProductOption({
          ...p,
          title: p.title || p.name || '未命名商品',
          productId: String(p.productId || '')
        }))
        .filter((item: { label: string; value: string }) => Boolean(item.value))
      return
    }

    const activityId = await ensureActivityId()
    if (!isPickLibraryMode.value && activityId) {
      const res: any = await getActivityProducts(activityId, {
        count: 20,
        productInfo: keyword || undefined,
        retrieveMode: 1
      })
      const items = Array.isArray(res?.data?.items) ? res.data.items : []
      productOptions.value = items.map(buildProductOption).filter((item: { label: string; value: string }) => Boolean(item.value))
      return
    }

    const res: any = await getProductPickPage({ size: 20 })
    const records = Array.isArray(res?.data?.records) ? res.data.records : []
    productOptions.value = records
      .map((p: any) => ({ label: String(p.name || p.title || ''), value: String(p.productId || '') }))
      .filter((item: { label: string; value: string }) => Boolean(item.value))
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

const refreshProducts = async () => {
  await fetchProducts(true)
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
  if (payload.action === 'putIntoLibrary') {
    handlePutIntoLibrary(payload.row)
    return
  }
  openDialog(payload.action as keyof typeof dialogs.value, payload.row)
}

const resolveRowActivityId = (item: any) => String(item?.sourceActivityId || item?.activityId || resolvedActivityId.value || '')

const handlePutIntoLibrary = async (item: any) => {
  const productId = String(item?.productId || '')
  const activityId = resolveRowActivityId(item)
  if (!productId || !activityId) {
    message.warning('缺少来源活动信息，暂时无法加入商品库')
    return
  }
  try {
    const res: any = await putActivityProductIntoLibrary(activityId, productId)
    const data = res?.data || {}
    const merged = normalizeItem({
      ...item,
      ...data,
      selectedToLibrary: true,
      libraryVisible: true,
      sourceActivityId: activityId
    })
    products.value = products.value.map((row: any) => (String(row.productId) === productId ? { ...row, ...merged } : row))
    if (String(currentRow.value?.productId || '') === productId) {
      currentRow.value = { ...currentRow.value, ...merged }
    }
    detailRefreshKey.value += 1
    message.success('已加入商品库，当前商品对全员可见')
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '加入商品库失败，请稍后重试')
  }
}

const copyPromotionLink = async (item: any) => {
  const productId = String(item?.productId || '')
  const activityId = resolveRowActivityId(item)
  if (!productId || !activityId) {
    message.warning('商品信息不完整，暂时无法生成推广链接')
    return
  }
  if (!item?.selectedToLibrary) {
    message.warning('请先完成审核并进入商品库后，再生成推广链接')
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

const renderTextAction = (label: string, onClick?: (event: MouseEvent) => void, muted = false) =>
  h(
    NButton,
    {
      text: true,
      size: 'small',
      type: muted ? 'default' : 'primary',
      class: ['table-link-action-button', muted ? 'is-muted' : ''],
      focusable: false,
      onClick: (event: MouseEvent) => {
        event.stopPropagation()
        onClick?.(event)
      }
    },
    {
      default: () => label
    }
  )

const renderProductInfo = (row: any) =>
  h('div', { class: 'table-product' }, [
    h(
      'div',
      { class: 'table-product-cover' },
      row.cover
        ? h('img', {
            class: 'img',
            src: row.cover,
            alt: row.title || row.name || '商品图片',
            style: {
              width: '68px',
              height: '68px',
              objectFit: 'contain',
              display: 'block',
              background: '#f5f6fa'
            }
          })
        : h('div', { class: 'table-product-fallback' }, '暂无图片')
    ),
    h('div', { class: 'table-product-body' }, [
      h('div', { class: 'table-product-title' }, row.title || row.name || '-'),
      h('div', { class: 'table-product-meta' }, `商品ID：${row.productId || '-'}`),
      h('div', { class: 'table-product-meta' }, `店铺：${row.shopName || '未识别店铺'}`),
      h('div', { class: 'table-product-meta' }, `售价：${row.priceText || '-'}    库存：${normalizeText(row.productStock) || normalizeText(row.stockText) || '-'}`),
      h('div', { class: 'table-product-meta' }, `来源类型：${normalizeText(row.sourceTypeLabel || row.sourceTypeName || row.sourceType) || '团长活动'}`)
    ])
  ])

const renderTagList = (row: any) => {
  const firstLabel = Array.isArray(row.systemTags) && row.systemTags.length
    ? row.systemTags[0]
    : Array.isArray(row.alertTags) && row.alertTags.length
      ? row.alertTags[0]
      : '暂无标签'
  const secondaryLabel = normalizeText(row.latestDecisionLabel)
  const canShowLibraryEntry =
    !row.selectedToLibrary &&
    ['APPROVED', 'BOUND'].includes(String(row.bizStatus || '')) &&
    canDo('audit')
  return h(
    'div',
    { class: 'table-tag-block' },
    [
      h('div', { class: 'table-stack-line' }, firstLabel),
      secondaryLabel ? h('div', { class: 'table-stack-line muted' }, secondaryLabel) : null,
      row.selectedToLibrary
        ? renderTextAction('已入商品库', undefined, true)
        : canShowLibraryEntry
          ? renderTextAction('加入商品库', () => handlePutIntoLibrary(row))
          : null
    ]
  )
}

const renderLineList = (lines: string[], className = 'table-stack') =>
  h(
    'div',
    { class: className },
    lines.map((line) => h('div', { class: 'table-stack-line' }, line))
  )

const renderCommission = (row: any) => renderLineList(getCommissionLines(row), 'table-stack compact')

const renderServiceFee = (row: any) =>
  h('div', { class: 'table-stack compact' }, [
    hasDoubleCommission(row) ? h('span', { class: 'table-inline-badge' }, '双佣金') : null,
    ...getServiceFeeLines(row).map((line) => h('div', { class: 'table-stack-line' }, line))
  ])

const renderProgress = (row: any) =>
  h('div', { class: 'table-stack' }, [
    h('div', { class: 'table-stack-line strong' }, row.assigneeName || '未分配负责人'),
    h('div', { class: 'table-stack-line muted' }, row.bizStatusLabel || getStatusLabel(row.bizStatus))
  ])

const renderActions = (row: any) => {
  const buttons: any[] = []
  if (row.bizStatus === 'PENDING_AUDIT' && canDo('audit')) {
    buttons.push(renderTextAction('审核商品', () => openDialog('audit', row)))
  }
  if (row.selectedToLibrary && ['APPROVED', 'BOUND'].includes(row.bizStatus) && canDo('assign')) {
    buttons.push(renderTextAction('分配招商', () => openDialog('assign', row)))
  }
  buttons.push(renderTextAction('详情', () => openDetail(row)))
  buttons.push(renderTextAction('操作日志', () => openDialog('logs', row)))
  if (row.selectedToLibrary && canDo('promotion')) {
    buttons.push(renderTextAction('复制推广链接', () => copyPromotionLink(row)))
  }
  return h('div', { class: 'table-actions' }, buttons)
}

const columns = computed(() => [
  {
    type: 'selection',
    width: 42,
    multiple: true
  },
  {
    title: '商品信息',
    key: 'product',
    minWidth: 360,
    render: (row: any) => renderProductInfo(row)
  },
  {
    title: '佣金率',
    key: 'commission',
    width: 240,
    render: (row: any) => renderCommission(row)
  },
  {
    title: '服务费率',
    key: 'serviceFee',
    width: 150,
    render: (row: any) => renderServiceFee(row)
  },
  {
    title: '标签',
    key: 'tags',
    width: 150,
    render: (row: any) => renderTagList(row)
  },
  {
    title: '招商跟进人',
    key: 'progress',
    width: 120,
    render: (row: any) => renderProgress(row)
  },
  {
    title: '合作设置',
    key: 'cooperation',
    minWidth: 260,
    render: (row: any) => renderLineList(getCooperationLines(row))
  },
  {
    title: '活动信息',
    key: 'activity',
    minWidth: 220,
    render: (row: any) => renderLineList(getActivityLines(row))
  },
  {
    title: '操作',
    key: 'actions',
    width: 140,
    render: (row: any) => renderActions(row)
  }
])

onMounted(async () => {
  try {
    await refreshProducts()
    await loadProductOptions('')
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '页面初始化失败')
  }
})

watch(
  () => [route.path, route.params.activityId],
  async () => {
    nextCursor.value = ''
    await refreshProducts()
    await loadProductOptions(productKeyword.value)
  }
)
</script>

<style scoped>
.product-page {
  padding: 24px;
}

.page-alert {
  margin-bottom: 16px;
}

.main-card {
  border-radius: 0;
  overflow: hidden;
  box-shadow: none;
  border: 1px solid #f0f0f0;
}

.table-empty {
  padding: 48px 0;
}

:deep(.table-product) {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

:deep(.table-product-cover) {
  width: 68px;
  height: 68px;
  border-radius: 0;
  overflow: hidden;
  background: #f5f6fa;
  border: 1px solid #f0f0f0;
  margin-top: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.table-product-cover img),
:deep(.table-product-cover .img) {
  width: 68px;
  height: 68px;
  object-fit: contain;
  display: block;
  background: #f5f6fa;
}

:deep(.table-product-fallback) {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 12px;
}

:deep(.table-product-body) {
  min-width: 0;
}

:deep(.table-product-title) {
  color: #ef4444;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 260px;
}

:deep(.table-product-meta),
:deep(.table-muted) {
  color: #666;
  font-size: 12px;
  line-height: 1.45;
}

:deep(.table-stack) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

:deep(.table-stack.compact) {
  gap: 4px;
}

:deep(.table-stack-line) {
  color: #333;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-line;
}

:deep(.table-stack-line.strong) {
  color: #111827;
  font-weight: 600;
}

:deep(.table-stack-line.muted) {
  color: #888;
}

:deep(.table-tag-block) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

:deep(.table-inline-badge) {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 3px;
  background: #fff2e8;
  color: #f97316;
  font-size: 12px;
  font-weight: 600;
  width: fit-content;
}

:deep(.table-link-action-button) {
  justify-content: flex-start;
  padding: 0;
  height: auto;
  min-height: auto;
  font-size: 12px;
  line-height: 1.5;
  --n-text-color: #ef4444 !important;
  --n-text-color-hover: #dc2626 !important;
  --n-text-color-pressed: #dc2626 !important;
  --n-text-color-focus: #dc2626 !important;
}

:deep(.table-link-action-button .n-button__content) {
  justify-content: flex-start;
}

:deep(.table-link-action-button.is-muted) {
  --n-text-color: #999 !important;
  --n-text-color-hover: #999 !important;
  --n-text-color-pressed: #999 !important;
  --n-text-color-focus: #999 !important;
  cursor: default;
  pointer-events: none;
}

:deep(.table-actions) {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 20px 0 4px;
}

.no-more {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.main-card :deep(.n-data-table-wrapper) {
  background: #fff;
}

.main-card :deep(.n-data-table-th) {
  background: #fff;
  color: #111827;
  font-size: 12px;
  font-weight: 700;
  border-bottom: 1px solid #f0f0f0;
}

.main-card :deep(.n-data-table-th--selection),
.main-card :deep(.n-data-table-td--selection) {
  padding-left: 10px;
  padding-right: 6px;
}

.main-card :deep(.n-data-table-td) {
  padding-top: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: top;
}
</style>
