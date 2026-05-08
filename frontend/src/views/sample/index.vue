<template>
  <div class="sample-kanban-page">
    <PageHeader :title="pageTitle" :description="pageDesc">
      <template #actions>
        <n-button v-if="canApplySample" type="primary" @click="$router.push('/sample/apply')">申请寄样</n-button>
        <n-button v-if="canExportSamples" type="info" @click="handleExport">导出寄样单</n-button>
        <n-button :loading="loading" @click="fetchData">刷新数据</n-button>
      </template>
    </PageHeader>

    <div class="shipping-table-card">
      <n-tabs v-model:value="activeTab" type="line" animated @update:value="handleTabChange">
        <n-tab-pane v-for="tab in tabList" :key="tab.value" :name="tab.value" :tab="tab.label">
          <n-data-table
            remote
            :columns="columns"
            :data="data"
            :loading="loading"
            :pagination="pagination"
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
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
import { exportSamples, getSamplePage } from '../../api/sample';
import PageHeader from '../../components/PageHeader.vue';
import SampleDetail from './SampleDetail.vue';
import type { SampleItem } from '../../types';
import { useAuthStore } from '../../stores/auth';
import { ROLE_CODES } from '../../constants/rbac';

const message = useMessage();
const authStore = useAuthStore();
const loading = ref(false);

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

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
});

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
const canExportSamples = computed(() => authStore.isAdmin || authStore.isLeader || isOpsStaffOnly.value);

const openDetail = (row: any) => {
  currentSampleId.value = row.id || row.sampleRequestId;
  showDetail.value = true;
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res: any = await getSamplePage({
      page: pagination.page,
      size: pagination.pageSize,
      status: activeTab.value
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
  try {
    const res: any = await exportSamples({ status: activeTab.value });
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

const handleTabChange = () => {
  pagination.page = 1;
  fetchData();
};

const handlePageChange = (page: number) => {
  pagination.page = page;
  fetchData();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.page = 1;
  fetchData();
};

const columns = [
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
          { type: 'warning', size: 'small', bordered: false },
          { default: () => '破格申请' }
        );
      }
      if (row.eligibilityCheck?.passed === true) {
        return h(
          NTag,
          { type: 'success', size: 'small', bordered: false },
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
        { size: 'small', type: 'info', onClick: () => openDetail(row) },
        { default: () => isChannelStaffOnly.value ? '查看详情' : '查看处理' }
      );
    }
  }
];

const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') {
    fetchData();
  }
};

onMounted(() => {
  fetchData();
  document.addEventListener('visibilitychange', handleVisibilityChange);
});

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<style scoped>
.sample-kanban-page {
  padding: var(--spacing-xl);
}

.shipping-table-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 4px 16px 16px;
  box-shadow: var(--shadow-card);
}
</style>
