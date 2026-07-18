<template>
  <div class="cooperation-page app-page" :data-testid="shippingOnly ? 'ops-shipping-page' : 'sample-page'">
    <PageHeader :title="pageTitle" :description="pageDesc">
      <template #actions>
        <n-button v-if="canApplySample && !shippingOnly" type="primary" data-testid="sample-apply" @click="$router.push('/sample/apply')">
          申请寄样
        </n-button>
        <n-button :loading="loading" data-testid="sample-refresh" @click="fetchData">刷新数据</n-button>
      </template>
    </PageHeader>

    <section v-if="!shippingOnly" class="notice-panel">
      <div class="notice-copy">
        <h2>合作单管理须知</h2>
        <p>合作单承接达人寄样、审核、发货、签收与交作业进度，当前只展示抖音合作履约数据。</p>
        <p>若合作来源为团队负责商品，招商负责审核，运营负责发货物流；渠道侧负责达人申请与后续交作业跟进。</p>
      </div>
      <div class="notice-visual" aria-hidden="true">
        <span class="box top"></span>
        <span class="box mid"></span>
        <span class="hand"></span>
      </div>
    </section>

    <section class="filter-panel app-panel">
      <div class="filter-grid">
        <n-input v-model:value="filters.productKeyword" clearable placeholder="商品ID / 商品名称" data-testid="sample-product-filter" />
        <n-input v-model:value="filters.shopKeyword" clearable placeholder="店铺ID / 店铺名称" data-testid="sample-shop-filter" />
        <n-input v-model:value="filters.trackingNo" clearable placeholder="物流单号" data-testid="sample-tracking-filter" />
        <n-input v-model:value="filters.requestNo" clearable placeholder="申请编号" data-testid="sample-request-no-filter" />
        <n-input v-model:value="filters.talentKeyword" clearable placeholder="达人昵称 / 达人号" data-testid="sample-talent-filter" />
        <n-select v-model:value="filters.cooperationType" clearable :options="cooperationTypeOptions" placeholder="合作类型" />
        <n-select
          v-model:value="filters.recruiterUserId"
          clearable
          filterable
          remote
          :options="recruiterOptions"
          :loading="recruiterOptionsLoading"
          placeholder="招商组长"
          data-testid="sample-recruiter-filter"
          @search="handleRecruiterSearch"
        />
        <n-select v-model:value="filters.sampleOwnerType" clearable :options="sampleOwnerTypeOptions" placeholder="寄样负责方" />
        <n-select v-model:value="filters.homeworkType" clearable :options="homeworkTypeOptions" placeholder="交作业类型" />
        <n-input v-model:value="filters.recipientName" clearable placeholder="收货人姓名" />
        <n-input v-model:value="filters.recipientPhone" clearable placeholder="收货人手机号" />
        <n-select
          v-model:value="filters.channelUserIds"
          multiple
          clearable
          filterable
          remote
          :options="channelOptions"
          :loading="channelOptionsLoading"
          placeholder="渠道负责人（可多选）"
          data-testid="sample-channel-filter"
          @search="handleChannelSearch"
        />
        <n-date-picker v-model:value="filters.applyRange" clearable type="datetimerange" start-placeholder="申请开始" end-placeholder="申请结束" />
        <n-date-picker v-model:value="filters.homeworkRange" clearable type="datetimerange" start-placeholder="交作业开始" end-placeholder="交作业结束" />
        <n-select v-model:value="filters.logisticsCompany" clearable :options="logisticsCompanyOptions" placeholder="物流公司" />
      </div>
      <div class="filter-actions">
        <n-button secondary data-testid="sample-filter-reset" @click="resetFilters">重置</n-button>
        <n-button type="primary" data-testid="sample-filter-search" @click="handleFilterSearch">搜索</n-button>
      </div>
    </section>

    <section class="list-panel app-panel app-table-shell">
      <div class="table-actions">
        <div class="status-funnel" data-testid="sample-status-funnel">
          <button
            v-for="tab in tabList"
            :key="tab.value"
            type="button"
            class="status-step"
            :class="{ active: activeTab === tab.value }"
            @click="setActiveTab(tab.value)"
          >
            {{ tab.label }}<span v-if="activeTab === tab.value">({{ pagination.itemCount }})</span>
          </button>
        </div>
        <n-space>
          <n-button v-if="canExportSamples" data-testid="sample-export" @click="handleExport">
            {{ activeTab === 'PENDING_SHIP' ? '导出发货单' : '导出合作单' }}
          </n-button>
          <n-button v-if="showShippingActions" type="primary" secondary data-testid="sample-logistics-import" @click="showLogisticsImport = true">
            批量导入物流单号
          </n-button>
          <n-button v-if="showShippingActions" @click="triggerFileInput">批量发货（Excel 预览）</n-button>
          <n-button
            v-if="showBatchAuditActions"
            type="success"
            :disabled="selectedRequestNos.length === 0"
            :loading="batchSubmitting"
            data-testid="sample-batch-approve"
            @click="handleBatchApprove"
          >
            批量通过
          </n-button>
          <n-button
            v-if="showBatchAuditActions"
            type="error"
            :disabled="selectedRequestNos.length === 0"
            :loading="batchSubmitting"
            data-testid="sample-batch-reject"
            @click="openBatchRejectModal"
          >
            批量拒绝
          </n-button>
        </n-space>
        <input ref="fileInputRef" type="file" accept=".xlsx,.xls,.csv" class="hidden-file" @change="handleFileChange" />
      </div>

      <div v-if="batchItems.length > 0" class="batch-preview-card">
        <n-card title="批量发货预览" size="small" class="app-panel">
          <n-alert v-if="parseErrors.length > 0" type="warning" title="解析警告" class="batch-alert">
            <div v-for="(err, i) in parseErrors" :key="i">{{ err }}</div>
          </n-alert>
          <n-data-table :columns="batchColumns" :data="batchItems" data-testid="ops-shipping-batch-preview" :max-height="300" size="small" />
          <n-space class="batch-actions">
            <n-button type="primary" :loading="batchSubmitting" @click="submitBatch">确认发货（{{ batchItems.length }} 条）</n-button>
            <n-button @click="clearBatch">取消</n-button>
          </n-space>
        </n-card>
      </div>

      <n-data-table
        remote
        :data-testid="shippingOnly ? 'ops-shipping-table' : 'sample-table'"
        :columns="columns"
        :data="data"
        :loading="tableLoading"
        :pagination="pagination"
        :row-props="rowProps"
        :row-key="rowKey"
        :checked-row-keys="checkedRowKeys"
        :scroll-x="1680"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
        @update:checked-row-keys="handleCheckedRowKeys"
      />
    </section>

    <SampleDetail v-model:show="showDetail" :sample-id="currentSampleId" @refresh="fetchData" />
    <SampleEditModal
      v-model:show="showEdit"
      :sample-id="currentActionSampleId"
      @saved="fetchData"
    />
    <PrivateNoteModal v-model:show="showPrivateNote" :sample-id="currentActionSampleId" />
    <ManualCopyModal v-model:show="showManualCopy" :content="manualCopyContent" />
    <SampleLogisticsImportModal v-model:show="showLogisticsImport" @success="handleLogisticsImportSuccess" />
    <SampleBatchRejectModal
      v-model:show="showBatchReject"
      :request-nos="selectedRequestNos"
      :submitting="batchSubmitting"
      @submit="handleBatchRejectSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue'
