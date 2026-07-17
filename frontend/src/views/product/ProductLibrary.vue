<template>
  <div ref="productPageRef" class="product-page app-page" data-testid="product-library-page">
    <PageHeader
      title="商品库"
      description="沉淀完成的共享商品库，对全员可见，可直接复用历史商品结果。"
    >
      <template #actions>
        <n-button :loading="loading" type="primary" data-testid="product-library-refresh" @click="refreshProducts">刷新商品</n-button>
      </template>
    </PageHeader>

    <ProductLibraryFilterPanel
      :filters="filters"
      :library-status="libraryStatus"
      :loading="loading"
      :category-options="libraryCategoryOptions"
      @update:filters="handleFiltersChange"
      @update:library-status="handleLibraryStatusChange"
      @search-click="refreshProducts"
      @reset="resetFilters"
    />

    <div
      v-if="appliedActivityId"
      class="product-library-activity-filter"
      data-testid="product-library-activity-filter-applied"
    >
      <span class="product-library-activity-filter__label">已按活动筛选</span>
      <code class="product-library-activity-filter__value">{{ appliedActivityId }}</code>
      <n-button
        text
        type="primary"
        size="small"
        data-testid="product-library-activity-filter-clear"
        @click="clearActivityFilter"
      >
        清除筛选
      </n-button>
    </div>

    <div
      v-if="products.length"
      class="product-list-toolbar"
      data-testid="product-library-list-toolbar"
    >
      <span class="product-list-meta">
        已加载 <strong>{{ products.length }}</strong>
        件
      </span>
      <n-space align="center" :size="8">
        <n-button
          v-if="hasMore"
          :loading="loadingMore"
          type="primary"
          secondary
          size="small"
          data-testid="product-library-load-more"
          @click="loadMore"
        >
          加载更多
        </n-button>
        <span v-else class="no-more-inline">已全部加载</span>
      </n-space>
    </div>

    <n-spin :show="showInitialLoading">
      <div
        v-if="products.length"
        ref="productGridRef"
        class="product-grid"
        :class="{ 'product-grid--virtual': virtualGridEnabled }"
        :style="virtualGridContainerStyle"
        data-testid="product-grid"
      >
        <div
          v-if="virtualGridEnabled"
          class="product-grid__virtual-window"
          :style="virtualGridWindowStyle"
          data-testid="product-grid-virtual-window"
        >
          <ProductSelectionCard
            v-for="item in visibleProducts"
            :key="productCardKey(item)"
            :card="item.card"
            :can-copy-brief="canCopyPromotionLink"
            :can-quick-sample="canQuickSample"
            :copy-brief-loading="promotionLoadingIds.has(item.card.productId)"
            @detail="openDetail"
            @copy-brief="copyPromotionLink"
            @quick-sample="openSampleApply"
            @refresh="refreshProductRow"
          />
        </div>
        <template v-else>
          <ProductSelectionCard
            v-for="item in visibleProducts"
            :key="productCardKey(item)"
            :card="item.card"
            :can-copy-brief="canCopyPromotionLink"
            :can-quick-sample="canQuickSample"
            :copy-brief-loading="promotionLoadingIds.has(item.card.productId)"
            @detail="openDetail"
            @copy-brief="copyPromotionLink"
            @quick-sample="openSampleApply"
            @refresh="refreshProductRow"
          />
        </template>
      </div>

      <div
        v-if="products.length && hasMore"
        ref="loadMoreTrigger"
        class="product-library-scroll-sentinel"
        data-testid="product-library-scroll-sentinel"
        aria-hidden="true"
      />

      <PageEmpty
        v-else-if="!loading"
        title="暂无商品数据"
        description="当前还没有加入商品库的商品，可先在商品管理中完成选品并加入商品库。"
      >
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="36" height="36">
            <path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/>
          </svg>
        </template>
      </PageEmpty>
    </n-spin>

    <ProductDetail
      v-model:show="showDetail"
      :product-id="currentRow?.productId ?? null"
      :activity-id="currentRow?.sourceActivityId || currentRow?.activityId || null"
      :pick-mode="false"
      :library-mode="true"
      :can-put-into-library="false"
      :refresh-key="detailRefreshKey"
      @action="handleDetailAction"
    />
    <QuickSampleModal v-model:show="quickSampleVisible" :product="quickSampleProduct" @success="refreshProducts" />
    <ManualCopyDialog
      :show="manualCopyDialog.show"
      :content="manualCopyDialog.content"
      :promotion-link="manualCopyDialog.promotionLink"
      :pick-source="manualCopyDialog.pickSource"
      :pick-source-warning="manualCopyDialog.pickSourceWarning"
      :reason="manualCopyDialog.reason"
      :baiying-url="manualCopyDialog.baiyingUrl"
      @retry="retryManualCopy"
      @close="closeManualCopyDialog"
    />
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import PageEmpty from '../../components/PageEmpty.vue'
import PageHeader from '../../components/PageHeader.vue'
import ManualCopyDialog from '../../components/common/ManualCopyDialog.vue'
import { getProducts, getProductLibraryCategories } from '../../api/product'
import { convertActivityProductLink } from '../../api/activityProduct'
import { useAuthStore } from '../../stores/auth'
import { useDelayedFlag } from '../../utils/delayedFlag'

