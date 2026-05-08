<template>
  <div class="ops-shipping">
    <PageHeader
      title="寄样发货台"
      description="集中处理待发货及后续物流中的寄样单，录入单号、跟踪签收进度，并处理物流异常。"
    />

    <div class="shipping-actions" v-if="activeTab === 'PENDING_SHIP'">
      <n-space>
        <n-button type="primary" @click="triggerFileInput">
          批量发货（Excel）
        </n-button>
        <n-button @click="handleExport">导出发货单</n-button>
      </n-space>
      <input
        ref="fileInputRef"
        type="file"
        accept=".xlsx,.xls,.csv"
        style="display: none"
        @change="handleFileChange"
      />
    </div>

    <div class="shipping-actions" v-else>
      <n-space>
        <n-button @click="handleExport">导出列表</n-button>
      </n-space>
    </div>

    <div v-if="batchItems.length > 0" class="batch-preview-card">
      <n-card title="批量发货预览" size="small">
        <n-alert v-if="parseErrors.length > 0" type="warning" title="解析警告" style="margin-bottom: 12px">
          <div v-for="(err, i) in parseErrors" :key="i">{{ err }}</div>
        </n-alert>
        <n-data-table
          :columns="batchColumns"
          :data="batchItems"
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
import { h, onMounted, onUnmounted, reactive, ref } from 'vue';
import { NButton, NTag, useMessage } from 'naive-ui';
import { getSamplePage, batchShipSamples, exportSamples } from '../../api/sample';
import PageHeader from '../../components/PageHeader.vue';
import SampleDetail from '../sample/SampleDetail.vue';
import * as XLSX from 'xlsx';

const message = useMessage();
const loading = ref(false);
const activeTab = ref('PENDING_SHIP');
const data = ref<any[]>([]);

const tabList = [
  { label: '待发货', value: 'PENDING_SHIP' },
  { label: '快递中', value: 'SHIPPED' },
  { label: '待交作业', value: 'PENDING_TASK' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' }
];

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
});

const showDetail = ref(false);
const currentSampleId = ref('');

// Batch shipping state
const fileInputRef = ref<HTMLInputElement | null>(null);
const batchItems = ref<{ requestNo: string; trackingNo: string }[]>([]);
const parseErrors = ref<string[]>([]);
const batchSubmitting = ref(false);

const batchColumns = [
  { title: '寄样单号', key: 'requestNo' },
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
    } else {
      data.value = [];
      pagination.itemCount = 0;
    }
  } catch (error: any) {
    message.error(error?.message || '获取物流列表失败');
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
  pagination.pageSize = pageSize;
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
    const buf = await file.arrayBuffer();
    const wb = XLSX.read(buf, { type: 'array' });
    const ws = wb.Sheets[wb.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json<(string | number | undefined)[]>(ws, { header: 1 });

    const items: { requestNo: string; trackingNo: string }[] = [];
    const errors: string[] = [];

    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      if (i === 0) {
        // Skip header row if it looks like a header
        const first = String(row[0] ?? '').trim();
        if (first === '寄样单ID' || first === '寄样单号' || first === 'requestNo' || first === 'id') continue;
      }
      const requestNo = String(row[0] ?? '').trim();
      const carrier = String(row[1] ?? '').trim();
      const trackingNo = String(row[2] ?? '').trim();

      if (!requestNo) continue; // Skip empty rows
      if (!trackingNo) {
        errors.push(`第 ${i + 1} 行：物流单号为空，已跳过`);
        continue;
      }

      items.push({ requestNo, trackingNo: carrier ? `${carrier} ${trackingNo}` : trackingNo });
    }

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
    message.error(error?.message || '导出失败');
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
.ops-shipping {
  max-width: 100%;
}

.shipping-actions {
  margin-bottom: 16px;
}

.batch-preview-card {
  margin-bottom: 16px;
}

.shipping-table-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 4px 16px 16px;
  box-shadow: var(--shadow-card);
}
</style>
