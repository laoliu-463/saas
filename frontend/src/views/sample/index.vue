<template>
  <div>
    <n-card :bordered="false" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
      <n-tabs type="line" animated>
        <n-tab-pane name="pending_audit" tab="待审核">
           <n-data-table :columns="columns" :data="mockSampleList" :bordered="false" striped />
        </n-tab-pane>
        <n-tab-pane name="pending_ship" tab="待发货">
           <n-empty description="没有待发货的寄样任务" />
        </n-tab-pane>
        <n-tab-pane name="shipping" tab="快递中"><n-empty description="无数据"/></n-tab-pane>
        <n-tab-pane name="pending_task" tab="待交作业"><n-empty description="无数据"/></n-tab-pane>
        <n-tab-pane name="finished" tab="已完成"><n-empty description="无数据"/></n-tab-pane>
        <n-tab-pane name="rejected" tab="已拒绝"><n-empty description="无数据"/></n-tab-pane>
        <n-tab-pane name="closed" tab="已关闭"><n-empty description="无数据"/></n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { mockSampleList } from '../../mock/sample';
import { NButton, NTag } from 'naive-ui';
import { h } from 'vue';

const columns = [
    { title: '寄样编号', key: 'id' },
    { title: '商品信息', key: 'product' },
    { title: '申请达人', key: 'talent' },
    { title: '当前状态', key: 'status', render(row: any) { return h(NTag, { type: 'warning' }, { default: () => '待审核' }); } },
    { title: '操作', key: 'action', render() { return h(NButton, { size: 'small', type: 'primary' }, { default: () => '处理' }); } }
];
</script>
