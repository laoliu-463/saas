<template>
  <div class="product-page app-page" :data-testid="isActivityProductMode ? 'activity-product-page' : 'product-manage-page'">
    <PageHeader :title="pageTitle" :description="pageDescription">
      <template #actions>
        <ProductManageToolbar
          :syncing="syncing"
          @sync-activities="handleSyncActivities"
          @sync-products="openSyncActivityProductsDialog"
        />
      </template>
    </PageHeader>

    <CurrentActivityBanner
      v-if="isProductManageProductsMode"
      :status="currentActivityContext.status"
      :activity-id="currentActivityContext.activityId"
      :activity-name="currentActivityContext.activityName"
    />

    <n-alert v-if="hasExplicitActivityRoute" type="info" class="page-alert app-page-alert">
      当前活动按“组长分配审核人、招商审核、手动入库、分配招商、渠道转链”推进。标签「审核入库后可展示」表示审核通过并入库后可在共享商品库列表看到；「审核后不可展示」表示商品自身联盟状态未达推广中等原因导致列表不可见（详见每行说明）。
      <template #action>
        <n-space>
          <n-button size="small" @click="router.push('/product/manage')">返回活动列表</n-button>
          <n-button size="small" secondary @click="router.push('/product/manage/products')">打开商品列表</n-button>
        </n-space>
      </template>
    </n-alert>

    <n-alert v-if="showSlowLoading" type="warning" class="page-alert app-page-alert">
      <template #icon>
        <n-icon><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg></n-icon>
      </template>
      查询较慢，请稍候…
    </n-alert>

    <section
      v-if="isActivityProductMode"
      class="activity-workbench"
      data-testid="activity-product-workbench"
    >
      <div class="activity-workbench-header">
        <div>
          <div class="activity-workbench-title">活动商品推进</div>
          <div class="activity-workbench-subtitle">
            {{ activityLoadSummary }}；推广中 {{ activityStats.promoting }} 个，待审核 {{ activityStats.pendingReview }} 个，未通过/终止/到期 {{ blockedUpstreamStatusCount }} 个。
          </div>
        </div>
        <div class="activity-workbench-actions">
          <n-button size="small" secondary data-testid="activity-filter-all" @click="applyActivityQuickFilter('all')">
            全部
          </n-button>
          <n-button size="small" type="success" secondary data-testid="activity-filter-promoting" @click="applyActivityQuickFilter('promoting')">
            只看推广中
          </n-button>
          <n-button size="small" type="warning" secondary data-testid="activity-filter-pending-review" @click="applyActivityQuickFilter('pendingReview')">
            只看待审核
          </n-button>
          <n-button size="small" type="error" secondary data-testid="activity-filter-rejected" @click="applyActivityQuickFilter('rejected')">
            只看未通过
          </n-button>
        </div>
      </div>
      <div class="activity-stage-row">
        <button
          v-for="stage in activityStages"
          :key="stage.key"
          type="button"
          class="activity-stage"
          :class="{ active: activeStage === stage.key }"
          :data-testid="`activity-stage-${stage.key}`"
          @click="applyActivityQuickFilter(stage.key)"
        >
          <span class="activity-stage-label">{{ stage.label }}</span>
          <strong>{{ stage.displayCount }}</strong>
          <span class="activity-stage-hint">{{ stage.hint }}</span>
        </button>
      </div>
    </section>

    <ProductStatusTabs
      :official-status="officialStatus"
      :status-counts="officialStatusCounts"
      @change-official-status="handleOfficialStatusChange"
    />

    <ProductManageFilters
      :filters="filters"
      :loading="loading"
      :show-assignee-filter="!authStore.roleCodes.includes('biz_staff') || authStore.roleCodes.includes('biz_leader') || authStore.isAdmin"
      :assigned-activity-options="assignedActivityOptions"
      :assigned-activity-options-loading="assignedActivityOptionsLoading"
      @update:filters="handleFiltersUpdate"
      @search-click="refreshProducts"
      @reset="resetFilters"
    />

    <div
      v-if="isProductManageProductsMode && resolvedActivityId"
      class="activity-result-summary"
      data-testid="activity-product-result-summary"
    >
      {{ activityLoadSummary }}
    </div>

    <ProductManageTable
      :rows="products"
      :columns="columns"
      :loading="showInitialLoading"
      :loading-more="loadingMore"
      :has-more="hasMore"
      :checked-row-keys="checkedRowKeys"
      :scroll-x="productTableScrollX"
      :empty-description="emptyDescription"
      @update:checked-row-keys="handleCheckedRowKeysUpdate"
      @load-more="loadMore"
    >
      <template #batch-toolbar>
        <div
          v-if="showBatchToolbar"
          class="batch-toolbar"
          data-testid="activity-product-batch-toolbar"
        >
          <span class="batch-toolbar-summary">已选 {{ checkedRowKeys.length }} 项</span>
          <n-space>
            <n-button
              v-if="canBatchAssign"
              size="small"
              :disabled="!checkedRowKeys.length"
              data-testid="batch-assign-button"
              @click="openBatchAssignDialog"
            >
              批量分配招商
            </n-button>
            <n-button
              v-if="canBatchLibraryEntry"
              size="small"
              :disabled="!checkedRowKeys.length"
              :loading="batchLoading === 'library'"
              data-testid="batch-library-entry-button"
              @click="handleBatchLibraryEntry"
            >
              批量加入商品库
            </n-button>
            <n-button
              v-if="canBatchPin"
              size="small"
              :disabled="!checkedRowKeys.length"
              :loading="batchLoading === 'pin'"
              data-testid="batch-pin-button"
              @click="handleBatchPin"
            >
              批量置顶
            </n-button>
            <n-button
              size="small"
              quaternary
              :disabled="!checkedRowKeys.length"
              data-testid="batch-clear-selection-button"
              @click="clearBatchSelection"
            >
              清空选择
            </n-button>
          </n-space>
        </div>
      </template>
    </ProductManageTable>

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
      :relation-id="resolveRelationId(currentRow)"
      :row="currentRow"
      @success="(payload: any) => handleActionSuccess('audit', payload)"
    />
    <ProductAssignDialog
      v-model:show="dialogs.assign"
      :product-id="currentRow?.productId ?? null"
      :activity-id="detailActivityId"
      :mode="assignDialogMode"
      @success="(payload: any) => handleActionSuccess(assignDialogMode === 'auditOwner' ? 'auditOwner' : 'assign', payload)"
    />
    <ProductBatchAssignDialog
      v-model:show="dialogs.batchAssign"
      :activity-id="resolvedActivityId || null"
      :product-ids="checkedRowKeys"
      @success="handleBatchActionSuccess"
    />
    <ProductOperationLogDrawer
      v-model:show="dialogs.logs"
      :product-id="currentRow?.productId ?? null"
      :activity-id="detailActivityId"
      :product-title="currentRow?.title"
    />
    <ProductEditModal v-model:show="dialogs.edit" :row="currentRow" @success="handleModalSuccess" />
    <CooperationSettingModal v-model:show="dialogs.cooperation" :row="currentRow" @success="handleModalSuccess" />
    <SampleSettingModal v-model:show="dialogs.sample" :row="currentRow" @success="handleModalSuccess" />
    <BatchSupplementModal v-model:show="dialogs.batchSupplement" :product-ids="checkedRowKeys" @success="handleBatchSupplementSuccess" />
    <ExtendPromotionModal v-model:show="dialogs.extendPromotion" :row="currentRow" @success="handleModalSuccess" />
    <ProductSyncActivityDialog
      v-model:show="dialogs.syncActivityProducts"
      :activity-options="assignedActivityOptions"
      :default-activity-id="filters.recruitActivityId || resolvedActivityId || null"
      :loading="assignedActivityOptionsLoading"
      :syncing="syncing"
      @refresh-options="refreshAssignedActivityOptions"
      @confirm="syncActivityProductsFromRemote"
    />
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
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import ManualCopyDialog from '../../components/common/ManualCopyDialog.vue'
import { useAuthStore } from '../../stores/auth'
import { hasAccess } from '../../constants/rbac'
import {
  batchPinActivityProducts,
  batchPutActivityProductsIntoLibrary,
  convertActivityProductLink,
  getActivityProducts,
  pinActivityProduct,
  putActivityProductIntoLibrary,
  syncActivityProducts,
  unpinActivityProduct
} from '../../api/activityProduct'
import { getColonelActivityPage } from '../../api/activity'
import { getProducts, getProductPickPage } from '../../api/product'
import { pauseProduct, resumeProduct } from '../../api/productManage'
import { loadAssignedActivityOptions } from './assigned-activity-options'
import ProductManageFilters from './components/ProductManageFilters.vue'
import ProductManageTable from './components/ProductManageTable.vue'
import ProductManageToolbar from './components/ProductManageToolbar.vue'
import ProductSyncActivityDialog from './components/ProductSyncActivityDialog.vue'
import ProductStatusTabs from './components/ProductStatusTabs.vue'
import ProductActionColumn from './components/ProductActionColumn.vue'
import {
  applyProductFilters,
  allianceStatusToUpstreamStatus,
  buildActivityProductInfoQuery,
  buildProductLibraryQueryParams,
  DEFAULT_PRODUCT_FILTERS,
  formatGmv30d,
  formatSales30d,
  type ProductFilterState
} from './product-filters'
import {
  normalizeActivityQueryId,
  isProductManageProductsPath,
  resolveActivityContextForManageProductsPath,
  shouldLoadActivityProducts
} from './product-page-data-source'
import ProductDetail from './ProductDetail.vue'
import ProductAuditDialog from './components/ProductAuditDialog.vue'
import ProductAssignDialog from './components/ProductAssignDialog.vue'
import ProductBatchAssignDialog from './components/ProductBatchAssignDialog.vue'
import ProductOperationLogDrawer from './components/ProductOperationLogDrawer.vue'
import ProductEditModal from './components/ProductEditModal.vue'
import CooperationSettingModal from './components/CooperationSettingModal.vue'
import SampleSettingModal from './components/SampleSettingModal.vue'
import BatchSupplementModal from './components/BatchSupplementModal.vue'
import ExtendPromotionModal from './components/ExtendPromotionModal.vue'
import CurrentActivityBanner from './components/CurrentActivityBanner.vue'
import {
  formatLibraryEntrySuccessMessage,
  getLibraryDisplayTags,
  mergeLibraryDisplayFields,
  resolveProductLibraryDisplay,
  resolveProductLibraryReadiness,
  shouldShowLibraryEntryAction
} from './product-library-display'
import {
  activityProductStageToAllianceStatus,
  activityProductStageToOfficialStatus,
  buildActivityProductStatusStages,
  countActivityProductStatusGroups,
  formatActivityProductLoadSummary,
  isActivityProductStageMatch,
  normalizeActivityProductStatusCounts,
  resolveActivityProductOfficialStatusView,
  type ActivityProductStatusCounts,
  type ActivityProductStatusStageKey
} from './activity-product-status-display'
import {
  formatBatchResultMessage,
  MAX_BATCH_PRODUCT_IDS,
  normalizeBatchProductIds,
  type BatchActionResult
} from './product-batch'
import { copyProductBriefWithLink } from './product-copy'
import { resolveProductRelationId } from './product-relation-id'
import { createEmptyManualCopyDialogState, resolveManualCopyDialogState } from './manual-copy'
import { useDelayedFlag } from '../../utils/delayedFlag'
import { tryCopyText } from '../../utils/clipboard'
import { POST_SYNC_REFRESH_DELAYS_MS, shouldSchedulePostSyncRefresh } from './activity-sync'
import type {
  ProductActionKey,
  ProductManageRow,
  ProductOfficialStatus
} from '../../types/productManage'

