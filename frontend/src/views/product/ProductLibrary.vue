<template>
  <div class="product-page app-page" data-testid="product-library-page">
    <PageHeader
      title="商品库"
      description="沉淀完成的共享商品库，对全员可见，可直接复用历史商品结果。"
    >
      <template #actions>
        <n-button :loading="loading" type="primary" data-testid="product-library-refresh" @click="refreshProducts">刷新商品</n-button>
      </template>
    </PageHeader>

    <ProductFilters
      mode="library"
      :filters="filters"
      :selected-product="selectedProduct"
      :status="null"
      :library-status="libraryStatus"
      :product-options="productOptions"
      :product-options-loading="productOptionsLoading"
      :loading="loading"
      :show-assignee-filter="false"
      :library-category-options="libraryCategoryOptions"
      :recruiter-options="recruiterOptions"
      @update:filters="filters = $event"
      @update:selected-product="selectedProduct = $event"
      @update:library-status="libraryStatus = $event"
      @search="handleProductSearch"
      @search-click="refreshProducts"
      @reset="resetFilters"
    />

    <div
      v-if="products.length"
      class="product-list-toolbar"
      data-testid="product-library-list-toolbar"
    >
      <span class="product-list-meta">
        已加载 <strong>{{ products.length }}</strong>
        <template v-if="totalCount > 0"> / {{ totalCount }}</template>
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
      <div v-if="products.length" class="product-grid" data-testid="product-grid">
        <ProductCard
          v-for="item in products"
          :key="item.productId"
          :product="item"
          :expanded="expandedProductId === item.productId"
          :can-audit="false"
          :can-assign="false"
          :can-assign-audit-owner="false"
          :pick-mode="false"
          :library-mode="true"
          :can-put-into-library="false"
          :can-copy-link="canCopyPromotionLink"
          :can-apply-sample="canCopyPromotionLink"
          :can-pin="canPinProduct"
          @toggle="expandedProductId = $event"
          @detail="openDetail"
          @copy-link="copyPromotionLink"
          @apply-sample="openSampleApply"
          @pin="pinProduct"
          @unpin="unpinProduct"
          @show-logs="openDialog('logs', $event)"
        />
      </div>

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
    <ProductOperationLogDrawer
      v-model:show="dialogs.logs"
      :product-id="currentRow?.productId ?? null"
      :activity-id="currentRow?.sourceActivityId || currentRow?.activityId || null"
    />
    <QuickSampleModal v-model:show="quickSampleVisible" :product="quickSampleProduct" @success="refreshProducts" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import PageEmpty from '../../components/PageEmpty.vue'
import PageHeader from '../../components/PageHeader.vue'
import { getProducts, getProductLibraryCategories } from '../../api/product'
import { getUserMasterRecruiters } from '../../api/sys'
import { convertActivityProductLink, pinActivityProduct, unpinActivityProduct } from '../../api/activityProduct'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES, hasAccess } from '../../constants/rbac'
import { useDelayedFlag } from '../../utils/delayedFlag'
import { useDebouncedFn } from '../../utils/debounce'
import { MAX_PAGE_SIZE } from '../../utils/pagination'

import ProductFilters from './components/ProductFilters.vue'
import ProductCard from './components/ProductCard.vue'
import QuickSampleModal from './components/QuickSampleModal.vue'
import ProductDetail from './ProductDetail.vue'
import ProductOperationLogDrawer from './components/ProductOperationLogDrawer.vue'
import {
  buildProductLibraryQueryParams,
  DEFAULT_PRODUCT_FILTERS,
  productDomainCategoryOptions,
  type ProductFilterState
} from './product-filters'
import { copyProductBriefWithLink } from './product-copy'

const PAGE_SIZE = 12

const message = useMessage()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const showInitialLoading = useDelayedFlag(loading, 200)
const loadingMore = ref(false)
const promotionLoadingIds = ref<Set<string>>(new Set())
const pinLoadingIds = ref<Set<string>>(new Set())
const products = ref<any[]>([])
const hasMore = ref(false)
const totalCount = ref(0)
const selectedProduct = ref<string | null>(null)
const productKeyword = ref('')
const libraryStatus = ref<number | null>(null)
const filters = ref<ProductFilterState>(DEFAULT_PRODUCT_FILTERS())
const libraryCategoryOptions = ref<{ label: string; value: string }[]>([])
const recruiterOptions = ref<{ label: string; value: string }[]>([])
const productOptions = ref<{ label: string; value: string }[]>([])
const productOptionsLoading = ref(false)
const currentRow = ref<any | null>(null)
const quickSampleVisible = ref(false)
const quickSampleProduct = ref<any | null>(null)
const expandedProductId = ref<string | null>(null)
const showDetail = ref(false)
const detailRefreshKey = ref(0)