import ProductLibraryFilterPanel from './components/ProductLibraryFilterPanel.vue'
import ProductSelectionCard from '../../components/product/ProductSelectionCard.vue'
import QuickSampleModal from './components/QuickSampleModal.vue'
import ProductDetail from './ProductDetail.vue'
import {
  canUseProductLibraryCursor,
  buildProductLibraryQueryParams,
  DEFAULT_PRODUCT_FILTERS,
  productDomainCategoryOptions,
  type ProductFilterState
} from './product-filters'
import {
  copyProductBriefWithLink,
  resolveProductBriefCopyMessage
} from './product-copy'
import { createEmptyManualCopyDialogState, resolveManualCopyDialogState } from './manual-copy'
import { mergeLibraryDisplayFields, normalizeProductCard } from './product-library-display'
import {
  buildQueryWithoutActivityId,
  isSameActivityId,
  resolveActivityIdFromQuery
} from './product-library-route-sync'
import { tryCopyText, tryCopyTextAndImage } from '../../utils/clipboard'
import {
  PRODUCT_LIBRARY_CARD_HEIGHT,
  PRODUCT_LIBRARY_GRID_GAP,
  PRODUCT_LIBRARY_ROW_HEIGHT
} from './product-library-layout'
import { canGenerateAttributionPromotionLink } from './product-actions'
import { canApplyQuickSampleByRole } from './product-permissions'

const PRODUCT_LIBRARY_REQUEST_BATCH_SIZE = 100
const PRODUCT_LIBRARY_BACKEND_MAX_LIMIT = 500
const PRODUCT_LIBRARY_CARD_MIN_TRACK_WIDTH = 230
const PRODUCT_LIBRARY_VIRTUAL_OVERSCAN_ROWS = 2
const PRODUCT_LIBRARY_PREFETCH_ROWS = 2
const PRODUCT_LIBRARY_VIRTUAL_GRID_ENABLED = import.meta.env.VITE_PRODUCT_LIBRARY_VIRTUAL_GRID !== 'false'
const PRODUCT_LIBRARY_PERF_DEBUG = import.meta.env.VITE_PRODUCT_LIBRARY_PERF_DEBUG === 'true'

const message = useMessage()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const showInitialLoading = useDelayedFlag(loading, 200)
const loadingMore = ref(false)
const promotionLoadingIds = ref<Set<string>>(new Set())
const products = ref<any[]>([])
const currentPage = ref(1)
const nextCursor = ref('')
const hasMore = ref(false)
const totalCount = ref(0)
const productPageRef = ref<HTMLElement | null>(null)
const loadMoreTrigger = ref<HTMLElement | null>(null)
const productGridRef = ref<HTMLElement | null>(null)
const viewportTop = ref(0)
const viewportHeight = ref(typeof window === 'undefined' ? 900 : window.innerHeight || 900)
const viewportWidth = ref(typeof window === 'undefined' ? 1280 : window.innerWidth || 1280)
const productGridTop = ref(0)
const productGridWidth = ref(0)
const coarsePointerGrid = ref(false)
const autoLoadSuspended = ref(false)
const libraryStatus = ref<number | null>(null)
const filters = ref<ProductFilterState>(DEFAULT_PRODUCT_FILTERS())
const libraryCategoryOptions = ref<{ label: string; value: string }[]>([])
const currentRow = ref<any | null>(null)
const quickSampleVisible = ref(false)
const quickSampleProduct = ref<any | null>(null)
const showDetail = ref(false)
const detailRefreshKey = ref(0)
const manualCopyDialog = ref(createEmptyManualCopyDialogState())
let loadMoreObserver: IntersectionObserver | null = null
let loadMoreObserverRoot: Element | null = null
let productGridViewportRaf: number | null = null
let productScrollTarget: Window | HTMLElement | null = null

