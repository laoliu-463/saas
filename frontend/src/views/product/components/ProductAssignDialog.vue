<template>
  <n-modal
    :show="show"
    preset="dialog"
    title="分配招商"
    positive-text="确认"
    negative-text="取消"
    @positive-click="handleSubmit"
    @update:show="updateShow"
  >
    <n-form-item label="负责人" required>
      <n-select
        v-model:value="assigneeId"
        :options="userOptions"
        :loading="loadingUsers"
        filterable
        clearable
        remote
        placeholder="请选择负责人"
        @search="handleSearch"
      />
    </n-form-item>
    <div class="form-tip">从系统用户中选择负责人，避免手动输入导致格式错误。</div>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { assignActivityProduct } from '../../../api/activityProduct';
import { getAssignableUserOptions } from '../../../api/sys';

const props = defineProps<{ show: boolean; activityId: string | number | null; productId: string | number | null }>();
const emit = defineEmits(['update:show', 'success']);
const message = useMessage();

const assigneeId = ref<string | null>(null);
const userOptions = ref<{ label: string; value: string }[]>([]);
const loadingUsers = ref(false);

watch(() => props.show, async (val) => {
  if (val) {
    assigneeId.value = null;
    if (!userOptions.value.length) {
      await fetchUsers('');
    }
  }
});

const updateShow = (val: boolean) => emit('update:show', val);

const buildUserOption = (user: any) => {
  const realName = String(user?.realName || '').trim();
  const username = String(user?.username || '').trim();
  const label = realName && username ? `${realName} (${username})` : (realName || username || String(user?.id || '未命名用户'));
  return {
    label,
    value: String(user?.id || '')
  };
};

const fetchUsers = async (keyword: string) => {
  loadingUsers.value = true;
  try {
    const res: any = await getAssignableUserOptions({
      keyword: keyword || undefined
    });
    const records = res?.data || [];
    userOptions.value = records
      .map(buildUserOption)
      .filter((item: { label: string; value: string }) => Boolean(item.value));
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '加载负责人列表失败');
  } finally {
    loadingUsers.value = false;
  }
};

const handleSearch = async (keyword: string) => {
  await fetchUsers(String(keyword || '').trim());
};

const handleSubmit = async () => {
  if (!props.activityId || !props.productId) {
    message.warning('商品信息不完整，暂不可分配');
    return false;
  }
  if (!assigneeId.value) {
    message.warning('请选择负责人');
    return false;
  }
  try {
    const res: any = await assignActivityProduct(props.activityId, props.productId, {
      assigneeId: assigneeId.value
    });
    message.success('分配招商成功');
    emit('success', res?.data);
    updateShow(false);
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '分配招商失败');
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
