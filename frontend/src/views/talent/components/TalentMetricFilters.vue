<template>
  <div class="metric-filters">
    <n-space wrap>
      <n-input
        :value="filters.keyword"
        placeholder="搜索达人昵称 / 抖音号 / UID"
        clearable
        style="width: 240px"
        @update:value="(value: string) => emitField('keyword', value)"
        @keyup.enter="emit('search')"
      />
      <n-select
        :value="filters.category"
        :options="categoryOptions"
        placeholder="经营分类"
        clearable
        style="width: 160px"
        @update:value="(value: string | null) => emitField('category', value)"
      />
      <n-select
        :value="filters.claimStatus"
        :options="claimStatusOptions"
        placeholder="认领状态"
        clearable
        style="width: 160px"
        @update:value="(value: string | null) => emitField('claimStatus', value)"
      />
      <n-input
        :value="filters.region"
        placeholder="地区"
        clearable
        style="width: 140px"
        @update:value="(value: string) => emitField('region', value)"
      />
      <n-input-number
        :value="filters.minFans"
        :min="0"
        placeholder="最低粉丝"
        style="width: 140px"
        @update:value="(value: number | null) => emitField('minFans', normalizeNumber(value))"
      />
      <n-input-number
        :value="filters.maxFans"
        :min="0"
        placeholder="最高粉丝"
        style="width: 140px"
        @update:value="(value: number | null) => emitField('maxFans', normalizeNumber(value))"
      />
      <n-button type="primary" @click="emit('search')">查询</n-button>
      <n-button @click="emit('reset')">重置</n-button>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import { toRefs } from 'vue'
import { CATEGORY_OPTIONS, CLAIM_STATUS_OPTIONS } from '../constants'
import type { TalentFiltersState } from '../composables/useTalentFilters'

const props = defineProps<{
  filters: TalentFiltersState
}>()

const { filters } = toRefs(props)

const emit = defineEmits<{
  (e: 'update:filters', value: Partial<TalentFiltersState>): void
  (e: 'search'): void
  (e: 'reset'): void
}>()

const categoryOptions = CATEGORY_OPTIONS
const claimStatusOptions = CLAIM_STATUS_OPTIONS

function emitField<K extends keyof TalentFiltersState>(key: K, value: TalentFiltersState[K]) {
  emit('update:filters', { [key]: value })
}

function normalizeNumber(value: number | null) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}
</script>

<style scoped>
.metric-filters {
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
}
</style>
