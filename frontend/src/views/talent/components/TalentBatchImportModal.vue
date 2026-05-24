<template>
  <n-modal :show="show" preset="card" title="批量导入达人" :style="{ width: MODAL_WIDTH.lg }" @update:show="closeModal">
    <n-alert type="info" :show-icon="false" style="margin-bottom: 16px">
      支持 .xlsx / .xls / .csv（≤10MB）。读取第一列或「抖音号 / 账号 / account」列；也可在下方粘贴，每行一个账号。
      必填：达人账号 / 抖音号 / 主页链接（三选一，按后端解析规则）。可选：昵称、备注、标签等请在导入后于详情中维护。
    </n-alert>

    <n-space vertical :size="16">
      <input
        ref="fileInputRef"
        type="file"
        accept=".xlsx,.xls,.csv"
        style="display: none"
        data-testid="talent-batch-import-file"
        @change="handleFileChange"
      />
      <n-space>
        <n-button data-testid="talent-batch-import-choose" @click="triggerFileInput">选择文件</n-button>
        <n-button tertiary data-testid="talent-batch-import-template" @click="downloadTemplate">下载模板</n-button>
        <n-text v-if="selectedFileName" depth="3">已选：{{ selectedFileName }}</n-text>
      </n-space>

      <n-input
        v-model:value="pasteText"
        type="textarea"
        :autosize="{ minRows: 4, maxRows: 10 }"
        placeholder="每行一个抖音号，或用逗号分隔"
        data-testid="talent-batch-import-paste"
      />

      <n-alert v-if="parseErrors.length > 0" type="warning" :show-icon="false">
        <div v-for="(err, idx) in parseErrors" :key="idx">{{ err }}</div>
      </n-alert>

      <n-alert v-if="importResult" type="info" :show-icon="false" data-testid="talent-batch-import-result">
        共 {{ importResult.total }} 条：新建 {{ importResult.created }}，跳过 {{ importResult.skipped }}，失败
        {{ importResult.failed }}
      </n-alert>

      <div v-if="failedItems.length > 0">
        <n-space justify="space-between" align="center" style="margin-bottom: 8px">
          <n-text strong>失败明细（{{ failedItems.length }}）</n-text>
          <n-button size="small" tertiary data-testid="talent-batch-import-download-failures" @click="downloadFailures">
            下载失败明细
          </n-button>
        </n-space>
        <n-data-table
          size="small"
          :columns="failureColumns"
          :data="failedItems"
          :pagination="false"
          :max-height="220"
          data-testid="talent-batch-import-failures"
        />
      </div>
    </n-space>

    <template #footer>
      <n-space justify="end">
        <n-button @click="closeModal">关闭</n-button>
        <n-button
          type="primary"
          :loading="submitting"
          :disabled="parsedAccounts.length === 0"
          data-testid="talent-batch-import-submit"
          @click="submit"
        >
          导入 {{ parsedAccounts.length > 0 ? `(${parsedAccounts.length})` : '' }}
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { h, ref, watch } from 'vue'
import { NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { MODAL_WIDTH } from '../../../constants/ui'
import { batchImportTalents } from '../../../api/talent'

export interface TalentBatchImportItemResult {
  account: string
  status: string
  talentId?: string | null
  message?: string | null
}

export interface TalentBatchImportResult {
  total: number
  created: number
  skipped: number
  failed: number
  items?: TalentBatchImportItemResult[]
}

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [] }>()

const message = useMessage()
const MAX_FILE_BYTES = 10 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['.xlsx', '.xls', '.csv']

const fileInputRef = ref<HTMLInputElement | null>(null)
const pasteText = ref('')
const selectedFileName = ref('')
const parseErrors = ref<string[]>([])
const parsedAccounts = ref<string[]>([])
const submitting = ref(false)
const importResult = ref<TalentBatchImportResult | null>(null)
const failedItems = ref<TalentBatchImportItemResult[]>([])

const failureColumns: DataTableColumns<TalentBatchImportItemResult> = [
  { title: '账号', key: 'account', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row: TalentBatchImportItemResult) =>
      h(
        NTag,
        { size: 'small', type: row.status === 'FAILED' ? 'error' : 'default' },
        { default: () => row.status }
      )
  },
  {
    title: '原因',
    key: 'message',
    ellipsis: { tooltip: true },
    render: (row: TalentBatchImportItemResult) => row.message || '-'
  }
]

const ACCOUNT_HEADERS = new Set(['抖音号', '账号', 'account', 'douyin', 'douyinno', 'douyin_no'])

watch(
  () => props.show,
  (visible) => {
    if (!visible) {
      resetState()
    }
  }
)

watch(pasteText, () => {
  mergeParsedAccounts(parsePasteAccounts(pasteText.value))
})

