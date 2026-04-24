<template>
  <div class="order-list">
    <n-card title="订单明细" :bordered="false">
      <!-- Toolbar -->
      <n-space style="margin-bottom: 16px;">
        <n-date-picker
          v-model:value="dateRange"
          type="daterange"
          clearable
          placeholder="选择日期范围"
          style="width: 250px"
        />
        <n-select v-model:value="searchParams.status" :options="statusOptions" placeholder="状态筛选" style="width: 150px" clearable />
        <n-button type="primary" @click="fetchData">查询</n-button>
        <n-button @click="handleSync" type="success" :loading="syncLoading">手动同步订单</n-button>
        <n-button @click="handleExport" type="info">导出 CSV</n-button>
        <n-button type="warning" :loading="decryptLoading" @click="handleDecrypt">批量解密（{{ checkedRowKeys.length }}）</n-button>
      </n-space>

      <!-- Table -->
      <n-data-table
        remote
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: any) => row.id"
        :checked-row-keys="checkedRowKeys"
        @update:checked-row-keys="handleCheck"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <n-modal v-model:show="showDetail" preset="card" title="订单详情" style="width: 600px;">
       <n-descriptions bordered column="1" v-if="currentOrder">
          <n-descriptions-item label="订单号">{{ currentOrder.id }}</n-descriptions-item>
          <n-descriptions-item label="商品信息">{{ currentOrder.productName || '未知商品' }}</n-descriptions-item>
          <n-descriptions-item label="达人信息">{{ currentOrder.talentName || '未知达人' }}</n-descriptions-item>
          <n-descriptions-item label="金额明细">
              <div>总价: ¥{{ currentOrder.amount || '0.00' }}</div>
              <div style="color: gray; font-size: 12px;">商品价: ¥{{ currentOrder.goodsPrice || currentOrder.amount }} | 佣金: ¥{{ currentOrder.commission || '0.00' }} | 运费: ¥{{ currentOrder.freight || '0.00' }}</div>
          </n-descriptions-item>
          <n-descriptions-item label="收件人信息">{{ currentOrder.receiverName || '张三' }} ({{ currentOrder.receiverPhone || '13800138000' }}) - {{ currentOrder.receiverAddress || '浙江省杭州市xxx' }}</n-descriptions-item>
          <n-descriptions-item label="联系电话">
            <template v-if="decryptMap[currentOrder.id]">
              <DecryptPhoneDisplay :item="decryptMap[currentOrder.id]" />
            </template>
            <template v-else>
              <n-text depth="3">未解密</n-text>
            </template>
          </n-descriptions-item>
          <n-descriptions-item label="快递信息">{{ currentOrder.expressCompany || '顺丰快递' }} - {{ currentOrder.expressNo || 'SF1234567890' }}</n-descriptions-item>
          <n-descriptions-item label="归因来源">
            <n-tag :type="currentOrder.attributionSource === 'EXCLUSIVE_MERCHANT' ? 'warning' : currentOrder.attributionSource === 'EXCLUSIVE_TALENT' ? 'success' : currentOrder.attributionSource === 'PICK_SOURCE_MAPPING' ? 'info' : 'default'" size="small">
              {{ currentOrder.attributionSource === 'EXCLUSIVE_MERCHANT' ? '独家招商' : currentOrder.attributionSource === 'EXCLUSIVE_TALENT' ? '独家达人' : currentOrder.attributionSource === 'PICK_SOURCE_MAPPING' ? '映射归属' : currentOrder.attributionSource || '默认归属' }}
            </n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="创建时间">{{ currentOrder.createTime }}</n-descriptions-item>
       </n-descriptions>
       <template #footer>
          <n-button @click="showDetail = false">关闭</n-button>
       </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted, computed, defineComponent } from 'vue';
import { NButton, NTag, NText, useMessage } from 'naive-ui';
import { getOrderPage, decryptOrders, exportOrders } from '../../api/data';
import { triggerOrderSync } from '../../api/order';
import { useAuthStore } from '../../stores/auth';
import type { DecryptResultItem } from '../../types';

// ---- 解密电话展示子组件 ----
const DecryptPhoneDisplay = defineComponent({
  props: { item: { type: Object as () => DecryptResultItem, required: true } },
  setup(props: { item: DecryptResultItem }) {
    return () => {
      const { item } = props;
      if (!item.isVirtualTel) {
        // 非虚拟号：直接展示 phone
        return h('span', item.phone || '-');
      }
      // 虚拟号：判断是否过期
      const expired = item.expireTime != null && item.expireTime * 1000 < Date.now();
      if (expired) {
        return h(NText, { type: 'warning' }, { default: () => '虚拟号已过期' });
      }
      // 虚拟号未过期
      return h('span', [
        h('span', { style: 'margin-right: 8px' }, `A: ${item.phoneNoA || '-'}`),
        h('span', `B: ${item.phoneNoB || '-'}`),
      ]);
    };
  },
});

// ---- 解密电话列内联渲染 ----
const renderDecryptPhone = (orderId: string) => {
  const item = decryptMap.value[orderId];
  if (!item) return h(NText, { depth: 3 }, { default: () => '未解密' });
  if (!item.isVirtualTel) {
    return h('span', item.phone || '-');
  }
  const expired = item.expireTime != null && item.expireTime * 1000 < Date.now();
  if (expired) {
    return h(NText, { type: 'warning' }, { default: () => '虚拟号已过期' });
  }
  return h('span', [
    h('span', { style: 'margin-right: 8px' }, `A: ${item.phoneNoA || '-'}`),
    h('span', `B: ${item.phoneNoB || '-'}`),
  ]);
};