import { NAvatar, NButton, NInput, NTag, useDialog, useMessage } from 'naive-ui'
import {
  actionSample,
  batchApproveSamples,
  batchRejectSamples,
  batchShipSamples,
  exportSamples,
  getSampleFilterOptions,
  getSampleOrderCopy,
  getSamplePage,
  getSamplePromotionCopy
} from '../../api/sample'
import PageHeader from '../../components/PageHeader.vue'
import type { CooperationActionKey, SampleCopyText, SampleItem } from '../../types'
import { ROLE_CODES, hasOnlyCanonicalRole } from '../../constants/rbac'
import { useAuthStore } from '../../stores/auth'
import { createPaginationState, normalizePageSize } from '../../utils/pagination'
import { useDelayedFlag } from '../../utils/delayedFlag'
import { parseBatchShipRows, type BatchShipItem, type BatchShipRow } from '../../utils/shippingBatch'
import { notifyApiFailure, notifyClientPermission } from '../../utils/requestError'
import { tryCopyText } from '../../utils/clipboard'
import {
  canApplySamplesByRole,
  canExportSamplesByRole,
  canReviewSamplesByRole,
  OPS_SHIPPING_TABS
} from './sample-permissions'
import {
  buildCooperationSampleFilterParams,
  type CooperationWorkbenchFilters
} from './cooperation-workbench-filters'
import { loadSampleChannelOptions, loadSampleRecruiterOptions, mapFilterOptionItems } from './sample-user-filter-options'
import CooperationActionColumn from './CooperationActionColumn.vue'
import ManualCopyModal from './ManualCopyModal.vue'
import PrivateNoteModal from './PrivateNoteModal.vue'
import SampleBatchRejectModal from './SampleBatchRejectModal.vue'
import SampleDetail from './SampleDetail.vue'
import SampleEditModal from './SampleEditModal.vue'
import SampleLogisticsImportModal from './SampleLogisticsImportModal.vue'

