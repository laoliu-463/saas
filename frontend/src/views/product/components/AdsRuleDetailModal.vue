<template>
  <n-modal
    :show="show"
    preset="card"
    title="投流规则详情"
    style="width: 480px"
    data-testid="ads-rule-detail-modal"
    @update:show="emit('update:show', $event)"
  >
    <n-spin :show="loading">
      <div v-if="loading" class="ads-rule-placeholder" />
      <p v-else-if="!ruleText" class="ads-rule-empty" data-testid="ads-rule-empty">暂无投流规则</p>
      <p v-else class="ads-rule-text" data-testid="ads-rule-content">{{ ruleText }}</p>
    </n-spin>
  </n-modal>
</template>

<script setup lang="ts">
defineProps<{
  show: boolean
  ruleText?: string
  loading?: boolean
}>()

const emit = defineEmits<{ 'update:show': [value: boolean] }>()
</script>

<style scoped>
.ads-rule-text,
.ads-rule-empty {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.ads-rule-empty {
  color: var(--text-color-3);
}

.ads-rule-placeholder {
  min-height: 48px;
}
</style>
