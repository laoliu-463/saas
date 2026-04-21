<template>
  <div class="product-list">
    <n-card title="商品库 - 商品列表" :bordered="false">
      <!-- Toolbar -->
      <n-space style="margin-bottom: 16px;">
        <n-input v-model:value="searchParams.productName" placeholder="请输入商品名" clearable />
        <n-select v-model:value="searchParams.status" :options="statusOptions" placeholder="状态筛选" style="width: 150px" clearable />
        <n-button type="primary" @click="fetchData">查询</n-button>
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

    <ProductDetail v-model:show="showDetail" :productId="currentProductId" @refresh="fetchData" />

    <!-- 转链结果弹窗 -->
    <n-modal v-model:show="showPromotionResult" preset="card" title="转链结果" style="width: 500px;">
      <n-descriptions v-if="currentPromotionResult" bordered column="1">
        <n-descriptions-item label="短链接">
          <n-button text type="primary" @click="copyToClipboard(currentPromotionResult.shortLink)">{{ currentPromotionResult.shortLink }}</n-button>
        </n-descriptions-item>
        <n-descriptions-item label="推广链接">
          <n-button text type="primary" @click="copyToClipboard(currentPromotionResult.promoteLink)">{{ currentPromotionResult.promoteLink }}</n-button>
        </n-descriptions-item>
      </n-descriptions>
      <template #footer>
        <n-button @click="showPromotionResult = false">关闭</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue';
import { NButton, NTag, NSpace, useMessage } from 'naive-ui';
import { getProductPage, generatePromotionLink } from '../../api/product';
import { useAuthStore } from '../../stores/auth';
import ProductDetail from './ProductDetail.vue';
import type { PromotionLinkResult } from '../../types';

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
  productName: '',
  status: null
});

const statusOptions = [
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已下架', value: 'OFF_SHELF' }
];

const showDetail = ref(false);
const currentProductId = ref('');

// ---- 转链 ----
const promotionLoading = ref<string | null>(null);
const showPromotionResult = ref(false);
const currentPromotionResult = ref<PromotionLinkResult | null>(null);

const handlePromotionLink = async (row: any) => {
  promotionLoading.value = row.id;
  try {
    const res: any = await generatePromotionLink(row.id);
    currentPromotionResult.value = res?.data || res;
    showPromotionResult.value = true;
    message.success('转链成功');
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '转链失败');
  } finally {
    promotionLoading.value = null;
  }
};

const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text);
    message.success('已复制到剪贴板');
  } catch {
    message.error('复制失败，请手动复制');
  }
};

const openDetail = (row: any) => {
  currentProductId.value = row.id;
  showDetail.value = true;
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getProductPage({
      page: pagination.page,
      size: pagination.pageSize,
      productName: searchParams.productName,
      status: searchParams.status
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
    message.error(error?.message || '获取商品列表失败');
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

const canBizOperate = authStore.isAdmin || authStore.roleCodes.includes('biz_leader');

const columns = [
  { title: '商品ID', key: 'id' },
  { title: '商品名称', key: 'productName' },
  { title: '状态', key: 'status', render(row: any) {
    const opt = statusOptions.find(o => o.value === row.status);
    return h(NTag, { type: row.status === 'APPROVED' ? 'success' : (row.status === 'PENDING' ? 'warning' : 'default') }, { default: () => opt ? opt.label : row.status });
  }},
  { title: '创建时间', key: 'createTime' },
  {
    title: '操作', key: 'actions', width: 240, fixed: 'right' as const, render(row: any) {
      const btns = [
        h(NButton, { size: 'small', type: 'primary', onClick: () => openDetail(row) }, { default: () => '详情' }),
        h(NButton, {
          size: 'small',
          type: 'warning',
          loading: promotionLoading.value === row.id,
          onClick: () => handlePromotionLink(row)
        }, { default: () => '一键转链' }),
      ];
      return h(NSpace, { size: 'small' }, { default: () => btns });
    }
  }
];

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.product-list { min-height: 100%; }
</style>