const canCopyPromotionLink = computed(() =>
  canGenerateAttributionPromotionLink({ roles: authStore.roleCodes })
)

const convertLinkForBriefCopy = (
  activityId: string | number,
  productId: string | number,
  data: { scene: 'PRODUCT_LIBRARY' | 'PRODUCT_DETAIL' | 'TALENT_SHARE' | 'SAMPLE_DESK' }
) => convertActivityProductLink(activityId, productId, data, { suppressErrorNotice: true })

/** 招商、渠道与管理员可发起快速寄样（后端 quick-sample 同限） */
const canQuickSample = computed(() => canApplyQuickSampleByRole(authStore.roleCodes, authStore.isAdmin))

const normalizeText = (value?: string | number | null) => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  return text === 'null' || text === 'undefined' ? '' : text
}

const normalizeItem = (item: any) => {
  const base = mergeLibraryDisplayFields({
    ...item,
    bizStatus: item?.bizStatus || 'PENDING_AUDIT',
    bizStatusLabel: item?.bizStatusLabel || '',
    productId: String(item?.productId ?? ''),
    title: item?.title || item?.name || item?.productName || '未命名商品',
    activityId: item?.activityId ? String(item.activityId) : '',
    sourceActivityId: item?.sourceActivityId
      ? String(item.sourceActivityId)
      : item?.activityId
        ? String(item.activityId)
        : '',
    shopName: normalizeText(item?.shopName) || item?.shopName,
    categoryName: normalizeText(item?.categoryName) || item?.categoryName,
    statusText: normalizeText(item?.statusText) || item?.statusText,
    sales30d: item?.sales30d ?? item?.sales ?? 0,
    gmv30d: item?.gmv30d ?? '0.00',
    estimatedServiceFee: item?.estimatedServiceFee ?? item?.estimatedServiceFeeAmount ?? '0.00',
    hasMaterial: item?.hasMaterial ?? false,
    hasSampleRule: item?.hasSampleRule ?? true,
    activityExpired: Boolean(item?.activityExpired),
    selectedToLibrary: item?.selectedToLibrary ?? true,
    systemTags: Array.isArray(item?.systemTags) ? item.systemTags : [],
    alertTags: Array.isArray(item?.alertTags) ? item.alertTags : [],
    promotionLink: item?.promotionLink || item?.promoteLink || item?.shortLink || null,
    promotion: item?.promotion || {
      status: item?.promotionLinkStatus || 'PENDING',
      statusLabel: item?.promotionLinkStatusLabel || '未生成',
      link: item?.promotionLink || item?.promoteLink || item?.shortLink || null,
      generatedAt: item?.promotionLinkGeneratedAt || null,
      expireAt: item?.promotionLinkExpireAt || null,
      failReason: item?.promotionLinkFailReason || null
    }
  })
  return {
    ...base,
    card: normalizeProductCard(base)
  }
}

const replaceProductRow = (productId: string, nextRow: any) => {
  products.value = products.value.map((row: any) =>
    String(row.productId) === productId ? nextRow : row
  )
  if (String(currentRow.value?.productId || '') === productId) {
    currentRow.value = { ...nextRow }
  }
}

const productCardKey = (item: any) => `${item.card.productId}-${item.card.activityId || item.card.id}`

const now = () => {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now()
  }
  return Date.now()
}

const isPerfDebugEnabled = () => {
  if (PRODUCT_LIBRARY_PERF_DEBUG) return true
  if (typeof window === 'undefined') return false
  try {
    return window.localStorage?.getItem('product-library-perf-debug') === '1'
  } catch {
    return false
  }
}

const logProductLibraryPerf = (payload: Record<string, unknown>) => {
  if (!isPerfDebugEnabled()) return
  // Dev-only/debug-only performance probe; payload contains timing and counts only.
  console.debug('[product-library:perf]', payload)
}

const updateCoarsePointerGrid = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    coarsePointerGrid.value = false
    return
  }
  coarsePointerGrid.value = window.matchMedia('(hover: none), (pointer: coarse)').matches
}

const isWindowScrollTarget = (target: Window | HTMLElement | null): target is Window =>
  typeof window !== 'undefined' && target === window

const isScrollableProductElement = (element: HTMLElement) => {
  if (typeof window === 'undefined') return false
  const overflowY = window.getComputedStyle(element).overflowY
  return /(auto|scroll|overlay)/.test(overflowY) && element.scrollHeight > element.clientHeight + 1
}