type ProductAction = 'audit' | 'assign' | 'auditOwner'
type AssignDialogMode = 'businessOwner' | 'auditOwner'

const PRODUCT_LIST_PAGE_SIZE = 5
const PRODUCT_TABLE_SCROLL_X = 1968
const PRODUCT_TABLE_SCROLL_X_WITH_SELECTION = 2010

const message = useMessage()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const syncing = ref(false)
const postSyncRefreshTimers: ReturnType<typeof window.setTimeout>[] = []
const loadingMore = ref(false)
const showSlowLoading = ref(false)
const showInitialLoading = useDelayedFlag(loading, 300)
let slowLoadingTimer: ReturnType<typeof setTimeout> | null = null
const products = ref<any[]>([])
const nextCursor = ref('')
const hasMore = ref(false)
const promotionLoadingIds = ref<Set<string>>(new Set())
const pinLoadingIds = ref<Set<string>>(new Set())
const checkedRowKeys = ref<string[]>([])
const batchLoading = ref<'library' | 'pin' | null>(null)
const assignedActivityOptions = ref<{ label: string; value: string }[]>([])
const assignedActivityOptionsLoading = ref(false)
const status = ref<string | null>(null)
const allianceStatus = ref<string | null>(null)
const officialStatus = ref<ProductOfficialStatus | null>(null)
const activeStage = ref<ActivityProductStatusStageKey>('all')
const activityQueryTotal = ref(0)
const activityStatusCounts = ref<ActivityProductStatusCounts | null>(null)
const fallbackActivityId = ref('')
const currentRow = ref<any | null>(null)
const showDetail = ref(false)
const detailRefreshKey = ref(0)
const assignDialogMode = ref<AssignDialogMode>('businessOwner')
const manualCopyDialog = ref(createEmptyManualCopyDialogState())

