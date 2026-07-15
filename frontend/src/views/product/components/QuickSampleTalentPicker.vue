<template>
  <section
    v-if="show"
    class="quick-sample-talent-picker"
    role="dialog"
    aria-modal="true"
    aria-labelledby="quick-sample-talent-picker-title"
    data-testid="quick-sample-talent-picker"
  >
    <header class="quick-sample-talent-picker__header">
      <div class="quick-sample-talent-picker__heading">
        <span class="quick-sample-talent-picker__mark" aria-hidden="true"><i /><i /></span>
        <h2 id="quick-sample-talent-picker-title" data-testid="quick-sample-talent-picker-title">
          选择合作达人({{ draftSelected.length }}/{{ maxSelection }})
        </h2>
      </div>

      <div class="quick-sample-talent-picker__actions">
        <button
          type="button"
          class="quick-sample-talent-picker__cancel"
          data-testid="quick-sample-talent-picker-cancel"
          @click="cancel"
        >
          取消
        </button>
        <button
          type="button"
          class="quick-sample-talent-picker__submit"
          data-testid="quick-sample-talent-picker-submit"
          @click="submit"
        >
          提交
        </button>
      </div>
    </header>

    <main class="quick-sample-talent-picker__main">
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
    </main>

    <footer class="quick-sample-talent-picker__footer">
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
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

export interface QuickSampleTalentRow {
  value: string
  nickname: string
  douyinNo: string
  fansCount?: number | string | null
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
  emit('update:show', false)
}

function submit() {
  if (!draftSelected.value.length) {
    validationMessage.value = '请至少选择一位达人'
    return
  }
  emit('update:selectedValues', [...draftSelected.value])
  emit('update:show', false)
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
  position: fixed;
  z-index: 3000;
  inset: 0;
  display: grid;
  grid-template-rows: 94px minmax(0, 1fr) 132px;
  overflow: hidden;
  color: #1e1e1e;
  background: #fff;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.quick-sample-talent-picker__header,
.quick-sample-talent-picker__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
  background: #fff;
}

.quick-sample-talent-picker__header {
  border-bottom: 1px solid #ededed;
}

.quick-sample-talent-picker__heading,
.quick-sample-talent-picker__actions,
.quick-sample-talent-picker__search,
.quick-sample-talent-picker__field,
.quick-sample-talent-picker__pagination {
  display: flex;
  align-items: center;
}

.quick-sample-talent-picker__heading {
  gap: 14px;
}

.quick-sample-talent-picker__heading h2 {
  margin: 0;
  color: #111;
  font-size: clamp(22px, 2vw, 28px);
  font-weight: 700;
  line-height: 1;
}

.quick-sample-talent-picker__mark {
  display: inline-flex;
  align-items: center;
  gap: 9px;
}

.quick-sample-talent-picker__mark i {
  display: block;
  width: 6px;
  height: 27px;
  border-radius: 4px;
  background: #f5222d;
}

.quick-sample-talent-picker__mark i:first-child {
  opacity: .75;
}

.quick-sample-talent-picker__actions {
  gap: 16px;
}

.quick-sample-talent-picker__actions button {
  width: 120px;
  height: 60px;
  border-radius: 9px;
  font-size: 26px;
  cursor: pointer;
}

.quick-sample-talent-picker__cancel {
  border: 1px solid #d9d9d9;
  color: #222;
  background: #fff;
  box-shadow: 0 1px 4px rgb(0 0 0 / 7%);
}

.quick-sample-talent-picker__submit {
  border: 1px solid #f5222d;
  color: #fff;
  background: #f5222d;
}

.quick-sample-talent-picker__actions button:hover {
  filter: brightness(.97);
}

.quick-sample-talent-picker__main {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 46px 40px 0;
  overflow: hidden;
}

.quick-sample-talent-picker__search {
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 68px;
}

.quick-sample-talent-picker__field {
  gap: 12px;
  white-space: nowrap;
}

.quick-sample-talent-picker__field > span {
  color: #222;
  font-size: clamp(18px, 1.65vw, 25px);
}

.quick-sample-talent-picker__field input {
  width: clamp(240px, 24.5vw, 376px);
  height: 60px;
  box-sizing: border-box;
  padding: 0 20px;
  border: 2px solid #dedede;
  border-radius: 11px;
  outline: 0;
  color: #222;
  background: #fff;
  font-size: 20px;
}

.quick-sample-talent-picker__field input::placeholder {
  color: #c9c9c9;
}

