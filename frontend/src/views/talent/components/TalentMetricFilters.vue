<template>
  <section class="metric-filters app-panel" data-testid="talent-metric-filters">
    <div class="filter-row">
      <span class="filter-label">主推类目</span>
      <div class="category-tags" data-testid="talent-category-tags">
        <button
          type="button"
          class="category-tag"
          :class="{ active: !filters.category }"
          @click="applyCategory(null)"
        >
          全部
        </button>
        <button
          v-for="item in visibleCategories"
          :key="item"
          type="button"
          class="category-tag"
          :class="{ active: filters.category === item }"
          @click="applyCategory(item)"
        >
          {{ item }}
        </button>
        <n-dropdown
          v-if="overflowCategories.length"
          :options="overflowCategoryOptions"
          trigger="click"
          @select="(key: string) => applyCategory(key)"
        >
          <button type="button" class="category-tag category-tag--more">更多</button>
        </n-dropdown>
      </div>
    </div>

    <div class="filter-row">
      <span class="filter-label">带货数据</span>
      <div class="filter-fields filter-fields--6">
        <n-select
          :value="filters.liveSalesBand"
          :options="salesBandOptions"
          placeholder="场均销售额"
          clearable
          @update:value="(value: string | null) => applyFilter('liveSalesBand', value)"
        />
        <n-select
          :value="filters.liveViewBand"
          :options="fansBandOptions"
          placeholder="直播观看人数"
          clearable
          @update:value="(value: string | null) => applyFilter('liveViewBand', value)"
        />
        <n-select
          :value="filters.liveGpmBand"
          :options="gpmBandOptions"
          placeholder="直播GPM"
          clearable
          @update:value="(value: string | null) => applyFilter('liveGpmBand', value)"
        />
        <n-select
          :value="filters.videoSalesBand"
          :options="salesBandOptions"
          placeholder="单视频销售额"
          clearable
          @update:value="(value: string | null) => applyFilter('videoSalesBand', value)"
        />
        <n-select
          :value="filters.videoPlayBand"
          :options="playBandOptions"
          placeholder="视频播放量"
          clearable
          @update:value="(value: string | null) => applyFilter('videoPlayBand', value)"
        />
        <n-select
          :value="filters.videoGpmBand"
          :options="gpmBandOptions"
          placeholder="视频GPM"
          clearable
          @update:value="(value: string | null) => applyFilter('videoGpmBand', value)"
        />
      </div>
    </div>

    <div class="filter-row">
      <span class="filter-label">达人数据</span>
      <div class="filter-fields filter-fields--4">
        <n-select
          :value="filters.level"
          :options="levelOptions"
          placeholder="达人等级"
          clearable
          @update:value="(value: string | null) => applyFilter('level', value)"
        />
        <n-select
          :value="filters.fansBand"
          :options="fansBandOptions"
          placeholder="粉丝数"
          clearable
          @update:value="(value: string | null) => applyFilter('fansBand', value)"
        />
        <n-select
          :value="filters.gender"
          :options="genderOptions"
          placeholder="达人性别"
          clearable
          @update:value="(value: string | null) => applyFilter('gender', value)"
        />
        <n-input
          :value="filters.region"
          placeholder="达人地区"
          clearable
          @update:value="(value: string) => emitField('region', value)"
        />
      </div>
    </div>

    <div class="filter-row filter-row--last">
      <span class="filter-label">达人信息</span>
      <div class="filter-fields filter-fields--4">
        <n-input
          :value="filters.douyinNo"
          placeholder="抖音号"
          clearable
          data-testid="talent-douyin-no-filter"
          @update:value="(value: string) => emitField('douyinNo', value)"
          @keyup.enter="emit('search')"
        />
        <n-input
          :value="filters.nickname"
          placeholder="达人昵称"
          clearable
          data-testid="talent-nickname-filter"
          @update:value="(value: string) => emitField('nickname', value)"
          @keyup.enter="emit('search')"
        />
        <n-select
          :value="filters.contactStatus"
          :options="contactStatusOptions"
          placeholder="联系方式"
          clearable
          @update:value="(value: string | null) => applyFilter('contactStatus', value)"
        />
        <n-select
          :value="filters.claimStatus"
          :options="claimStatusOptions"
          placeholder="认领状态"
          clearable
          @update:value="(value: string | null) => applyFilter('claimStatus', value)"
        />
      </div>
      <div class="filter-actions">
        <n-button data-testid="talent-filter-reset" @click="emit('reset')">重置</n-button>
        <n-button type="primary" data-testid="talent-filter-search" @click="emit('search')">搜索</n-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, toRefs } from 'vue'
