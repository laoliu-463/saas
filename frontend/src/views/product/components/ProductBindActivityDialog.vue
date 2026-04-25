<template>
  <n-modal :show="show" preset="dialog" title="绑定活动" positive-text="确认" negative-text="取消" @positive-click="handleSubmit" @update:show="updateShow">
    <n-form-item label="目标活动ID" required>
      <n-input v-model:value="bindActivityId" placeholder="请输入要绑定的活动ID" />
    </n-form-item>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { bindActivityProduct } from '../../../api/activityProduct';

const props = defineProps<{ show: boolean; activityId: string | number; productId: string | number }>();
const emit = defineEmits(['update:show', 'success']);
const message = useMessage();

const bindActivityId = ref('');

watch(() => props.show, (val) => {
  if (val) bindActivityId.value = '';
});

const updateShow = (val: boolean) => emit('update:show', val);

const handleSubmit = async () => {
  if (!bindActivityId.value.trim()) {
    message.warning('请输入目标活动ID');
    return false;
  }
  try {
    const res: any = await bindActivityProduct(props.activityId, props.productId, {
      boundActivityId: bindActivityId.value.trim()
    });
    message.success('绑定活动成功');
    emit('success', res?.data);
    updateShow(false);
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '绑定活动失败');
    return false;
  }
};
</script>