const resolveProductScrollTarget = (): Window | HTMLElement | null => {
  if (typeof window === 'undefined') return null
  const root = productGridRef.value || productPageRef.value
  if (!root) return window

  let current: HTMLElement | null = root
  while (current && current !== document.body && current !== document.documentElement) {
    if (isScrollableProductElement(current)) return current
    current = current.parentElement
  }
  return window
}

const removeProductScrollTargetListener = () => {
  productScrollTarget?.removeEventListener('scroll', scheduleProductGridViewportUpdate)
}

const syncProductScrollTarget = () => {
  const nextTarget = resolveProductScrollTarget()
  if (!nextTarget || nextTarget === productScrollTarget) return
  removeProductScrollTargetListener()
  productScrollTarget = nextTarget
  productScrollTarget.addEventListener('scroll', scheduleProductGridViewportUpdate, { passive: true })
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
  loadMoreObserverRoot = null
}

const getProductScrollTop = () => {
  if (typeof window === 'undefined') return 0
  if (productScrollTarget && !isWindowScrollTarget(productScrollTarget)) {
    return productScrollTarget.scrollTop || 0
  }
  return window.scrollY || window.pageYOffset || 0
}

const getProductViewportHeight = () => {
  if (typeof window === 'undefined') return viewportHeight.value
  if (productScrollTarget && !isWindowScrollTarget(productScrollTarget)) {
    return productScrollTarget.clientHeight || window.innerHeight || viewportHeight.value
  }
  return window.innerHeight || viewportHeight.value
}

const updateProductGridViewport = () => {
  if (typeof window === 'undefined') return
  syncProductScrollTarget()
  const scrollTop = getProductScrollTop()
  viewportTop.value = scrollTop
  viewportHeight.value = getProductViewportHeight()
  viewportWidth.value = productScrollTarget && !isWindowScrollTarget(productScrollTarget)
    ? productScrollTarget.clientWidth || window.innerWidth || viewportWidth.value
    : window.innerWidth || viewportWidth.value
  updateCoarsePointerGrid()
  const grid = productGridRef.value
  if (!grid) return
  const rect = grid.getBoundingClientRect()
  if (productScrollTarget && !isWindowScrollTarget(productScrollTarget)) {
    const scrollRect = productScrollTarget.getBoundingClientRect()
    productGridTop.value = rect.top - scrollRect.top + scrollTop
    productGridWidth.value = rect.width || productScrollTarget.clientWidth || viewportWidth.value
    return
  }
  productGridTop.value = rect.top + scrollTop
  productGridWidth.value = rect.width || viewportWidth.value
}

const requestFrame = (callback: FrameRequestCallback) => {
  if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    return window.requestAnimationFrame(callback)
  }
  return window.setTimeout(() => callback(now()), 16)
}

const cancelFrame = (frameId: number) => {
  if (typeof window !== 'undefined' && typeof window.cancelAnimationFrame === 'function') {
    window.cancelAnimationFrame(frameId)
    return
  }
  window.clearTimeout(frameId)
}

const scheduleProductGridViewportUpdate = () => {
  if (productGridViewportRaf !== null) return
  productGridViewportRaf = requestFrame(() => {
    productGridViewportRaf = null
    updateProductGridViewport()
    maybePrefetchMore()
  })
}

const productGridColumnCount = computed(() => {
  const width = productGridWidth.value || viewportWidth.value
  if (width <= 720) return 1
  const maxColumns = (viewportWidth.value || width) >= 1600 ? 5 : 4
  const fitColumns = Math.max(
    1,
    Math.floor((width + PRODUCT_LIBRARY_GRID_GAP) / (PRODUCT_LIBRARY_CARD_MIN_TRACK_WIDTH + PRODUCT_LIBRARY_GRID_GAP))
  )
  return Math.min(maxColumns, fitColumns)
})

const mobileProductGrid = computed(() => viewportWidth.value <= 720 || coarsePointerGrid.value)

const virtualGridEnabled = computed(() =>
  PRODUCT_LIBRARY_VIRTUAL_GRID_ENABLED &&
  !mobileProductGrid.value &&
  products.value.length > PRODUCT_LIBRARY_REQUEST_BATCH_SIZE
)

const virtualGridTotalRows = computed(() =>
  Math.ceil(products.value.length / productGridColumnCount.value)
)

