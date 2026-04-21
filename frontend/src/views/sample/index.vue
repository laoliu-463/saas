<template>
  <div class="sample-index">
    <n-card title="寄样台" :bordered="false">
      <n-space style="margin-bottom: 16px;">
        <n-button v-if="canApply" type="primary" @click="$router.push('/sample/apply')">寄样申请</n-button>
      </n-space>

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
    </n-card>

    <SampleDetail v-model:show="showDetail" :sample-id="currentSampleId" @refresh="fetchData" />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, onUnmounted, reactive, ref } from 'vue';
import { NButton, NPopconfirm, useMessage } from 'naive-ui';
import { useAuthStore } from '../../stores/auth';
import { ROLE_CODES } from '../../constants/rbac';
import { getSamplePage, deleteSample } from '../../api/sample';
import SampleDetail from './SampleDetail.vue';

const message = useMessage();
const authStore = useAuthStore();

const canApply =
  authStore.isAdmin ||
  authStore.roleCodes.includes(ROLE_CODES.CHANNEL_LEADER) ||
  authStore.roleCodes.includes(ROLE_CODES.CHANNEL_STAFF);

const loading = ref(false);
const activeTab = ref('PENDING_AUDIT');
const data = ref<any[]>([]);
const showDetail = ref(false);
const currentSampleId = ref('');

const tabList = [
  { label: '待审核', value: 'PENDING_AUDIT' },
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

const doDeleteSample = async (id: string) => {
  try {
    await deleteSample(id);
    message.success('寄样单删除成功');
    fetchData();
  } catch (error: any) {
    message.error(error?.message || '删除失败');
  }
};

const columns = [
  { title: '寄样编号', key: 'id' },
  { title: '商品信息', key: 'productName', render: (row: any) => row.productName || row.product?.productName || '-' },
  { title: '申请达人', key: 'talentName', render: (row: any) => row.talentNickname || row.talentName || row.talent?.talentName || '-' },
  {
    title: '达人粉丝',
    key: 'talentFansCount',
    render: (row: any) => (row.talentFansCount ? `${(row.talentFansCount / 10000).toFixed(1)}万` : '-')
  },
  { title: '申请数量', key: 'quantity' },
  { title: '申请时间', key: 'createTime' },
  {
    title: '操作',
    key: 'actions',
    render(row: any) {
      const actions = [
        h(
          NButton,
          { size: 'small', type: 'info', onClick: () => openDetail(row) },
          { default: () => '详情处理' }
        )
      ];

      if (row.status === 'PENDING_AUDIT' || row.status === 'REJECTED') {
        actions.push(
          h(
            NPopconfirm,
            { onPositiveClick: () => doDeleteSample(row.id) },
            {
              trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
              default: () => '确认要删除这条寄样记录吗？不可恢复！'
            }
          )
        );
      }
      return h('div', { style: 'display: flex; gap: 8px;' }, actions);
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
.sample-index {
  min-height: 100%;
}
</style>
