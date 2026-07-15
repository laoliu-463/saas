<template>
  <n-drawer
    :show="show"
    :width="DRAWER_WIDTH_PX.lg"
    placement="right"
    data-testid="quick-sample-talent-picker-drawer"
    @update:show="updateShow"
  >
    <n-drawer-content :native-scrollbar="false" closable>
      <template #header>
        <div class="quick-sample-talent-picker__header">
          <div class="quick-sample-talent-picker__heading">
            <span class="quick-sample-talent-picker__mark" aria-hidden="true" />
            <span id="quick-sample-talent-picker-title" data-testid="quick-sample-talent-picker-title">
              选择合作达人({{ draftSelected.length }}/{{ maxSelection }})
            </span>
          </div>
        </div>
      </template>

      <section
        v-if="show"
        class="quick-sample-talent-picker"
        role="dialog"
        aria-modal="true"
        aria-labelledby="quick-sample-talent-picker-title"
        data-testid="quick-sample-talent-picker"
      >
        <form class="quick-sample-talent-picker__search" @submit.prevent="search">
          <label class="quick-sample-talent-picker__field">
            <span>达人昵称：</span>
            <input
              v-model="nicknameQuery"
              type="search"
              placeholder="请输入"
              data-testid="quick-sample-talent-nickname-search"
            />
          </label>
          <label class="quick-sample-talent-picker__field">
            <span>抖音号：</span>
            <input
              v-model="douyinNoQuery"
              type="search"
              placeholder="请输入"
              data-testid="quick-sample-talent-douyin-search"
            />
          </label>
          <button type="submit" class="quick-sample-talent-picker__search-button" data-testid="quick-sample-talent-search">
            搜索
          </button>
        </form>

        <div class="quick-sample-talent-picker__table" role="table" aria-label="合作达人列表">
          <div class="quick-sample-talent-picker__table-head quick-sample-talent-picker__grid" role="row">
            <span role="columnheader" aria-label="选择" />
            <span role="columnheader">达人昵称</span>
            <span role="columnheader">抖音号</span>
            <span role="columnheader">粉丝数</span>
          </div>

          <div class="quick-sample-talent-picker__table-body" role="rowgroup">
            <div v-if="loading" class="quick-sample-talent-picker__empty" data-testid="quick-sample-talent-loading">
              加载中…
            </div>
            <div v-else-if="!pageRows.length" class="quick-sample-talent-picker__empty" data-testid="quick-sample-talent-empty">
              {{ emptyText }}
            </div>
            <template v-else>
              <div
                v-for="row in pageRows"
                :key="row.value"
                class="quick-sample-talent-picker__table-row quick-sample-talent-picker__grid"
                :class="{ 'is-selected': isSelected(row.value) }"
                role="row"
                tabindex="0"
                :data-testid="`quick-sample-talent-row-${row.value}`"
                @click="toggleRow(row.value)"
                @keydown.enter.prevent="toggleRow(row.value)"
              >
                <span role="cell" class="quick-sample-talent-picker__checkbox-cell">
                  <button
                    type="button"
                    class="quick-sample-talent-picker__checkbox"
                    :class="{ 'is-selected': isSelected(row.value) }"
                    :aria-label="`${isSelected(row.value) ? '取消选择' : '选择'} ${row.nickname || row.douyinNo || '达人'}`"
                    :aria-pressed="isSelected(row.value)"
                    :disabled="isSelectionDisabled(row.value)"
                    @click.stop="toggleRow(row.value)"
                  >
                    <span v-if="isSelected(row.value)" aria-hidden="true">✓</span>
                  </button>
                </span>
                <span role="cell" class="quick-sample-talent-picker__nickname">{{ row.nickname || row.douyinNo || '未命名达人' }}</span>
                <span role="cell">{{ row.douyinNo || row.value || '-' }}</span>
                <span role="cell">{{ formatFans(row.fansCount) }}</span>
              </div>
            </template>
          </div>
        </div>

        <div class="quick-sample-talent-picker__table-footer">
          <span v-if="validationMessage" class="quick-sample-talent-picker__validation" role="alert">{{ validationMessage }}</span>
          <span class="quick-sample-talent-picker__total">共{{ filteredRows.length }}条达人数据</span>
          <nav class="quick-sample-talent-picker__pagination" aria-label="达人分页">
            <button
              type="button"
              class="quick-sample-talent-picker__page-arrow"
              aria-label="上一页"
              :disabled="page <= 1"
              data-testid="quick-sample-talent-page-prev"
              @click="changePage(page - 1)"
            >
              ‹
            </button>
            <button
              v-for="pageNumber in pageNumbers"
              :key="pageNumber"
              type="button"
              class="quick-sample-talent-picker__page-number"
              :class="{ 'is-active': pageNumber === page }"
              :aria-current="pageNumber === page ? 'page' : undefined"
              @click="changePage(pageNumber)"
            >
              {{ pageNumber }}
            </button>
            <button
              type="button"
              class="quick-sample-talent-picker__page-arrow"
              aria-label="下一页"
              :disabled="page >= pageCount"
              data-testid="quick-sample-talent-page-next"
              @click="changePage(page + 1)"
            >
              ›
            </button>
          </nav>
        </div>
      </section>

      <template #footer>
        <div class="quick-sample-talent-picker__footer">
          <button
            type="button"
            class="quick-sample-talent-picker__footer-button quick-sample-talent-picker__footer-button--cancel"
            data-testid="quick-sample-talent-picker-cancel"
            @click="cancel"
          >
            取消
          </button>
          <button
            type="button"
            class="quick-sample-talent-picker__footer-button quick-sample-talent-picker__footer-button--submit"
            data-testid="quick-sample-talent-picker-submit"
            @click="submit"
          >
            提交
          </button>
        </div>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { DRAWER_WIDTH_PX } from '../../../constants/ui'

