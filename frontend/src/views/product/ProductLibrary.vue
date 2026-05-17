<template>
  <div class="product-page" data-testid="product-library-page">
    <PageHeader
      title="商品库"
      description="沉淀完成的共享商品库，对全员可见，可直接复用历史商品结果。"
    >
      <template #actions>
        <n-button :loading="loading" type="primary" data-testid="product-library-refresh" @click="refreshProducts">刷新商品</n-button>
      </template>
    </PageHeader>

    <div class="toolbar">
      <n-space>
        <n-select
          v-model:value="selectedProduct"
          :options="productOptions"
          :loading="productOptionsLoading"
          filterable
          remote
          clearable
          placeholder="搜索商品名称"
          style="width: 280px"
          @search="handleProductSearch"
          @update:value="refreshProducts"
        />
        <n-button type="primary" @click="refreshProducts">搜索</n-button>
        <n-button @click="resetFilters">重置</n-button>
      </n-space>
    </div>

    <n-spin :show="loading">
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
          @toggle="expandedProductId = $event"
          @detail="openDetail"
          @copy-link="copyPromotionLink"
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

    <div v-if="products.length" class="load-more">
      <n-button v-if="hasMore" :loading="loadingMore" secondary @click="loadMore">加载更多</n-button>
      <span v-else class="no-more">已加载全部商品</span>
    </div>

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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import PageEmpty from '../../components/PageEmpty.vue'
import PageHeader from '../../components/PageHeader.vue'
import { getProducts } from '../../api/product'
import { convertActivityProductLink } from '../../api/activityProduct'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES, hasAccess } from '../../constants/rbac'

import ProductCard from './components/ProductCard.vue'
import ProductDetail from './ProductDetail.vue'
import ProductOperationLogDrawer from './components/ProductOperationLogDrawer.vue'

const message = useMessage()
const authStore = useAuthStore()

const loading = ref(false)
const loadingMore = ref(false)
const promotionLoadingIds = ref<Set<string>>(new Set())
const products = ref<any[]>([])
const hasMore = ref(false)
const selectedProduct = ref<string | null>(null)
const productKeyword = ref('')
const productOptions = ref<{ label: string; value: string }[]>([])
const productOptionsLoading = ref(false)
const currentRow = ref<any | null>(null)
const expandedProductId = ref<string | null>(null)
const showDetail = ref(false)
const detailRefreshKey = ref(0)

const dialogs = ref({
  logs: false
})

const canCopyPromotionLink = computed(() =>
  hasAccess(authStore.roleCodes, [ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.CHANNEL_STAFF])
)

const normalizeItem = (item: any) => ({
  ...item,
  bizStatus: item?.bizStatus || 'PENDING_AUDIT',
  bizStatusLabel: item?.bizStatusLabel || '',
  productId: String(item?.productId ?? ''),
  activityId: item?.activityId ? String(item.activityId) : '',
  sourceActivityId: item?.sourceActivityId ? String(item.sourceActivityId) : item?.activityId ? String(item.activityId) : '',
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
    const page = reset ? 1 : Math.floor(products.value.length / 20) + 1
    const res: any = await getProducts({
      page,
      size: 20,
      keyword: selectedProduct.value || productKeyword.value.trim() || undefined
    })
    const data = res?.data || {}
    const records = Array.isArray(data.records) ? data.records : []
    const items = records.map((p: any) => normalizeItem({
      ...p,
      title: p.title || p.name || '未命名商品',
      productId: String(p.productId || '')
    }))
    products.value = reset ? items : products.value.concat(items)
    const currentPage = Number(data.page || page || 1)
    const pageSize = Number(data.size || 20)
    const total = Number(data.total || 0)
    hasMore.value = currentPage * pageSize < total
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '商品查询失败')
    if (reset) {
      products.value = []
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

const handleProductSearch = async (keyword: string) => {
  productKeyword.value = String(keyword || '').trim()
  await loadProductOptions(productKeyword.value)
}

const resetFilters = () => {
  selectedProduct.value = null
  productKeyword.value = ''
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
  const existingLink = item?.promotion?.link || item?.promotionLink || item?.promoteLink || item?.shortLink
  if (existingLink) {
    try {
      await navigator.clipboard.writeText(existingLink)
      message.success('推广链接已复制')
    } catch {
      message.warning('推广链接已生成，但浏览器未允许写入剪贴板，请手动复制')
    }
    return
  }
  if (promotionLoadingIds.value.has(productId)) return
  promotionLoadingIds.value = new Set(promotionLoadingIds.value).add(productId)
  try {
    const res: any = await convertActivityProductLink(activityId, productId, { scene: 'PRODUCT_LIBRARY' })
    const data = res?.data || {}
    const link = data.promoteLink || data.promotionUrl || data.shortLink
    if (!link) {
      message.warning('推广链接生成成功，但没有返回可复制的链接地址')
      return
    }
    const merged = normalizeItem({
      ...item,
      bizStatus: item?.bizStatus === 'PENDING_AUDIT' ? 'LINKED' : item?.bizStatus,
      bizStatusLabel: item?.bizStatus === 'PENDING_AUDIT' ? '已转链' : item?.bizStatusLabel,
      promotion: {
        status: 'READY',
        statusLabel: data.statusLabel || '已生成',
        link,
        generatedAt: new Date().toISOString(),
        expireAt: null,
        failReason: null
      },
      promotionLink: link,
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
    await navigator.clipboard.writeText(link)
    message.success('推广链接已复制，后续订单将归因到当前渠道')
  } catch (error: any) {
    if (error?.name === 'NotAllowedError') {
      message.warning('推广链接已生成，但浏览器未允许写入剪贴板，请手动复制')
      return
    }
    message.error(error?.response?.data?.msg || error?.message || '推广链接生成失败，请稍后重试')
  } finally {
    const next = new Set(promotionLoadingIds.value)
    next.delete(productId)
    promotionLoadingIds.value = next
  }
}

onMounted(async () => {
  try {
    await refreshProducts()
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '页面初始化失败')
  }
})
</script>

<style scoped>
.product-page {
  max-width: 100%;
  padding: var(--spacing-xl);
}

.toolbar {
  margin-bottom: var(--spacing-md);
  background: var(--bg-card);
  padding: 16px;
  border-radius: var(--radius-md);
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
</style>