const props = defineProps<{ shippingOnly?: boolean }>()

const message = useMessage()
const dialog = useDialog()
const authStore = useAuthStore()
const loading = ref(false)
const tableLoading = useDelayedFlag(loading, 200)
const EXPORT_ROW_LIMIT = 20000
const ROLE = ROLE_CODES

const ALL_TABS = [
  { label: '全部', value: '' },
  { label: '待审核', value: 'PENDING_AUDIT' },
  { label: '待发货', value: 'PENDING_SHIP' },
  { label: '快递中', value: 'SHIPPED' },
  { label: '待交作业', value: 'PENDING_TASK' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' }
]

const isOpsStaffOnly = computed(() => {
  return hasOnlyCanonicalRole(authStore.roleCodes, ROLE.OPS_STAFF)
})
const isBizStaffOnly = computed(() => {
  return hasOnlyCanonicalRole(authStore.roleCodes, ROLE.BIZ_STAFF)
})
const isChannelStaffOnly = computed(() => {
  return hasOnlyCanonicalRole(authStore.roleCodes, ROLE.CHANNEL_STAFF)
})

const tabList = computed(() => (props.shippingOnly || isOpsStaffOnly.value ? OPS_SHIPPING_TABS : ALL_TABS))
const activeTab = ref(props.shippingOnly || isOpsStaffOnly.value ? 'PENDING_SHIP' : '')
const data = ref<SampleItem[]>([])
const checkedRowKeys = ref<Array<string | number>>([])
const pagination = reactive(createPaginationState())

const filters = reactive<CooperationWorkbenchFilters>({
  productKeyword: '',
  shopKeyword: '',
  trackingNo: '',
  requestNo: '',
  talentKeyword: '',
  cooperationType: null as string | null,
  sampleOwnerType: null as string | null,
  homeworkType: null as string | null,
  recipientName: '',
  recipientPhone: '',
  channelUserIds: [] as string[],
  recruiterUserId: null as string | null,
  applyRange: null as [number, number] | null,
  homeworkRange: null as [number, number] | null,
  logisticsCompany: null as string | null
})

const channelOptions = ref<Array<{ label: string; value: string }>>([])
const recruiterOptions = ref<Array<{ label: string; value: string }>>([])
const cooperationTypeOptions = ref<Array<{ label: string; value: string }>>([])
const sampleOwnerTypeOptions = ref<Array<{ label: string; value: string }>>([])
const homeworkTypeOptions = ref<Array<{ label: string; value: string }>>([])
const logisticsCompanyOptions = ref<Array<{ label: string; value: string }>>([])
const channelOptionsLoading = ref(false)
const recruiterOptionsLoading = ref(false)

const showDetail = ref(false)
const showEdit = ref(false)
const showPrivateNote = ref(false)
const showManualCopy = ref(false)
const showLogisticsImport = ref(false)
const showBatchReject = ref(false)
const currentSampleId = ref('')
const currentActionSampleId = ref('')
const manualCopyContent = ref('')
const batchSubmitting = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const batchItems = ref<BatchShipItem[]>([])
const parseErrors = ref<string[]>([])

const pageTitle = computed(() => {
  if (props.shippingOnly || isOpsStaffOnly.value) return '发货台'
  if (isBizStaffOnly.value) return '我的审核工作台'
  if (isChannelStaffOnly.value) return '我的寄样申请'
  return '合作单'
})

const pageDesc = computed(() => {
  if (props.shippingOnly || isOpsStaffOnly.value) return '处理合作单发货、物流单号录入、签收跟进和异常物流处理。'
  if (isBizStaffOnly.value) return '处理我负责商品的合作单审核，并跟进后续履约状态。'
  if (isChannelStaffOnly.value) return '为我的达人申请寄样，跟进发货、签收与交作业。'
  return '集中管理达人合作履约：寄样申请、审核、发货、签收、交作业与完成状态。'
})

const canApplySample = computed(() => {
  return canApplySamplesByRole(authStore.roleCodes)
})
const canExportSamples = computed(() => canExportSamplesByRole(authStore.roleCodes))
const canBatchAudit = computed(() => canReviewSamplesByRole(authStore.roleCodes))
const canShipSamples = computed(() => authStore.isAdmin || authStore.roleCodes.includes(ROLE.OPS_STAFF))
const showBatchAuditActions = computed(() => canBatchAudit.value && activeTab.value === 'PENDING_AUDIT' && !props.shippingOnly)
const showShippingActions = computed(() => canShipSamples.value && activeTab.value === 'PENDING_SHIP')
const selectedRequestNos = computed(() => checkedRowKeys.value.map((key) => String(key)).filter(Boolean))

const batchColumns = [
  { title: '合作单号', key: 'requestNo' },
  { title: '物流公司', key: 'shipperCode', render: (row: BatchShipItem) => row.shipperCode || '-' },
  { title: '物流单号', key: 'trackingNo' }
]

const buildSampleFilterParams = () => buildCooperationSampleFilterParams(filters, activeTab.value)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await getSamplePage({
      page: pagination.page,
      size: pagination.pageSize,
      ...buildSampleFilterParams()
    })
    const responseData = res?.data || res
    if (responseData?.records && Array.isArray(responseData.records)) {
      data.value = responseData.records
      pagination.itemCount = responseData.total || 0
    } else {
      data.value = []
      pagination.itemCount = 0
    }
  } catch (error: any) {
    data.value = []
    pagination.itemCount = 0
    notifyApiFailure(error, message, {
      permissionFallback: '当前角色无权查看此合作单列表',
      fallbackMessage: '获取合作单列表失败'
    })
  } finally {
    loading.value = false
  }
}

