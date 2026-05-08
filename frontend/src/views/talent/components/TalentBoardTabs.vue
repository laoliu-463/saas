<template>
  <div class="board-tabs">
    <n-tabs :value="modelValue" type="line" animated @update:value="handleUpdate">
      <n-tab-pane
        v-for="item in tabs"
        :key="item.value"
        :name="item.value"
        :tab="`${item.label} · ${summary[item.value] ?? 0}`"
      />
    </n-tabs>
    <div class="board-help">{{ helpText }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, toRefs } from 'vue'
import { TALENT_VIEW_HELP_MAP, TALENT_VIEW_OPTIONS } from '../constants'

const props = defineProps<{
  modelValue: string
  summary: Record<string, number>
  tabs?: ReadonlyArray<{ label: string; value: string }>
}>()

const { modelValue, summary } = toRefs(props)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const tabs = computed(() => props.tabs?.length ? props.tabs : TALENT_VIEW_OPTIONS)

const helpText = computed(() => TALENT_VIEW_HELP_MAP[modelValue.value] || '')

function handleUpdate(value: string) {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.board-tabs {
  padding: 4px 16px 12px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
}

.board-help {
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--text-secondary);
}
</style>
