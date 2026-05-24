<template>
  <div class="ops-shipping app-page" data-testid="ops-shipping-page">
    <PageHeader
      title="寄样发货台"
      description="集中处理待发货及后续物流中的寄样单，录入单号、跟踪签收进度，并处理物流异常。"
    />

    <n-alert
      v-if="permissionHint"
      type="warning"
      :bordered="false"
      class="sample-permission-hint"
      data-testid="ops-shipping-permission-hint"
    >
      {{ permissionHint }}
    </n-alert>

    <div v-if="activeTab === 'PENDING_SHIP'" class="shipping-actions">
      <n-space>
        <n-button type="primary" data-testid="ops-logistics-import" @click="showImportModal = true">
          批量导入物流单号
        </n-button>
        <n-button @click="triggerFileInput">
          批量发货（Excel 预览）
        </n-button>
        <n-button v-if="canExportSamples" @click="handleExport">导出发货单</n-button>
      </n-space>
      <input
        ref="fileInputRef"
        type="file"
        accept=".xlsx,.xls,.csv"
        style="display: none"
        @change="handleFileChange"
      />
    </div>

    <div class="shipping-actions" v-else-if="canExportSamples">
      <n-space>
        <n-button @click="handleExport">导出列表</n-button>
      </n-space>
    </div>

    <div v-if="batchItems.length > 0" class="batch-preview-card">
      <n-card title="批量发货预览" size="small" class="app-panel">
        <n-alert v-if="parseErrors.length > 0" type="warning" title="解析警告" style="margin-bottom: 12px">
          <div v-for="(err, i) in parseErrors" :key="i">{{ err }}</div>
        </n-alert>
        <n-data-table
          :columns="batchColumns"
          :data="batchItems"
          data-testid="ops-shipping-batch-preview"
          :max-height="300"
          size="small"
        />
        <n-space style="margin-top: 12px">
          <n-button type="primary" :loading="batchSubmitting" @click="submitBatch">
            确认发货（{{ batchItems.length }} 条）
          </n-button>
          <n-button @click="clearBatch">取消</n-button>
        </n-space>
      </n-card>
    </div>

    <div class="shipping-table-card app-panel app-table-shell">
      <n-tabs v-model:value="activeTab" type="line" animated @update:value="handleTabChange">
        <n-tab-pane v-for="tab in tabList" :key="tab.value" :name="tab.value" :tab="tab.label">
          <n-data-table
            remote
            data-testid="ops-shipping-table"
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
    <SampleLogisticsImportModal v-model:show="showImportModal" @success="fetchData" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue';
import { NButton, NTag, useMessage } from 'naive-ui';
import { getSamplePage, batchShipSamples, exportSamples } from '../../api/sample';
import PageHeader from '../../components/PageHeader.vue';
import SampleDetail from '../sample/SampleDetail.vue';
import SampleLogisticsImportModal from '../sample/SampleLogisticsImportModal.vue';
import { parseBatchShipRows, type BatchShipItem, type BatchShipRow } from '../../utils/shippingBatch';
import { createPaginationState, normalizePageSize } from '../../utils/pagination';
import { useAuthStore } from '../../stores/auth';
import { canExportSamplesByRole, OPS_SHIPPING_TABS } from '../sample/sample-permissions';
import { handleApiFailure } from '../../utils/requestError';

const message = useMessage();
const authStore = useAuthStore();
const loading = ref(false);
const activeTab = ref('PENDING_SHIP');
const data = ref<any[]>([]);

const tabList = OPS_SHIPPING_TABS;

const pagination = reactive(createPaginationState());

const showDetail = ref(false);
const showImportModal = ref(false);
const currentSampleId = ref('');
const canExportSamples = computed(() => canExportSamplesByRole(authStore.roleCodes));

// Batch shipping state
const fileInputRef = ref<HTMLInputElement | null>(null);
const batchItems = ref<BatchShipItem[]>([]);
const parseErrors = ref<string[]>([]);
const batchSubmitting = ref(false);
const permissionHint = ref('');

const batchColumns = [
  { title: '寄样单号', key: 'requestNo' },
  { title: '物流公司', key: 'shipperCode', render: (row: BatchShipItem) => row.shipperCode || '-' },
  { title: '物流单号', key: 'trackingNo' }
];

const openDetail = (row: any) => {
  currentSampleId.value = row.id;
  showDetail.value = true;
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getSamplePage({
      page: pagination.page,
      size: pagination.pageSize,
      status: activeTab.value
    });
    const responseData = res?.data || res;
    if (responseData?.records && Array.isArray(responseData.records)) {
      data.value = responseData.records;
      pagination.itemCount = responseData.total || 0;
      permissionHint.value = '';
    } else {
      data.value = [];
      pagination.itemCount = 0;
    }
  } catch (error: any) {
    data.value = [];
    pagination.itemCount = 0;
    handleApiFailure(error, {
      onPermissionHint: (msg) => { permissionHint.value = msg; },
      permissionFallback: '当前角色无权查看此物流列表',
      onFallback: (msg) => message.error(msg),
      fallbackMessage: '获取物流列表失败'
    });
  } finally {
    loading.value = false;
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
  pagination.pageSize = normalizePageSize(pageSize);
  pagination.page = 1;
  fetchData();
};