const initFilterOptions = async () => {
  try {
    const res: any = await getSampleFilterOptions()
    const body = res?.data || res
    channelOptions.value = mapFilterOptionItems(body?.channels || [])
    recruiterOptions.value = mapFilterOptionItems(body?.recruiters || [])
    cooperationTypeOptions.value = mapFilterOptionItems(body?.cooperationTypes || [])
    sampleOwnerTypeOptions.value = mapFilterOptionItems(body?.sampleOwnerTypes || [])
    homeworkTypeOptions.value = mapFilterOptionItems(body?.homeworkTypes || [])
    logisticsCompanyOptions.value = mapFilterOptionItems(body?.logisticsCompanies || [])
  } catch {
    // 降级到人员主数据接口，不阻断合作单列表。
  }
}

const fetchChannelOptions = async (keyword = '') => {
  channelOptionsLoading.value = true
  try {
    channelOptions.value = await loadSampleChannelOptions(keyword)
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载渠道负责人失败' })
  } finally {
    channelOptionsLoading.value = false
  }
}
const fetchRecruiterOptions = async (keyword = '') => {
  recruiterOptionsLoading.value = true
  try {
    recruiterOptions.value = await loadSampleRecruiterOptions(keyword)
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载招商组长失败' })
  } finally {
    recruiterOptionsLoading.value = false
  }
}
const handleChannelSearch = (keyword: string) => fetchChannelOptions(keyword)
const handleRecruiterSearch = (keyword: string) => fetchRecruiterOptions(keyword)

