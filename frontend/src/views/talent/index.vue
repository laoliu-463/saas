<template>
  <div class="talent-page app-page" data-testid="talent-page">
    <PageHeader
      :title="pageTitle"
      :description="pageDesc"
    >
      <template #actions>
        <n-button
          v-if="authStore.roleCodes.includes('channel_leader')"
          secondary
          :loading="weeklyRefreshing"
          data-testid="talent-weekly-refresh"
          @click="handleWeeklyRefresh"
        >
          触发周更
        </n-button>
        <n-button type="primary" secondary :loading="loading" data-testid="talent-refresh" @click="fetchData">刷新数据</n-button>
        <n-button type="primary" data-testid="talent-create" @click="showCreate = true">新增达人</n-button>
        <n-button data-testid="talent-batch-import" @click="showBatchImport = true">批量导入</n-button>
      </template>
    </PageHeader>

    <div class="summary-grid">
      <n-card :bordered="false" class="summary-card app-panel">
        <div class="summary-label">当前视图</div>
        <div class="summary-value">{{ currentViewLabel }}</div>
        <div class="summary-help">{{ currentViewHelp }}</div>
      </n-card>
      <n-card :bordered="false" class="summary-card app-panel">
        <div class="summary-label">列表总量</div>
        <div class="summary-value">{{ pagination.itemCount }}</div>
        <div class="summary-help">当前经营视图下满足筛选条件的达人数量</div>
      </n-card>
      <n-card :bordered="false" class="summary-card app-panel">
        <div class="summary-label">当前页自然出单</div>
        <div class="summary-value">{{ pageOrderTalentCount }}</div>
        <div class="summary-help">当前页订单数大于 0 的达人</div>
      </n-card>
      <n-card :bordered="false" class="summary-card app-panel">
        <div class="summary-label">当前页服务费贡献</div>
        <div class="summary-value">{{ pageServiceFeeText }}</div>
        <div class="summary-help">用于快速判断经营结果密度</div>
      </n-card>
    </div>

    <TalentBoardTabs v-model="activeView" :summary="viewSummary" :tabs="availableViewOptions" />
    <TalentMetricFilters
      :filters="filters"
      @update:filters="handleFilterPatch"
      @search="handleSearch"
      @reset="handleReset"
    />

    <n-card :bordered="false" class="main-card app-panel">
      <n-data-table
        remote
        data-testid="talent-table"
        :columns="columns"
        :data="data"
        :loading="tableLoading"
        :pagination="pagination"
        :row-key="(row: TalentListItem) => row.id"
        :scroll-x="1560"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />

      <PageEmpty
        v-if="!loading && data.length === 0"
        title="当前视图暂无达人"
        description="可调整经营筛选条件，或切换视图查看其他达人池。"
        icon="CRM"
      >
        <template #action>
          <n-space>
            <n-button @click="handleReset">重置筛选</n-button>
            <n-button secondary type="primary" @click="fetchData">重新加载</n-button>
          </n-space>
        </template>
      </PageEmpty>
    </n-card>

    <TalentCreateModal v-model:show="showCreate" @success="handleCreated" />
    <TalentBatchImportModal v-model:show="showBatchImport" @success="fetchData" />
    <TalentDetailModal
      v-model:show="showDetail"
      :talent-id="activeTalentId"
      @apply-sample="handleApplySample"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { NAvatar, NButton, NSpace, NTag, useDialog, useMessage, type DataTableColumns } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import PageEmpty from '../../components/PageEmpty.vue'
import PageHeader from '../../components/PageHeader.vue'
import { useAuthStore } from '../../stores/auth'
import {
  blacklistTalent,
  claimTalent,
  getTalentPage,
  refreshWeeklyTalents,
  releaseTalent,
  unblacklistTalent,
  type TalentListItem
} from '../../api/talent'
import TalentDetailModal from './components/TalentDetailModal.vue'
import TalentCreateModal from './components/TalentCreateModal.vue'
import TalentBatchImportModal from './components/TalentBatchImportModal.vue'
import TalentBoardTabs from './components/TalentBoardTabs.vue'
import TalentMetricFilters from './components/TalentMetricFilters.vue'
import TalentStatusActions from './components/TalentStatusActions.vue'
import { resolveSafeAvatarUrl } from '../../utils/media'
import { useTalentFilters, type TalentFiltersState } from './composables/useTalentFilters'
import { createPaginationState, normalizePage, normalizePageSize } from '../../utils/pagination'
import { useDelayedFlag } from '../../utils/delayedFlag'
import {
  formatDateTime,
  formatFans,
  formatMoney,
  getPoolLabel,
  getPoolTagType,
  TALENT_VIEW_HELP_MAP,
  TALENT_VIEW_LABEL_MAP,
  TALENT_VIEW_OPTIONS
} from './constants'

