<template>
  <div class="activity-list">
    <n-card title="团长活动列表" :bordered="false">
      <n-space style="margin-bottom: 16px" wrap>
        <n-select v-model:value="filters.status" :options="statusOptions" style="width: 180px" />
        <n-select v-model:value="filters.searchType" :options="searchTypeOptions" style="width: 180px" />
        <n-select v-model:value="filters.sortType" :options="sortTypeOptions" style="width: 140px" />
        <n-input
          v-model:value="filters.activityInfo"
          clearable
          placeholder="活动名称或活动ID"
          style="width: 260px"
          @keydown.enter="handleQuery"
        />
        <n-button type="primary" :loading="loading" @click="handleQuery">查询</n-button>
      </n-space>

      <n-alert v-if="isMockData" type="warning" :show-icon="false" style="margin-bottom: 16px">
        当前为 Mock 测试环境，活动数据来自后端 Mock 服务。
      </n-alert>

      <n-data-table
        :columns="columns"
        :data="activityList"
        :loading="loading"
        :pagination="pagination"
        remote
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue';
import { NTag, useMessage } from 'naive-ui';
import { getColonelActivityPage } from '../../api/product';

const message = useMessage();
const loading = ref(false);
const isMockData = ref(false);
const activityList = ref<any[]>([]);

const filters = reactive({
  status: 0,
  searchType: 0,
  sortType: 1,
  activityInfo: ''
});

const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20]
});

const statusOptions = [
  { label: '任意状态', value: 0 },
  { label: '未上线', value: 1 },
  { label: '报名未开始', value: 2 },
  { label: '报名中', value: 3 },
  { label: '推广未开始', value: 4 },
  { label: '推广中', value: 5 },
  { label: '报名结束', value: 7 }
];

const searchTypeOptions = [
  { label: '创建时间', value: 0 },
  { label: '报名开始时间', value: 1 },
  { label: '报名结束时间', value: 2 }
];

const sortTypeOptions = [
  { label: '降序', value: 1 },
  { label: '升序', value: 0 }
];

const formatCategories = (value: unknown) => {
  if (!value) return '-';
  if (typeof value === 'string') return value;
  if (typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>)
      .map(([k, v]) => `${k}: ${String(v)}`)
      .join(' / ');
  }
  return String(value);
};

const statusTagType = (status: number) => {
  if (status === 3 || status === 5) return 'success';
  if (status === 4 || status === 2) return 'warning';
  if (status === 7) return 'default';
  return 'info';
};

const columns = computed(() => [
  { title: '活动ID', key: 'activityId', width: 120 },
  { title: '活动名称', key: 'activityName', minWidth: 220 },
  {
    title: '活动状态',
    key: 'statusText',
    width: 120,
    render: (row: any) => h(NTag, { type: statusTagType(Number(row.status || 0)), bordered: false }, { default: () => row.statusText || '-' })
  },
  { title: '报名开始时间', key: 'applicationStartTime', width: 170 },
  { title: '报名结束时间', key: 'applicationEndTime', width: 170 },
  { title: '活动开始时间', key: 'activityStartTime', width: 170 },
  { title: '活动结束时间', key: 'activityEndTime', width: 170 },
  { title: '团长百应ID', key: 'colonelBuyinId', width: 160 },
  {
    title: '类目限制',
    key: 'categoriesLimit',
    minWidth: 220,
    render: (row: any) => formatCategories(row.categoriesLimit)
  }
]);

const fetchData = async () => {
  loading.value = true;
  try {
    const res: any = await getColonelActivityPage({
      status: filters.status,
      searchType: filters.searchType,
      sortType: filters.sortType,
      page: pagination.page,
      pageSize: pagination.pageSize,
      activityInfo: filters.activityInfo?.trim() || undefined
    });

    const data = res?.data || {};
    activityList.value = Array.isArray(data.activityList) ? data.activityList : [];
    pagination.itemCount = Number(data.total || activityList.value.length || 0);
    isMockData.value = Boolean(data.mock);

    if (pagination.page === 1) {
      message.success('活动调用成功');
    }
  } catch (error: any) {
    activityList.value = [];
    pagination.itemCount = 0;
    isMockData.value = false;
    message.error(error?.response?.data?.msg || error?.message || '活动调用失败，请稍后重试');
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
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

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.activity-list {
  min-height: 100%;
}
</style>
