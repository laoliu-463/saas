<template>
  <n-modal :show="show" preset="dialog" title="审核商品" positive-text="提交" negative-text="取消" @positive-click="handleSubmit" @update:show="updateShow">
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
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { auditActivityProduct } from '../../../api/activityProduct';

const props = defineProps<{ show: boolean; activityId: string | number | null; productId: string | number | null }>();
const emit = defineEmits(['update:show', 'success']);
const message = useMessage();

const auditApproved = ref(true);
const auditReason = ref('');

watch(() => props.show, (val) => {
  if (val) {
    auditApproved.value = true;
    auditReason.value = '';
  }
});

const updateShow = (val: boolean) => {
  emit('update:show', val);
};

const handleSubmit = async () => {
  if (!props.activityId || !props.productId) {
    message.warning('商品信息不完整，暂不可提交审核');
    return false;
  }
  if (!auditApproved.value && !auditReason.value.trim()) {
    message.warning('驳回时必须填写原因');
    return false;
  }
  try {
    const res: any = await auditActivityProduct(props.activityId, props.productId, {
      approved: auditApproved.value,
      reason: auditApproved.value ? undefined : auditReason.value.trim()
    });
    message.success(auditApproved.value ? '审核通过' : '审核驳回');
    emit('success', res?.data);
    updateShow(false);
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '审核失败');
    return false;
  }
};
</script>
