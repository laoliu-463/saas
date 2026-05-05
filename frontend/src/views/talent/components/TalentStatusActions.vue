<template>
  <n-space :size="8" wrap>
    <n-button size="small" quaternary @click="emit('detail')">查看详情</n-button>
    <n-button v-if="canClaim" size="small" quaternary type="primary" @click="emit('claim')">认领</n-button>
    <n-button v-if="canRelease" size="small" quaternary type="warning" @click="emit('release')">释放</n-button>
    <n-button v-if="canBlacklist" size="small" quaternary type="error" @click="emit('blacklist')">拉黑</n-button>
    <n-button v-if="canUnblacklist" size="small" quaternary type="success" @click="emit('unblacklist')">解除拉黑</n-button>
  </n-space>
</template>

<script setup lang="ts">
defineProps<{
  canClaim?: boolean
  canRelease?: boolean
  canBlacklist?: boolean
  canUnblacklist?: boolean
}>()

const emit = defineEmits<{
  (e: 'detail'): void
  (e: 'claim'): void
  (e: 'release'): void
  (e: 'blacklist'): void
  (e: 'unblacklist'): void
}>()
</script>