const handleFilterSearch = () => {
  pagination.page = 1
  checkedRowKeys.value = []
  fetchData()
}
const resetFilters = () => {
  Object.assign(filters, {
    productKeyword: '',
    shopKeyword: '',
    trackingNo: '',
    requestNo: '',
    talentKeyword: '',
    cooperationType: null,
    sampleOwnerType: null,
    homeworkType: null,
    recipientName: '',
    recipientPhone: '',
    channelUserIds: [],
    recruiterUserId: null,
    applyRange: null,
    homeworkRange: null,
    logisticsCompany: null
  })
  handleFilterSearch()
}
const setActiveTab = (status: string) => {
  activeTab.value = status
  pagination.page = 1
  checkedRowKeys.value = []
  fetchData()
}

const rowKey = (row: SampleItem) => row.requestNo || row.id
const rowProps = () => ({ 'data-testid': 'sample-row' })
const handleCheckedRowKeys = (keys: Array<string | number>) => {
  checkedRowKeys.value = keys
}
const openDetail = (row: SampleItem) => {
  currentSampleId.value = row.id
  showDetail.value = true
}

const runStatusAction = async (row: SampleItem, payload: { action: 'APPROVED' | 'REJECTED'; reason?: string }) => {
  try {
    await actionSample(row.id, payload)
    message.success(payload.action === 'APPROVED' ? '合作单已通过' : '合作单已拒绝')
    await fetchData()
  } catch (error) {
    notifyApiFailure(error, message, {
      permissionFallback: '当前角色无权审核合作单',
      fallbackMessage: '操作失败'
    })
    throw error
  }
}

const confirmApprove = (row: SampleItem) => {
  dialog.warning({
    title: '通过合作单',
    content: '确认通过该合作单吗？',
    positiveText: '确认通过',
    negativeText: '取消',
    onPositiveClick: () => runStatusAction(row, { action: 'APPROVED' })
  })
}

const confirmReject = (row: SampleItem) => {
  let reason = ''
  dialog.warning({
    title: '拒绝合作单',
    content: () => h(NInput, {
      type: 'textarea',
      placeholder: '请输入拒绝原因',
      maxlength: 200,
      showCount: true,
      onUpdateValue: (value: string) => { reason = value }
    }),
    positiveText: '确认拒绝',
    negativeText: '取消',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: () => {
      if (!reason.trim()) {
        message.warning('请输入拒绝原因')
        return false
      }
      return runStatusAction(row, { action: 'REJECTED', reason: reason.trim() })
    }
  })
}

const copySampleText = async (row: SampleItem, action: 'COPY_LINK' | 'COPY_ORDER') => {
  try {
    const response: any = action === 'COPY_LINK'
      ? await getSamplePromotionCopy(row.id)
      : await getSampleOrderCopy(row.id)
    const result = (response?.data || response) as SampleCopyText
    if (!result?.text) {
      message.warning('服务端未返回可复制内容')
      return
    }
    if (await tryCopyText(result.text)) {
      message.success(action === 'COPY_LINK' ? '推广信息已复制' : '订单信息已复制')
      return
    }
    manualCopyContent.value = result.text
    showManualCopy.value = true
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '生成复制内容失败' })
  }
}

