<template>
  <n-modal :show="show" @update:show="closeDetail" preset="card" title="达人详情" style="width: 600px;">
    <n-spin :show="loading">
      <div v-if="talent">
        <n-descriptions bordered column="1">
          <n-descriptions-item label="达人名称">{{ talent.talentName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="达人等级">{{ talent.talentLevel || '-' }}</n-descriptions-item>
          <n-descriptions-item label="擅长类目">{{ talent.category || '-' }}</n-descriptions-item>
          <n-descriptions-item label="粉丝数">{{ talent.fansCount || '0' }}</n-descriptions-item>
          <n-descriptions-item label="近30天带货GMV">{{ talent.gmv30d ? `¥${talent.gmv30d.toLocaleString()}` : '-' }}</n-descriptions-item>
          <n-descriptions-item label="所在状态">{{ talent.status || '-' }}</n-descriptions-item>
          <n-descriptions-item label="联系方式">{{ talent.contactInfo || '暂无公共联系方式' }}</n-descriptions-item>
          <n-descriptions-item label="入库时间">{{ talent.createTime || '-' }}</n-descriptions-item>
        </n-descriptions>
      </div>
      <n-empty v-else description="无达人数据" />
    </n-spin>

    <template #footer>
      <div style="display: flex; justify-content: flex-end; gap: 12px;">
        <template v-if="talent && talent.status === 'PENDING_AUDIT' && (authStore.isAdmin || authStore.isLeader)">
          <n-button type="success" @click="handleAction('CLAIM')">认领</n-button>
          <n-button type="error" @click="handleAction('REJECT')">拒绝</n-button>
        </template>
        <n-button @click="closeDetail">关闭</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { getTalentById, claimTalent } from '../../api/talent';
import { useAuthStore } from '../../stores/auth';

const props = defineProps<{ show: boolean, talentId: string }>();
const emit = defineEmits(['update:show', 'refresh']);
const message = useMessage();
const authStore = useAuthStore();

const talent = ref<any>(null);
const loading = ref(false);

const closeDetail = () => {
  emit('update:show', false);
};

const handleAction = async (action: string) => {
  if (action === 'CLAIM') {
     try {
         await claimTalent(props.talentId);
         message.success('认领请求成功');
     } catch(err: any) {
         message.error(err?.message || '认领由于保护期冲突失败');
     }
  } else {
     message.success('已拒绝当前操作');
  }
  emit('refresh');
  closeDetail();
};

const loadDetail = async () => {
  loading.value = true;
  try {
    const res = await getTalentById(props.talentId);
    talent.value = res?.data || res;
  } catch(err: any) {
    message.error(err?.message || '无法获取详情');
    talent.value = null;
  } finally {
    loading.value = false;
  }
};

watch(() => props.show, (val) => { 
  if (val && props.talentId) loadDetail(); 
});
</script>
