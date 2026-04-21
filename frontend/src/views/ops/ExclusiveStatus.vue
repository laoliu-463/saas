<template>
  <div class="exclusive-status">
    <n-card title="独家状态看板" :bordered="false">
      <div style="margin-bottom: 24px;">
        <n-alert type="info" show-icon>
          此处显示系统每月由后端的定时任务评估产生的“独家生效状态”。
        </n-alert>
      </div>
      
      <n-grid :x-gap="16" :cols="2">
        <n-gi>
          <n-card title="本月独家达人" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <n-data-table
              size="small"
              :columns="talentColumns"
              :data="talentData"
              :loading="false"
              virtual-scroll
              :style="{ height: '300px' }"
            />
          </n-card>
        </n-gi>
        <n-gi>
          <n-card title="本月独家商家" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <n-data-table
              size="small"
              :columns="merchantColumns"
              :data="merchantData"
              :loading="false"
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
import { ref } from 'vue';

// 达人列定义
const talentColumns = [
  { title: '达人名称', key: 'talentName' },
  { title: '渠道名称', key: 'channelName' },
  { title: '服务费占比', key: 'feeRatio' },
  { title: '有效寄样数', key: 'sampleCount' },
  { title: '生效状态', key: 'status' }
];

// 商家列定义
const merchantColumns = [
  { title: '商家名称', key: 'merchantName' },
  { title: '招商名称', key: 'zsName' },
  { title: '服务费占比', key: 'feeRatio' },
  { title: '生效状态', key: 'status' }
];

// 后端 API 对接前，使用暂未出数据的标志
const talentData = ref([]);
const merchantData = ref([]);
</script>

<style scoped>
.exclusive-status {
  min-height: 100%;
}
</style>
