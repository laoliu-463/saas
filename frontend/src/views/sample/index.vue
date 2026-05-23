<template>
  <div class="sample-kanban-page app-page" data-testid="sample-page">
    <PageHeader :title="pageTitle" :description="pageDesc">
      <template #actions>
        <n-button v-if="canApplySample" type="primary" data-testid="sample-apply" @click="$router.push('/sample/apply')">申请寄样</n-button>
        <n-button v-if="canExportSamples" type="info" data-testid="sample-export" @click="handleExport">导出寄样单</n-button>
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
          @click="handleBatchReject"
        >
          批量拒绝
        </n-button>
        <n-button :loading="loading" data-testid="sample-refresh" @click="fetchData">刷新数据</n-button>
      </template>
    </PageHeader>

    <div class="shipping-table-card app-panel">
      <div class="sample-filter-toolbar">
        <n-select
          v-model:value="filters.channelUserId"
          clearable
          filterable
          remote
          :options="channelOptions"
          :loading="channelOptionsLoading"
          placeholder="渠道负责人"
          class="sample-filter-select"
          data-testid="sample-channel-filter"
          @search="handleChannelSearch"
          @update:value="handleFilterChange"
        />
        <n-select
          v-model:value="filters.recruiterUserId"
          clearable
          filterable
          remote
          :options="recruiterOptions"
          :loading="recruiterOptionsLoading"
          placeholder="招商负责人"
          class="sample-filter-select"
          data-testid="sample-recruiter-filter"
          @search="handleRecruiterSearch"
          @update:value="handleFilterChange"
        />
        <n-button secondary data-testid="sample-filter-reset" @click="resetUserFilters">重置筛选</n-button>
      </div>
      <n-tabs v-model:value="activeTab" type="line" animated @update:value="handleTabChange">
        <n-tab-pane v-for="tab in tabList" :key="tab.value" :name="tab.value" :tab="tab.label">
          <n-data-table
            remote
            data-testid="sample-table"
            :columns="columns"
            :data="data"
            :loading="tableLoading"
            :pagination="pagination"
            :row-key="rowKey"
            :checked-row-keys="checkedRowKeys"
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
            @update:checked-row-keys="handleCheckedRowKeys"
          />
        </n-tab-pane>
      </n-tabs>
    </div>

    <SampleDetail v-model:show="showDetail" :sample-id="currentSampleId" @refresh="fetchData" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue';
import { NButton, NTag, useMessage } from 'naive-ui';
import { batchApproveSamples, batchRejectSamples, exportSamples, getSamplePage } from '../../api/sample';
import PageHeader from '../../components/PageHeader.vue';
import SampleDetail from './SampleDetail.vue';
import type { SampleItem } from '../../types';
import { useAuthStore } from '../../stores/auth';
import { ROLE_CODES } from '../../constants/rbac';
import { createPaginationState, normalizePageSize } from '../../utils/pagination';
import { useDelayedFlag } from '../../utils/delayedFlag';
import { canExportSamplesByRole } from './sample-permissions';
import { loadSampleChannelOptions, loadSampleRecruiterOptions } from './sample-user-filter-options';

const message = useMessage();
const authStore = useAuthStore();
const loading = ref(false);
const tableLoading = useDelayedFlag(loading, 200);
const EXPORT_ROW_LIMIT = 20000;
const batchSubmitting = ref(false);

const isOpsStaffOnly = computed(() => {
  const roles = authStore.roleCodes;
  return roles.includes(ROLE_CODES.OPS_STAFF) && !authStore.isAdmin;
});