function resetState() {
  pasteText.value = ''
  selectedFileName.value = ''
  parseErrors.value = []
  parsedAccounts.value = []
  importResult.value = null
  failedItems.value = []
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function closeModal() {
  emit('update:show', false)
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function isAllowedFile(file: File): boolean {
  const lower = file.name.toLowerCase()
  return ALLOWED_EXTENSIONS.some((ext) => lower.endsWith(ext))
}

function normalizeAccount(value: unknown): string | null {
  if (value == null) {
    return null
  }
  const text = String(value).trim()
  return text.length > 0 ? text : null
}

function dedupeAccounts(accounts: string[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const account of accounts) {
    const normalized = normalizeAccount(account)
    if (!normalized || seen.has(normalized)) {
      continue
    }
    seen.add(normalized)
    result.push(normalized)
  }
  return result
}

function parsePasteAccounts(text: string): string[] {
  if (!text.trim()) {
    return []
  }
  return dedupeAccounts(
    text
      .split(/[\r\n,，;；]+/)
      .map((line) => line.trim())
      .filter(Boolean)
  )
}

function parseSheetRows(rows: unknown[][]): { accounts: string[]; errors: string[] } {
  const errors: string[] = []
  if (!rows.length) {
    return { accounts: [], errors: ['文件为空'] }
  }

  const headerRow = rows[0] || []
  let accountColumnIndex = 0
  const headerTexts = headerRow.map((cell) => String(cell ?? '').trim().toLowerCase())
  const matchedHeaderIndex = headerTexts.findIndex((cell) => ACCOUNT_HEADERS.has(cell) || ACCOUNT_HEADERS.has(cell.toLowerCase()))
  if (matchedHeaderIndex >= 0) {
    accountColumnIndex = matchedHeaderIndex
  }

  const dataRows = matchedHeaderIndex >= 0 ? rows.slice(1) : rows
  const accounts: string[] = []
  dataRows.forEach((row, index) => {
    const rowNumber = matchedHeaderIndex >= 0 ? index + 2 : index + 1
    const account = normalizeAccount(row?.[accountColumnIndex])
    if (!account) {
      if (row && row.some((cell) => normalizeAccount(cell))) {
        errors.push(`第 ${rowNumber} 行未找到账号列`)
      }
      return
    }
    accounts.push(account)
  })

  return { accounts: dedupeAccounts(accounts), errors }
}

function mergeParsedAccounts(accounts: string[]) {
  parsedAccounts.value = dedupeAccounts([...parsedAccounts.value, ...accounts])
}

function downloadTemplate() {
  const header = '抖音号\n'
  const sample = '123456789\n987654321\n'
  const blob = new Blob(['\ufeff' + header + sample], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'talent-import-template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

function downloadFailures() {
  if (failedItems.value.length === 0) {
    return
  }
  const lines = ['账号,状态,原因', ...failedItems.value.map((item) => {
    const reason = (item.message || '').replace(/"/g, '""')
    return `"${item.account}","${item.status}","${reason}"`
  })]
  const blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'talent-import-failures.csv'
  link.click()
  URL.revokeObjectURL(url)
}

function applyImportResult(result: TalentBatchImportResult, submittedCount: number): TalentBatchImportResult {
  const summary: TalentBatchImportResult = {
    total: result.total ?? submittedCount,
    created: result.created ?? 0,
    skipped: result.skipped ?? 0,
    failed: result.failed ?? 0,
    items: Array.isArray(result.items) ? result.items : []
  }
  importResult.value = summary
  failedItems.value = summary.items?.filter((item) => item.status === 'FAILED') ?? []
  return summary
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) {
    return
  }

  selectedFileName.value = file.name
  parseErrors.value = []
  importResult.value = null
  failedItems.value = []

  if (!isAllowedFile(file)) {
    message.error('仅支持 .xlsx / .xls / .csv 文件')
    target.value = ''
    return
  }
  if (file.size > MAX_FILE_BYTES) {
    message.error('文件不能超过 10MB')
    target.value = ''
    return
  }

  try {
    const lowerName = file.name.toLowerCase()
    let rows: unknown[][] = []
    if (lowerName.endsWith('.csv')) {
      const text = await file.text()
      rows = text
        .split(/\r?\n/)
        .map((line) => line.split(/,|\t/).map((cell) => cell.trim()))
        .filter((line) => line.some((cell) => cell))
    } else {
      const XLSX = await import('xlsx')
      const buf = await file.arrayBuffer()
      const wb = XLSX.read(buf, { type: 'array' })
      const ws = wb.Sheets[wb.SheetNames[0]]
      rows = XLSX.utils.sheet_to_json<unknown[]>(ws, { header: 1, defval: '' }) as unknown[][]
    }

    const { accounts, errors } = parseSheetRows(rows)
    mergeParsedAccounts(accounts)
    parseErrors.value = errors
    if (accounts.length === 0) {
      message.warning('未解析到有效抖音号，请检查文件格式')
    } else {
      message.success(`已从文件解析 ${accounts.length} 个账号`)
    }
  } catch (error: any) {
    message.error('文件解析失败：' + (error?.message || '未知错误'))
  }

  target.value = ''
}

async function submit() {
  const accounts = dedupeAccounts([...parsedAccounts.value, ...parsePasteAccounts(pasteText.value)])
  if (accounts.length === 0) {
    message.warning('请先上传文件或粘贴抖音号')
    return
  }

  submitting.value = true
  importResult.value = null
  failedItems.value = []
  try {
    const result = (await batchImportTalents(accounts)) as TalentBatchImportResult
    const summary = applyImportResult(result, accounts.length)
    if (summary.failed > 0) {
      message.warning(
        `导入完成：新建 ${summary.created}，跳过 ${summary.skipped}，失败 ${summary.failed}（见下方明细）`
      )
    } else {
      message.success(
        `导入完成：新建 ${summary.created}，跳过 ${summary.skipped}，失败 ${summary.failed}`
      )
    }
    if (summary.created > 0 || summary.skipped > 0) {
      emit('success')
    }
  } catch (error: any) {
    message.error(error?.message || error?.msg || '批量导入失败')
  } finally {
    submitting.value = false
  }
}
</script>
