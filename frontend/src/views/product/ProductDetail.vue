<template>
  <n-drawer :show="show" width="680" placement="right" @update:show="emit('update:show', $event)">
    <n-drawer-content title="商品详情（复用组件）" closable>
      <n-descriptions v-if="detail" bordered column="1">
        <n-descriptions-item label="商品ID">{{ detail.productId || '-' }}</n-descriptions-item>
        <n-descriptions-item label="商品名称">{{ detail.title || '-' }}</n-descriptions-item>
        <n-descriptions-item label="店铺">{{ detail.shopName || '-' }}</n-descriptions-item>
        <n-descriptions-item label="售价">{{ detail.priceText || '-' }}</n-descriptions-item>
        <n-descriptions-item label="状态">{{ detail.statusText || '-' }}</n-descriptions-item>
        <n-descriptions-item label="库存">{{ detail.productStock || '-' }}</n-descriptions-item>
        <n-descriptions-item label="销量">{{ detail.sales ?? '-' }}</n-descriptions-item>
      </n-descriptions>
      <n-empty v-else description="暂无详情数据" />
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
defineProps<{
  show: boolean;
  detail?: Record<string, any> | null;
}>();

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
}>();
</script>