const triggerFileInput = () => {
  fileInputRef.value?.click();
};

const handleFileChange = async (e: Event) => {
  const target = e.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  parseErrors.value = [];

  try {
    const XLSX = await import('xlsx');
    const buf = await file.arrayBuffer();
    const wb = XLSX.read(buf, { type: 'array' });
    const ws = wb.Sheets[wb.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json<BatchShipRow>(ws, { header: 1 });
    const { items, errors } = parseBatchShipRows(rows);

    if (items.length === 0) {
      message.warning('未解析到有效数据行，请检查 Excel 格式（第1列：寄样单号，第2列：物流公司，第3列：单号）');
      target.value = '';
      return;
    }

    batchItems.value = items;
    parseErrors.value = errors;
    if (errors.length > 0) {
      message.warning(`解析完成，${errors.length} 行有问题`);
    } else {
      message.success(`解析成功，共 ${items.length} 条待发货`);
    }
  } catch (err: any) {
    message.error('Excel 解析失败：' + (err?.message || '未知错误'));
  }

  target.value = '';
};

const submitBatch = async () => {
  if (batchItems.value.length === 0) return;
  batchSubmitting.value = true;
  try {
    const res = await batchShipSamples({ items: batchItems.value });
    const result = res?.data || res;
    const success = result?.success ?? 0;
    const fail = result?.fail ?? 0;
    message.success(`批量发货完成：成功 ${success} 条，失败 ${fail} 条`);
    clearBatch();
    fetchData();
  } catch (error: any) {
    message.error(error?.message || '批量发货失败');
  } finally {
    batchSubmitting.value = false;
  }
};

const clearBatch = () => {
  batchItems.value = [];
  parseErrors.value = [];
};

const handleExport = async () => {
  try {
    const response = await exportSamples({ status: activeTab.value }) as any;
    const blob = response instanceof Blob ? response : response?.data;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `shipping-${activeTab.value}-${Date.now()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (error: any) {
    handleApiFailure(error, {
      onPermissionHint: (msg) => { permissionHint.value = msg; },
      permissionFallback: '当前角色无权导出寄样数据',
      onFallback: (msg) => message.error(msg),
      fallbackMessage: '导出失败'
    });
  }
};

const statusTagMap: Record<string, { type: 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error'; label: string }> = {
  PENDING_SHIP: { type: 'warning', label: '待发货' },
  SHIPPED: { type: 'info', label: '快递中' },
  PENDING_TASK: { type: 'primary', label: '待交作业' },
  FINISHED: { type: 'success', label: '已完成' },
  REJECTED: { type: 'error', label: '已拒绝' },
  CLOSED: { type: 'default', label: '已关闭' }
};

const columns = [
  { title: '寄样编号', key: 'requestNo', render: (row: any) => row.requestNo || row.id || '-' },
  { title: '商品', key: 'productName', render: (row: any) => row.productName || row.product?.productName || '-' },
  { title: '达人', key: 'talentName', render: (row: any) => row.talentNickname || row.talentName || row.talent?.talentName || '-' },
  { title: '申请渠道', key: 'channelUserName', render: (row: any) => row.channelUserName || '-' },
  { title: '数量', key: 'quantity' },
  { title: '快递单号', key: 'trackingNo', render: (row: any) => row.trackingNo || '-' },
  { title: '物流状态', key: 'logisticsStatusName', render: (row: any) => row.logisticsStatusName || row.logisticsStatus || '-' },
  { title: '最近同步', key: 'logisticsLastQueryAt', render: (row: any) => row.logisticsLastQueryAt || '-' },
  {
    title: '状态',
    key: 'status',
    render: (row: any) => {
      const tag = statusTagMap[row.status] || { type: 'default', label: row.status };
      return h(NTag, { type: tag.type, size: 'small', bordered: false }, { default: () => tag.label });
    }
  },
  { title: '申请时间', key: 'createTime' },
  {
    title: '操作',
    key: 'actions',
    render(row: any) {
      return h(
        NButton,
        { size: 'small', type: 'info', onClick: () => openDetail(row) },
        { default: () => '查看处理' }
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
.sample-permission-hint {
  margin-bottom: var(--content-gap);
}

.shipping-actions {
  margin-bottom: var(--content-gap);
}

.batch-preview-card {
  margin-bottom: var(--content-gap);
}
</style>