import { CLAIM_STATUS_OPTIONS } from '../constants'
import type { TalentFiltersState } from '../composables/useTalentFilters'
import {
  TALENT_CONTACT_STATUS_OPTIONS,
  TALENT_FANS_BAND_OPTIONS,
  TALENT_GENDER_OPTIONS,
  TALENT_GPM_BAND_OPTIONS,
  TALENT_LEVEL_OPTIONS,
  TALENT_MAIN_CATEGORY_TAGS,
  TALENT_PLAY_BAND_OPTIONS,
  TALENT_SALES_BAND_OPTIONS
} from '../talent-filter-options'

const VISIBLE_CATEGORY_COUNT = 10

const props = defineProps<{
  filters: TalentFiltersState
}>()

const { filters } = toRefs(props)

const emit = defineEmits<{
  (e: 'update:filters', value: Partial<TalentFiltersState>): void
  (e: 'search'): void
  (e: 'reset'): void
}>()

const salesBandOptions = TALENT_SALES_BAND_OPTIONS.filter((item) => item.value !== null)
const fansBandOptions = TALENT_FANS_BAND_OPTIONS.filter((item) => item.value !== null)
const playBandOptions = TALENT_PLAY_BAND_OPTIONS.filter((item) => item.value !== null)
const gpmBandOptions = TALENT_GPM_BAND_OPTIONS.filter((item) => item.value !== null)
const levelOptions = TALENT_LEVEL_OPTIONS.filter((item) => item.value !== null)
const genderOptions = TALENT_GENDER_OPTIONS.filter((item) => item.value !== null)
const contactStatusOptions = TALENT_CONTACT_STATUS_OPTIONS.filter((item) => item.value !== null)
const claimStatusOptions = CLAIM_STATUS_OPTIONS.filter((item) => item.value !== null)

const visibleCategories = computed(() => TALENT_MAIN_CATEGORY_TAGS.slice(0, VISIBLE_CATEGORY_COUNT))
const overflowCategories = computed(() => TALENT_MAIN_CATEGORY_TAGS.slice(VISIBLE_CATEGORY_COUNT))
const overflowCategoryOptions = computed(() =>
  overflowCategories.value.map((item) => ({ label: item, key: item }))
)

function emitField<K extends keyof TalentFiltersState>(key: K, value: TalentFiltersState[K]) {
  emit('update:filters', { [key]: value })
}

function applyFilter<K extends keyof TalentFiltersState>(key: K, value: TalentFiltersState[K]) {
  emitField(key, value)
  emit('search')
}

function applyCategory(value: string | null) {
  applyFilter('category', value)
}
</script>

<style scoped>
.metric-filters {
  padding: 16px 20px 20px;
  background: linear-gradient(180deg, #fff7f8 0%, #fff 42%);
  border: 1px solid #ffe8ec;
}

.filter-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px 16px;
  align-items: start;
  padding: 10px 0;
  border-top: 1px solid #f5e8eb;
}

.filter-row:first-of-type {
  border-top: 0;
}

.filter-row--last {
  grid-template-columns: 72px minmax(0, 1fr) auto;
  align-items: end;
}

.filter-label {
  padding-top: 6px;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}

.category-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-tag {
  border: 0;
  border-radius: 999px;
  padding: 4px 12px;
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}

.category-tag:hover {
  color: #f5222d;
}

.category-tag.active {
  background: #fff1f1;
  color: #f5222d;
  font-weight: 600;
}

.category-tag--more {
  border: 1px dashed #f0c8cf;
}

.filter-fields {
  display: grid;
  gap: 12px 14px;
}

.filter-fields--6 {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.filter-fields--4 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-bottom: 2px;
}

@media (max-width: 1440px) {
  .filter-fields--6 {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .filter-row,
  .filter-row--last {
    grid-template-columns: minmax(0, 1fr);
  }

  .filter-label {
    padding-top: 0;
  }

  .filter-fields--6,
  .filter-fields--4 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .filter-fields--6,
  .filter-fields--4 {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