const ALL_TABS = [
  { label: '待审核', value: 'PENDING_AUDIT' },
  { label: '待发货', value: 'PENDING_SHIP' },
  { label: '快递中', value: 'SHIPPED' },
  { label: '待交作业', value: 'PENDING_TASK' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' }
];

const OPS_HIDDEN_TABS = new Set(['PENDING_AUDIT']);

const tabList = computed(() =>
  isOpsStaffOnly.value
    ? ALL_TABS.filter((t) => !OPS_HIDDEN_TABS.has(t.value))
    : ALL_TABS
);

const activeTab = ref(isOpsStaffOnly.value ? 'PENDING_SHIP' : 'PENDING_AUDIT');
const data = ref<SampleItem[]>([]);
const checkedRowKeys = ref<Array<string | number>>([]);
const filters = reactive({
  channelUserId: null as string | null,
  recruiterUserId: null as string | null
});
const channelOptions = ref<Array<{ label: string; value: string }>>([]);
const recruiterOptions = ref<Array<{ label: string; value: string }>>([]);
const channelOptionsLoading = ref(false);
const recruiterOptionsLoading = ref(false);

const pagination = reactive(createPaginationState());

const showDetail = ref(false);
const currentSampleId = ref('');

const isBizStaffOnly = computed(() => {
  const roles = authStore.roleCodes;
  return roles.includes(ROLE_CODES.BIZ_STAFF) && !roles.includes(ROLE_CODES.BIZ_LEADER) && !authStore.isAdmin;
});

const isChannelStaffOnly = computed(() => {
  const roles = authStore.roleCodes;
  return roles.includes(ROLE_CODES.CHANNEL_STAFF) && !roles.includes(ROLE_CODES.CHANNEL_LEADER) && !authStore.isAdmin;
});

const pageTitle = computed(() => {
  if (isOpsStaffOnly.value) return '寄样发货台';
  if (isBizStaffOnly.value) return '我的审核工作台';
  if (isChannelStaffOnly.value) return '我的寄样申请';
  return '寄样台';
});

const pageDesc = computed(() => {
  if (isOpsStaffOnly.value) return '处理待发货寄样单，录入物流单号并跟踪后续签收与履约状态。';
  if (isBizStaffOnly.value) return '处理由我负责商品的寄样申请，并跟进后续发货与履约状态。';
  if (isChannelStaffOnly.value) return '为我的达人申请商品寄样，跟进物流进度并敦促交作业。';
  return '管理寄样全流程：审核 → 发货 → 签收 → 交作业。';
});

const canApplySample = computed(() => {
  const roles = authStore.roleCodes;
  return roles.includes(ROLE_CODES.CHANNEL_LEADER) || roles.includes(ROLE_CODES.CHANNEL_STAFF);
});
const canExportSamples = computed(() => canExportSamplesByRole(authStore.roleCodes));
const canBatchAudit = computed(() => authStore.isAdmin || authStore.roleCodes.includes(ROLE_CODES.BIZ_STAFF));
const showBatchAuditActions = computed(() => canBatchAudit.value && activeTab.value === 'PENDING_AUDIT');
const selectedRequestNos = computed(() => checkedRowKeys.value.map((key) => String(key)).filter(Boolean));

const openDetail = (row: any) => {
  currentSampleId.value = row.id || row.sampleRequestId;
  showDetail.value = true;
};

const rowKey = (row: any) => row.requestNo || row.id || row.sampleRequestId;

const handleCheckedRowKeys = (keys: Array<string | number>) => {
  checkedRowKeys.value = keys;
};

const buildSampleFilterParams = () => ({
  status: activeTab.value,
  channelUserId: filters.channelUserId || undefined,
  recruiterUserId: filters.recruiterUserId || undefined
});

const fetchChannelOptions = async (keyword = '') => {
  channelOptionsLoading.value = true;
  try {
    channelOptions.value = await loadSampleChannelOptions(keyword);
  } catch (error: any) {
    message.error(error?.message || '加载渠道负责人失败');
  } finally {
    channelOptionsLoading.value = false;
  }
};

const fetchRecruiterOptions = async (keyword = '') => {
  recruiterOptionsLoading.value = true;
  try {
    recruiterOptions.value = await loadSampleRecruiterOptions(keyword);
  } catch (error: any) {
    message.error(error?.message || '加载招商负责人失败');
  } finally {
    recruiterOptionsLoading.value = false;
  }
};

const handleChannelSearch = (keyword: string) => {
  fetchChannelOptions(keyword);
};

const handleRecruiterSearch = (keyword: string) => {
  fetchRecruiterOptions(keyword);
};

const handleFilterChange = () => {
  pagination.page = 1;
  checkedRowKeys.value = [];
  fetchData();
};

const resetUserFilters = () => {
  filters.channelUserId = null;
  filters.recruiterUserId = null;
  handleFilterChange();
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res: any = await getSamplePage({
      page: pagination.page,
      size: pagination.pageSize,
      ...buildSampleFilterParams()
    });
    const responseData = res?.data || res;
    if (responseData?.records && Array.isArray(responseData.records)) {
      data.value = responseData.records;
      pagination.itemCount = responseData.total || 0;
    } else {
      data.value = [];
      pagination.itemCount = 0;
    }
  } catch (error: any) {
    message.error(error?.message || '获取寄样列表失败');
  } finally {
    loading.value = false;
  }
};

