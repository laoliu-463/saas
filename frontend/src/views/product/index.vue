<template>
  <div class="product-list">
    <n-card title="商品库（活动商品主链路）" :bordered="false">
      <n-space style="margin-bottom: 16px" wrap>
        <n-select
          v-model:value="selectedActivityId"
          :options="activityOptions"
          placeholder="请选择活动"
          filterable
          clearable
          :loading="activityLoading"
          style="width: 320px"
        />
        <n-input
          v-model:value="productInfo"
          placeholder="商品名称或商品ID"
          clearable
          style="width: 220px"
          @keydown.enter="refreshProducts"
        />
        <n-select v-model:value="status" :options="statusOptions" style="width: 150px" />
        <n-button type="default" :loading="activityLoading" @click="loadActivities">刷新活动</n-button>
        <n-button type="primary" :loading="loading" @click="refreshProducts">查询商品</n-button>
      </n-space>

      <n-alert v-if="isMockData" type="warning" :show-icon="false" style="margin-bottom: 16px">
        当前为 Mock 测试环境：活动商品数据为 Mock，商品操作会写入测试数据库。
      </n-alert>

      <n-data-table :columns="columns" :data="products" :loading="loading" />

      <n-space justify="center" style="margin-top: 16px">
        <n-button v-if="hasMore" :loading="loadingMore" @click="loadMore">加载更多</n-button>
        <n-text v-else depth="3">没有更多数据了</n-text>
      </n-space>
    </n-card>

    <n-drawer v-model:show="showDetail" width="720" placement="right">
      <n-drawer-content title="商品详情" closable>
        <n-descriptions v-if="currentDetail" bordered column="1" label-placement="left">
          <n-descriptions-item label="商品ID">{{ currentDetail.productId || '-' }}</n-descriptions-item>
          <n-descriptions-item label="商品名称">{{ currentDetail.title || '-' }}</n-descriptions-item>
          <n-descriptions-item label="店铺">{{ currentDetail.shopName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="售价">{{ currentDetail.priceText || '-' }}</n-descriptions-item>
          <n-descriptions-item label="活动佣金率">{{ currentDetail.activityCosRatioText || '-' }}</n-descriptions-item>
          <n-descriptions-item label="佣金类型">{{ currentDetail.cosTypeText || '-' }}</n-descriptions-item>
          <n-descriptions-item label="双佣金服务费率">{{ formatPercent(currentDetail.adServiceRatio) }}</n-descriptions-item>
          <n-descriptions-item label="双佣金投放期佣金率">{{ formatPercent(currentDetail.activityAdCosRatio) }}</n-descriptions-item>
          <n-descriptions-item label="商品状态">{{ currentDetail.statusText || '-' }}</n-descriptions-item>
          <n-descriptions-item label="库存">{{ currentDetail.productStock || '-' }}</n-descriptions-item>
          <n-descriptions-item label="销量">{{ currentDetail.sales ?? '-' }}</n-descriptions-item>
          <n-descriptions-item label="类目">{{ currentDetail.categoryName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="抖in好物">{{ currentDetail.hasDouinGoodsTag ? '是' : '否' }}</n-descriptions-item>
          <n-descriptions-item label="绑定活动ID">{{ currentDetail.boundActivityId || '-' }}</n-descriptions-item>
          <n-descriptions-item label="分配招商">{{ currentDetail.assigneeId || '-' }}</n-descriptions-item>
          <n-descriptions-item label="审核状态">{{ auditStatusText(currentDetail.auditStatus) }}</n-descriptions-item>
          <n-descriptions-item label="审核备注">{{ currentDetail.auditRemark || '-' }}</n-descriptions-item>
          <n-descriptions-item label="短链">
            <n-button text type="primary" @click="copyText(currentDetail.shortLink)">{{ currentDetail.shortLink || '-' }}</n-button>
          </n-descriptions-item>
          <n-descriptions-item label="推广链接">
            <n-button text type="primary" @click="copyText(currentDetail.promoteLink)">{{ currentDetail.promoteLink || '-' }}</n-button>
          </n-descriptions-item>
        </n-descriptions>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="showBindModal" preset="dialog" title="绑定活动" positive-text="确认" negative-text="取消" @positive-click="submitBind">
      <n-form-item label="目标活动ID" required>
        <n-input v-model:value="bindActivityId" placeholder="请输入要绑定的活动ID" />
      </n-form-item>
    </n-modal>

    <n-modal v-model:show="showAssignModal" preset="dialog" title="分配招商" positive-text="确认" negative-text="取消" @positive-click="submitAssign">
      <n-form-item label="负责人UUID" required>
        <n-input v-model:value="assigneeId" placeholder="请输入负责人UUID" />
      </n-form-item>
    </n-modal>

    <n-modal v-model:show="showAuditModal" preset="dialog" title="审核商品" positive-text="提交" negative-text="取消" @positive-click="submitAudit">
      <n-space vertical>
        <n-radio-group v-model:value="auditApproved">
          <n-radio :value="true">通过</n-radio>
          <n-radio :value="false">驳回</n-radio>
        </n-radio-group>
        <n-input
          v-if="auditApproved === false"
          v-model:value="auditReason"
          type="textarea"
          :rows="3"
          placeholder="驳回时必须填写原因"
        />
      </n-space>
    </n-modal>

    <n-modal v-model:show="showPromotionModal" preset="dialog" title="生成转链" positive-text="生成" negative-text="取消" @positive-click="submitPromotion">
      <n-space vertical>
        <n-input v-model:value="promotionExternalId" placeholder="externalUniqueId（可选）" />
        <n-select v-model:value="promotionScene" :options="promotionSceneOptions" />
      </n-space>
    </n-modal>

    <n-modal v-model:show="showLogsModal" preset="card" title="操作日志" style="width: 820px">
      <n-data-table :columns="logColumns" :data="operationLogs" :pagination="false" size="small" />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showLogsModal = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue';
import { NButton, NTag, useMessage } from 'naive-ui';
import {
  auditColonelActivityProduct,
  assignColonelActivityProduct,
  bindColonelActivityProduct,
  generateColonelActivityPromotionLink,
  getColonelActivityPage,
  getColonelActivityProductDetail,
  getColonelActivityProductLogs,
  getColonelActivityProducts
} from '../../api/product';

const message = useMessage();

const activityLoading = ref(false);
const loading = ref(false);
const loadingMore = ref(false);

const activities = ref<any[]>([]);
const selectedActivityId = ref<string | null>(null);
const products = ref<any[]>([]);
const nextCursor = ref('');
const isMockData = ref(false);

const productInfo = ref('');
const status = ref<number | null>(null);

const currentRow = ref<any | null>(null);
const currentDetail = ref<any | null>(null);
const showDetail = ref(false);

const showBindModal = ref(false);
const bindActivityId = ref('');

const showAssignModal = ref(false);
const assigneeId = ref('');

const showAuditModal = ref(false);
const auditApproved = ref(true);
const auditReason = ref('');

const showPromotionModal = ref(false);
const promotionExternalId = ref('');
const promotionScene = ref(4);

const showLogsModal = ref(false);
const operationLogs = ref<any[]>([]);

const statusOptions = [
  { label: '全部状态', value: null },
  { label: '待审核', value: 0 },
  { label: '推广中', value: 1 },
  { label: '申请未通过', value: 2 },
  { label: '合作已终止', value: 3 },
  { label: '合作前取消', value: 4 },
  { label: '合作已到期', value: 6 }
];

const promotionSceneOptions = [
  { label: '默认场景(4)', value: 4 },
  { label: '直播间场景(2)', value: 2 },
  { label: '橱窗场景(1)', value: 1 }
];

const activityOptions = computed(() =>
  activities.value.map((item) => ({
    label: `${item.activityName} (${item.activityId})`,
    value: String(item.activityId)
  }))
);

const hasMore = computed(() => Boolean(nextCursor.value));

const formatPercent = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-';
  const n = Number(value);
  if (Number.isNaN(n)) return String(value);
  return `${n}%`;
};

const auditStatusText = (value: unknown) => {
  const status = Number(value ?? 0);
  if (status === 2) return '审核通过';
  if (status === 3) return '审核驳回';
  if (status === 1) return '待审核';
  return '未处理';
};

const copyText = async (text: string) => {
  if (!text) {
    message.warning('暂无可复制内容');
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    message.success('已复制到剪贴板');
  } catch (_error) {
    message.error('复制失败，请手动复制');
  }
};

const requireTarget = () => {
  if (!selectedActivityId.value || !currentRow.value?.productId) {
    message.warning('请先选择活动并选中商品');
    return false;
  }
  return true;
};

const loadActivities = async () => {
  activityLoading.value = true;
  try {
    const res: any = await getColonelActivityPage({
      status: 0,
      searchType: 0,
      sortType: 1,
      page: 1,
      pageSize: 20
    });
    const data = res?.data || {};
    activities.value = Array.isArray(data.activityList) ? data.activityList : [];
    if (!selectedActivityId.value && activities.value.length > 0) {
      selectedActivityId.value = String(activities.value[0].activityId);
    }
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '活动加载失败');
  } finally {
    activityLoading.value = false;
  }
};

const fetchProducts = async (reset: boolean) => {
  if (!selectedActivityId.value) {
    message.warning('请先选择一个活动');
    return;
  }
  if (reset) loading.value = true;
  else loadingMore.value = true;

  try {
    const res: any = await getColonelActivityProducts(selectedActivityId.value, {
      searchType: 4,
      sortType: 1,
      count: 20,
      retrieveMode: 1,
      cursor: reset ? undefined : nextCursor.value,
      status: status.value ?? undefined,
      productInfo: productInfo.value.trim() || undefined
    });
    const data = res?.data || {};
    const items = Array.isArray(data.items) ? data.items : [];
    products.value = reset ? items : products.value.concat(items);
    nextCursor.value = String(data.nextCursor || '');
    isMockData.value = Boolean(data.mock);

    if (reset) message.success('活动商品加载成功');
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '活动商品查询失败');
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

const refreshProducts = () => {
  nextCursor.value = '';
  fetchProducts(true);
};

const loadMore = () => {
  if (!hasMore.value) return;
  fetchProducts(false);
};

const openDetail = async (row: any) => {
  if (!selectedActivityId.value) return;
  currentRow.value = row;
  try {
    const res: any = await getColonelActivityProductDetail(selectedActivityId.value, row.productId);
    currentDetail.value = res?.data || {};
    showDetail.value = true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '加载详情失败');
  }
};

const openBind = (row: any) => {
  currentRow.value = row;
  bindActivityId.value = '';
  showBindModal.value = true;
};

const submitBind = async () => {
  if (!requireTarget()) return false;
  if (!bindActivityId.value.trim()) {
    message.warning('请输入目标活动ID');
    return false;
  }
  try {
    await bindColonelActivityProduct(selectedActivityId.value!, currentRow.value.productId, {
      boundActivityId: bindActivityId.value.trim()
    });
    message.success('绑定活动成功');
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '绑定活动失败');
    return false;
  }
};

const openAssign = (row: any) => {
  currentRow.value = row;
  assigneeId.value = '';
  showAssignModal.value = true;
};

const submitAssign = async () => {
  if (!requireTarget()) return false;
  if (!assigneeId.value.trim()) {
    message.warning('请输入负责人UUID');
    return false;
  }
  try {
    await assignColonelActivityProduct(selectedActivityId.value!, currentRow.value.productId, {
      assigneeId: assigneeId.value.trim()
    });
    message.success('分配招商成功');
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '分配招商失败');
    return false;
  }
};