const dialogs = ref({
  logs: false
})

const canCopyPromotionLink = computed(() =>
  hasAccess(authStore.roleCodes, [ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.CHANNEL_STAFF])
)

const canPinProduct = computed(() =>
  hasAccess(authStore.roleCodes, [ROLE_CODES.BIZ_LEADER, ROLE_CODES.BIZ_STAFF])
)

const normalizeText = (value?: string | number | null) => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  return text === 'null' || text === 'undefined' ? '' : text
}

const normalizeItem = (item: any) => ({
  ...item,
  bizStatus: item?.bizStatus || 'PENDING_AUDIT',
  bizStatusLabel: item?.bizStatusLabel || '',
  productId: String(item?.productId ?? ''),
  activityId: item?.activityId ? String(item.activityId) : '',
  sourceActivityId: item?.sourceActivityId ? String(item.sourceActivityId) : item?.activityId ? String(item.activityId) : '',
  shopName: normalizeText(item?.shopName) || item?.shopName,
  categoryName: normalizeText(item?.categoryName) || item?.categoryName,
  statusText: normalizeText(item?.statusText) || item?.statusText,
  sales30d: item?.sales30d ?? item?.sales ?? 0,
  gmv30d: item?.gmv30d ?? '0.00',
  estimatedServiceFee: item?.estimatedServiceFee ?? item?.estimatedServiceFeeAmount ?? '0.00',
  hasMaterial: item?.hasMaterial ?? false,
  hasSampleRule: item?.hasSampleRule ?? true,
  activityExpired: Boolean(item?.activityExpired),
  selectedToLibrary: true,
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

const fetchProducts = async (reset: boolean) => {
  if (reset) loading.value = true
  else loadingMore.value = true

  try {
    const page = reset ? 1 : Math.floor(products.value.length / PAGE_SIZE) + 1
    const res: any = await getProducts(buildProductLibraryQueryParams(filters.value, {
      page,
      size: PAGE_SIZE,
      keyword: selectedProduct.value || productKeyword.value.trim() || undefined,
      status: libraryStatus.value ?? undefined,
      partnerId: route.query.partnerId as string | undefined,
      partnerType: route.query.partnerType as string | undefined,
      sortBy: (route.query.sortBy as string | undefined) || 'default'
    }))
    const data = res?.data || {}
    const records = Array.isArray(data.records) ? data.records : []
    const items = records.map((p: any) => normalizeItem({
      ...p,
      title: p.title || p.name || '未命名商品',
      productId: String(p.productId || '')
    }))
    products.value = reset ? items : products.value.concat(items)
    const currentPage = Number(data.page || page || 1)
    const pageSize = Number(data.size || PAGE_SIZE)
    const total = Number(data.total || 0)
    totalCount.value = total
    hasMore.value = currentPage * pageSize < total
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '商品查询失败')
    if (reset) {
      products.value = []
      hasMore.value = false
      totalCount.value = 0
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
  const normalizedKeyword = String(keyword || '').trim()
  if (!normalizedKeyword) {
    productOptions.value = []
    return
  }
  productOptionsLoading.value = true
  try {
    const res: any = await getProducts({ page: 1, size: 20, keyword: normalizedKeyword })
    const records = Array.isArray(res?.data?.records) ? res.data.records : []
    productOptions.value = records
      .map((p: any) => buildProductOption({
        ...p,
        title: p.title || p.name || '未命名商品',
        productId: String(p.productId || '')
      }))
      .filter((item: { label: string; value: string }) => Boolean(item.value))
  } catch (error: any) {
    message.warning(error?.response?.data?.msg || '商品搜索暂不可用')
    productOptions.value = []
  } finally {
    productOptionsLoading.value = false
  }
}

const debouncedLoadProductOptions = useDebouncedFn((keyword: string) => {
  void loadProductOptions(keyword)
}, 250)

const handleProductSearch = (keyword: string) => {
  productKeyword.value = String(keyword || '').trim()
  debouncedLoadProductOptions(productKeyword.value)
}

const resetFilters = () => {
  selectedProduct.value = null
  productKeyword.value = ''
  libraryStatus.value = null
  filters.value = DEFAULT_PRODUCT_FILTERS()
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

const handleDetailAction = (_payload: { action: string; row: any }) => {
  // 商品库模式下不支持审核/分配等操作
}

const copyPromotionLink = async (item: any) => {
  if (!canCopyPromotionLink.value) {
    message.warning('当前角色仅可查看商品库，推广链接由渠道角色生成')
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
  let clipboardWriteFailed = false
  try {
    const result = await copyProductBriefWithLink({
      item,
      activityId,
      productId,
      scene: 'PRODUCT_LIBRARY',
      convertLink: convertActivityProductLink,
      writeText: async (text: string) => {
        try {
          await navigator.clipboard.writeText(text)
        } catch {
          clipboardWriteFailed = true
        }
      }
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
      products.value = products.value.map((row: any) =>
        String(row.productId) === productId ? { ...row, ...merged } : row
      )
      if (String(currentRow.value?.productId || '') === productId) {
        currentRow.value = { ...currentRow.value, ...merged }
      }
    }

    if (clipboardWriteFailed) {
      message.warning('讲解已生成，但浏览器未允许写入剪贴板，请手动复制')
    } else if (result.linkGenerationFailed) {
      message.error('短链生成失败，已复制讲解（不含短链）')
    } else {
      message.success('讲解 + 短链已复制')
    }
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '讲解复制失败，请稍后重试')
  } finally {
    const next = new Set(promotionLoadingIds.value)
    next.delete(productId)
    promotionLoadingIds.value = next
  }
}

const updatePinnedState = (item: any, pinned: boolean, pinnedUntil?: string | null) => {
  const productId = String(item?.productId || '')
  const merged = normalizeItem({
    ...item,
    pinned,
    pinnedUntil: pinned ? pinnedUntil || item?.pinnedUntil || null : null
  })
  products.value = products.value.map((row: any) =>
    String(row.productId) === productId ? { ...row, ...merged } : row
  )
  if (String(currentRow.value?.productId || '') === productId) {
    currentRow.value = { ...currentRow.value, ...merged }
  }
}

const setProductPinned = async (item: any, pinned: boolean) => {
  if (!canPinProduct.value) {
    message.warning('仅招商角色可以置顶商品')
    return
  }
  const productId = String(item?.productId || '')
  const activityId = String(item?.sourceActivityId || item?.activityId || '')
  if (!productId || !activityId) {
    message.warning('商品缺少来源活动信息，暂时无法置顶')
    return
  }
  if (pinLoadingIds.value.has(productId)) return
  pinLoadingIds.value = new Set(pinLoadingIds.value).add(productId)
  try {
    const res: any = pinned
      ? await pinActivityProduct(activityId, productId)
      : await unpinActivityProduct(activityId, productId)
    updatePinnedState(item, Boolean(res?.data?.pinned ?? pinned), res?.data?.pinnedUntil || null)
    message.success(pinned ? '商品已置顶 24 小时' : '已取消置顶')
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || (pinned ? '置顶失败，请稍后重试' : '取消置顶失败，请稍后重试'))
  } finally {
    const next = new Set(pinLoadingIds.value)
    next.delete(productId)
    pinLoadingIds.value = next
  }
}

const pinProduct = (item: any) => setProductPinned(item, true)

const unpinProduct = (item: any) => setProductPinned(item, false)

const openSampleApply = (item: any) => {
  if (!item?.id) {
    message.warning('商品信息不完整，暂不可发起快速寄样')
    return
  }
  quickSampleProduct.value = item
  quickSampleVisible.value = true
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
    const [categoryRes, recruiterRes]: any[] = await Promise.all([
      getProductLibraryCategories(),
      getUserMasterRecruiters({ limit: MAX_PAGE_SIZE })
    ])
    const categories = Array.isArray(categoryRes?.data) ? categoryRes.data : []
    libraryCategoryOptions.value = mergeCategoryOptions(categories)
    const recruiters = Array.isArray(recruiterRes?.data) ? recruiterRes.data : []
    recruiterOptions.value = recruiters
      .map((item: any) => ({
        label: String(item?.label || item?.realName || item?.username || '').trim(),
        value: String(item?.id || item?.userId || '').trim()
      }))
      .filter((item: { label: string; value: string }) => item.label && item.value)
  } catch {
    categoryFailed = true
    libraryCategoryOptions.value = []
    recruiterOptions.value = []
  }
  if (categoryFailed) {
    message.warning('类目选项加载失败，可继续浏览商品库')
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadFilterOptions(), refreshProducts()])
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '页面初始化失败')
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

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--spacing-md);
  align-items: start;
}
</style>