const handleCooperationAction = (action: CooperationActionKey, row: SampleItem) => {
  if (action === 'APPROVE') return confirmApprove(row)
  if (action === 'REJECT') return confirmReject(row)
  if (action === 'PROGRESS') return openDetail(row)
  if (action === 'COPY_LINK' || action === 'COPY_ORDER') return copySampleText(row, action)
  currentActionSampleId.value = row.id
  if (action === 'EDIT') showEdit.value = true
  if (action === 'NOTE') showPrivateNote.value = true
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

const handleExport = async () => {
  if (!canExportSamples.value) {
    notifyClientPermission('当前角色无权导出合作单')
    return
  }
  if (!data.value.length) {
    message.warning('暂无合作单可导出')
    return
  }
  if (pagination.itemCount > EXPORT_ROW_LIMIT) {
    message.warning(`当前筛选结果 ${pagination.itemCount} 条，超过 ${EXPORT_ROW_LIMIT} 条导出上限，请缩小筛选范围`)
    return
  }
  try {
    const res: any = await exportSamples(buildSampleFilterParams())
    const blob = res instanceof Blob ? res : res?.data || res
    const url = URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `cooperation-${activeTab.value || 'all'}-${new Date().toISOString().slice(0, 10)}.csv`)
    document.body.appendChild(link)
    link.click()
    link.parentNode?.removeChild(link)
    URL.revokeObjectURL(url)
    message.success('合作单导出成功')
  } catch (error: any) {
    notifyApiFailure(error, message, {
      permissionFallback: '当前角色无权导出合作单',
      fallbackMessage: '合作单导出失败'
    })
  }
}

const triggerFileInput = () => fileInputRef.value?.click()
const handleFileChange = async (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  parseErrors.value = []
  try {
    const XLSX = await import('xlsx')
    const buf = await file.arrayBuffer()
    const wb = XLSX.read(buf, { type: 'array' })
    const ws = wb.Sheets[wb.SheetNames[0]]
    const rows = XLSX.utils.sheet_to_json<BatchShipRow>(ws, { header: 1 })
    const { items, errors } = parseBatchShipRows(rows)
    if (items.length === 0) {
      message.warning('未解析到有效数据行，请检查 Excel 格式（第1列：合作单号，第2列：物流公司，第3列：单号）')
      target.value = ''
      return
    }
    batchItems.value = items
    parseErrors.value = errors
    errors.length ? message.warning(`解析完成，${errors.length} 行有问题`) : message.success(`解析成功，共 ${items.length} 条待发货`)
  } catch (error: any) {
    message.error('Excel 解析失败：' + (error?.message || '未知错误'))
  }
  target.value = ''
}
const clearBatch = () => {
  batchItems.value = []
  parseErrors.value = []
}
const submitBatch = async () => {
  if (!batchItems.value.length) return
  batchSubmitting.value = true
  try {
    const res: any = await batchShipSamples({ items: batchItems.value })
    const result = res?.data || res
    message.success(`批量发货完成：成功 ${result?.success ?? 0} 条，失败 ${result?.fail ?? 0} 条`)
    clearBatch()
    fetchData()
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '批量发货失败' })
  } finally {
    batchSubmitting.value = false
  }
}

