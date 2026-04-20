<template>
  <div class="page-header">
    <h2 class="title">{{ title }}</h2>
    <div class="extra">
      <slot name="extra"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{title: string}>()
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2225;
}
</style>
