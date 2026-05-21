<template>
  <n-drawer :show="show" :width="DRAWER_WIDTH_PX.md" placement="right" @update:show="updateShow">
    <n-drawer-content title="操作日志" closable>
      <n-spin :show="loading">
        <n-data-table :columns="columns" :data="logs" :pagination="false" size="small" />
        <div v-if="!logs.length && !loading" class="empty-state">
          暂无操作日志
        </div>
      </n-spin>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { DRAWER_WIDTH_PX } from '../../../constants/ui';
import { getActivityProductOperationLogs } from '../../../api/activityProduct';

const props = defineProps<{ show: boolean; activityId: string | number | null; productId: string | number | null }>();
const emit = defineEmits(['update:show']);
const message = useMessage();

const loading = ref(false);
const logs = ref<any[]>([]);

const normalizeText = (value?: string | null) => {
  if (!value) return '';
  const text = String(value).trim();
  return text && text !== 'null' && text !== 'undefined' ? text : '';
};

const parseOperationPayload = (raw?: string | null) => {
  const payload: Record<string, string> = {};
  const text = normalizeText(raw);
  if (!text) return payload;
  const trimmed = text.startsWith('{') && text.endsWith('}') ? text.slice(1, -1) : text;
  trimmed.split(', ').forEach((pair) => {
    const index = pair.indexOf('=');
    if (index <= 0) return;
    const key = pair.slice(0, index).trim();
    const value = pair.slice(index + 1).trim();
    if (key) payload[key] = value;
  });
  return payload;
};

const operationTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    ASSIGN: '分配招商',
    AUDIT: '商品审核',
    SYNC: '同步商品',
    LINK: '生成推广链接',
    PROMOTION_LINK: '生成推广链接',
    TALENT_FOLLOW: '达人跟进',
    TALENT_FOLLOW_APPEND: '追加跟进'
  };
  return map[type || ''] || type || '未知事件';
};

const statusLabel = (status?: string | null) => {
  const map: Record<string, string> = {
    PENDING_AUDIT: '待审核',
    APPROVED: '审核通过',
    REJECTED: '审核拒绝',
    BOUND: '历史已绑定',
    ASSIGNED: '已分配招商',
    LINKED: '已转链',
    FOLLOWING: '已转交达人 CRM'
  };
  return map[status || ''] || status || '-';
};

const formatStatusFlow = (beforeStatus?: string | null, afterStatus?: string | null) => {
  return `${statusLabel(beforeStatus)} -> ${statusLabel(afterStatus)}`;
};

const isUuidLike = (value?: string | null) => {
  const text = normalizeText(value);
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(text);
};

const formatOperatorDisplay = (row: any) => {
  const payload = parseOperationPayload(row?.operationPayload);
  if (payload.operatorName) return payload.operatorName;
  if (row?.operationType === 'SYNC') return '系统';
  if (isUuidLike(row?.operatorId)) return '历史记录';
  return normalizeText(row?.operatorId) || '-';
};

const buildSummary = (row: any) => {
  const payload = parseOperationPayload(row?.operationPayload);
  if (row?.operationType === 'ASSIGN') {
    return row?.operationRemark || `已分配给 ${payload.assigneeName || '负责人'}`;
  }
  return row?.operationRemark || normalizeText(row?.operationPayload) || '-';
};

const columns = [
  { title: '时间', key: 'createTime', width: 180 },
  { title: '事件', key: 'operationType', width: 140, render: (row: any) => operationTypeLabel(row?.operationType) },
  { title: '状态流转', key: 'statusFlow', width: 180, render: (row: any) => formatStatusFlow(row?.beforeStatus, row?.afterStatus) },
  { title: '操作说明', key: 'operationRemark', width: 220, render: (row: any) => buildSummary(row) },
  { title: '操作人', key: 'operatorId', width: 140, render: (row: any) => formatOperatorDisplay(row) },
  { title: '内容', key: 'operationPayload', minWidth: 220, render: (row: any) => normalizeText(row?.operationPayload) || '-' }
];

const fetchLogs = async () => {
  if (!props.activityId || !props.productId) return;
  loading.value = true;
  const activityId = props.activityId;
  const productId = props.productId;
  try {
    const res: any = await getActivityProductOperationLogs(activityId, productId, {
      page: 1,
      size: 100
    });
    logs.value = res?.data?.records || [];
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '加载操作日志失败');
  } finally {
    loading.value = false;
  }
};

watch(() => props.show, (val) => {
  if (val) {
    fetchLogs();
  } else {
    logs.value = [];
  }
});

const updateShow = (val: boolean) => emit('update:show', val);
</script>

<style scoped>
.empty-state {
  text-align: center;
  padding: 40px;
  color: var(--text-muted);
}
</style>
