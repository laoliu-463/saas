<template>
  <div class="exclusive-status app-page" data-testid="exclusive-status-page">
    <n-card title="独家状态看板" :bordered="false" class="app-panel">
      <div style="margin-bottom: 24px;">
        <n-alert type="info" show-icon>
          此处显示系统每月由后端的定时任务评估产生的“独家生效状态”。
        </n-alert>
      </div>
      
      <n-grid :x-gap="16" :cols="2">
        <n-gi>
          <n-card title="本月独家达人" class="nested-panel">
            <n-data-table
              size="small"
              :columns="talentColumns"
              :data="talentData"
              :loading="loading"
              virtual-scroll
              :style="{ height: '300px' }"
            />
          </n-card>
        </n-gi>
        <n-gi>
          <n-card title="本月独家商家" class="nested-panel">
            <n-data-table
              size="small"
              :columns="merchantColumns"
              :data="merchantData"
              :loading="loading"
              virtual-scroll
              :style="{ height: '300px' }"
            />
          </n-card>
        </n-gi>
      </n-grid>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { h, ref, onMounted } from 'vue';
import { NTag, useMessage } from 'naive-ui';
import { getExclusiveTalentStatus, getExclusiveMerchantStatus } from '../../api/data';
import { resolveExclusiveStatusView } from './exclusive-status';

const message = useMessage();

type PageLikeResponse<T> = {
  data?: T[] | { records?: T[] } | null
  records?: T[]
}

const resolveRecords = <T,>(response: PageLikeResponse<T> | T[] | null | undefined): T[] => {
  if (Array.isArray(response)) return response;
  if (Array.isArray(response?.data)) return response.data;
  if (Array.isArray(response?.data?.records)) return response.data.records;
  if (Array.isArray(response?.records)) return response.records;
  return [];
};

const renderStatus = (row: any) => {
  const statusView = resolveExclusiveStatusView(row);
  return h(NTag, { size: 'small', type: statusView.type, round: true }, {
    default: () => statusView.label
  });
};

// 达人列定义
const talentColumns = [
  { title: '达人 UID', key: 'talentUid' },
  { title: '渠道归属', key: 'userId' },
  { title: '服务费占比', key: 'serviceFeeRatio' },
  { title: '有效寄样数', key: 'monthlySamples' },
  { title: '生效状态', key: 'status', render: renderStatus }
];

// 商家列定义
const merchantColumns = [
  { title: '商家名称', key: 'merchantName' },
  { title: '商家 ID', key: 'merchantId' },
  { title: '招商归属', key: 'userId' },
  { title: '服务费占比', key: 'serviceFeeRatio' },
  { title: '生效状态', key: 'status', render: renderStatus }
];

const loading = ref(false);
const talentData = ref<any[]>([]);
const merchantData = ref<any[]>([]);

onMounted(async () => {
  loading.value = true;
  try {
    const [tRes, mRes] = await Promise.all([
      getExclusiveTalentStatus(),
      getExclusiveMerchantStatus()
    ]);
    talentData.value = resolveRecords(tRes as any);
    merchantData.value = resolveRecords(mRes as any);
  } catch (error: any) {
    message.error('加载独家状态失败');
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.nested-panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-xs);
}
</style>
