<template>
  <n-modal
    :show="show"
    preset="dialog"
    :title="modeConfig.title"
    positive-text="确认"
    negative-text="取消"
    @positive-click="handleSubmit"
    @update:show="updateShow"
  >
    <n-form-item :label="modeConfig.label" required>
      <n-select
        v-model:value="assigneeId"
        :options="userOptions"
        :loading="loadingUsers"
        filterable
        clearable
        remote
        :placeholder="modeConfig.placeholder"
        @search="handleSearch"
      />
    </n-form-item>
    <div class="form-tip">{{ modeConfig.tip }}</div>
  </n-modal>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../../utils/requestError'
import { computed, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { assignActivityProduct, assignActivityProductAuditOwner } from '../../../api/activityProduct';
import { useDebouncedFn } from '../../../utils/debounce';
import { loadProductAssigneeOptions } from '../product-assignee-options';

type AssignMode = 'businessOwner' | 'auditOwner';

const props = withDefaults(defineProps<{
  show: boolean;
  activityId: string | number | null;
  productId: string | number | null;
  mode?: AssignMode;
}>(), {
  mode: 'businessOwner'
});
const emit = defineEmits(['update:show', 'success']);
const message = useMessage();

const assigneeId = ref<string | null>(null);
const userOptions = ref<{ label: string; value: string }[]>([]);
const loadingUsers = ref(false);

const modeConfig = computed(() => {
  if (props.mode === 'auditOwner') {
    return {
      title: '分配审核人',
      label: '审核人',
      placeholder: '请选择审核人',
      tip: '从本组招商专员中选择审核负责人，分配后商品仍保持待审核状态。',
      success: '分配审核人成功',
      error: '分配审核人失败'
    };
  }
  return {
    title: '分配招商',
    label: '招商组长',
    placeholder: '请选择招商组长',
    tip: '从本组招商用户中选择招商组长，审核通过入库后可再次调整归属。',
    success: '分配招商成功',
    error: '分配招商失败'
  };
});

watch(() => props.show, async (val) => {
  if (val) {
    assigneeId.value = null;
    if (!userOptions.value.length) {
      await fetchUsers('');
    }
  }
});

const updateShow = (val: boolean) => emit('update:show', val);

const fetchUsers = async (keyword: string) => {
  loadingUsers.value = true;
  try {
    userOptions.value = await loadProductAssigneeOptions(keyword);
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: `加载${modeConfig.value.label}列表失败` });
  } finally {
    loadingUsers.value = false;
  }
};

const debouncedFetchUsers = useDebouncedFn((keyword: string) => {
  void fetchUsers(keyword);
}, 250);

const handleSearch = (keyword: string) => {
  debouncedFetchUsers(String(keyword || '').trim());
};

const handleSubmit = async () => {
  if (!props.activityId || !props.productId) {
    message.warning('商品信息不完整，暂不可分配');
    return false;
  }
  if (!assigneeId.value) {
    message.warning(`请选择${modeConfig.value.label}`);
    return false;
  }
  try {
    const assignRequest = props.mode === 'auditOwner' ? assignActivityProductAuditOwner : assignActivityProduct;
    const res: any = await assignRequest(props.activityId, props.productId, {
      assigneeId: assigneeId.value
    });
    message.success(modeConfig.value.success);
    emit('success', res?.data);
    updateShow(false);
    return true;
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: modeConfig.value.error });
    return false;
  }
};
</script>

<style scoped>
.form-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