const openAudit = (row: any) => {
  currentRow.value = row;
  auditApproved.value = true;
  auditReason.value = '';
  showAuditModal.value = true;
};

const submitAudit = async () => {
  if (!requireTarget()) return false;
  if (!auditApproved.value && !auditReason.value.trim()) {
    message.warning('驳回时必须填写原因');
    return false;
  }
  try {
    await auditColonelActivityProduct(selectedActivityId.value!, currentRow.value.productId, {
      approved: auditApproved.value,
      reason: auditApproved.value ? undefined : auditReason.value.trim()
    });
    message.success(auditApproved.value ? '审核通过' : '审核驳回');
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '审核失败');
    return false;
  }
};

const openPromotion = (row: any) => {
  currentRow.value = row;
  promotionExternalId.value = '';
  promotionScene.value = 4;
  showPromotionModal.value = true;
};

const submitPromotion = async () => {
  if (!requireTarget()) return false;
  try {
    const res: any = await generateColonelActivityPromotionLink(selectedActivityId.value!, currentRow.value.productId, {
      externalUniqueId: promotionExternalId.value.trim() || undefined,
      promotionScene: promotionScene.value,
      needShortLink: true
    });
    const data = res?.data || {};
    message.success(data.shortLink ? `活动调用成功，短链：${data.shortLink}` : '活动调用成功');
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '转链失败');
    return false;
  }
};

