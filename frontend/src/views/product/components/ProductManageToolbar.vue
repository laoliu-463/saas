<template>
  <n-space wrap :size="8" align="center" class="product-manage-toolbar">
    <n-button secondary size="small" @click="$emit('syncActivities')">同步最新活动</n-button>
    <n-button secondary size="small" :loading="syncing" @click="$emit('syncProducts')">一键同步商品</n-button>
  </n-space>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  syncing?: boolean
}>(), {
  syncing: false
})

defineEmits<{
  syncActivities: []
  syncProducts: []
}>()
</script>

<style scoped>
.product-manage-toolbar {
  justify-content: flex-end;
}
</style>
