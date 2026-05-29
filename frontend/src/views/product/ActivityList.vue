<template>
  <div class="activity-page app-page" data-testid="activity-list-page">
    <PageHeader
      title="活动列表"
      description="同步并查看抖音官方报名的团长活动，进入活动商品推进工作台处理入库与分配。"
    >
      <template #actions>
        <n-button :loading="loading" data-testid="activity-refresh" @click="fetchData">刷新数据</n-button>
      </template>
    </PageHeader>

    <section class="filter-panel app-panel">
      <div class="filter-grid">
        <n-input
          v-model:value="filters.activityId"
          clearable
          placeholder="活动 ID"
          data-testid="activity-id-filter"
        />
        <n-input
          v-model:value="filters.activityName"
          clearable
          placeholder="活动名称"
          data-testid="activity-name-filter"
        />
      </div>
      <div class="filter-actions">
        <n-button secondary data-testid="activity-filter-reset" @click="resetFilters">重置</n-button>
        <n-button type="primary" data-testid="activity-filter-search" @click="handleFilterSearch">搜索</n-button>
      </div>
    </section>

    <n-alert :type="activityAlertType" class="app-page-alert" data-testid="activity-env-hint">
      {{ activityDataSourceHint }}
    </n-alert>

    <section class="list-panel app-panel app-table-shell">
      <div class="table-actions">
        <div class="status-funnel" role="tablist" aria-label="活动状态筛选" data-testid="activity-status-funnel">
          <button
            v-for="tab in ACTIVITY_STATUS_TABS"
            :key="tab.status"
            type="button"
            role="tab"
            class="status-step"
            :class="{ active: activeStatus === tab.status }"
            :data-testid="`activity-status-tab-${tab.status}`"
            :aria-selected="activeStatus === tab.status"
            @click="selectStatusTab(tab.status)"
          >
            {{ tab.label }}<span v-if="activeStatus === tab.status">({{ pagination.itemCount }})</span>
          </button>
        </div>
        <n-space>
          <n-dropdown :options="batchOptions" trigger="click" @select="handleBatchAction">
            <n-button secondary data-testid="activity-batch-actions">批量操作</n-button>
          </n-dropdown>
          <n-button
            type="primary"
            :loading="loading"
            data-testid="activity-sync-latest"
            @click="syncLatestActivities"
          >
            同步最新活动列表
          </n-button>
        </n-space>
      </div>

      <n-data-table
        remote
        data-testid="activity-table"
        :columns="columns as any"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: ActivityRow) => String(row.activityId)"
        :checked-row-keys="checkedRowKeys"
        :scroll-x="ACTIVITY_TABLE_SCROLL_X"
        :single-line="false"
        :scrollbar-props="{ trigger: 'none' }"
        @update:checked-row-keys="handleCheckedRowKeysUpdate"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </section>

    <n-modal
      v-model:show="assignModalVisible"
      preset="card"
      title="分配招商组长"
      :style="{ width: '480px' }"
      data-testid="activity-assign-modal"
    >
      <n-form label-placement="left" label-width="96">
        <n-form-item label="招商组长" required>
          <n-select
            v-model:value="assignForm.assigneeId"
            filterable
            remote
            clearable
            placeholder="搜索并选择招商组长"
            :options="assigneeOptions"
            :loading="assigneeLoading"
            data-testid="activity-assign-select"
            @search="loadAssigneeOptions"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="assignModalVisible = false">取消</n-button>
          <n-button
            type="primary"
            :loading="assignSubmitting"
            data-testid="activity-assign-submit"
            @click="submitAssignActivity"
          >
            确认分配
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NModal, NForm, NFormItem, NSelect, NSpace, useMessage } from 'naive-ui'
import { useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import { assignColonelActivity, getColonelActivityPage } from '../../api/activity'
import { getActivityProducts } from '../../api/activityProduct'
import { getDouyinInstitutionInfo } from '../../api/douyin'
import { exportActivities } from '../../api/data'
import { useRuntimeEnvironment } from '../../composables/useRuntimeEnvironment'
import { useAuthStore } from '../../stores/auth'
import { loadProductAssigneeOptions } from './product-assignee-options'
import { DEFAULT_PAGE, normalizePageSize } from '../../utils/pagination'
import { notifyApiFailure } from '../../utils/requestError'
import {
  ACTIVITY_STATUS_TABS,
  type ActivityProductStats,
  type ActivityRow,
  buildBuyinActivityUrl,
  countActivityProductStats,
  extractInstitutionName,
  formatActivityCategories,
  formatDateRange,
  formatMechanismSummary,
  resolveActivityAssigneeName,
  resolveActivityRequirement,
  resolveActivityStatusLabel,
  resolveColonelName
} from './activity-list-display'

const ACTIVITY_TABLE_SCROLL_X = 1880
const ACTIVITY_PAGE_SIZE_OPTIONS = [10, 20, 50] as const

const message = useMessage()
const router = useRouter()
const authStore = useAuthStore()
const { activityDataSourceHint, activityAlertType } = useRuntimeEnvironment()
const canAssignActivity = computed(() => authStore.isAdmin || authStore.roleCodes.includes('admin'))

const loading = ref(false)
const exporting = ref(false)
const data = ref<ActivityRow[]>([])
const checkedRowKeys = ref<Array<string | number>>([])
const activeStatus = ref(0)
const institutionName = ref('')
const productStatsMap = ref<Record<string, ActivityProductStats>>({})
const assignModalVisible = ref(false)
const assignSubmitting = ref(false)
const assigneeLoading = ref(false)
const assigneeOptions = ref<Array<{ label: string; value: string }>>([])
const assignTargetActivityId = ref('')
const assignForm = reactive({
  assigneeId: null as string | null
})

const filters = reactive({
  activityId: '',
  activityName: ''
})

const pagination = reactive({
  page: DEFAULT_PAGE,
  pageSize: ACTIVITY_PAGE_SIZE_OPTIONS[0] as number,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [...ACTIVITY_PAGE_SIZE_OPTIONS]
})

const batchOptions = computed(() => [
  { label: '导出当前筛选 CSV', key: 'export-all' },
  {
    label: '导出已选活动 CSV',
    key: 'export-selected',
    disabled: !checkedRowKeys.value.length
  }
])

const renderCellLines = (lines: Array<{ label?: string; value?: string; link?: boolean; onClick?: () => void }>) =>
  h(
    'div',
    { class: 'activity-cell-lines' },
    lines.map((line, index) => {
      if (line.link && line.onClick) {
        return h(
          'button',
          {
            key: `link-${index}`,
            type: 'button',
            class: 'activity-cell-link',
            onClick: (event: MouseEvent) => {
              event.stopPropagation()
              line.onClick?.()
            }
          },
          line.value || '—'
        )
      }
      return h('div', { key: `line-${index}`, class: 'activity-cell-line' }, [
        line.label ? h('span', { class: 'activity-cell-label' }, `${line.label}：`) : null,
        h('span', { class: 'activity-cell-value' }, line.value || '—')
      ])
    })
  )

const copyText = async (text: string, successMessage: string) => {
  const value = String(text || '').trim()
  if (!value) {
    message.warning('暂无可复制内容')
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    message.success(successMessage)
  } catch {
    message.warning('浏览器未允许写入剪贴板')
  }
}

const openBuyinActivity = (row: ActivityRow) => {
  const url = buildBuyinActivityUrl(String(row.activityId ?? ''))
  if (!url) {
    message.warning('暂无百应活动链接')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

const getProductStats = (activityId: string | number | undefined) =>
  productStatsMap.value[String(activityId ?? '')] || { promoting: null, pending: null }

const columns = computed(() => [
  { type: 'selection' as const, fixed: 'left' as const, width: 48 },
  {
    title: '活动名称',
    key: 'activityName',
    width: 220,
    fixed: 'left' as const,
    render: (row: ActivityRow) =>
      renderCellLines([
        { label: '活动名称', value: String(row.activityName ?? '—') },
        { label: '', value: '去百应', link: true, onClick: () => openBuyinActivity(row) },
        { label: '团长名称', value: resolveColonelName(row, institutionName.value) },
        { label: '百应活动ID', value: String(row.activityId ?? '—') }
      ])
  },
  {
    title: '商品数',
    key: 'productCount',
    width: 120,
    render: (row: ActivityRow) => {
      const stats = getProductStats(row.activityId as string | number)
      const promoting = stats.promoting == null ? '—' : String(stats.promoting)
      const pending = stats.pending == null ? '—' : String(stats.pending)
      return renderCellLines([
        { label: '推广中', value: promoting },
        { label: '待审核', value: pending }
      ])
    }
  },
  {
    title: '招商组长',
    key: 'recruiterName',
    width: 120,
    render: (row: ActivityRow) => resolveActivityAssigneeName(row)
  },
  {
    title: '活动状态',
    key: 'activityStatus',
    width: 100,
    render: (row: ActivityRow) => resolveActivityStatusLabel(row)
  },
  {
    title: '招商类目',
    key: 'categoriesLimit',
    width: 260,
    render: (row: ActivityRow) =>
      h('div', { class: 'activity-cell-wrap' }, formatActivityCategories(row.categoriesLimit))
  },
  {
    title: '报名时间',
    key: 'applicationTime',
    width: 150,
    render: (row: ActivityRow) => {
      const range = formatDateRange(row.applicationStartTime ?? row.applyStartTime, row.applicationEndTime ?? row.applyEndTime)
      return renderCellLines([
        { label: '开始', value: range.start },
        { label: '结束', value: range.end }
      ])
    }
  },
  {
    title: '商品机制',
    key: 'mechanism',
    width: 220,
    render: (row: ActivityRow) => {
      const mechanism = formatMechanismSummary(row)
      return renderCellLines([
        { label: '类型', value: mechanism.typeLabel },
        { label: '佣金率', value: mechanism.commissionLine },
        { label: '服务费率', value: mechanism.serviceLine }
      ])
    }
  },
  {
    title: '活动要求',
    key: 'activityRequirement',
    width: 180,
    render: (row: ActivityRow) =>
      h('div', { class: 'activity-cell-wrap' }, resolveActivityRequirement(row))
  },
  {
    title: '操作',
    key: 'actions',
    width: 170,
    fixed: 'right' as const,
    render: (row: ActivityRow) => {
      const actions = [
        h(
          NButton,
          {
            text: true,
            type: 'error',
            size: 'small',
            'data-testid': 'activity-copy-link',
            onClick: () => copyText(buildBuyinActivityUrl(String(row.activityId ?? '')), '已复制活动链接')
          },
          { default: () => '复制链接' }
        ),
        h(
          NButton,
          {
            text: true,
            type: 'error',
            size: 'small',
            'data-testid': 'activity-view-products',
            onClick: () => router.push(`/product/manage/${row.activityId}`)
          },
          { default: () => '商品信息' }
        )
      ]
      if (canAssignActivity.value) {
        actions.unshift(
          h(
            NButton,
            {
              text: true,
              type: 'primary',
              size: 'small',
              'data-testid': 'activity-assign-recruiter',
              onClick: () => openAssignModal(row)
            },
            { default: () => '分配招商组长' }
          )
        )
      }
      return h('div', { class: 'activity-action-links' }, actions)
    }
  }
])

const hydrateProductStats = async (rows: ActivityRow[]) => {
  if (!rows.length) return
  const entries = await Promise.all(
    rows.map(async (row) => {
      const activityId = String(row.activityId ?? '').trim()
      if (!activityId) return [activityId, { promoting: null, pending: null }] as const
      try {
        const res: any = await getActivityProducts(activityId, { count: 20, refresh: false })
        const payload = res?.data ?? res ?? {}
        const items = Array.isArray(payload.items)
          ? payload.items
          : Array.isArray(payload.data)
            ? payload.data
            : []
        return [activityId, countActivityProductStats(items)] as const
      } catch {
        return [activityId, { promoting: null, pending: null }] as const
      }
    })
  )
  const next: Record<string, ActivityProductStats> = { ...productStatsMap.value }
  entries.forEach(([activityId, stats]) => {
    if (activityId) next[activityId] = stats
  })
  productStatsMap.value = next
}

const buildActivityInfoKeyword = () => {
  const activityId = String(filters.activityId || '').trim()
  const activityName = String(filters.activityName || '').trim()
  return activityId || activityName || undefined
}

const loadAssigneeOptions = async (keyword = '') => {
  assigneeLoading.value = true
  try {
    assigneeOptions.value = await loadProductAssigneeOptions(keyword)
  } catch (err: any) {
    notifyApiFailure(err, message, { fallbackMessage: '加载招商组长候选失败' })
  } finally {
    assigneeLoading.value = false
  }
}

const openAssignModal = async (row: ActivityRow) => {
  assignTargetActivityId.value = String(row.activityId ?? '').trim()
  assignForm.assigneeId = String(
    row.activityAssigneeId ?? row.assigneeId ?? ''
  ).trim() || null
  assignModalVisible.value = true
  await loadAssigneeOptions('')
}

const submitAssignActivity = async () => {
  if (!assignTargetActivityId.value) {
    message.warning('活动 ID 无效')
    return
  }
  if (assignForm.assigneeId === '') {
    message.warning('请选择招商组长')
    return
  }
  assignSubmitting.value = true
  try {
    await assignColonelActivity(assignTargetActivityId.value, {
      assigneeId: assignForm.assigneeId
    })
    message.success('活动已分配招商组长')
    assignModalVisible.value = false
    await fetchData()
  } catch (err: any) {
    notifyApiFailure(err, message, { fallbackMessage: '分配失败' })
  } finally {
    assignSubmitting.value = false
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await getColonelActivityPage({
      page: pagination.page,
      pageSize: pagination.pageSize,
      status: activeStatus.value,
      searchType: 0,
      sortType: 1,
      activityInfo: buildActivityInfoKeyword()
    })
    const result = res.data || {}
    const rows = (result.activityList || []) as ActivityRow[]
    data.value = rows
    pagination.itemCount = Number(result.total || rows.length || 0)
    checkedRowKeys.value = checkedRowKeys.value.filter((key) =>
      rows.some((row) => String(row.activityId) === String(key))
    )
    void hydrateProductStats(rows)
  } catch (err: any) {
    notifyApiFailure(err, message, { fallbackMessage: '加载活动列表失败' })
  } finally {
    loading.value = false
  }
}

const selectStatusTab = (status: number) => {
  if (activeStatus.value === status) return
  activeStatus.value = status
  pagination.page = 1
  fetchData()
}

const handleFilterSearch = () => {
  pagination.page = 1
  fetchData()
}

const resetFilters = () => {
  filters.activityId = ''
  filters.activityName = ''
  pagination.page = 1
  fetchData()
}

const syncLatestActivities = () => {
  pagination.page = 1
  fetchData()
}

const handlePageChange = (page: number) => {
  pagination.page = page
  fetchData()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = normalizePageSize(pageSize)
  pagination.page = 1
  fetchData()
}

const handleCheckedRowKeysUpdate = (keys: Array<string | number>) => {
  checkedRowKeys.value = keys
}

const exportRowsToCsv = (rows: ActivityRow[]) => {
  const header = '活动ID,活动名称,开始时间,结束时间,状态'
  const lines = rows.map((row) => {
    const values = [
      String(row.activityId ?? ''),
      String(row.activityName ?? ''),
      String(row.applicationStartTime ?? row.startTime ?? ''),
      String(row.applicationEndTime ?? row.endTime ?? ''),
      resolveActivityStatusLabel(row)
    ]
    return values.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(',')
  })
  const content = `\ufeff${header}\n${lines.join('\n')}`
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `activities-selected-${new Date().toISOString().slice(0, 10)}.csv`)
  document.body.appendChild(link)
  link.click()
  link.parentNode?.removeChild(link)
  window.URL.revokeObjectURL(url)
}

const handleExport = async (activityIds?: Array<string | number>) => {
  if (activityIds?.length) {
    const selected = data.value.filter((row) => activityIds.some((id) => String(id) === String(row.activityId)))
    if (!selected.length) {
      message.warning('当前页未找到已选活动')
      return
    }
    exportRowsToCsv(selected)
    message.success('导出成功')
    return
  }

  exporting.value = true
  try {
    const res: any = await exportActivities({})
    const filename = `activities-${new Date().toISOString().slice(0, 10)}.csv`
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', filename)
    document.body.appendChild(link)
    link.click()
    link.parentNode?.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (err: any) {
    notifyApiFailure(err, message, { fallbackMessage: '导出失败' })
  } finally {
    exporting.value = false
  }
}

const handleBatchAction = (key: string) => {
  if (key === 'export-all') {
    void handleExport()
    return
  }
  if (key === 'export-selected') {
    if (!checkedRowKeys.value.length) {
      message.warning('请先选择活动')
      return
    }
    void handleExport(checkedRowKeys.value)
  }
}

const loadInstitutionName = async () => {
  try {
    const res = await getDouyinInstitutionInfo()
    institutionName.value = extractInstitutionName(res)
  } catch {
    institutionName.value = ''
  }
}

onMounted(async () => {
  await loadInstitutionName()
  await fetchData()
})
</script>

<style scoped>
.activity-page {
  padding: 24px;
}

.filter-panel {
  padding: 20px 24px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 14px 18px;
}

.filter-actions,
.table-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.list-panel {
  padding: 20px 24px 24px;
}

.table-actions {
  align-items: center;
  justify-content: space-between;
  margin: 0 0 18px;
}

.status-funnel {
  display: flex;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
}

.status-step {
  position: relative;
  min-width: 126px;
  height: 44px;
  padding: 0 22px;
  border: 0;
  background: #f1f4f7;
  color: var(--text-primary);
  cursor: pointer;
  clip-path: polygon(0 0, calc(100% - 16px) 0, 100% 50%, calc(100% - 16px) 100%, 0 100%, 16px 50%);
}

.status-step:first-child {
  border-radius: 22px 0 0 22px;
  clip-path: polygon(0 0, calc(100% - 16px) 0, 100% 50%, calc(100% - 16px) 100%, 0 100%);
}

.status-step.active {
  background: #fff1f1;
  color: #f5222d;
  font-weight: 700;
}

:deep(.activity-cell-lines) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  line-height: 1.5;
}

:deep(.activity-cell-line) {
  color: #333;
}

:deep(.activity-cell-label) {
  color: #666;
}

:deep(.activity-cell-value) {
  color: #333;
  word-break: break-all;
}

:deep(.activity-cell-wrap) {
  display: block;
  font-size: 12px;
  line-height: 1.6;
  color: #333;
  word-break: break-word;
}

:deep(.activity-cell-link) {
  appearance: none;
  border: none;
  background: transparent;
  color: #e53935;
  cursor: pointer;
  font-size: 12px;
  padding: 0;
  text-align: left;
}

:deep(.activity-cell-link:hover) {
  text-decoration: underline;
}

:deep(.activity-action-links) {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

@media (max-width: 1200px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }

  .table-actions {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 720px) {
  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
