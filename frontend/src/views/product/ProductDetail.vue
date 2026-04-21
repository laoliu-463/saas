<template>
  <n-modal :show="show" @update:show="closeDetail" preset="card" title="商品详情" style="width: 680px;">
    <n-spin :show="loading">
      <div v-if="detail">
        <n-descriptions bordered column="1">
          <n-descriptions-item label="商品名称">{{ detail.productName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="商品编码">{{ detail.productCode || '-' }}</n-descriptions-item>
          <n-descriptions-item label="商品价格">{{ detail.price ? `¥${detail.price}` : '-' }}</n-descriptions-item>
          <n-descriptions-item label="库存数量">{{ detail.stock ?? '-' }}</n-descriptions-item>
          <n-descriptions-item label="适用活动">{{ detail.activityName || '无' }}</n-descriptions-item>
          <n-descriptions-item label="招商负责人">{{ detail.assigneeName || '未分配' }}</n-descriptions-item>
          <n-descriptions-item label="商品状态">{{ detail.status || '-' }}</n-descriptions-item>
          <n-descriptions-item label="审核备注">{{ detail.auditRemark || '无' }}</n-descriptions-item>
          <n-descriptions-item label="上架时间">{{ detail.createTime || '-' }}</n-descriptions-item>
          <n-descriptions-item label="商品描述">{{ detail.description || '暂无描述' }}</n-descriptions-item>
        </n-descriptions>

        <!-- 转链结果 -->
        <n-card v-if="promotionResult" title="转链结果" size="small" style="margin-top: 16px;">
          <n-descriptions bordered column="1">
            <n-descriptions-item label="短链接">
              <n-button text type="primary" @click="copyToClipboard(promotionResult.shortLink)">{{ promotionResult.shortLink }}</n-button>
            </n-descriptions-item>
            <n-descriptions-item label="推广链接">
              <n-button text type="primary" @click="copyToClipboard(promotionResult.promoteLink)">{{ promotionResult.promoteLink }}</n-button>
            </n-descriptions-item>
          </n-descriptions>
        </n-card>
      </div>
      <n-empty v-else description="无法加载商品详情" />
    </n-spin>
    <template #footer>
      <div style="display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap;">
        <!-- 一键转链 -->
        <n-button type="primary" :loading="promotionLoading" @click="handlePromotionLink">一键转链</n-button>
        <!-- 绑定活动 -->
        <n-button v-if="authStore.isAdmin || authStore.isLeader" @click="showBindActivity = true">绑定活动</n-button>
        <!-- 分配招商 -->
        <n-button v-if="authStore.isAdmin || authStore.roleCodes.includes('biz_leader')" @click="showAssign = true">分配招商</n-button>
        <!-- 审核通过 -->
        <n-button v-if="detail?.status === 'PENDING_AUDIT' && (authStore.isAdmin || authStore.isLeader)" type="success" @click="handleAudit(true)">通过</n-button>
        <!-- 审核拒绝 -->
        <n-button v-if="detail?.status === 'PENDING_AUDIT' && (authStore.isAdmin || authStore.isLeader)" type="error" @click="showRejectDialog = true">拒绝</n-button>
        <n-button @click="closeDetail">关闭</n-button>
      </div>
    </template>
  </n-modal>

  <!-- 绑定活动弹窗 -->
  <n-modal v-model:show="showBindActivity" preset="dialog" title="绑定活动" positive-text="确认" negative-text="取消"
    @positive-click="handleBindActivity">
    <n-form-item label="选择活动" required>
      <n-select v-model:value="bindActivityId" :options="activityOptions" placeholder="请选择活动" filterable
        :loading="activityLoading" />
    </n-form-item>
  </n-modal>

  <!-- 分配招商弹窗 -->
  <n-modal v-model:show="showAssign" preset="dialog" title="分配招商" positive-text="确认" negative-text="取消"
    @positive-click="handleAssign">
    <n-form-item label="招商负责人" required>
      <n-input v-model:value="assigneeId" placeholder="请输入负责人 UUID" />
    </n-form-item>
  </n-modal>

  <!-- 拒绝原因弹窗 -->
  <n-modal v-model:show="showRejectDialog" preset="dialog" title="拒绝原因" positive-text="确认拒绝" negative-text="取消"
    :mask-closable="false" @positive-click="handleAudit(false)">
    <n-form-item label="拒绝原因" required>
      <n-input v-model:value="rejectReason" type="textarea" placeholder="请输入拒绝原因" :rows="3" />
    </n-form-item>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { getProductById, bindActivity, assignProduct, auditProduct, generatePromotionLink, getActivityPage } from '../../api/product';
import { useAuthStore } from '../../stores/auth';
import type { PromotionLinkResult } from '../../types';

interface ProductDetail {
  id: string
  productName: string
  productCode: string
  status: 'PENDING_AUDIT' | 'APPROVED' | 'REJECTED' | 'OFF_SHELF'
  price: number
  stock: number
  description: string
  activityName?: string
  assigneeName?: string
  auditRemark?: string
  createTime: string
}

const props = defineProps<{ show: boolean, productId: string }>();
const emit = defineEmits(['update:show', 'refresh']);
const message = useMessage();
const authStore = useAuthStore();
const loading = ref(false);
const detail = ref<ProductDetail | null>(null);

// ---- 转链 ----
const promotionLoading = ref(false);
const promotionResult = ref<PromotionLinkResult | null>(null);

const handlePromotionLink = async () => {
  if (!detail.value) return;
  promotionLoading.value = true;
  try {
    const res = await generatePromotionLink(detail.value.id);
    promotionResult.value = res?.data || res;
    message.success('转链成功');
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '转链失败');
  } finally {
    promotionLoading.value = false;
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

// ---- 绑定活动 ----
const showBindActivity = ref(false);
const bindActivityId = ref<string | null>(null);
const activityOptions = ref<{ label: string; value: string }[]>([]);
const activityLoading = ref(false);

watch(showBindActivity, async (val) => {
  if (val) {
    activityLoading.value = true;
    try {
      const res: any = await getActivityPage({ page: 1, size: 200 });
      const records = res?.data?.records || res?.records || [];
      activityOptions.value = records.map((a: any) => ({
        label: a.activityName || a.id,
        value: a.id
      }));
    } catch {
      activityOptions.value = [];
    } finally {
      activityLoading.value = false;
    }
  }
});

const handleBindActivity = async () => {
  if (!detail.value || !bindActivityId.value) {
    message.warning('请选择活动');
    return false;
  }
  try {
    await bindActivity(detail.value.id, { activityId: bindActivityId.value });
    message.success('绑定活动成功');
    bindActivityId.value = null;
    showBindActivity.value = false;
    emit('refresh');
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '绑定失败');
    return false;
  }
};

// ---- 分配招商 ----
const showAssign = ref(false);
const assigneeId = ref('');

const handleAssign = async () => {
  if (!detail.value || !assigneeId.value.trim()) {
    message.warning('请输入负责人 UUID');
    return false;
  }
  try {
    await assignProduct(detail.value.id, { assigneeId: assigneeId.value.trim() });
    message.success('分配招商成功');
    assigneeId.value = '';
    showAssign.value = false;
    emit('refresh');
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '分配失败');
    return false;
  }
};

// ---- 审核 ----
const showRejectDialog = ref(false);
const rejectReason = ref('');

const handleAudit = async (approved: boolean) => {
  if (!detail.value) return false;
  if (!approved && !rejectReason.value.trim()) {
    message.warning('请输入拒绝原因');
    return false;
  }
  try {
    await auditProduct(detail.value.id, {
      approved,
      reason: approved ? undefined : rejectReason.value.trim()
    });
    message.success(approved ? '审核通过' : '已拒绝');
    rejectReason.value = '';
    showRejectDialog.value = false;
    closeDetail();
    emit('refresh');
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '审核操作失败');
    return false;
  }
};

// ---- 加载详情 ----
const closeDetail = () => {
  promotionResult.value = null;
  emit('update:show', false);
};

watch(() => props.show, async (newVal) => {
  if (newVal && props.productId) {
    loading.value = true;
    promotionResult.value = null;
    try {
      const res = await getProductById(props.productId);
      detail.value = res?.data || res;
    } catch(err: any) {
      message.error(err?.message || '无法获取详情');
      detail.value = null;
    } finally {
      loading.value = false;
    }
  } else {
    detail.value = null;
  }
});
</script>