const handleLogisticsImportSuccess = () => {
  pagination.page = 1
  fetchData()
}
const batchResultText = (result: any) => `成功 ${result?.success ?? 0} 条，失败 ${result?.fail ?? 0} 条`
const handleBatchApprove = async () => {
  if (!selectedRequestNos.value.length) {
    message.warning('请先选择待审核合作单')
    return
  }
  batchSubmitting.value = true
  try {
    const res: any = await batchApproveSamples({ requestNos: selectedRequestNos.value, remark: '批量审批通过' })
    message.success(`批量通过完成：${batchResultText(res?.data || res)}`)
    checkedRowKeys.value = []
    fetchData()
  } catch (error: any) {
    notifyApiFailure(error, message, {
      permissionFallback: '当前角色无权审核合作单',
      fallbackMessage: '批量通过失败'
    })
  } finally {
    batchSubmitting.value = false
  }
}
const openBatchRejectModal = () => {
  if (!selectedRequestNos.value.length) {
    message.warning('请先选择待审核合作单')
    return
  }
  showBatchReject.value = true
}
const handleBatchRejectSubmit = async (remark: string) => {
  batchSubmitting.value = true
  try {
    const res: any = await batchRejectSamples({ requestNos: selectedRequestNos.value, remark })
    message.success(`批量拒绝完成：${batchResultText(res?.data || res)}`)
    checkedRowKeys.value = []
    showBatchReject.value = false
    fetchData()
  } catch (error: any) {
    notifyApiFailure(error, message, {
      permissionFallback: '当前角色无权审核合作单',
      fallbackMessage: '批量拒绝失败'
    })
  } finally {
    batchSubmitting.value = false
  }
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}
function compactText(value?: string | number | null) {
  if (value == null || value === '') return '-'
  return String(value)
}
function statusType(status?: string) {
  if (status === 'FINISHED') return 'success'
  if (status === 'REJECTED') return 'error'
  if (status === 'CLOSED') return 'default'
  if (status === 'SHIPPED') return 'info'
  if (status === 'PENDING_SHIP' || status === 'PENDING_AUDIT') return 'warning'
  return 'primary'
}
function statusLabel(status?: string) {
  return ALL_TABS.find((item) => item.value === status)?.label || status || '-'
}
function renderStack(lines: Array<string | number | null | undefined>, className = 'cell-stack') {
  return h('div', { class: className }, lines.filter((line) => line != null && line !== '').map((line) => h('span', String(line))))
}

const columns = computed(() => [
  ...(showBatchAuditActions.value
    ? [{
        type: 'selection' as const,
        fixed: 'left' as const,
        disabled: (row: SampleItem) => !row.requestNo
      }]
    : []),
  {
    title: '带货达人',
    key: 'talent',
    width: 260,
    fixed: 'left' as const,
    render: (row: SampleItem) => h('div', { class: 'talent-cell' }, [
      h(NAvatar, { round: true, size: 44, src: row.talentAvatarUrl || undefined }, { default: () => compactText(row.talentName || row.talentNickname).slice(0, 1) }),
      h('div', { class: 'cell-stack strong' }, [
        h('span', row.talentName || row.talentNickname || '-'),
        h('small', `达人号：${compactText(row.talentUid || row.talentId)}`),
        h('small', `${compactText(row.talentMainCategory)} · 粉丝 ${compactText(row.talentFansCount)}`)
      ])
    ])
  },
  {
    title: '商品信息',
    key: 'product',
    width: 300,
    render: (row: SampleItem) => h('div', { class: 'product-cell' }, [
      row.productCover ? h('img', { src: row.productCover, alt: '', class: 'product-cover' }) : h('div', { class: 'product-cover empty' }),
      renderStack([
        row.productName,
        `商品ID：${compactText(row.productExternalId || row.productId)}`,
        row.productPriceText ? `现售价：${row.productPriceText}` : null
      ], 'cell-stack strong')
    ])
  },
  {
    title: '合作方信息',
    key: 'partner',
    width: 220,
    render: (row: SampleItem) => renderStack([
      `店铺：${compactText(row.shopName || row.shopId)}`,
      `合作类型：${compactText(row.cooperationTypeLabel)}`,
      `寄样负责方：${compactText(row.sampleOwnerTypeLabel)}`
    ])
  },
  {
    title: '申请信息',
    key: 'apply',
    width: 260,
    render: (row: SampleItem) => renderStack([
      `申请编号：${compactText(row.requestNo)}`,
      `申请时间：${formatDateTime(row.createTime)}`,
      `申请人：${compactText(row.applicantName || row.channelUserName)}`,
      `申请数量：${compactText(row.quantity)}`
    ])
  },
  {
    title: '招商',
    key: 'colonel',
    width: 180,
    render: (row: SampleItem) => renderStack([row.colonelUserName || '-', row.applySourceLabel ? `来源：${row.applySourceLabel}` : null])
  },
  {
    title: '物流 / 进度',
    key: 'progress',
    width: 260,
    render: (row: SampleItem) => h('div', { class: 'cell-stack' }, [
      h(NTag, { type: statusType(row.status) as any, size: 'small', bordered: false }, { default: () => statusLabel(row.status) }),
      h('span', `物流：${compactText(row.shipperCode || row.logisticsCompany)} ${compactText(row.trackingNo)}`),
      h('span', `签收：${formatDateTime(row.signedAt || row.deliverTime)}`),
      h('span', `交作业：${compactText(row.homeworkTypeLabel)}`)
    ])
  },
  {
    title: '收货信息',
    key: 'recipient',
    width: 260,
    render: (row: SampleItem) => renderStack([row.recipientName, row.recipientPhone, row.recipientAddress])
  },
  {
    title: '操作',
    key: 'actions',
    width: props.shippingOnly ? 120 : 148,
    fixed: 'right' as const,
    render(row: SampleItem) {
      if (props.shippingOnly) {
        return h(NButton, { size: 'small', type: 'info', onClick: () => openDetail(row) }, { default: () => '查看处理' })
      }
      return h(CooperationActionColumn, {
        availability: row.actionAvailability,
        onSelect: (action: CooperationActionKey) => handleCooperationAction(action, row)
      })
    }
  }
])

