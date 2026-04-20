<template>
  <div>
    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 24px; margin-bottom: 24px;">
       <n-card title="总订单数" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
          <div style="font-size: 28px; font-weight: bold; color: var(--primary-color);">3,420</div>
       </n-card>
       <n-card title="订单总额" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
          <div style="font-size: 28px; font-weight: bold; color: var(--primary-color);">¥124,500.00</div>
       </n-card>
       <n-card title="服务费收益" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
          <div style="font-size: 28px; font-weight: bold; color: var(--primary-color);">¥21,800.00</div>
       </n-card>
       <n-card title="招商提成" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
          <div style="font-size: 28px; font-weight: bold; color: #ff6a00;">¥4,200.00</div>
       </n-card>
    </div>
    
    <n-card title="订单明细" :bordered="false" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
        <template #header-extra>
            <n-button type="primary" size="small">导出数据</n-button>
        </template>
        <div style="margin-bottom: 16px; display: flex; gap: 16px;">
            <n-date-picker type="daterange" style="width: 300px;" />
            <n-select placeholder="订单状态" :options="[]" style="width: 150px" />
        </div>
        <n-data-table :columns="columns" :data="mockOrderList" :bordered="false" striped :pagination="{ pageSize: 10 }" />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { mockOrderList } from '../../mock/data';
import { NTag } from 'naive-ui';
import { h } from 'vue';

const columns = [
    { title: '订单号', key: 'orderId' },
    { title: '金额', key: 'amount' },
    { title: '状态', key: 'status', render(row: any) { 
        return h(NTag, { type: row.status === 'completed' ? 'success' : 'warning' }, { default: () => row.status === 'completed' ? '已完成' : '进行中' }); 
    }}
];
</script>