const dialogs = ref({
  audit: false,
  assign: false,
  logs: false,
  batchAssign: false,
  edit: false,
  cooperation: false,
  sample: false,
  batchSupplement: false,
  extendPromotion: false,
  syncActivityProducts: false
})

const filters = ref<ProductFilterState>(DEFAULT_PRODUCT_FILTERS())

const forcedStatusMap: Record<ProductAction, string> = {
  audit: 'APPROVED',
  assign: 'ASSIGNED',
  auditOwner: 'PENDING_AUDIT'
}

const canDo = (action: string) => {
  const roles = authStore.roleCodes
  if (roles.includes('admin')) return true
  if (action === 'audit') return hasAccess(roles, ['biz_staff'])
  if (action === 'assign') return hasAccess(roles, ['biz_leader'])
  if (action === 'auditOwner') return hasAccess(roles, ['biz_leader'])
  if (action === 'promotion') return hasAccess(roles, ['channel_leader', 'channel_staff'])
  if (action === 'pin') return hasAccess(roles, ['biz_leader', 'biz_staff'])
  if (action === 'libraryEntry') return false
  if (action === 'decision') return false
  return true
}

const routeQueryActivityId = computed(() =>
  normalizeActivityQueryId(route.query.activityId || route.query.recruitActivityId)
)
const resolvedActivityId = computed(() =>
  String(route.params.activityId || filters.value.recruitActivityId || routeQueryActivityId.value || fallbackActivityId.value || '')
)
const hasExplicitActivityRoute = computed(() => Boolean(route.params.activityId))
const isSharedLibraryMode = computed(() => route.path === '/product')
const isActivityProductMode = computed(() => hasExplicitActivityRoute.value)
const isProductManageProductsMode = computed(() => isProductManageProductsPath(route.path))
const isPickLibraryMode = computed(() => isProductManageProductsMode.value || (!isSharedLibraryMode.value && !isActivityProductMode.value))

// [PRODUCT-FIX-001] 解析当前活动上下文（替代旧 fallback 逻辑）
const currentActivityContext = computed(() =>
  resolveActivityContextForManageProductsPath({
    routePath: route.path,
    queryActivityId: routeQueryActivityId.value,
    assignedOptions: assignedActivityOptions.value,
    isAdmin: Boolean(authStore.isAdmin),
    isOptionsLoading: assignedActivityOptionsLoading.value
  })
)
const isActivityContextBlocked = computed(
  () => isProductManageProductsMode.value
    && currentActivityContext.value.status !== 'ready'
    && currentActivityContext.value.status !== 'loading'
)
const isBizLeader = computed(() => authStore.roleCodes.includes('biz_leader') || authStore.isAdmin)
const isBizStaffOnly = computed(() => authStore.roleCodes.includes('biz_staff') && !isBizLeader.value)
const showBatchSelection = computed(() => !isSharedLibraryMode.value)
const productTableScrollX = computed(() =>
  showBatchSelection.value ? PRODUCT_TABLE_SCROLL_X_WITH_SELECTION : PRODUCT_TABLE_SCROLL_X
)

const syncRouteActivityIdToFilters = () => {
  const activityId = routeQueryActivityId.value
  if (!isProductManageProductsMode.value || !activityId) return
  fallbackActivityId.value = activityId
  if (filters.value.recruitActivityId === activityId && filters.value.activityId === activityId) return
  filters.value = {
    ...filters.value,
    recruitActivityId: activityId,
    activityId
  }
}
const showBatchToolbar = computed(() => showBatchSelection.value && (canBatchAssign.value || canBatchLibraryEntry.value || canBatchPin.value))
const canBatchAssign = computed(() => canDo('assign'))
const canBatchLibraryEntry = computed(() => hasAccess(authStore.roleCodes, ['biz_staff']))
const canBatchPin = computed(() => canDo('pin'))
const selectedBatchProductIds = computed(() => normalizeBatchProductIds(checkedRowKeys.value))

const pageTitle = computed(() => {
  if (isActivityProductMode.value) return '活动商品推进'
  if (isProductManageProductsMode.value) return isBizStaffOnly.value ? '我负责的活动商品' : '活动商品列表'
  return '商品库'
})

const pageDescription = computed(() => {
  if (isActivityProductMode.value) return '围绕当前活动处理审核人分配、商品审核、入库分配与协作推进。'
  if (isProductManageProductsMode.value) {
    return isBizStaffOnly.value
      ? '按活动查看我负责的实际同步商品，补全推广资料并完成审核。'
      : '按活动查看实际同步商品，给待审核商品分配审核人，并在入库后分配招商组长。'
  }
  if (isSharedLibraryMode.value) return '沉淀完成的共享商品库，对全员可见，可直接复用历史商品结果。'
  return '支持候选商品浏览、审核、转链和入库沉淀，作为商品协同的统一工作台。'
})

const emptyDescription = computed(() => {
  if (isSharedLibraryMode.value) return '当前还没有加入商品库的商品，可先在商品管理里进入活动并将商品沉淀到商品库。'
  if (isActivityProductMode.value) return '当前活动暂无商品数据，可先同步活动商品，或返回活动列表切换活动。'
  return isBizStaffOnly.value
    ? '当前活动暂无我负责的实际商品，如有疑问请联系组长分配或切换活动。'
    : '当前活动暂无实际商品，可进入活动列表同步或切换活动。'
})

const activityStats = computed(() =>
  activityStatusCounts.value || countActivityProductStatusGroups(products.value)
)

const officialStatusCounts = computed(() => ({
  PENDING_REVIEW: activityStats.value.pendingReview,
  PROMOTING: activityStats.value.promoting,
  REJECTED: activityStats.value.rejected,
  TERMINATED: activityStats.value.terminated,
  EXPIRED: activityStats.value.expired
}))

const activityLoadSummary = computed(() =>
  formatActivityProductLoadSummary(products.value.length, activityQueryTotal.value)
)

const blockedUpstreamStatusCount = computed(() =>
  activityStats.value.rejected + activityStats.value.terminated + activityStats.value.expired
)

const activityStages = computed(() => buildActivityProductStatusStages(activityStats.value))

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

const convertLinkForBriefCopy = (
  activityId: string | number,
  productId: string | number,
  data: { scene: 'PRODUCT_LIBRARY' | 'PRODUCT_DETAIL' | 'TALENT_SHARE' | 'SAMPLE_DESK' }
) => convertActivityProductLink(activityId, productId, data, { suppressErrorNotice: true })

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
  syncRouteActivityIdToFilters()
  const selectedRecruitActivityId = normalizeText(filters.value.recruitActivityId)
  if (selectedRecruitActivityId) {
    fallbackActivityId.value = selectedRecruitActivityId
    return selectedRecruitActivityId
  }
  // [PRODUCT-FIX-001] /product/manage/products 路径不再 fallback 到 assigned[0]。
  // 移除以下整段:const firstAssignedActivity = assignedActivityOptions.value[0]?.value
  // + if (isProductManageProductsMode.value && firstAssignedActivity) { ... }
  // 改由 resolveActivityContextForManageProductsPath + CurrentActivityBanner 显式提示。
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
      const activityId = String(activity.activityId)
      fallbackActivityId.value = activityId
      if (isProductManageProductsMode.value) {
        filters.value = {
          ...filters.value,
          recruitActivityId: activityId,
          activityId,
          recruitActivityName: normalizeText(activity.activityName || activity.name) || null
        }
      }
      return fallbackActivityId.value
    }
  } catch {
    // Token 未配置时保持空态或走本地候选数据兜底
  }
  return ''
}