const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') fetchData()
}
onMounted(async () => {
  await initFilterOptions()
  if (!channelOptions.value.length) fetchChannelOptions()
  if (!recruiterOptions.value.length) fetchRecruiterOptions()
  fetchData()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})
onUnmounted(() => document.removeEventListener('visibilitychange', handleVisibilityChange))
</script>

<style scoped>
.cooperation-page {
  gap: 22px;
}

.notice-panel {
  position: relative;
  min-height: 132px;
  padding: 26px 32px;
  overflow: hidden;
  border-radius: var(--radius-lg);
  background: linear-gradient(100deg, #e9fff6 0%, #effcff 58%, #f8fffd 100%);
  border: 1px solid rgba(16, 185, 129, 0.12);
}

.notice-copy {
  max-width: 820px;
}

.notice-copy h2 {
  margin: 0 0 10px;
  font-size: 20px;
  color: var(--text-primary);
}

.notice-copy p {
  margin: 4px 0;
  color: var(--text-secondary);
  line-height: 1.55;
}

.notice-visual {
  position: absolute;
  right: 72px;
  top: 26px;
  width: 150px;
  height: 88px;
}

.box {
  position: absolute;
  width: 46px;
  height: 36px;
  border-radius: 8px;
  background: #ffb85c;
  box-shadow: 0 12px 18px rgba(245, 158, 11, 0.18);
  transform: rotate(18deg);
}

.box.top {
  right: 40px;
  top: 0;
}

.box.mid {
  right: 78px;
  top: 18px;
  background: #ffd18a;
}

.hand {
  position: absolute;
  right: 0;
  bottom: 4px;
  width: 112px;
  height: 38px;
  border-radius: 999px;
  background: linear-gradient(90deg, #ffd3b0, #f7b383);
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

.batch-preview-card {
  margin-bottom: 16px;
}

.batch-alert,
.batch-actions {
  margin-top: 12px;
}

.hidden-file {
  display: none;
}

:deep(.cell-stack) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  line-height: 1.35;
  color: var(--text-secondary);
}

:deep(.cell-stack.strong span:first-child) {
  color: var(--text-primary);
  font-weight: 600;
}

:deep(.cell-stack small) {
  color: var(--text-secondary);
}

:deep(.talent-cell),
:deep(.product-cell) {
  display: flex;
  align-items: center;
  gap: 12px;
}

:deep(.product-cover) {
  width: 52px;
  height: 52px;
  flex: 0 0 52px;
  border-radius: 6px;
  object-fit: cover;
  background: #eef2f7;
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

  .notice-visual {
    display: none;
  }
}
</style>