export interface QuickSampleTalentRow {
  value: string
  nickname: string
  douyinNo: string
  fansCount?: number | string | null
  avatarUrl?: string | null
  talentLevel?: string | null
  remark?: string | null
  recipientName?: string | null
  recipientPhone?: string | null
  recipientAddress?: string | null
}

const PAGE_SIZE = 200

const props = withDefaults(defineProps<{
  show: boolean
  rows: QuickSampleTalentRow[]
  selectedValues: string[]
  loading?: boolean
  emptyText?: string
  maxSelection?: number
}>(), {
  loading: false,
  emptyText: '暂无可选择达人',
  maxSelection: 20
})

const emit = defineEmits<{
  'update:show': [value: boolean]
  'update:selectedValues': [value: string[]]
}>()

const draftSelected = ref<string[]>([])
const nicknameQuery = ref('')
const douyinNoQuery = ref('')
const page = ref(1)
const validationMessage = ref('')

const normalizedNicknameQuery = computed(() => nicknameQuery.value.trim().toLowerCase())
const normalizedDouyinNoQuery = computed(() => douyinNoQuery.value.trim().toLowerCase())

const filteredRows = computed(() => props.rows.filter((row) => {
  const nickname = row.nickname.toLowerCase()
  const douyinNo = row.douyinNo.toLowerCase()
  return (!normalizedNicknameQuery.value || nickname.includes(normalizedNicknameQuery.value))
    && (!normalizedDouyinNoQuery.value || douyinNo.includes(normalizedDouyinNoQuery.value))
}))

const pageCount = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / PAGE_SIZE)))
const pageRows = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return filteredRows.value.slice(start, start + PAGE_SIZE)
})

const pageNumbers = computed(() => {
  if (pageCount.value <= 5) return Array.from({ length: pageCount.value }, (_, index) => index + 1)
  if (page.value <= 3) return [1, 2, 3, pageCount.value]
  if (page.value >= pageCount.value - 2) return [1, pageCount.value - 2, pageCount.value - 1, pageCount.value]
  return [1, page.value - 1, page.value, page.value + 1, pageCount.value]
})

watch(() => props.show, (show) => {
  if (show) {
    draftSelected.value = [...props.selectedValues]
    nicknameQuery.value = ''
    douyinNoQuery.value = ''
    page.value = 1
    validationMessage.value = ''
  }
}, { immediate: true })

watch(filteredRows, () => {
  if (page.value > pageCount.value) page.value = pageCount.value
})

function isSelected(value: string) {
  return draftSelected.value.includes(value)
}

function isSelectionDisabled(value: string) {
  return !isSelected(value) && draftSelected.value.length >= props.maxSelection
}

function toggleRow(value: string) {
  if (isSelected(value)) {
    draftSelected.value = draftSelected.value.filter((item) => item !== value)
    validationMessage.value = ''
    return
  }
  if (isSelectionDisabled(value)) {
    validationMessage.value = `最多选择${props.maxSelection}位达人`
    return
  }
  draftSelected.value = [...draftSelected.value, value]
  validationMessage.value = ''
}

function search() {
  page.value = 1
}

function changePage(nextPage: number) {
  page.value = Math.min(Math.max(nextPage, 1), pageCount.value)
}

function cancel() {
  updateShow(false)
}

function submit() {
  if (!draftSelected.value.length) {
    validationMessage.value = '请至少选择一位达人'
    return
  }
  emit('update:selectedValues', [...draftSelected.value])
  updateShow(false)
}

function updateShow(value: boolean) {
  emit('update:show', value)
}

function formatFans(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') return '-'
  const count = Number(value)
  if (!Number.isFinite(count)) return '-'
  if (count < 10000) return String(count)
  const wan = count / 10000
  return `${wan >= 100 ? Math.round(wan) : wan.toFixed(1).replace(/\.0$/, '')}W`
}
</script>