const normalizeItem = (item: any) => mergeLibraryDisplayFields({
  ...item,
  cover: resolveProductImage(item),
  bizStatus: item?.bizStatus || 'PENDING_AUDIT',
  bizStatusLabel: item?.bizStatusLabel || getStatusLabel(item?.bizStatus),
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

const applyFilters = (items: any[]) => {
  let result = applyProductFilters(items, filters.value, status.value)
  if (activeStage.value !== 'all') {
    result = result.filter((item) => isActivityProductStageMatch(item, activeStage.value))
  }
  return result
}

const buildActivityProductsQuery = (reset: boolean, forceRemote: boolean) => ({
  count: PRODUCT_LIST_PAGE_SIZE,
  cursor: reset ? undefined : nextCursor.value,
  productInfo: buildActivityProductInfoQuery(
    filters.value.productId,
    filters.value.productName || '',
    filters.value.shopKeyword
  ),
  bizStatus: status.value || undefined,
  status: filters.value.allianceStatus
    ? allianceStatusToUpstreamStatus[filters.value.allianceStatus]
    : undefined,
  goodsTags: filters.value.goodsTags?.length ? filters.value.goodsTags.join(',') : undefined,
  productTags: filters.value.productTags?.length ? filters.value.productTags.join(',') : undefined,
  publishStatus: filters.value.publishStatus === 'ALL' ? undefined : filters.value.publishStatus || undefined,
  retrieveMode: 1,
  refresh: forceRemote || undefined
})

const applyActivityProductsPage = (data: any, reset: boolean) => {
  const items = applyFilters((Array.isArray(data.items) ? data.items : []).map(normalizeItem))
  products.value = reset ? items : products.value.concat(items)
  activityQueryTotal.value = Number(data.total || 0)
  if (data.statusCounts) {
    activityStatusCounts.value = normalizeActivityProductStatusCounts(data.statusCounts)
  } else if (reset) {
    activityStatusCounts.value = null
  }
  nextCursor.value = String(data.nextCursor || '')
  hasMore.value = Boolean(data.hasMore || data.nextCursor)
}

const refreshAssignedActivityOptions = async () => {
  assignedActivityOptionsLoading.value = true
  try {
    assignedActivityOptions.value = await loadAssignedActivityOptions(authStore.isAdmin)
  } catch {
    assignedActivityOptions.value = []
  } finally {
    assignedActivityOptionsLoading.value = false
  }
}

const fetchProducts = async (reset: boolean, forceRemote = false, overrideActivityId?: string): Promise<boolean> => {
  if (slowLoadingTimer) { clearTimeout(slowLoadingTimer); slowLoadingTimer = null }
  showSlowLoading.value = false
  if (reset) loading.value = true
  else loadingMore.value = true
  // Show "查询较慢" hint after 10s if still loading
  slowLoadingTimer = setTimeout(() => {
    if (loading.value || loadingMore.value) showSlowLoading.value = true
  }, 10_000)

  try {
    const selectedActivityId = normalizeText(overrideActivityId)
    if (selectedActivityId) {
      const res: any = await getActivityProducts(selectedActivityId, buildActivityProductsQuery(reset, forceRemote))
      applyActivityProductsPage(res?.data || {}, reset)
      return true
    }

    if (isSharedLibraryMode.value) {
      const page = reset ? 1 : Math.floor(products.value.length / PRODUCT_LIST_PAGE_SIZE) + 1
      const res: any = await getProducts(buildProductLibraryQueryParams(filters.value, {
        page,
        size: PRODUCT_LIST_PAGE_SIZE,
        keyword: filters.value.productId || filters.value.productName || undefined,
        productIdMode: 'keyword'
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
      const pageSize = Number(data.size || PRODUCT_LIST_PAGE_SIZE)
      const total = Number(data.total || 0)
      hasMore.value = currentPage * pageSize < total
      nextCursor.value = ''
      return true
    }

    const pickActivityId = normalizeText(filters.value.recruitActivityId)
    if (isPickLibraryMode.value && pickActivityId) {
      const res: any = await getActivityProducts(pickActivityId, buildActivityProductsQuery(reset, forceRemote))
      applyActivityProductsPage(res?.data || {}, reset)
      return true
    }

    const activityId = selectedActivityId || await ensureActivityId()
    if (shouldLoadActivityProducts(route.path, hasExplicitActivityRoute.value) && activityId) {
      // [PRODUCT-FIX-001] 显式禁止 /product/manage/products 在 forbidden/empty 状态下发请求
      if (isProductManageProductsMode.value && isActivityContextBlocked.value) {
        products.value = reset ? [] : products.value
        activityQueryTotal.value = 0
        activityStatusCounts.value = null
        hasMore.value = false
        nextCursor.value = ''
        return true
      }
      const res: any = await getActivityProducts(activityId, buildActivityProductsQuery(reset, forceRemote))
      applyActivityProductsPage(res?.data || {}, reset)
      return true
    }

    // 商品管理页必须按活动商品实际数据展示；没有可解析活动时只展示空态，不回退商品库口径。
    if (isPickLibraryMode.value) {
      products.value = reset ? [] : products.value
      activityQueryTotal.value = 0
      activityStatusCounts.value = null
      hasMore.value = false
      nextCursor.value = ''
      return true
    }

    const page = reset ? 1 : Math.floor(products.value.length / PRODUCT_LIST_PAGE_SIZE) + 1
    const res: any = await getProductPickPage({ page, size: PRODUCT_LIST_PAGE_SIZE })
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
    const pageSize = Number(data.size || PRODUCT_LIST_PAGE_SIZE)
    const total = Number(data.total || 0)
    hasMore.value = currentPage * pageSize < total
    nextCursor.value = ''
    return true
  } catch (error: any) {
    if (hasExplicitActivityRoute.value && !forceRemote) {
      products.value = []
      activityQueryTotal.value = 0
      activityStatusCounts.value = null
      nextCursor.value = ''
      hasMore.value = false
    } else {
      notifyApiFailure(error, message, { fallbackMessage: '商品查询失败' })
      if (reset) {
        products.value = []
        activityQueryTotal.value = 0
        activityStatusCounts.value = null
        nextCursor.value = ''
        hasMore.value = false
      }
    }
    return false
  } finally {
    if (slowLoadingTimer) { clearTimeout(slowLoadingTimer); slowLoadingTimer = null }
    showSlowLoading.value = false
    loading.value = false
    loadingMore.value = false
  }
}

const handleFiltersUpdate = (value: typeof filters.value) => {
  filters.value = value
  activeStage.value = 'all'
}

const officialStatusToAllianceStatus: Record<ProductOfficialStatus, string> = {
  PENDING_REVIEW: 'pending_audit',
  PROMOTING: 'promoting',
  REJECTED: 'rejected',
  TERMINATED: 'terminated',
  EXPIRED: 'expired'
}

const officialStatusToActivityStage: Record<ProductOfficialStatus, ActivityProductStatusStageKey> = {
  PENDING_REVIEW: 'pendingReview',
  PROMOTING: 'promoting',
  REJECTED: 'rejected',
  TERMINATED: 'terminated',
  EXPIRED: 'expired'
}

const handleOfficialStatusChange = (value: ProductOfficialStatus | null) => {
  officialStatus.value = value
  allianceStatus.value = value ? officialStatusToAllianceStatus[value] : null
  filters.value = { ...filters.value, allianceStatus: allianceStatus.value }
  // 切官方状态时清空业务状态 stage,避免"联盟推广状态=promoting AND bizStatus=PENDING_AUDIT"等
  // 隐式 AND 死集;同时把 activeStage 复位为 all。
  if (value !== null) {
    status.value = null
  }
  activeStage.value = value ? officialStatusToActivityStage[value] : 'all'
  refreshProducts()
}

/**
 * 活动快速筛选(stage 按钮 / 阶段 chip)互斥重置:
 * 1. 切 stage 时清 `filters.allianceStatus` 与 `officialStatus.value`,避免官方状态 tab 的选择叠加成"业务状态+联盟状态"两条件相交导致空集;
 * 2. 同时清空 `categories` / `systemTag` / `commission` / `salesRange` / `promotionLink` / `goodsTags` /
 *    `productTags` / `publishStatus` / `hasSample` / `freeSample` / `listed` / `published` /
 *    `supportsAds` / `merchantStatus` / `categoryName` / `serviceFee` / `cooperationType` / `productMechanism` /
 *    `materialDownload` / `exclusivePrice` / `productChain` / `handCard` / `doubleCommission` /
 *    `notInLibrary` / `dedup` / `supplemented` / 时间区间 / 招商/活动 ID 等,以保证 stage 表达的是
 *    "按业务状态 + 招商归属"独立语义,不会被其他窄化条件 AND 死集;
 * 3. 保留 `productId` / `productName` / `shopKeyword` / `colonelName` 等用户已显式输入的查询字段(其
 *    语义是定位,与 stage 正交)。
 *
 * See also: `handleOfficialStatusChange`(切官方状态时反向清 `status.value`)。
 */
function resetStageExclusiveFilters() {
  allianceStatus.value = null
  officialStatus.value = null
  filters.value = {
    ...filters.value,
    assignee: null,
    allianceStatus: null,
    categoryName: null,
    systemTag: null,
    commission: null,
    serviceFee: null,
    salesRange: null,
    promotionLink: null,
    hasSample: null,
    publishStatus: null,
    goodsTags: [],
    productTags: [],
    categories: [],
    merchantStatus: null,
    cooperationType: null,
    productMechanism: null,
    supportsAds: null,
    published: null,
    listed: null,
    freeSample: null,
    materialDownload: false,
    exclusivePrice: false,
    productChain: false,
    handCard: false,
    doubleCommission: false,
    notInLibrary: false,
    dedup: false,
    supplemented: null,
    updatedTimeRange: null,
    syncTimeRange: null,
    livePriceMin: null,
    livePriceMax: null,
    stockMin: null,
    stockMax: null,
    commissionMin: null,
    commissionMax: null,
    sampleSalesMin: null,
    sampleSalesMax: null
  }
}

const applyActivityQuickFilter = (stage: ActivityProductStatusStageKey) => {
  activeStage.value = stage
  status.value = null
  resetStageExclusiveFilters()
  const nextAllianceStatus = activityProductStageToAllianceStatus(stage)
  allianceStatus.value = nextAllianceStatus
  officialStatus.value = activityProductStageToOfficialStatus(stage)
  filters.value = { ...filters.value, allianceStatus: nextAllianceStatus }
  refreshProducts()
}

const resetFilters = () => {
  status.value = null
  allianceStatus.value = null
  officialStatus.value = null
  activeStage.value = 'all'
  filters.value = DEFAULT_PRODUCT_FILTERS()
  refreshProducts()
}

const refreshProducts = async () => {
  clearBatchSelection()
  await fetchProducts(true)
}

const clearPostSyncRefreshTimers = () => {
  postSyncRefreshTimers.splice(0).forEach((timer) => window.clearTimeout(timer))
}

const refreshProductsAfterActivitySync = async (activityId: string) => {
  if (normalizeText(fallbackActivityId.value) !== activityId) return
  await refreshProducts()
}

const schedulePostSyncRefreshes = (activityId: string, syncStatus?: string) => {
  clearPostSyncRefreshTimers()
  if (!shouldSchedulePostSyncRefresh(syncStatus)) return
  POST_SYNC_REFRESH_DELAYS_MS.forEach((delay) => {
    const timer = window.setTimeout(() => {
      void refreshProductsAfterActivitySync(activityId)
    }, delay)
    postSyncRefreshTimers.push(timer)
  })
}

const openSyncActivityProductsDialog = () => {
  dialogs.value.syncActivityProducts = true
  if (!assignedActivityOptions.value.length && !assignedActivityOptionsLoading.value) {
    void refreshAssignedActivityOptions()
  }
}

const syncActivityProductsFromRemote = async (activityId: string) => {
  const selectedActivityId = normalizeText(activityId)
  if (!selectedActivityId) {
    message.warning('缺少活动 ID，暂时无法同步活动商品')
    return
  }
  // 三联赋值:recruitActivityId / activityId(供 T2 商品库联动) / recruitActivityName
  // name 从 assignedActivityOptions 的 label 解析,label 形如 `${name} (${value})`
  const matchedOption = assignedActivityOptions.value.find(
    (option) => option.value === selectedActivityId
  )
  const matchedName = matchedOption
    ? matchedOption.label.replace(/\s*\([^)]*\)\s*$/, '').trim()
    : ''
  filters.value = {
    ...filters.value,
    recruitActivityId: selectedActivityId,
    activityId: selectedActivityId,
    recruitActivityName: matchedName || null
  }
  fallbackActivityId.value = selectedActivityId
  clearBatchSelection()
  syncing.value = true
  try {
    const res: any = await syncActivityProducts(selectedActivityId)
    const data = res?.data || {}
    dialogs.value.syncActivityProducts = false
    if (data.syncStatus === 'RUNNING') {
      message.info('商品同步已在后台执行，正在自动刷新列表')
    } else {
      message.success('商品同步已提交，正在自动刷新列表')
    }
    await refreshProductsAfterActivitySync(selectedActivityId)
    schedulePostSyncRefreshes(selectedActivityId, data.syncStatus)
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '发起商品同步失败' })
  } finally {
    syncing.value = false
  }
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

const openAssignDialog = (row: any, mode: AssignDialogMode) => {
  assignDialogMode.value = mode
  openDialog('assign', row)
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
  if (payload.action === 'auditOwner') {
    openAssignDialog(payload.row, 'auditOwner')
    return
  }
  if (payload.action === 'assign') {
    openAssignDialog(payload.row, 'businessOwner')
    return
  }
  openDialog(payload.action as keyof typeof dialogs.value, payload.row)
}

const resolveRelationId = (row: ProductManageRow | null) => resolveProductRelationId(row)

const handleModalSuccess = async () => {
  detailRefreshKey.value += 1
  await refreshProducts()
}

const handleBatchSupplementSuccess = async () => {
  clearBatchSelection()
  await handleModalSuccess()
}

const handleSyncActivities = () => {
  router.push('/product/manage')
}

const copyScriptOnly = async (row: ProductManageRow) => {
  const script = normalizeText(row.promotionMaterialPack?.outreachScript as string | number | null | undefined) ||
    normalizeText(row.auditSupplement?.promotionScript as string | number | null | undefined) ||
    normalizeText(row.productName || row.title || row.name)
  if (!script) {
    message.warning('当前商品暂无可复制话术')
    return
  }
  try {
    await navigator.clipboard.writeText(script)
    message.success('推广话术已复制')
  } catch {
    message.warning('浏览器未允许写入剪贴板，请手动复制')
  }
}

const handlePauseOrResume = async (row: ProductManageRow, paused: boolean) => {
  const id = resolveRelationId(row)
  if (!id) {
    message.warning('缺少有效商品关系 ID，暂时无法操作发布状态')
    return
  }
  try {
    if (paused) {
      await pauseProduct(id)
      message.success('商品已暂停发布')
    } else {
      await resumeProduct(id)
      message.success('商品已恢复发布')
    }
    await refreshProducts()
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: paused ? '暂停发布失败' : '恢复发布失败' })
  }
}

const handleProductAction = async (payload: { action: ProductActionKey; row: ProductManageRow }) => {
  const row = payload.row
  if (payload.action === 'approve' || payload.action === 'reject') {
    openDialog('audit', row)
    return
  }
  if (payload.action === 'detail') {
    openDetail(row)
    return
  }
  if (payload.action === 'pause') {
    await handlePauseOrResume(row, true)
    return
  }
  if (payload.action === 'resume') {
    await handlePauseOrResume(row, false)
    return
  }
  if (payload.action === 'edit') {
    openDialog('edit', row)
    return
  }
  if (payload.action === 'cooperationSetting') {
    openDialog('cooperation', row)
    return
  }
  if (payload.action === 'sampleSetting') {
    openDialog('sample', row)
    return
  }
  if (payload.action === 'copyScript') {
    await copyScriptOnly(row)
    return
  }
  if (payload.action === 'copyLink') {
    await copyPromotionLink(row)
    return
  }
  if (payload.action === 'downloadHandCard') {
    const url = normalizeText(row.handCardUrl)
    if (!url) {
      message.warning('当前商品暂无手卡素材')
      return
    }
    window.open(url, '_blank', 'noopener,noreferrer')
    return
  }
  if (payload.action === 'assign') {
    openAssignDialog(row, 'businessOwner')
    return
  }
  if (payload.action === 'extendPromotion') {
    openDialog('extendPromotion', row)
    return
  }
  if (payload.action === 'viewOrders') {
    router.push({
      path: '/orders',
      query: {
        productId: String(row.productId || ''),
        activityId: String(row.sourceActivityId || row.activityId || '')
      }
    })
    return
  }
  if (payload.action === 'openBaiying') {
    const url = normalizeText(row.baiyingUrl || row.detailUrl)
    if (!url) {
      message.warning('当前商品暂无百应链接')
      return
    }
    window.open(url, '_blank', 'noopener,noreferrer')
    return
  }
  if (payload.action === 'pin' || payload.action === 'unpin') {
    await handlePinProduct(row, payload.action === 'pin')
  }
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
      sourceActivityId: activityId
    })
    products.value = products.value.map((row: any) => (String(row.productId) === productId ? { ...row, ...merged } : row))
    if (String(currentRow.value?.productId || '') === productId) {
      currentRow.value = { ...currentRow.value, ...merged }
    }
    detailRefreshKey.value += 1
    const entryDisplay = resolveProductLibraryDisplay(merged)
    message[entryDisplay.libraryVisible ? 'success' : 'warning'](formatLibraryEntrySuccessMessage(merged))
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加入商品库失败，请稍后重试' })
  }
}

