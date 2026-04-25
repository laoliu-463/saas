<template>
  <n-drawer :show="show" width="640" placement="right" @update:show="updateShow">
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
import { getActivityProductOperationLogs } from '../../../api/activityProduct';

const props = defineProps<{ show: boolean; activityId: string | number; productId: string | number }>();
const emit = defineEmits(['update:show']);
const message = useMessage();

const loading = ref(false);
const logs = ref<any[]>([]);

const columns = [
  { title: '时间', key: 'createTime', width: 180 },
  { title: '操作类型', key: 'operationType', width: 140 },
  { title: '操作说明', key: 'operationRemark', width: 180 },
  { title: '操作人', key: 'operatorId', width: 140 },
  { title: '内容', key: 'operationPayload', minWidth: 200 }
];

const fetchLogs = async () => {
  if (!props.productId) return;
  loading.value = true;
  try {
    const res: any = await getActivityProductOperationLogs(props.activityId, props.productId, {
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
