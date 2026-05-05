<template>
  <div class="sample-kanban-page">
    <PageHeader title="寄样台" description="管理寄样全流程：审核 → 发货 → 签收 → 交作业。">
      <template #actions>
        <n-button type="primary" @click="$router.push('/sample/apply')">申请寄样</n-button>
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
import { h, onMounted, onUnmounted, reactive, ref } from 'vue';
import { NButton, useMessage } from 'naive-ui';
import { getSamplePage } from '../../api/sample';
import PageHeader from '../../components/PageHeader.vue';
import SampleDetail from './SampleDetail.vue';

const message = useMessage();
const loading = ref(false);
const activeTab = ref('PENDING_AUDIT');
const data = ref<any[]>([]);

const tabList = [
  { label: '待审核', value: 'PENDING_AUDIT' },
  { label: '待发货', value: 'PENDING_SHIP' },
  { label: '快递中', value: 'SHIPPED' },
  { label: '待交作业', value: 'PENDING_TASK' },
  { label: '已完成', value: 'FINISHED' }
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
  { title: '数量', key: 'quantity' },
  { title: '快递单号', key: 'trackingNo', render: (row: any) => row.trackingNo || '-' },
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
