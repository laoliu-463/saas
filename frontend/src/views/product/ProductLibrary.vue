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

    <ProductLibraryFilterPanel
      :filters="filters"
      :library-status="libraryStatus"
      :loading="loading"
      :category-options="libraryCategoryOptions"
      @update:filters="filters = $event"
      @update:library-status="libraryStatus = $event"
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
        <ProductSelectionCard
          v-for="item in products"
          :key="`${item.card.productId}-${item.card.activityId || item.card.id}`"
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
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import PageEmpty from '../../components/PageEmpty.vue'
import PageHeader from '../../components/PageHeader.vue'
import { getProducts, getProductLibraryCategories } from '../../api/product'
import { convertActivityProductLink } from '../../api/activityProduct'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES, hasAccess } from '../../constants/rbac'
import { useDelayedFlag } from '../../utils/delayedFlag'

import ProductLibraryFilterPanel from './components/ProductLibraryFilterPanel.vue'
import ProductSelectionCard from '../../components/product/ProductSelectionCard.vue'
import QuickSampleModal from './components/QuickSampleModal.vue'
import ProductDetail from './ProductDetail.vue'
import {
  buildProductLibraryQueryParams,
  DEFAULT_PRODUCT_FILTERS,
  productDomainCategoryOptions,
  type ProductFilterState
} from './product-filters'
import { copyProductBriefWithLink, resolveProductBriefCopyMessage } from './product-copy'
import { mergeLibraryDisplayFields, normalizeProductCard } from './product-library-display'

const PAGE_SIZE = 12

const message = useMessage()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const showInitialLoading = useDelayedFlag(loading, 200)
const loadingMore = ref(false)
const promotionLoadingIds = ref<Set<string>>(new Set())
const products = ref<any[]>([])
const hasMore = ref(false)
const totalCount = ref(0)
const libraryStatus = ref<number | null>(null)
const filters = ref<ProductFilterState>(DEFAULT_PRODUCT_FILTERS())
const libraryCategoryOptions = ref<{ label: string; value: string }[]>([])
const currentRow = ref<any | null>(null)
const quickSampleVisible = ref(false)
const quickSampleProduct = ref<any | null>(null)
const showDetail = ref(false)
const detailRefreshKey = ref(0)

const canCopyPromotionLink = computed(() =>
  hasAccess(authStore.roleCodes, [ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.CHANNEL_STAFF])
)

const convertLinkForBriefCopy = (
  activityId: string | number,
  productId: string | number,
  data: { scene: 'PRODUCT_LIBRARY' | 'PRODUCT_DETAIL' | 'TALENT_SHARE' | 'SAMPLE_DESK' }
) => convertActivityProductLink(activityId, productId, data, { suppressErrorNotice: true })

/** 渠道与管理员可发起快速寄样（后端 quick-sample 同限） */
const canQuickSample = computed(() => canCopyPromotionLink.value || authStore.isAdmin)

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

const fetchProducts = async (reset: boolean) => {
  if (reset) loading.value = true
  else loadingMore.value = true

  try {
    const page = reset ? 1 : Math.floor(products.value.length / PAGE_SIZE) + 1
    const res: any = await getProducts(buildProductLibraryQueryParams(filters.value, {
      page,
      size: PAGE_SIZE,
      status: libraryStatus.value ?? undefined,
      sortBy: (route.query.sortBy as string | undefined) || 'default'
    }))
    const data = res?.data || {}
    const records = Array.isArray(data.records) ? data.records : []
    const items = records.map((p: any) =>
      normalizeItem({
        ...p,
        productId: String(p.productId || '')
      })
    )
    products.value = reset ? items : products.value.concat(items)
    const currentPage = Number(data.page || page || 1)
    const pageSize = Number(data.size || PAGE_SIZE)
    const total = Number(data.total || 0)
    totalCount.value = total
    hasMore.value = currentPage * pageSize < total
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '商品查询失败' })
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

const resetFilters = () => {
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
      convertLink: convertLinkForBriefCopy,
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
      replaceProductRow(productId, normalizeItem(merged))
    }

    const notice = resolveProductBriefCopyMessage({
      clipboardWriteFailed,
      linkGenerationFailed: result.linkGenerationFailed,
      promotionLinkGenerated: result.promotionLinkGenerated
    })
    message[notice.type](notice.content)
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '讲解复制失败，请稍后重试' })
  } finally {
    const next = new Set(promotionLoadingIds.value)
    next.delete(productId)
    promotionLoadingIds.value = next
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
  try {
    await Promise.all([loadFilterOptions(), refreshProducts()])
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '页面初始化失败' })
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
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  align-items: start;
}

@media (min-width: 1280px) {
  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .product-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