const virtualGridStartRow = computed(() => {
  if (!virtualGridEnabled.value) return 0
  const scrolledInsideGrid = Math.max(0, viewportTop.value - productGridTop.value)
  return Math.max(0, Math.floor(scrolledInsideGrid / PRODUCT_LIBRARY_ROW_HEIGHT) - PRODUCT_LIBRARY_VIRTUAL_OVERSCAN_ROWS)
})

const virtualGridEndRow = computed(() => {
  if (!virtualGridEnabled.value) return virtualGridTotalRows.value
  const scrolledInsideGrid = Math.max(0, viewportTop.value - productGridTop.value)
  const visibleBottom = scrolledInsideGrid + viewportHeight.value
  return Math.min(
    virtualGridTotalRows.value,
    Math.ceil(visibleBottom / PRODUCT_LIBRARY_ROW_HEIGHT) + PRODUCT_LIBRARY_VIRTUAL_OVERSCAN_ROWS
  )
})

const virtualGridStartIndex = computed(() => virtualGridStartRow.value * productGridColumnCount.value)

const virtualGridEndIndex = computed(() =>
  Math.min(products.value.length, virtualGridEndRow.value * productGridColumnCount.value)
)

const visibleProducts = computed(() =>
  virtualGridEnabled.value
    ? products.value.slice(virtualGridStartIndex.value, virtualGridEndIndex.value)
    : products.value
)

const virtualGridContainerStyle = computed(() => {
  if (!virtualGridEnabled.value) return undefined
  const rows = virtualGridTotalRows.value
  const height = rows > 0
    ? rows * PRODUCT_LIBRARY_CARD_HEIGHT + Math.max(0, rows - 1) * PRODUCT_LIBRARY_GRID_GAP
    : 0
  return {
    height: `${height}px`
  }
})

const productGridTemplateColumns = computed(() =>
  `repeat(${productGridColumnCount.value}, minmax(0, 1fr))`
)

const virtualGridWindowStyle = computed(() => {
  if (!virtualGridEnabled.value) return undefined
  return {
    transform: `translateY(${virtualGridStartRow.value * PRODUCT_LIBRARY_ROW_HEIGHT}px)`,
    gridTemplateColumns: productGridTemplateColumns.value
  }
})

const maybePrefetchMore = () => {
  if (!virtualGridEnabled.value || autoLoadSuspended.value || !canLoadNextPage()) return
  const remainingLoaded = products.value.length - virtualGridEndIndex.value
  const threshold = productGridColumnCount.value * PRODUCT_LIBRARY_PREFETCH_ROWS
  if (remainingLoaded <= threshold) {
    triggerLoadMore('auto')
  }
}

const fetchProducts = async (reset: boolean) => {
  const fetchStartedAt = now()
  if (reset) loading.value = true
  else loadingMore.value = true
  if (reset) {
    currentPage.value = 1
    nextCursor.value = ''
    products.value = []
    hasMore.value = false
    totalCount.value = 0
    autoLoadSuspended.value = false
  }

  try {
    const page = reset ? 1 : currentPage.value + 1
    const cursorMode = canUseProductLibraryCursor(filters.value)
    if (!cursorMode) nextCursor.value = ''
    const commonQuery = {
      keyword: filters.value.productId || filters.value.productName || undefined,
      productIdMode: 'keyword',
      status: libraryStatus.value ?? undefined
    } as const
    const res: any = await getProducts(buildProductLibraryQueryParams(filters.value, cursorMode
      ? {
          ...commonQuery,
          cursor: reset ? undefined : nextCursor.value || undefined,
          limit: PRODUCT_LIBRARY_REQUEST_BATCH_SIZE
        }
      : {
          ...commonQuery,
          page,
          size: PRODUCT_LIBRARY_REQUEST_BATCH_SIZE
        }))
    const responseReceivedAt = now()
    const data = res?.data || {}
    const records = Array.isArray(data.records) ? data.records : []
    const normalizeStartedAt = now()
    const items = records.map((p: any) =>
      normalizeItem({
        ...p,
        productId: String(p.productId || '')
      })
    )
    const normalizeFinishedAt = now()
    products.value = reset ? items : products.value.concat(items)
    const responsePage = Number(data.page || page || 1)
    const pageSize = Number(data.size || PRODUCT_LIBRARY_REQUEST_BATCH_SIZE)
    const total = Number(data.total || 0)
    currentPage.value = Number.isFinite(responsePage) && responsePage > 0 ? responsePage : page
    totalCount.value = total
    if (cursorMode && (Object.prototype.hasOwnProperty.call(data, 'hasMore') || Object.prototype.hasOwnProperty.call(data, 'nextCursor'))) {
      nextCursor.value = String(data.nextCursor || '')
      hasMore.value = Boolean(data.hasMore || nextCursor.value)
    } else {
      nextCursor.value = ''
      hasMore.value = total > 0
        ? products.value.length < total
        : items.length >= pageSize
    }
    autoLoadSuspended.value = false
    const appendFinishedAt = now()
    void nextTick(() => {
      updateProductGridViewport()
      logProductLibraryPerf({
        reset,
        requestMs: Math.round(responseReceivedAt - fetchStartedAt),
        normalizeMs: Math.round(normalizeFinishedAt - normalizeStartedAt),
        renderMs: Math.round(now() - appendFinishedAt),
        batchSize: PRODUCT_LIBRARY_REQUEST_BATCH_SIZE,
        backendLimit: PRODUCT_LIBRARY_BACKEND_MAX_LIMIT,
        received: records.length,
        loaded: products.value.length,
        rendered: visibleProducts.value.length,
        virtual: virtualGridEnabled.value
      })
      maybePrefetchMore()
    })
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '商品查询失败' })
    if (reset) {
      products.value = []
      currentPage.value = 1
      nextCursor.value = ''
      hasMore.value = false
      totalCount.value = 0
      autoLoadSuspended.value = false
    } else {
      autoLoadSuspended.value = true
    }
  } finally {
    loading.value = false
    loadingMore.value = false
    void scheduleLoadMoreObservation()
  }
}