const openLogs = async (row: any) => {
  if (!selectedActivityId.value) return;
  currentRow.value = row;
  try {
    const res: any = await getColonelActivityProductLogs(selectedActivityId.value, row.productId, { page: 1, size: 50 });
    operationLogs.value = res?.data?.records || [];
    showLogsModal.value = true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '加载操作日志失败');
  }
};

const columns = [
  {
    title: '商品主图',
    key: 'cover',
    width: 90,
    render: (row: any) => h('img', { src: row.cover, style: 'width:44px;height:44px;border-radius:4px;object-fit:cover;' })
  },
  { title: '商品名称', key: 'title', minWidth: 220 },
  { title: '商品ID', key: 'productId', width: 120 },
  { title: '店铺', key: 'shopName', minWidth: 150 },
  { title: '售价', key: 'priceText', width: 100 },
  { title: '活动佣金', key: 'activityCosRatioText', width: 110 },
  { title: '服务费率', key: 'adServiceRatio', width: 110, render: (row: any) => formatPercent(row.adServiceRatio) },
  {
    title: '佣金类型',
    key: 'cosTypeText',
    width: 100,
    render: (row: any) =>
      h(NTag, { type: row.cosType === 1 ? 'success' : 'default', bordered: false }, { default: () => row.cosTypeText || '-' })
  },
  { title: '商品状态', key: 'statusText', width: 110 },
  { title: '库存', key: 'productStock', width: 80 },
  { title: '销量', key: 'sales', width: 80 },
  {
    title: '操作',
    key: 'actions',
    width: 420,
    render: (row: any) =>
      h('div', { style: 'display:flex;gap:6px;flex-wrap:wrap;' }, [
        h(NButton, { size: 'tiny', onClick: () => openDetail(row) }, { default: () => '详情' }),
        h(NButton, { size: 'tiny', onClick: () => openBind(row) }, { default: () => '绑定活动' }),
        h(NButton, { size: 'tiny', onClick: () => openAssign(row) }, { default: () => '分配招商' }),
        h(NButton, { size: 'tiny', onClick: () => openAudit(row) }, { default: () => '审核' }),
        h(NButton, { size: 'tiny', type: 'primary', onClick: () => openPromotion(row) }, { default: () => '转链' }),
        h(NButton, { size: 'tiny', onClick: () => openLogs(row) }, { default: () => '操作日志' })
      ])
  }
];

const logColumns = [
  { title: '时间', key: 'createTime', width: 180 },
  { title: '操作类型', key: 'operationType', width: 140 },
  { title: '操作说明', key: 'operationRemark', width: 180 },
  { title: '操作人', key: 'operatorId', width: 220 },
  { title: '内容', key: 'operationPayload', minWidth: 260 }
];

onMounted(async () => {
  await loadActivities();
  if (selectedActivityId.value) refreshProducts();
});
</script>

<style scoped>
.product-list {
  min-height: 100%;
}
</style>