const authStore = useAuthStore();
const message = useMessage();
const loading = ref(false);
const decryptLoading = ref(false);
const syncLoading = ref(false);

const handleSync = async () => {
  syncLoading.value = true;
  try {
    await triggerOrderSync();
    message.success('已触发同步订单');
    fetchData();
  } catch (error: any) {
    message.error(error?.message || '同步失败');
  } finally {
    syncLoading.value = false;
  }
};

const data = ref([]);
const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
});

const dateRange = ref<[number, number] | null>(null);

const searchParams = reactive({
  status: null
});

const showDetail = ref(false);
const currentOrder = ref<any>(null);

// ---- 批量选择 ----
const checkedRowKeys = ref<string[]>([]);
const handleCheck = (keys: string[]) => {
  checkedRowKeys.value = keys;
};

// ---- 解密数据存储 ----
const decryptMap = ref<Record<string, DecryptResultItem>>({});

// ---- 批量解密 ----
const handleDecrypt = async () => {
  if (checkedRowKeys.value.length === 0) {
    message.warning('请先选择订单');
    return;
  }
  if (checkedRowKeys.value.length > 50) {
    message.warning('单次最多解密 50 条订单');
    return;
  }
  decryptLoading.value = true;
  try {
    const res: any = await decryptOrders(checkedRowKeys.value);
    const results: DecryptResultItem[] = res?.data || res || [];
    const map: Record<string, DecryptResultItem> = {};
    for (const item of results) {
      map[item.orderId] = item;
    }
    decryptMap.value = { ...decryptMap.value, ...map };
    message.success(`成功解密 ${results.length} 条订单`);
  } catch (error: any) {
    const msg = error?.response?.data?.msg || error?.message || '解密失败';
    message.error(msg);
  } finally {
    decryptLoading.value = false;
  }
};

const openDetail = (row: any) => {
  currentOrder.value = row;
  showDetail.value = true;
};

const statusOptions = [
  { label: '已下单', value: 'ORDERED' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已取消', value: 'CANCELLED' }
];

const fetchData = async () => {
  loading.value = true;
  try {
    let startDate, endDate;
    if (dateRange.value) {
      startDate = new Date(dateRange.value[0]).toISOString().split('T')[0];
      endDate = new Date(dateRange.value[1]).toISOString().split('T')[0];
    }

    const res = await getOrderPage({
      page: pagination.page,
      size: pagination.pageSize,
      status: searchParams.status,
      startDate,
      endDate
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
    message.error(error?.message || '获取订单列表失败');
  } finally {
    loading.value = false;
  }
};

const canExport = computed(() =>
  authStore.isAdmin || authStore.isLeader || authStore.userInfo?.roleCodes?.includes('DATA_VIEWER')
);

const handleExport = async () => {
  if (!canExport.value) {
    message.warning('当前角色无权导出订单数据');
    return;
  }
  if (!data.value.length) {
    message.warning('无数据可导出');
    return;
  }
  try {
    let startDate, endDate;
    if (dateRange.value) {
      startDate = new Date(dateRange.value[0]).toISOString().split('T')[0];
      endDate = new Date(dateRange.value[1]).toISOString().split('T')[0];
    }
    const res: any = await exportOrders({ status: searchParams.status, startDate, endDate });
    const url = window.URL.createObjectURL(new Blob([res]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'orders.csv');
    document.body.appendChild(link);
    link.click();
    link.parentNode?.removeChild(link);
    message.success('导出成功');
  } catch (error: any) {
    message.error('导出失败');
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
  { type: 'selection' },
  { title: '订单号', key: 'id' },
  { title: '商品名称', key: 'productName' },
  { title: '达人名称', key: 'talentName' },
  { title: '归因来源', key: 'attributionSource', render(row: any) {
      const source = row.attributionSource;
      const label = source === 'EXCLUSIVE_MERCHANT' ? '独家招商' : source === 'EXCLUSIVE_TALENT' ? '独家达人' : source === 'PICK_SOURCE_MAPPING' ? '映射归属' : source || '默认归属';
      let type: 'success' | 'warning' | 'info' | 'default' = 'default';
      if (source === 'EXCLUSIVE_MERCHANT') type = 'warning';
      else if (source === 'EXCLUSIVE_TALENT') type = 'success';
      else if (source === 'PICK_SOURCE_MAPPING') type = 'info';
      return h(NTag, { type, size: 'small' }, { default: () => label });
  }},
  { title: '金额', key: 'amount' },
  { title: '状态', key: 'status', render(row: any) {
      const opt = statusOptions.find(o => o.value === row.status);
      return h(NTag, { type: row.status === 'FINISHED' ? 'success' : 'default' }, { default: () => opt ? opt.label : row.status });
  }},
  { title: '联系电话', key: 'decryptPhone', width: 200, render(row: any) {
      return renderDecryptPhone(row.id);
  }},
  { title: '创建时间', key: 'createTime' },
  { title: '操作', key: 'actions', width: 100, fixed: 'right' as const, render(row: any) {
      return h(NButton, { size: 'small', onClick: () => openDetail(row) }, { default: () => '详情' })
  }}
];

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.order-list { min-height: 100%; }
</style>
