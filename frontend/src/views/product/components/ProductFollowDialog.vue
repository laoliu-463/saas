<template>
  <n-modal
    :show="show"
    preset="dialog"
    title="达人跟进"
    positive-text="提交"
    negative-text="取消"
    @positive-click="handleSubmit"
    @update:show="updateShow"
  >
    <n-form>
      <n-form-item label="达人名称" required>
        <n-input v-model:value="form.talentName" placeholder="输入需要跟进的达人名称" />
      </n-form-item>
      <n-form-item label="跟进状态" required>
        <n-select v-model:value="form.followStatus" :options="statusOptions" />
      </n-form-item>
      <n-form-item label="跟进内容" required>
        <n-input
          v-model:value="form.content"
          type="textarea"
          placeholder="填写本次跟进记录"
        />
      </n-form-item>
      <n-form-item label="下次跟进时间">
        <n-date-picker v-model:value="form.nextFollowTime" type="datetime" clearable />
      </n-form-item>
    </n-form>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { followActivityProduct } from '../../../api/activityProduct';

const props = defineProps<{ show: boolean; activityId: string | number; productId: string | number }>();
const emit = defineEmits(['update:show', 'success']);
const message = useMessage();

const createDefaultForm = () => ({
  talentName: '',
  followStatus: 'INVITED',
  content: '',
  nextFollowTime: null as number | null
});

const form = ref(createDefaultForm());

const statusOptions = [
  { label: '未联系', value: 'NOT_CONTACTED' },
  { label: '已邀约', value: 'INVITED' },
  { label: '已回复', value: 'REPLIED' },
  { label: '合作中', value: 'COOPERATING' },
  { label: '推广中', value: 'PROMOTING' },
  { label: '已结束', value: 'CLOSED' },
  { label: '无效', value: 'INVALID' }
];

watch(
  () => props.show,
  (val) => {
    if (val) {
      form.value = createDefaultForm();
    }
  }
);

const updateShow = (val: boolean) => emit('update:show', val);

const formatTime = (ts: number | null) => {
  if (!ts) return undefined;
  const d = new Date(ts);
  const pad = (value: number) => String(value).padStart(2, '0');

  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

const resolveFollowErrorMessage = (error: any) => {
  const rawMessage = String(error?.response?.data?.msg || error?.message || '').trim();

  if (!rawMessage) {
    return '达人跟进失败，请稍后重试';
  }

  if (
    rawMessage.includes('LocalDateTime') ||
    rawMessage.includes('DateTimeParseException') ||
    rawMessage.includes('nextFollowTime')
  ) {
    return '跟进时间格式不正确，请重新选择时间';
  }

  return rawMessage;
};

const handleSubmit = async () => {
  if (!form.value.talentName.trim()) {
    message.warning('请输入达人名称');
    return false;
  }
  if (!form.value.content.trim()) {
    message.warning('请输入跟进内容');
    return false;
  }

  try {
    const res: any = await followActivityProduct(props.activityId, props.productId, {
      talentName: form.value.talentName.trim(),
      followStatus: form.value.followStatus,
      content: form.value.content.trim(),
      nextFollowTime: formatTime(form.value.nextFollowTime)
    });
    message.success('达人跟进记录已添加');
    emit('success', res?.data);
    updateShow(false);
    return true;
  } catch (error: any) {
    message.error(resolveFollowErrorMessage(error));
    return false;
  }
};
</script>
