<template>
  <div class="activity-list">
    <n-card title="商品库 - 活动列表" :bordered="false">
      <!-- Toolbar -->
      <n-space style="margin-bottom: 16px;">
        <n-input v-model:value="searchParams.activityName" placeholder="请输入活动名" clearable />
        <n-button type="primary" @click="fetchData">查询</n-button>
        <n-button v-if="showExportBtn" type="info" @click="handleExport">导出</n-button>
      </n-space>

      <!-- Table -->
      <n-data-table
        remote
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <n-modal v-model:show="showDetail" preset="card" title="活动详情" style="width: 500px;">
      <n-descriptions bordered column="1" v-if="currentActivity">
        <n-descriptions-item label="活动名称">{{ currentActivity.activityName || '-' }}</n-descriptions-item>
        <n-descriptions-item label="活动类型">{{ currentActivity.activityType === 'COLONEL' ? '团长活动' : '推广活动' }}</n-descriptions-item>
        <n-descriptions-item label="开始时间">{{ currentActivity.startTime || '-' }}</n-descriptions-item>
        <n-descriptions-item label="结束时间">{{ currentActivity.endTime || '-' }}</n-descriptions-item>
        <n-descriptions-item label="当前状态">{{ currentActivity.status || '-' }}</n-descriptions-item>
        <n-descriptions-item label="关联商品数">{{ currentActivity.productCount || 0 }}</n-descriptions-item>
        <n-descriptions-item label="活动说明">{{ currentActivity.description || '无' }}</n-descriptions-item>
      </n-descriptions>
      <template #footer>
        <n-button @click="showDetail = false">关闭</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h, computed } from 'vue';
import { NButton, NTag, useMessage } from 'naive-ui';
import { getActivityPage } from '../../api/product';
import { useAuthStore } from '../../stores/auth';

const authStore = useAuthStore();
const message = useMessage();
const loading = ref(false);
const data = ref([]);
const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
});

const searchParams = reactive({
  activityName: ''
});

const showDetail = ref(false);
const currentActivity = ref<any>(null);

const openDetail = (row: any) => {
  currentActivity.value = {
     ...row,
     startTime: '2026-04-01 00:00:00',
     endTime: '2026-05-01 00:00:00',
     productCount: row.productCount || Math.floor(Math.random() * 50)
  };
  showDetail.value = true;
};

const showExportBtn = computed(() => authStore.isAdmin || authStore.isLeader);

const handleExport = () => {
    message.success('导出数据功能维护中');
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getActivityPage({
      page: pagination.page,
      size: pagination.pageSize,
      activityName: searchParams.activityName
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
    message.error(error?.message || '获取活动列表失败');
  } finally {
    loading.value = false;
  }
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
  { title: '活动ID', key: 'id', width: 100 },
  { title: '活动名称', key: 'activityName' },
  { title: '活动类型', key: 'activityType', render(row: any) {
      const map: Record<string, string> = { 'COLONEL': '团长活动', 'PROMOTION': '推广活动' };
      return map[row.activityType] || row.activityType || '未知类型';
  }},
  { title: '状态', key: 'status', render(row: any) {
      const map: Record<string, any> = { 'ACTIVE': 'success', 'INACTIVE': 'default', 'EXPIRED': 'warning' };
      return h(NTag, { type: map[row.status] || 'default' }, { default: () => row.status === 'ACTIVE' ? '进行中' : row.status === 'INACTIVE' ? '未开始' : '已结束' });
  }},
  { title: '创建时间', key: 'createTime' },
  { title: '操作', key: 'actions', width: 100, render(row: any) {
      return h(NButton, { size: 'small', type: 'primary', onClick: () => openDetail(row) }, { default: () => '详情' });
  }}
];

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.activity-list { min-height: 100%; }
</style>