const message = useMessage()
const dialog = useDialog()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const tableLoading = useDelayedFlag(loading, 200)
const weeklyRefreshing = ref(false)
const showCreate = ref(false)
const showBatchImport = ref(false)
const showDetail = ref(false)
const activeTalentId = ref('')
const data = ref<TalentListItem[]>([])
const viewSummary = reactive<Record<string, number>>(
  TALENT_VIEW_OPTIONS.reduce<Record<string, number>>((acc, item) => {
    acc[item.value] = 0
    return acc
  }, {})
)

const {
  filters,
  resetFilters,
  applyQuery,
  toRequestParams,
  toRouteQuery
} = useTalentFilters()

const pagination = reactive(createPaginationState())

const currentViewLabel = computed(() => TALENT_VIEW_LABEL_MAP[activeView.value] || '达人经营台')
const currentViewHelp = computed(() => TALENT_VIEW_HELP_MAP[activeView.value] || '')

const isChannelStaffOnly = computed(() => {
  const roles = authStore.roleCodes
  return roles.includes('channel_staff') && !roles.includes('channel_leader') && !authStore.isAdmin
})
const canManageBlacklist = computed(() => authStore.isAdmin || authStore.roleCodes.includes('channel_leader'))

const pageTitle = computed(() => isChannelStaffOnly.value ? '我的达人管理' : '达人经营台')
const pageDesc = computed(() => isChannelStaffOnly.value
  ? '管理我认领的合作达人，跟进合作状态与产出数据，或从公海中发掘新达人。'
  : '围绕团队公海、我的达人、本组达人、自然出单达人和黑名单，完成认领、释放与风险处置闭环。'
)
const availableViewOptions = computed(() =>
  authStore.roleCodes.includes('channel_leader')
    ? TALENT_VIEW_OPTIONS
    : TALENT_VIEW_OPTIONS.filter((item) => ['TEAM_PUBLIC', 'MY_TALENTS'].includes(item.value))
)
const activeView = ref(resolveView(route.query.view))
const pageOrderTalentCount = computed(() => data.value.filter((item) => Number(item.orderCount || 0) > 0).length)
const pageServiceFeeText = computed(() => {
  const total = data.value.reduce((sum, item) => sum + Number(item.serviceFeeContribution || 0), 0)
  return formatMoney(total)
})

function resolveView(raw: unknown) {
  const value = typeof raw === 'string' ? raw : ''
  const allowed = availableViewOptions.value
  return allowed.some((item) => item.value === value) ? value : allowed[0].value
}

function syncFromRoute() {
  activeView.value = resolveView(route.query.view)
  applyQuery(route.query)
  pagination.page = normalizePage(route.query.page)
  pagination.pageSize = normalizePageSize(route.query.size)
}

function syncRoute() {
  router.replace({
    query: {
      ...toRouteQuery(activeView.value),
      page: pagination.page,
      size: pagination.pageSize
    }
  })
}

function isMine(row: TalentListItem) {
  const currentUserId = String(authStore.userInfo?.id || '')
  return Boolean(currentUserId) && currentUserId === String(row.ownerId || '')
}

function isBlacklisted(row: TalentListItem) {
  return row.blacklisted === true || activeView.value === 'BLACKLIST'
}

function resolvePoolStatus(row: TalentListItem) {
  if (isBlacklisted(row)) return 'BLACKLIST'
  if (activeView.value === 'TEAM_PRIVATE' && Number(row.activeClaimCount || 0) > 0) {
    return 'PRIVATE'
  }
  return row.poolStatus
}

function openDetail(row: TalentListItem) {
  activeTalentId.value = row.id
  showDetail.value = true
}