const resetFilters = () => {
  libraryStatus.value = null
  filters.value = DEFAULT_PRODUCT_FILTERS()
  // 重置时同步清掉 URL 上的 activityId，避免 query 与 filters 漂移
  if (appliedActivityId.value) {
    const nextQuery = buildQueryWithoutActivityId(route.query)
    void router.replace({ path: route.path, query: nextQuery })
  }
  refreshProducts()
}

const refreshProducts = async () => {
  await fetchProducts(true)
}

const canLoadNextPage = () => hasMore.value && !loading.value && !loadingMore.value

const triggerLoadMore = (source: 'auto' | 'manual') => {
  if (source === 'auto' && autoLoadSuspended.value) return
  if (source === 'manual') autoLoadSuspended.value = false
  if (canLoadNextPage()) void fetchProducts(false)
}

const loadMore = () => {
  triggerLoadMore('manual')
}

const getLoadMoreObserver = () => {
  if (typeof window === 'undefined' || typeof window.IntersectionObserver === 'undefined') {
    return null
  }
  const nextRoot = productScrollTarget && !isWindowScrollTarget(productScrollTarget)
    ? productScrollTarget
    : null
  if (loadMoreObserver && loadMoreObserverRoot !== nextRoot) {
    loadMoreObserver.disconnect()
    loadMoreObserver = null
  }
  if (!loadMoreObserver) {
    loadMoreObserverRoot = nextRoot
    loadMoreObserver = new window.IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        triggerLoadMore('auto')
      }
    }, {
      root: nextRoot,
      rootMargin: '1200px 0px',
      threshold: 0
    })
  }
  return loadMoreObserver
}

const refreshLoadMoreObserver = () => {
  syncProductScrollTarget()
  const observer = getLoadMoreObserver()
  if (!observer) return
  observer.disconnect()
  if (hasMore.value && !autoLoadSuspended.value && loadMoreTrigger.value) {
    observer.observe(loadMoreTrigger.value)
  }
}

const scheduleLoadMoreObservation = async () => {
  await nextTick()
  refreshLoadMoreObserver()
}

const handleFiltersChange = (nextFilters: ProductFilterState) => {
  filters.value = nextFilters
  void refreshProducts()
}

const handleLibraryStatusChange = (nextStatus: number | null) => {
  libraryStatus.value = nextStatus
  void refreshProducts()
}

const openDetail = (row: any) => {
  currentRow.value = { ...row }
  showDetail.value = true
}

const handleDetailAction = (_payload: { action: string; row: any }) => {
  // 商品库模式下不支持审核/分配等操作
}

