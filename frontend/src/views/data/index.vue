<template>
  <div>
    <n-spin :show="loading">
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 24px; margin-bottom: 24px;">
         <n-card title="总订单数" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 28px; font-weight: bold; color: var(--primary-color);">{{ metrics.totalOrders || 0 }}</div>
         </n-card>
         <n-card title="订单总额" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 28px; font-weight: bold; color: var(--primary-color);">¥{{ metrics.totalAmount || '0.00' }}</div>
         </n-card>
         <n-card title="服务费净收" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 28px; font-weight: bold; color: var(--primary-color);">¥{{ metrics.serviceFee || '0.00' }}</div>
         </n-card>
         <n-card title="招商+渠道提成" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 28px; font-weight: bold; color: #ff6a00;">¥{{ metrics.commission || '0.00' }}</div>
         </n-card>
         <n-card title="毛利" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 28px; font-weight: bold; color: #18a058;">¥{{ metrics.grossProfit || '0.00' }}</div>
         </n-card>
         <n-card title="服务费收入" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 20px; font-weight: bold; color: var(--text-color);">¥{{ metrics.serviceFeeIncome || '0.00' }}</div>
         </n-card>
         <n-card title="技术服务费" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 20px; font-weight: bold; color: var(--text-color);">¥{{ metrics.techServiceFee || '0.00' }}</div>
         </n-card>
         <n-card title="达人分佣" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 20px; font-weight: bold; color: var(--text-color);">¥{{ metrics.talentCommission || '0.00' }}</div>
         </n-card>
         <n-card title="招商提成" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 20px; font-weight: bold; color: var(--text-color);">¥{{ metrics.bizCommission || '0.00' }}</div>
         </n-card>
         <n-card title="渠道提成" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
            <div style="font-size: 20px; font-weight: bold; color: var(--text-color);">¥{{ metrics.channelCommission || '0.00' }}</div>
         </n-card>
      </div>
    </n-spin>

    <n-card title="订单明细管理" :bordered="false" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
        <template #header-extra>
            <n-button type="primary" size="small" @click="$router.push('/data/orders')">查看完整明细</n-button>
        </template>
        <div style="text-align: center; color: #999; padding: 40px 0;">
           如需高级筛选及导出数据，请点击右上角「查看完整明细」
        </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useMessage } from 'naive-ui';
import { getMetrics } from '../../api/data';

const message = useMessage();
const loading = ref(false);
const metrics = ref<any>({});

const loadMetrics = async () => {
    loading.value = true;
    try {
        const res = await getMetrics();
        metrics.value = res?.data || res || {};
    } catch (error: any) {
        message.warning(error?.message || '获取指标异常');
    } finally {
        loading.value = false;
    }
};

onMounted(() => {
    loadMetrics();
});
</script>