function handleApplySample(query: Record<string, string>) {
  showDetail.value = false
  router.push({ path: '/sample/apply', query })
}

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getTalentPage({
      page: pagination.page,
      size: pagination.pageSize,
      ...toRequestParams(activeView.value)
    })
    const payload = res?.data || {}
    data.value = Array.isArray(payload.records) ? payload.records : []
    pagination.itemCount = Number(payload.total || 0)
    viewSummary[activeView.value] = pagination.itemCount
    syncRoute()
  } catch (error: any) {
    message.error(error?.msg || error?.message || '加载达人列表失败')
  } finally {
    loading.value = false
  }
}

async function handleWeeklyRefresh() {
  weeklyRefreshing.value = true
  try {
    await refreshWeeklyTalents()
    message.success('已触发达人周更任务')
    fetchData()
  } catch (error: any) {
    message.error(error?.msg || error?.message || '触发周更失败')
  } finally {
    weeklyRefreshing.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchData()
}

function handleReset() {
  resetFilters()
  pagination.page = 1
  fetchData()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchData()
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = normalizePageSize(pageSize)
  pagination.page = 1
  fetchData()
}

function handleCreated() {
  pagination.page = 1
  fetchData()
}

function handleFilterPatch(patch: Partial<TalentFiltersState>) {
  Object.assign(filters, patch)
}

function handleClaim(row: TalentListItem) {
  dialog.warning({
    title: '确认认领达人',
    content: `认领后，该达人会进入你的私海并开始保护期。若已有其他同事认领，你会与对方同时保留认领关系。是否认领「${row.nickname || '未命名达人'}」？`,
    positiveText: '确认认领',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await claimTalent(row.id)
        message.success('认领成功，该达人已转入我的达人')
        fetchData()
      } catch (error: any) {
        const msg = error?.response?.data?.msg || error?.msg || error?.message || '认领失败'
        const status = Number(error?.response?.status || error?.response?.data?.code || error?.code || 0)
        if (status === 409 || String(msg).includes('保护期')) {
          message.warning('该达人在保护期内')
          return
        }
        message.error(msg)
      }
    }
  })
}

function handleRelease(row: TalentListItem) {
  dialog.warning({
    title: '确认释放达人',
    content: `释放后，该达人会回到团队公海。是否释放「${row.nickname || '未命名达人'}」？`,
    positiveText: '确认释放',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await releaseTalent(row.id)
        message.success('释放成功，该达人已回到团队公海')
        fetchData()
      } catch (error: any) {
        message.error(error?.msg || error?.message || '释放失败')
      }
    }
  })
}

function handleBlacklist(row: TalentListItem) {
  dialog.error({
    title: '确认拉黑达人',
    content: `拉黑后，该达人会移入黑名单视图并退出当前经营池。是否继续处理「${row.nickname || '未命名达人'}」？`,
    positiveText: '确认拉黑',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await blacklistTalent(row.id)
        message.success('已拉黑该达人')
        fetchData()
      } catch (error: any) {
        message.error(error?.msg || error?.message || '拉黑失败')
      }
    }
  })
}

function handleUnblacklist(row: TalentListItem) {
  dialog.warning({
    title: '确认解除拉黑',
    content: `解除拉黑后，该达人将重新回到团队可经营范围。是否恢复「${row.nickname || '未命名达人'}」？`,
    positiveText: '确认恢复',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await unblacklistTalent(row.id)
        message.success('已解除拉黑')
        fetchData()
      } catch (error: any) {
        message.error(error?.msg || error?.message || '解除拉黑失败')
      }
    }
  })
}