const updatePinnedState = (item: any, pinned: boolean, pinnedUntil?: string | null) => {
  const productId = String(item?.productId || '')
  const merged = normalizeItem({
    ...item,
    pinned,
    pinnedUntil: pinned ? pinnedUntil || item?.pinnedUntil || null : null
  })
  products.value = products.value.map((row: any) => (String(row.productId) === productId ? { ...row, ...merged } : row))
  if (String(currentRow.value?.productId || '') === productId) {
    currentRow.value = { ...currentRow.value, ...merged }
  }
}

const handlePinProduct = async (item: any, pinned: boolean) => {
  const productId = String(item?.productId || '')
  const activityId = resolveRowActivityId(item)
  if (!productId || !activityId) {
    message.warning('缺少来源活动信息，暂时无法置顶')
    return
  }
  if (pinLoadingIds.value.has(productId)) return
  pinLoadingIds.value = new Set(pinLoadingIds.value).add(productId)
  try {
    const res: any = pinned
      ? await pinActivityProduct(activityId, productId)
      : await unpinActivityProduct(activityId, productId)
    updatePinnedState(item, Boolean(res?.data?.pinned ?? pinned), res?.data?.pinnedUntil || null)
    detailRefreshKey.value += 1
    await refreshProducts()
    message.success(pinned ? '商品已置顶 24 小时' : '已取消置顶')
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: pinned ? '置顶失败，请稍后重试' : '取消置顶失败，请稍后重试' })
  } finally {
    const next = new Set(pinLoadingIds.value)
    next.delete(productId)
    pinLoadingIds.value = next
  }
}
void handlePinProduct