.quick-sample-talent-picker__field input:focus {
  border-color: #f5222d;
}

.quick-sample-talent-picker__search-button {
  height: 46px;
  padding: 0 16px;
  border: 2px solid #f5222d;
  border-radius: 8px;
  color: #f5222d;
  background: #fff;
  font-size: 22px;
  cursor: pointer;
}

.quick-sample-talent-picker__table {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  border-radius: 14px 14px 0 0;
}

.quick-sample-talent-picker__grid {
  display: grid;
  grid-template-columns: 145px minmax(0, 1.45fr) minmax(180px, .9fr) minmax(130px, .55fr);
  align-items: center;
  padding: 0 30px;
}

.quick-sample-talent-picker__table-head {
  flex: 0 0 102px;
  color: #161616;
  background: #fafafa;
  font-size: clamp(18px, 1.65vw, 25px);
  font-weight: 700;
}

.quick-sample-talent-picker__table-head span + span,
.quick-sample-talent-picker__table-row span + span {
  border-left: 1px solid #ececec;
  padding-left: 30px;
}

.quick-sample-talent-picker__table-body {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  border-top: 1px solid #ededed;
}

.quick-sample-talent-picker__table-row {
  min-height: 102px;
  box-sizing: border-box;
  border-bottom: 1px solid #ededed;
  color: #313131;
  font-size: clamp(17px, 1.5vw, 23px);
  cursor: pointer;
}

.quick-sample-talent-picker__table-row:hover,
.quick-sample-talent-picker__table-row.is-selected {
  background: #fffafa;
}

.quick-sample-talent-picker__checkbox-cell {
  display: flex;
  align-items: center;
}

.quick-sample-talent-picker__checkbox {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 2px solid #ddd;
  border-radius: 8px;
  color: #fff;
  background: #fff;
  font-size: 21px;
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
  min-height: 180px;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 18px;
}

.quick-sample-talent-picker__footer {
  justify-content: flex-end;
  gap: 30px;
  border-top: 1px solid #fff;
}

.quick-sample-talent-picker__total {
  color: #242424;
  font-size: clamp(18px, 1.65vw, 25px);
}

.quick-sample-talent-picker__validation {
  color: #f5222d;
  font-size: 16px;
}

.quick-sample-talent-picker__pagination {
  gap: 17px;
}

.quick-sample-talent-picker__page-number,
.quick-sample-talent-picker__page-arrow {
  display: inline-flex;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 8px;
  color: #242424;
  background: transparent;
  font-size: 22px;
  cursor: pointer;
}

.quick-sample-talent-picker__page-number.is-active {
  border-color: #f5222d;
  color: #f5222d;
}

.quick-sample-talent-picker__page-arrow {
  color: #222;
  font-size: 38px;
  line-height: 1;
}

.quick-sample-talent-picker__page-arrow:disabled {
  color: #ccc;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .quick-sample-talent-picker {
    grid-template-rows: 78px minmax(0, 1fr) 92px;
  }

  .quick-sample-talent-picker__header,
  .quick-sample-talent-picker__footer {
    padding: 0 16px;
  }

  .quick-sample-talent-picker__actions {
    gap: 8px;
  }

  .quick-sample-talent-picker__actions button {
    width: 78px;
    height: 44px;
    font-size: 18px;
  }

  .quick-sample-talent-picker__main {
    padding: 24px 16px 0;
    overflow: auto;
  }

  .quick-sample-talent-picker__search {
    margin-bottom: 24px;
  }

  .quick-sample-talent-picker__field {
    width: 100%;
    justify-content: space-between;
  }

  .quick-sample-talent-picker__field input {
    flex: 1;
  }

  .quick-sample-talent-picker__grid {
    grid-template-columns: 54px minmax(140px, 1fr) minmax(110px, .8fr) minmax(70px, .5fr);
    padding: 0 12px;
  }

  .quick-sample-talent-picker__table-head span + span,
  .quick-sample-talent-picker__table-row span + span {
    padding-left: 10px;
  }

  .quick-sample-talent-picker__table-head {
    flex-basis: 64px;
  }

  .quick-sample-talent-picker__table-row {
    min-height: 72px;
    font-size: 14px;
  }

  .quick-sample-talent-picker__checkbox {
    width: 24px;
    height: 24px;
  }

  .quick-sample-talent-picker__footer {
    gap: 12px;
  }

  .quick-sample-talent-picker__pagination {
    gap: 4px;
  }
}
</style>