const handleExport = async () => {
  if (!canExportSamples.value) {
    message.warning('当前角色无权导出寄样单');
    return;
  }
  if (!data.value.length) {
    message.warning('暂无寄样单可导出');
    return;
  }
  if (pagination.itemCount > EXPORT_ROW_LIMIT) {
    message.warning(`当前筛选结果 ${pagination.itemCount} 条，超过 ${EXPORT_ROW_LIMIT} 条导出上限，请缩小状态或筛选范围`);
    return;
  }
  try {
    const res: any = await exportSamples(buildSampleFilterParams());
    const filename = `samples-${activeTab.value.toLowerCase()}-${new Date().toISOString().slice(0, 10)}.csv`;
    const url = window.URL.createObjectURL(new Blob([res]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.parentNode?.removeChild(link);
    window.URL.revokeObjectURL(url);
    message.success('寄样单导出成功');
  } catch (error: any) {
    message.error(error?.message || '寄样单导出失败');
  }
};

const batchResultText = (result: any) => {
  const success = result?.success ?? 0;
  const fail = result?.fail ?? 0;
  return `成功 ${success} 条，失败 ${fail} 条`;
};

const handleBatchApprove = async () => {
  if (!selectedRequestNos.value.length) {
    message.warning('请先选择待审核寄样单');
    return;
  }
  batchSubmitting.value = true;
  try {
    const res: any = await batchApproveSamples({
      requestNos: selectedRequestNos.value,
      remark: '批量审批通过'
    });
    const result = res?.data || res;
    message.success(`批量通过完成：${batchResultText(result)}`);
    checkedRowKeys.value = [];
    fetchData();
  } catch (error: any) {
    message.error(error?.message || '批量通过失败');
  } finally {
    batchSubmitting.value = false;
  }
};

const handleBatchReject = async () => {
  if (!selectedRequestNos.value.length) {
    message.warning('请先选择待审核寄样单');
    return;
  }
  const remark = window.prompt('请输入批量拒绝原因')?.trim();
  if (!remark) {
    message.warning('批量拒绝原因不能为空');
    return;
  }
  batchSubmitting.value = true;
  try {
    const res: any = await batchRejectSamples({
      requestNos: selectedRequestNos.value,
      remark
    });
    const result = res?.data || res;
    message.success(`批量拒绝完成：${batchResultText(result)}`);
    checkedRowKeys.value = [];
    fetchData();
  } catch (error: any) {
    message.error(error?.message || '批量拒绝失败');
  } finally {
    batchSubmitting.value = false;
  }
};

const handleTabChange = () => {
  pagination.page = 1;
  checkedRowKeys.value = [];
  fetchData();
};

const handlePageChange = (page: number) => {
  pagination.page = page;
  fetchData();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = normalizePageSize(pageSize);
  pagination.page = 1;
  fetchData();
};

const columns = computed(() => [
  ...(showBatchAuditActions.value
    ? [{
        type: 'selection' as const,
        disabled: (row: any) => !row.requestNo
      }]
    : []),
  { title: '寄样编号', key: 'id', render: (row: any) => row.id || row.sampleRequestId },
  { title: '商品', key: 'productName', render: (row: any) => row.productName || row.product?.productName || '-' },
  { title: '达人', key: 'talentName', render: (row: any) => row.talentNickname || row.talentName || row.talent?.talentName || '-' },
  {
    title: '资格',
    key: 'eligibility',
    render: (row: SampleItem) => {
      if (row.eligibilityCheck?.passed === false) {
      return h(
        NTag,
        { type: 'warning', size: 'small', bordered: false, 'data-testid': 'sample-status' },
        { default: () => '破格申请' }
      );
      }
      if (row.eligibilityCheck?.passed === true) {
        return h(
        NTag,
        { type: 'success', size: 'small', bordered: false, 'data-testid': 'sample-status' },
        { default: () => '达标' }
      );
      }
      return '-';
    }
  },
  { title: '数量', key: 'quantity' },
  { title: '快递单号', key: 'trackingNo', render: (row: any) => row.trackingNo || '-' },
  {
    title: '申请说明',
    key: 'applyReason',
    ellipsis: { tooltip: true },
    render: (row: SampleItem) => row.applyReason || row.remark || '-'
  },
  { title: '申请时间', key: 'createTime' },
  {
    title: '操作',
    key: 'actions',
    render(row: any) {
      return h(
        NButton,
        { size: 'small', type: 'info', 'data-testid': 'sample-row', onClick: () => openDetail(row) },
        { default: () => isChannelStaffOnly.value ? '查看详情' : '查看处理' }
      );
    }
  }
]);

const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') {
    fetchData();
  }
};

onMounted(() => {
  fetchChannelOptions();
  fetchRecruiterOptions();
  fetchData();
  document.addEventListener('visibilitychange', handleVisibilityChange);
});

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<style scoped>
.shipping-table-card {
  padding: 4px 16px 16px;
}

.sample-filter-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
}

.sample-filter-select {
  width: 220px;
}
</style>
