<template>
  <div>
    <n-card :bordered="false" style="border-radius: var(--border-radius); box-shadow: var(--shadow-base);">
      <n-tabs type="line" animated>
        <n-tab-pane name="selection" tab="选品（渠道）">
          <div style="display: flex; gap: 16px; margin-bottom: 24px;">
             <n-input placeholder="搜索宝贝名称..." style="width: 250px" />
             <n-select placeholder="类目" :options="[]" style="width: 150px" />
             <n-select placeholder="负责人" :options="[]" style="width: 150px" />
             <n-button type="primary">搜索</n-button>
          </div>
          <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 24px;">
            <n-card v-for="p in mockProductList" :key="p.id" hoverable style="border-radius: var(--border-radius); overflow: hidden; box-shadow: var(--shadow-base);">
              <template #cover>
                <img :src="p.imageUrl" style="height: 180px; object-fit: cover; width: 100%; border-bottom: 1px solid #efefef;" />
              </template>
              <h3 style="font-size: 16px; margin: 0 0 8px 0; color: #333;">{{ p.name }}</h3>
              <p style="color: #888; font-size: 13px; margin: 0 0 12px 0;">{{ p.shop }}</p>
              <div style="display: flex; justify-content: space-between; align-items: center; background: #fafafa; padding: 8px; border-radius: 4px;">
                 <n-tag type="success" size="small" round>佣金: {{ p.commissionRate * 100 }}%</n-tag>
                 <span style="font-weight: 700; color: var(--primary-color); font-size: 18px;">¥{{ p.price }}</span>
              </div>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="audit" tab="审核（招商）">
          <n-data-table :columns="auditColumns" :data="mockProductList" :bordered="false" striped />
        </n-tab-pane>
        <n-tab-pane name="manage" tab="管理（组长）">
          <div style="margin-bottom: 16px;"><n-button type="primary">新建活动</n-button></div>
          <n-data-table :columns="activityColumns" :data="mockActivityList" :bordered="false" striped />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { mockProductList, mockActivityList } from '../../mock/product';
import { NButton } from 'naive-ui';
import { h } from 'vue';

const auditColumns = [
    { title: '商品ID', key: 'productId' },
    { title: '商品名', key: 'name' },
    { title: '店铺', key: 'shop' },
    { title: '状态', key: 'status' },
    { title: '操作', key: 'action', render() { return h(NButton, { size: 'small', type: 'primary' }, { default: () => '审核' }); } }
];

const activityColumns = [
    { title: '活动ID', key: 'id' },
    { title: '活动名称', key: 'name' },
    { title: '商品数', key: 'productCount' },
    { title: '开始时间', key: 'startTime' }
];
</script>
