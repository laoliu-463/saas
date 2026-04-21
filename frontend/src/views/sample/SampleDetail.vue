<template>
  <n-modal :show="show" preset="card" title="寄样单详情" style="width: 760px" @update:show="closeDetail">
    <n-spin :show="loading || actionLoading">
      <div v-if="detail">
        <n-descriptions bordered :column="2">
          <n-descriptions-item label="寄样单号">{{ detail.id || '-' }}</n-descriptions-item>
          <n-descriptions-item label="当前状态">
            <n-tag :type="getStatusType(detail.status)">{{ getStatusName(detail.status) }}</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="达人信息" :span="2">
            <div style="display: flex; align-items: center; gap: 12px; margin-top: 4px">
              <n-avatar v-if="detail.talentAvatarUrl" :src="detail.talentAvatarUrl" round />
              <div>
                <div style="font-weight: 500">
                  {{ detail.talentNickname || detail.talentName || detail.talent?.talentName || '-' }}
                </div>
                <div style="font-size: 12px; color: #999">
                  粉丝: {{ detail.talentFansCount ? (detail.talentFansCount / 10000).toFixed(1) + '万' : '-' }} |
                  评分: {{ detail.talentCreditScore || '-' }} |
                  类目: {{ detail.talentMainCategory || '-' }}
                </div>
              </div>
            </div>
          </n-descriptions-item>
          <n-descriptions-item label="商品名称" :span="2">{{ detail.productName || detail.product?.productName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="寄样数量">{{ detail.quantity || 1 }}</n-descriptions-item>
          <n-descriptions-item label="申请时间">{{ detail.createTime || '-' }}</n-descriptions-item>
          <n-descriptions-item label="备注说明" :span="2">{{ detail.remark || '-' }}</n-descriptions-item>
          <n-descriptions-item v-if="detail.rejectReason" label="拒绝原因" :span="2" style="color: #d03050">
            {{ detail.rejectReason }}
          </n-descriptions-item>
          <n-descriptions-item v-if="detail.trackingNo" label="快递单号" :span="2">
            {{ detail.trackingNo }}
          </n-descriptions-item>
        </n-descriptions>

        <n-divider />
        <n-space style="margin-top: 16px">
          <n-button v-if="canAudit && detail.status === 'PENDING_AUDIT'" type="success" @click="handleAction('APPROVED')">
            通过申请
          </n-button>
          <n-button v-if="canAudit && detail.status === 'PENDING_AUDIT'" type="error" @click="handleAction('REJECTED')">
            拒绝申请
          </n-button>
          <n-button v-if="canShip && detail.status === 'PENDING_SHIP'" type="info" @click="handleAction('SHIPPED')">
            发货
          </n-button>
        </n-space>
      </div>
      <n-empty v-else description="无寄样数据" />
    </n-spin>

    <template #footer>
      <div style="display: flex; justify-content: flex-end">
        <n-button @click="closeDetail">关闭</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { h, ref, watch } from 'vue';
import { NInput, useDialog, useMessage } from 'naive-ui';
import { getSampleById, actionSample } from '../../api/sample';
import { useAuthStore } from '../../stores/auth';
import { ROLE_CODES } from '../../constants/rbac';

const props = defineProps<{ show: boolean; sampleId: string }>();
const emit = defineEmits(['update:show', 'refresh']);

const message = useMessage();
const dialog = useDialog();
const authStore = useAuthStore();

const loading = ref(false);
const actionLoading = ref(false);
const detail = ref<any>(null);

const canAudit = authStore.isAdmin || authStore.isLeader;
const canShip = canAudit || authStore.roleCodes.includes(ROLE_CODES.OPS_STAFF);

const getStatusName = (status: string) => {
  const map: Record<string, string> = {
    PENDING_AUDIT: '待审核',
    PENDING_SHIP: '待发货',
    SHIPPED: '快递中',
    PENDING_TASK: '待交作业',
    FINISHED: '已完成',
    REJECTED: '已拒绝',
    CLOSED: '已关闭'
  };
  return map[status] || status;
};

const getStatusType = (status: string) => {
  const map: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
    PENDING_AUDIT: 'warning',
    PENDING_SHIP: 'info',
    SHIPPED: 'success',
    REJECTED: 'error',
    FINISHED: 'success',
    CLOSED: 'default',
    PENDING_TASK: 'info'
  };
  return map[status] || 'default';
};

const closeDetail = () => {
  emit('update:show', false);
};

const loadDetail = async () => {
  loading.value = true;
  try {
    const res = await getSampleById(props.sampleId);
    detail.value = res?.data || res;
  } catch (error: any) {
    detail.value = null;
    message.error(error?.message || '无法获取寄样详情');
  } finally {
    loading.value = false;
  }
};

const doActionSample = async (payload: any) => {
  actionLoading.value = true;
  try {
    await actionSample(props.sampleId, payload);
    message.success('状态流转成功');
    emit('refresh');
    await loadDetail();
  } catch (error: any) {
    message.error(error?.message || '操作失败');
  } finally {
    actionLoading.value = false;
  }
};

const askReason = async () => {
  return new Promise<string>((resolve) => {
    let value = '';
    dialog.warning({
      title: '拒绝原因',
      content: () =>
        h(NInput, {
          placeholder: '请输入拒绝原因',
          onUpdateValue: (v) => {
            value = v;
          }
        }),
      positiveText: '确认拒绝',
      negativeText: '取消',
      positiveButtonProps: { type: 'error' },
      onPositiveClick: () => resolve(value || '原因未填写'),
      onNegativeClick: () => resolve('')
    });
  });
};

const askTrackingNo = async () => {
  return new Promise<string>((resolve) => {
    let value = '';
    dialog.warning({
      title: '填写快递单号',
      content: () =>
        h('div', [
          h('div', { style: 'margin-bottom: 8px;' }, '快递单号：'),
          h(NInput, {
            placeholder: 'SF1234567890',
            onUpdateValue: (v) => {
              value = v;
            }
          })
        ]),
      positiveText: '确认发货',
      negativeText: '取消',
      onPositiveClick: () => resolve(value || ''),
      onNegativeClick: () => resolve('')
    });
  });
};

const handleAction = async (action: string) => {
  if (action === 'REJECTED') {
    const reason = await askReason();
    if (!reason) return;
    await doActionSample({ action, reason });
    return;
  }
  if (action === 'SHIPPED') {
    const trackingNo = await askTrackingNo();
    if (!trackingNo) return;
    await doActionSample({ action, trackingNo });
    return;
  }
  await doActionSample({ action });
};

watch(
  () => props.show,
  (newVal) => {
    if (newVal && props.sampleId) {
      loadDetail();
      return;
    }
    detail.value = null;
  }
);
</script>