const handleCheckedRowKeysUpdate = (keys: Array<string | number>) => {
  checkedRowKeys.value = keys.map((key) => String(key))
}

const clearBatchSelection = () => {
  checkedRowKeys.value = []
}

const resolveBatchContext = (): { activityId: string; productIds: string[] } | null => {
  const productIds = selectedBatchProductIds.value
  if (!productIds.length) {
    message.warning('请先选择商品')
    return null
  }
  const activityId = resolvedActivityId.value
  if (!activityId) {
    message.warning('缺少活动 ID，暂不可批量操作')
    return null
  }
  if (checkedRowKeys.value.length > MAX_BATCH_PRODUCT_IDS) {
    message.warning(`单次最多处理 ${MAX_BATCH_PRODUCT_IDS} 个商品，已自动截断前 ${MAX_BATCH_PRODUCT_IDS} 项`)
  }
  return { activityId, productIds }
}

const openBatchAssignDialog = () => {
  if (!resolveBatchContext()) return
  dialogs.value.batchAssign = true
}

const handleBatchActionSuccess = async (_payload: BatchActionResult) => {
  clearBatchSelection()
  await refreshProducts()
}

const handleBatchLibraryEntry = async () => {
  const context = resolveBatchContext()
  if (!context) return
  batchLoading.value = 'library'
  try {
    const res: any = await batchPutActivityProductsIntoLibrary(context.activityId, {
      productIds: context.productIds
    })
    message.success(formatBatchResultMessage((res?.data || {}) as BatchActionResult, '批量加入商品库'))
    clearBatchSelection()
    await refreshProducts()
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '批量加入商品库失败' })
  } finally {
    batchLoading.value = null
  }
}