const columns: DataTableColumns<TalentListItem> = [
  {
    title: '达人',
    key: 'talent',
    width: 280,
    render: (row) =>
      h('div', { class: 'talent-main' }, [
        h('div', { class: 'talent-head' }, [
          h(NAvatar, {
            round: true,
            size: 40,
            src: resolveSafeAvatarUrl(row.avatarUrl)
          }),
          h('div', { class: 'talent-meta' }, [
            h('div', { class: 'talent-name' }, row.nickname || '未命名达人'),
            h('div', { class: 'talent-sub' }, `抖音号：${row.douyinNo || '-'}`),
            h('div', { class: 'talent-sub' }, `UID：${row.douyinUid || row.uid || '-'}`)
          ])
        ])
      ])
  },
  {
    title: '经营标签',
    key: 'tags',
    width: 220,
    render: (row) =>
      h(NSpace, { size: 8, wrap: true }, {
        default: () => [
            h(NTag, { type: getPoolTagType(resolvePoolStatus(row)), size: 'small' }, {
            default: () => getPoolLabel(resolvePoolStatus(row))
            }),
          h(NTag, { type: 'info', size: 'small' }, { default: () => row.mainCategory || '未分类' }),
          h(NTag, { type: 'default', size: 'small' }, { default: () => row.ipLocation || '未知地区' })
        ]
      })
  },
  {
    title: '达人数据',
    key: 'stats',
    width: 200,
    render: (row) =>
      h('div', { class: 'stats-col' }, [
        h('div', null, `粉丝数：${formatFans(row.fansCount)}`),
        h('div', null, `获赞数：${formatFans(row.likesCount)}`),
        h('div', null, `作品数：${row.worksCount ?? '-'}`)
      ])
  },
  {
    title: '经营结果',
    key: 'business',
    width: 220,
    render: (row) =>
      h('div', { class: 'stats-col' }, [
        h('div', null, `近 30 天销售额：${formatMoney(row.monthlySales)}`),
        h('div', null, `订单数：${row.orderCount ?? 0}`),
        h('div', null, `服务费贡献：${formatMoney(row.serviceFeeContribution)}`),
        h('div', null, `直播销售区间：${row.liveSalesBand || '-'}`)
      ])
  },
  {
    title: '合作记录',
    key: 'related',
    width: 200,
    render: (row) =>
      h('div', { class: 'stats-col' }, [
        h('div', null, `寄样：${row.sampleCount ?? 0}`),
        h('div', null, `自然出单：${row.naturalOrderTalent ? '是' : '否'}`),
        h('div', null, `视频销售区间：${row.videoSalesBand || '-'}`)
      ])
  },
  {
    title: '归属信息',
    key: 'owner',
    width: 220,
    render: (row) =>
        h('div', { class: 'stats-col' }, [
        h('div', null, `认领人：${row.ownerName || '-'}`),
        h('div', null, `认领状态：${row.poolStatus === 'PRIVATE' ? '我已认领' : (Number(row.activeClaimCount || 0) > 0 ? `他人已认领 ${row.activeClaimCount} 人` : '未认领')}`),
        h('div', null, `保护期：${formatDateTime(row.protectedUntil)}`)
      ])
  },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 260,
    render: (row) =>
      h(TalentStatusActions, {
        canClaim: !isBlacklisted(row) && row.poolStatus !== 'PRIVATE',
        canRelease: !isBlacklisted(row) && row.poolStatus === 'PRIVATE' && isMine(row),
        canBlacklist: canManageBlacklist.value && !isBlacklisted(row),
        canUnblacklist: canManageBlacklist.value && isBlacklisted(row),
        onDetail: () => openDetail(row),
        onClaim: () => handleClaim(row),
        onRelease: () => handleRelease(row),
        onBlacklist: () => handleBlacklist(row),
        onUnblacklist: () => handleUnblacklist(row)
      })
  }
]

watch(activeView, () => {
  pagination.page = 1
  fetchData()
})

watch(
  () => route.query,
  () => {
    syncFromRoute()
  }
)

watch(
  availableViewOptions,
  (options) => {
    if (!options.some((item) => item.value === activeView.value)) {
      activeView.value = options[0]?.value || 'TEAM_PUBLIC'
      syncRoute()
    }
  },
  { immediate: true }
)

onMounted(() => {
  syncFromRoute()
  fetchData()
})
</script>

<style scoped>
.talent-page {
  display: flex;
  flex-direction: column;
  gap: var(--content-gap);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  min-height: 116px;
}

.summary-label {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.summary-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.summary-help {
  margin-top: 8px;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
}

.talent-main {
  display: flex;
  flex-direction: column;
}

.talent-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.talent-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.talent-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.talent-sub,
.stats-col {
  font-size: var(--text-xs);
  line-height: 1.7;
  color: var(--text-secondary);
}

@media (max-width: 1280px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
