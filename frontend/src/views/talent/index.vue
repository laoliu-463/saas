<template>
  <div>
    <n-card :bordered="false" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
      <n-tabs type="line" animated>
        <n-tab-pane name="public" tab="公海">
          <div style="margin-bottom: 16px; display: flex; gap: 16px;">
            <n-input placeholder="达人昵称" style="width: 200px" />
            <n-select placeholder="平台" :options="[{label:'抖音',value:'douyin'}]" style="width: 150px" />
            <n-button type="primary">检索</n-button>
          </div>
          <n-data-table :columns="publicColumns" :data="mockTalentList" :bordered="false" striped />
        </n-tab-pane>
        <n-tab-pane name="private" tab="私海">
          <n-empty description="私海空空如也，快去公海认领吧！" style="margin-top: 40px;" />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { mockTalentList } from '../../mock/talent';
import { NButton } from 'naive-ui';
import { h } from 'vue';

const publicColumns = [
    { title: '达人昵称', key: 'name' },
    { title: '粉丝量', key: 'followers' },
    { title: '擅长类目', key: 'category' },
    { title: '操作', key: 'action', render() { return h(NButton, { size: 'small', type: 'info' }, { default: () => '认领' }); } }
];
</script>