<style scoped>
.quick-sample-talent-picker {
  width: 100%;
  color: var(--text-primary, #1f2937);
  background: var(--card-color, #fff);
  font-size: 14px;
}

.quick-sample-talent-picker__header,
.quick-sample-talent-picker__heading,
.quick-sample-talent-picker__field,
.quick-sample-talent-picker__table-footer,
.quick-sample-talent-picker__pagination,
.quick-sample-talent-picker__footer {
  display: flex;
  align-items: center;
}

.quick-sample-talent-picker__header {
  width: 100%;
}

.quick-sample-talent-picker__heading {
  gap: 12px;
  color: #1f2937;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.quick-sample-talent-picker__mark {
  width: 4px;
  height: 20px;
  flex: 0 0 auto;
  border-radius: 2px;
  background: #f5222d;
}

.quick-sample-talent-picker__search {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 18px;
}

.quick-sample-talent-picker__field {
  min-width: 0;
  flex: 1;
  gap: 8px;
  color: #333;
  white-space: nowrap;
}

.quick-sample-talent-picker__field input {
  width: 100%;
  min-width: 0;
  height: 34px;
  box-sizing: border-box;
  padding: 0 11px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  outline: none;
  color: var(--text-primary, #1f2937);
  background: #fff;
  font: inherit;
}

.quick-sample-talent-picker__field input:focus {
  border-color: #f5222d;
  box-shadow: 0 0 0 2px rgba(245, 34, 45, .12);
}

.quick-sample-talent-picker__field input::placeholder {
  color: #b8b8b8;
}

.quick-sample-talent-picker__search-button {
  min-width: 64px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid #f5222d;
  border-radius: 6px;
  color: #f5222d;
  background: #fff;
  font: inherit;
  cursor: pointer;
}

.quick-sample-talent-picker__table {
  overflow: hidden;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
}

.quick-sample-talent-picker__grid {
  display: grid;
  grid-template-columns: 46px minmax(0, 1.4fr) minmax(120px, 1fr) 84px;
  align-items: center;
  column-gap: 0;
  padding: 0 16px;
}

.quick-sample-talent-picker__table-head {
  min-height: 46px;
  color: #333;
  background: var(--bg-sidebar, #fafafa);
  font-size: 14px;
  font-weight: 600;
}

.quick-sample-talent-picker__table-head span + span,
.quick-sample-talent-picker__table-row span + span {
  padding-left: 16px;
  border-left: 1px solid #f0f0f0;
}

.quick-sample-talent-picker__table-body {
  max-height: min(560px, calc(100vh - 310px));
  overflow: auto;
}

.quick-sample-talent-picker__table-row {
  min-height: 52px;
  border-top: 1px solid var(--border-color, #e5e7eb);
  color: #333;
  font-size: 14px;
  cursor: pointer;
  transition: background-color .15s ease;
}

.quick-sample-talent-picker__table-row:hover,
.quick-sample-talent-picker__table-row.is-selected {
  background: #fff8f8;
}

.quick-sample-talent-picker__checkbox-cell {
  display: flex;
  align-items: center;
}

.quick-sample-talent-picker__checkbox {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid #c8cdd3;
  border-radius: 4px;
  color: #fff;
  background: #fff;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.quick-sample-talent-picker__checkbox.is-selected {
  border-color: #f5222d;
  background: #f5222d;
}

.quick-sample-talent-picker__checkbox:disabled {
  cursor: not-allowed;
  opacity: .45;
}

.quick-sample-talent-picker__nickname {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-sample-talent-picker__empty {
  display: flex;
  min-height: 160px;
  align-items: center;
  justify-content: center;
  color: var(--text-muted, #999);
  font-size: 14px;
}

.quick-sample-talent-picker__table-footer {
  justify-content: flex-end;
  gap: 16px;
  margin-top: 12px;
  min-height: 32px;
}

.quick-sample-talent-picker__total {
  color: #333;
  font-size: 14px;
}

.quick-sample-talent-picker__validation {
  margin-right: auto;
  color: #f5222d;
  font-size: 13px;
}

.quick-sample-talent-picker__pagination {
  gap: 4px;
}

.quick-sample-talent-picker__page-number,
.quick-sample-talent-picker__page-arrow {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 4px;
  color: #333;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
}

.quick-sample-talent-picker__page-number.is-active {
  border-color: #f5222d;
  color: #f5222d;
}

.quick-sample-talent-picker__page-arrow {
  color: #666;
  font-size: 22px;
  line-height: 1;
}

.quick-sample-talent-picker__page-arrow:disabled {
  color: #ccc;
  cursor: not-allowed;
}

.quick-sample-talent-picker__footer {
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}

.quick-sample-talent-picker__footer-button {
  min-width: 72px;
  height: 34px;
  padding: 0 16px;
  border-radius: 6px;
  font: inherit;
  cursor: pointer;
}

.quick-sample-talent-picker__footer-button--cancel {
  border: 1px solid #d9d9d9;
  color: #333;
  background: #fff;
}

.quick-sample-talent-picker__footer-button--submit {
  border: 1px solid #f5222d;
  color: #fff;
  background: #f5222d;
}

@media (max-width: 640px) {
  .quick-sample-talent-picker__search {
    align-items: stretch;
    flex-direction: column;
  }

  .quick-sample-talent-picker__field {
    width: 100%;
  }

  .quick-sample-talent-picker__grid {
    grid-template-columns: 38px minmax(120px, 1fr) minmax(100px, .9fr) 64px;
    padding: 0 10px;
  }

  .quick-sample-talent-picker__table-head span + span,
  .quick-sample-talent-picker__table-row span + span {
    padding-left: 8px;
  }
}
</style>