const copyPromotionLink = async (item: any) => {
  if (!canCopyPromotionLink.value) {
    message.warning('仅渠道或招商角色可生成可归因推广链接')
    return
  }
  const productId = String(item?.productId || '')
  const activityId = String(item?.sourceActivityId || item?.activityId || '')
  if (!productId || !activityId) {
    message.warning('商品缺少来源活动信息，暂时无法生成推广链接')
    return
  }
  if (promotionLoadingIds.value.has(productId)) return
  promotionLoadingIds.value = new Set(promotionLoadingIds.value).add(productId)
  try {
    const result = await copyProductBriefWithLink({
      item,
      activityId,
      productId,
      scene: 'PRODUCT_LIBRARY',
      format: 'DOUYIN_SHARE',
      convertLink: convertLinkForBriefCopy,
      writeText: async (text: string) => tryCopyText(text),
      writeContent: async ({ text, imageUrl }) => tryCopyTextAndImage(text, imageUrl)
    })

    if (result.link && result.responseData) {
      const data = result.responseData
      const merged = normalizeItem({
        ...item,
        bizStatus: item?.bizStatus === 'PENDING_AUDIT' ? 'LINKED' : item?.bizStatus,
        bizStatusLabel: item?.bizStatus === 'PENDING_AUDIT' ? '已转链' : item?.bizStatusLabel,
        promotion: {
          status: 'READY',
          statusLabel: data.statusLabel || '已生成',
          link: result.link,
          generatedAt: new Date().toISOString(),
          expireAt: null,
          failReason: null
        },
        promotionLink: result.link,
        promotionLinkStatus: 'READY',
        promotionLinkStatusLabel: data.statusLabel || '已生成',
        promotionLinkFailReason: null
      })
      replaceProductRow(productId, normalizeItem(merged))
    }

    const manualState = resolveManualCopyDialogState(result, item)
    if (manualState) {
      manualCopyDialog.value = manualState
      if (manualState.reason === 'PROMOTION_LINK_FAILED') {
        message.warning('真实推广链接未生成，请前往百应手动生成')
      } else if (manualState.reason === 'PROMOTION_LINK_MISSING_PICK_SOURCE') {
        message.warning('推广链接已生成，但无法确认 pick_source，请手动核对')
      } else {
        message.warning('简介已生成，但浏览器未允许写入剪贴板，请手动复制')
      }
    } else {
      const notice = resolveProductBriefCopyMessage({
        clipboardWriteFailed: !result.copied,
        linkGenerationFailed: result.linkGenerationFailed,
        promotionLinkGenerated: result.promotionLinkGenerated,
        imageCopyAttempted: result.imageCopyAttempted,
        imageCopied: result.imageCopied
      })
      message[notice.type](notice.content)
    }
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '讲解复制失败，请稍后重试' })
  } finally {
    const next = new Set(promotionLoadingIds.value)
    next.delete(productId)
    promotionLoadingIds.value = next
  }
}

const closeManualCopyDialog = () => {
  manualCopyDialog.value = createEmptyManualCopyDialogState()
}

const retryManualCopy = async () => {
  const ok = await tryCopyText(manualCopyDialog.value.content)
  if (ok) {
    message.success('复制成功')
    closeManualCopyDialog()
  } else {
    message.warning('仍无法自动写入剪贴板，请手动复制下方内容')
  }
}

const refreshProductRow = async (item: any) => {
  const productId = String(item?.productId || '')
  if (!productId) {
    message.warning('商品信息不完整，无法刷新')
    return
  }
  try {
    const res: any = await getProducts(
      buildProductLibraryQueryParams({ ...DEFAULT_PRODUCT_FILTERS(), productId }, {
        page: 1,
        size: 1
      })
    )
    const record = Array.isArray(res?.data?.records) ? res.data.records[0] : null
    if (!record) {
      message.warning('未找到该商品，请稍后重试')
      return
    }
    replaceProductRow(productId, normalizeItem({ ...record, productId }))
    message.success('商品信息已刷新')
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '刷新商品失败' })
  }
}

const openSampleApply = (item: any) => {
  const relationId = String(item?.id || item?.relationId || '')
  if (!relationId) {
    message.warning('商品信息不完整，暂不可发起快速寄样')
    return
  }
  quickSampleProduct.value = item
  quickSampleVisible.value = true
}

/**
 * 当前 URL 上活动筛选的"权威值"。活动列表 / 路由重定向都会把 activityId
 * 写入 query，商品库只读 query 作为单一事实源，避免 path / query 两套入口
 * 出现"两边都改但不一致"的状态。详见 ADR-003。
 */
const appliedActivityId = computed(() => resolveActivityIdFromQuery(route.query))

