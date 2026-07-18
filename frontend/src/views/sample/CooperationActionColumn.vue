<template>
  <div class="cooperation-actions">
    <n-tooltip v-for="action in actions" :key="action.key" :disabled="action.enabled">
      <template #trigger>
        <span>
          <n-button
            text
            size="small"
            :type="action.key === 'REJECT' ? 'error' : 'primary'"
            :disabled="!action.enabled"
            :data-testid="`cooperation-action-${action.key}`"
            @click="emit('select', action.key)"
          >
            {{ action.label }}
          </n-button>
        </span>
      </template>
      {{ action.disabledReason }}
    </n-tooltip>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CooperationActionKey, SampleActionAvailabilityMap } from '../../types'
import { resolveCooperationActions } from './cooperation-actions'

const props = defineProps<{ availability?: SampleActionAvailabilityMap | null }>()
const emit = defineEmits<{ select: [action: CooperationActionKey] }>()
const actions = computed(() => resolveCooperationActions(props.availability))
</script>

<style scoped>
.cooperation-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
</style>