const handleBatchPin = async () => {
  const context = resolveBatchContext()
  if (!context) return
  batchLoading.value = 'pin'
  try {
    const res: any = await batchPinActivityProducts(context.activityId, {
      productIds: context.productIds
    })
    message.success(formatBatchResultMessage((res?.data || {}) as BatchActionResult, '批量置顶'))
    clearBatchSelection()
    await refreshProducts()
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '批量置顶失败' })
  } finally {
    batchLoading.value = null
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
  promotionLoadingIds.value = new Set(promotionLoadingIds.value).add(productId)
  try {
    const result = await copyProductBriefWithLink({
      item,
      activityId,
      productId,
      scene: 'PRODUCT_LIBRARY',
      convertLink: convertLinkForBriefCopy,
      writeText: async (text: string) => tryCopyText(text)
    })

    if (result.link && result.responseData) {
      const data = result.responseData
      const merged = normalizeItem({
        ...item,
        bizStatus: 'LINKED',
        bizStatusLabel: getStatusLabel('LINKED'),
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
      products.value = products.value.map((row: any) => (String(row.productId) === productId ? { ...row, ...merged } : row))
      if (String(currentRow.value?.productId || '') === productId) {
        currentRow.value = { ...currentRow.value, ...merged }
      }
      detailRefreshKey.value += 1
    }

    const manualState = resolveManualCopyDialogState(result, item)
    if (manualState) {
      manualCopyDialog.value = manualState
      if (manualState.reason === 'PROMOTION_LINK_FAILED') {
        detailRefreshKey.value += 1
        message.warning('真实推广链接未生成，请前往百应手动生成')
      } else if (manualState.reason === 'PROMOTION_LINK_MISSING_PICK_SOURCE') {
        message.warning('推广链接已生成，但无法确认 pick_source，请手动核对')
      } else {
        message.warning('讲解已生成，但浏览器未允许写入剪贴板，请手动复制')
      }
    } else if (result.linkGenerationFailed) {
      detailRefreshKey.value += 1
      message.error('短链生成失败，已复制讲解（不含短链）')
    } else {
      message.success('讲解 + 短链已复制')
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

const renderTextAction = (label: string, onClick?: (event: MouseEvent) => void, muted = false, testId?: string) =>
  h(
    NButton,
    {
      text: true,
      size: 'small',
      type: muted ? 'default' : 'primary',
      class: ['table-link-action-button', muted ? 'is-muted' : ''],
      'data-testid': testId,
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

const formatPinnedUntil = (value?: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const renderProductInfo = (row: any) =>
  h('div', { class: 'table-product' }, [
    h(
      'div',
      { class: 'table-product-cover' },
      [
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
          : h('div', { class: 'table-product-fallback' }, '暂无图片'),
        row.pinned
          ? h('span', { class: 'table-product-pin-badge', 'data-testid': 'product-pinned-badge' }, '置顶')
          : null
      ]
    ),
    h('div', { class: 'table-product-body' }, [
      h('div', { class: 'table-product-title-row' }, [
        h('div', { class: 'table-product-title' }, row.title || row.name || '-'),
        row.pinned ? h('span', { class: 'table-inline-badge table-inline-badge-pin' }, '置顶') : null
      ]),
      row.pinned
        ? h(
            'div',
            { class: 'table-product-meta table-product-pin-meta' },
            row.pinnedUntil ? `置顶至 ${formatPinnedUntil(row.pinnedUntil)}` : '当前商品已置顶'
          )
        : null,
      h('div', { class: 'table-product-meta' }, `商品ID：${row.productId || '-'}`),
      h('div', { class: 'table-product-meta' }, `店铺：${row.shopName || '未识别店铺'}`),
      h('div', { class: 'table-product-meta' }, `类目：${row.categoryName || '-'}`),
      h('div', { class: 'table-product-meta' }, `售价：${row.priceText || '-'}    库存：${normalizeText(row.productStock) || normalizeText(row.stockText) || '-'}`),
      h('div', { class: 'table-product-meta' }, `来源类型：${normalizeText(row.sourceTypeLabel || row.sourceTypeName || row.sourceType) || '团长活动'}`)
    ])
  ])

const renderMetrics = (row: any) => {
  const readiness = resolveProductLibraryReadiness(row)
  const upstreamStatus = resolveActivityProductOfficialStatusView(row)
  const upstreamStatusText = normalizeText(row.statusText || row.allianceStatusText)
  const lines = [
    `近30天销量：${formatSales30d(row)}`,
    `近30天 GMV：${formatGmv30d(row)}`,
    `上游状态：${upstreamStatusText && upstreamStatusText !== upstreamStatus.label ? `${upstreamStatus.label}（${upstreamStatusText}）` : upstreamStatus.label}`,
    isSharedLibraryMode.value
      ? `商品库：${readiness.label}`
      : `本地进度：${row.bizStatusLabel || getStatusLabel(row.bizStatus)}`
  ]
  if (row.libraryStatusHint) {
    lines.push(`说明：${row.libraryStatusHint}`)
  }
  lines.push(`转链：${normalizeText(row.promotion?.statusLabel || row.promotionLinkStatusLabel) || '未生成'}`)
  return renderLineList(lines, 'table-stack compact')
}

const renderTagList = (row: any) => {
  const firstLabel = Array.isArray(row.systemTags) && row.systemTags.length
    ? row.systemTags[0]
    : Array.isArray(row.alertTags) && row.alertTags.length
      ? row.alertTags[0]
      : '暂无标签'
  const secondaryLabel = normalizeText(row.latestDecisionLabel)
  const libraryDisplay = resolveProductLibraryDisplay(row)
  const readiness = resolveProductLibraryReadiness(row)
  const upstreamStatus = resolveActivityProductOfficialStatusView(row)
  const libraryTags = isSharedLibraryMode.value
    ? getLibraryDisplayTags(row, { manageMode: true })
    : []
  const canShowLibraryEntry = shouldShowLibraryEntryAction(row, canDo('audit'))
  return h(
    'div',
    { class: 'table-tag-block' },
    [
      !isSharedLibraryMode.value
        ? h(
            'div',
            {
              class: [
                'table-stack-line',
                'upstream-status-tag',
                `upstream-status-tag-${upstreamStatus.tagType}`
              ],
              'data-testid': 'activity-product-upstream-status'
            },
            upstreamStatus.label
          )
        : null,
      h('div', { class: 'table-stack-line' }, firstLabel),
      secondaryLabel ? h('div', { class: 'table-stack-line muted' }, secondaryLabel) : null,
      !isSharedLibraryMode.value
        ? h('div', { class: 'table-stack-line muted' }, row.bizStatusLabel || getStatusLabel(row.bizStatus))
        : null,
      ...libraryTags.map((tag) =>
        h(
          'div',
          {
            class: ['table-stack-line', 'library-status-tag', tag.type === 'warning' ? 'library-status-tag-warning' : ''].filter(Boolean).join(' ')
          },
          tag.text
        )
      ),
      row.selectedToLibrary
        ? renderTextAction(
            libraryDisplay.entryLabel,
            undefined,
            !libraryDisplay.hiddenFromList
          )
        : canShowLibraryEntry
          ? renderTextAction('加入商品库', () => handlePutIntoLibrary(row))
          : readiness.code === 'READY_AFTER_ENTRY'
            ? null
            : readiness.code === 'BLOCKED_AFTER_ENTRY' || readiness.code === 'PENDING_AUDIT'
              ? null
              : null,
      row.libraryStatusHint
        ? h('div', { class: 'table-stack-line muted library-status-hint', 'data-testid': 'product-library-status-hint' }, row.libraryStatusHint)
        : row.libraryHiddenFromList
          ? h('div', { class: 'table-stack-line muted' }, libraryDisplay.listVisibilityLabel)
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
    h(
      'div',
      { class: 'table-stack-line strong' },
      row.bizStatus === 'PENDING_AUDIT'
        ? (row.assigneeName ? `审核人：${row.assigneeName}` : '待分配审核人')
        : (row.assigneeName ? `招商组长：${row.assigneeName}` : '未分配招商组长')
    ),
    h('div', { class: 'table-stack-line muted' }, row.bizStatusLabel || getStatusLabel(row.bizStatus)),
    row.selectedToLibrary && ['APPROVED', 'BOUND'].includes(String(row.bizStatus || '')) && !row.assigneeName
      ? h('div', { class: 'table-stack-line action-needed' }, '待组长分配')
      : null
  ])

const renderActions = (row: any) =>
  h(ProductActionColumn, {
    row,
    roles: authStore.roleCodes,
    isAdmin: authStore.isAdmin,
    canPin: Boolean(row.selectedToLibrary) && canDo('pin'),
    onAction: handleProductAction
  })

const columns = computed(() => [
  ...(showBatchSelection.value
    ? [{
        type: 'selection' as const,
        width: 42,
        multiple: true
      }]
    : []),
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
    title: '经营指标',
    key: 'metrics',
    width: 168,
    render: (row: any) => renderMetrics(row)
  },
  {
    title: '招商组长/审核人',
    key: 'progress',
    width: 160,
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
    width: 160,
    render: (row: any) => renderActions(row)
  }
])

onMounted(async () => {
  try {
    syncRouteActivityIdToFilters()
    await Promise.all([refreshAssignedActivityOptions(), refreshProducts()])
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '页面初始化失败' })
  }
})

onBeforeUnmount(() => {
  clearPostSyncRefreshTimers()
})

watch(
  () => [route.path, route.params.activityId, route.query.activityId, route.query.recruitActivityId],
  async () => {
    nextCursor.value = ''
    syncRouteActivityIdToFilters()
    await refreshProducts()
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

.activity-result-summary {
  margin: 0 0 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

.activity-workbench {
  margin-bottom: 16px;
  padding: 16px;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 6px;
}

.activity-workbench-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.activity-workbench-title {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
}

.activity-workbench-subtitle {
  color: #6b7280;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 2px;
}

.activity-workbench-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.activity-stage-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.activity-stage {
  appearance: none;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  border-radius: 6px;
  padding: 12px;
  text-align: left;
  cursor: pointer;
  min-height: 90px;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.activity-stage:hover,
.activity-stage.active {
  border-color: #ef4444;
  background: #fff7f7;
  box-shadow: 0 4px 14px rgba(239, 68, 68, 0.08);
}

.activity-stage-label,
.activity-stage-hint {
  display: block;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}

.activity-stage strong {
  display: block;
  color: #111827;
  font-size: 24px;
  line-height: 1.2;
  margin: 4px 0;
}

.main-card {
  overflow: hidden;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px 0;
  flex-wrap: wrap;
}

.batch-toolbar-summary {
  color: #374151;
  font-size: 13px;
  font-weight: 600;
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
  position: relative;
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

:deep(.table-product-pin-badge) {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 1;
  padding: 1px 6px;
  border-radius: 3px;
  background: #d92d20;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
  box-shadow: 0 2px 8px rgba(217, 45, 32, 0.22);
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

:deep(.table-product-title-row) {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin-bottom: 2px;
}

:deep(.table-product-title) {
  color: #ef4444;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 220px;
}

:deep(.table-product-pin-meta) {
  color: #d92d20;
  font-weight: 600;
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

:deep(.library-status-tag-warning) {
  color: #d97706;
  font-weight: 600;
}

:deep(.library-status-hint) {
  line-height: 1.45;
  max-width: 280px;
}

:deep(.table-stack-line.action-needed) {
  color: #ef4444;
  font-weight: 600;
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

:deep(.table-inline-badge-pin) {
  background: #fee4e2;
  color: #d92d20;
  flex-shrink: 0;
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
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
  border-bottom: 1px solid var(--border-color);
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

@media (max-width: 960px) {
  .activity-workbench-header {
    flex-direction: column;
  }

  .activity-workbench-actions {
    justify-content: flex-start;
  }

  .activity-stage-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .product-page {
    padding: 16px;
  }

  .activity-stage-row {
    grid-template-columns: 1fr;
  }
}
</style>