/** 清除活动筛选：清 query、filters.activityId、立即刷新。 */
const clearActivityFilter = () => {
  if (!appliedActivityId.value && !filters.value.activityId) return
  const nextQuery = buildQueryWithoutActivityId(route.query)
  void router.replace({ path: route.path, query: nextQuery })
  filters.value = { ...filters.value, activityId: null }
  void refreshProducts()
}

const mergeCategoryOptions = (dynamicNames: string[]) => {
  const seen = new Set<string>()
  const options: { label: string; value: string }[] = []
  const append = (name: string) => {
    const normalized = String(name || '').trim()
    if (normalized && !seen.has(normalized)) {
      seen.add(normalized)
      options.push({ label: normalized, value: normalized })
    }
  }
  productDomainCategoryOptions.forEach((item) => append(item.value))
  dynamicNames.forEach(append)
  return options
}

const loadFilterOptions = async () => {
  let categoryFailed = false
  try {
    const categoryRes: any = await getProductLibraryCategories()
    const categories = Array.isArray(categoryRes?.data) ? categoryRes.data : []
    libraryCategoryOptions.value = mergeCategoryOptions(categories)
  } catch {
    categoryFailed = true
    libraryCategoryOptions.value = []
  }
  if (categoryFailed) {
    message.warning('类目选项加载失败，可继续浏览商品库')
  }
}

onMounted(async () => {
  if (typeof window !== 'undefined') {
    syncProductScrollTarget()
    updateProductGridViewport()
    window.addEventListener('resize', scheduleProductGridViewportUpdate, { passive: true })
  }
  // 首次进入：把 query.activityId 同步进 filters 作为"硬筛选"。
  // 后端 GET /products 已支持 activityId 过滤，前端不二次判断"是否应用"。
  if (appliedActivityId.value && !filters.value.activityId) {
    filters.value = { ...filters.value, activityId: appliedActivityId.value }
  }
  try {
    await Promise.all([loadFilterOptions(), refreshProducts()])
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '页面初始化失败' })
  }
})

/**
 * 监听 query 变化：例如活动列表点其他活动的"商品信息"、或外部代码
 * router.push({ query: { activityId: '...' } })，都要把 filters.activityId
 * 一起刷新，避免"URL 是新活动、列表还停留在旧活动"的状态。
 */
watch(
  () => appliedActivityId.value,
  (next) => {
    const normalized = next || null
    if (isSameActivityId(filters.value.activityId, normalized)) return
    filters.value = { ...filters.value, activityId: normalized }
    void refreshProducts()
  }
)

watch(
  () => [hasMore.value, products.value.length, autoLoadSuspended.value],
  () => {
    scheduleProductGridViewportUpdate()
    void scheduleLoadMoreObservation()
  },
  { flush: 'post' }
)

onBeforeUnmount(() => {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
  loadMoreObserverRoot = null
  if (typeof window !== 'undefined') {
    removeProductScrollTargetListener()
    window.removeEventListener('resize', scheduleProductGridViewportUpdate)
  }
  productScrollTarget = null
  if (productGridViewportRaf !== null) {
    cancelFrame(productGridViewportRaf)
    productGridViewportRaf = null
  }
})
</script>

<style scoped>
.product-page {
  max-width: 100%;
}


.product-list-toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--surface-elevated, #fff);
  border: 1px solid var(--border-subtle, rgba(0, 0, 0, 0.08));
  border-radius: var(--radius-md, 8px);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}

.product-list-meta {
  color: var(--text-secondary, #64748b);
  font-size: var(--text-sm, 13px);
}

.product-list-meta strong {
  color: var(--text-primary, #0f172a);
  font-weight: 600;
}

.no-more-inline {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.product-library-activity-filter {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0 0;
  padding: 8px 14px;
  border: 1px solid rgba(232, 69, 85, 0.32);
  border-radius: 8px;
  background: rgba(255, 246, 248, 0.92);
  color: #7f1d1d;
  font-size: 13px;
}

.product-library-activity-filter__label {
  font-weight: 600;
}

.product-library-activity-filter__value {
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(220, 38, 38, 0.08);
  color: #b91c1c;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 16px;
  align-items: start;
  justify-items: center;
}

.product-grid--virtual {
  position: relative;
  display: block;
  overflow: visible;
}

.product-grid__virtual-window {
  position: absolute;
  inset: 0 0 auto;
  display: grid;
  gap: 16px;
  align-items: start;
  justify-items: center;
  will-change: transform;
}

.product-library-scroll-sentinel {
  width: 100%;
  height: 1px;
}

@media (min-width: 1600px) {
  .product-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) and (max-width: 1599px) {
  .product-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .product-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
