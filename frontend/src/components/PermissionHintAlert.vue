<template>
  <n-alert
    v-if="hint"
    type="warning"
    :bordered="false"
    class="permission-hint-alert"
    data-testid="global-permission-hint"
  >
    {{ hint }}
  </n-alert>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { globalPermissionHint } from '../stores/permissionHint'

const props = defineProps<{ modelValue?: string }>()

const hint = computed(() => props.modelValue ?? globalPermissionHint.value)
</script>

<style scoped>
.permission-hint-alert {
  margin-bottom: var(--content-gap, 12px);
}
</style>
