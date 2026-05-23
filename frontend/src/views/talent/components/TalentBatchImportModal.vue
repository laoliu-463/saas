<template>
  <n-modal :show="show" preset="card" title="批量导入达人" :style="{ width: MODAL_WIDTH.lg }" @update:show="closeModal">
    <n-alert type="info" :show-icon="false" style="margin-bottom: 16px">
      上传 Excel（.xlsx/.xls）或 CSV，读取第一列或「抖音号/账号/account」列作为抖音号；也可在下方粘贴，每行一个账号。
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

      <n-alert v-if="importResult" type="success" :show-icon="false" data-testid="talent-batch-import-result">
        共 {{ importResult.total }} 条：新建 {{ importResult.created }}，跳过 {{ importResult.skipped }}，失败
        {{ importResult.failed }}
      </n-alert>
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
import { ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { MODAL_WIDTH } from '../../../constants/ui'
import { batchImportTalents } from '../../../api/talent'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [] }>()

const message = useMessage()
const fileInputRef = ref<HTMLInputElement | null>(null)
const pasteText = ref('')
const selectedFileName = ref('')
const parseErrors = ref<string[]>([])
const parsedAccounts = ref<string[]>([])
const submitting = ref(false)
const importResult = ref<{
  total: number
  created: number
  skipped: number
  failed: number
} | null>(null)

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

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) {
    return
  }

  selectedFileName.value = file.name
  parseErrors.value = []
  importResult.value = null

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
  try {
    const result = await batchImportTalents(accounts)
    importResult.value = {
      total: result?.total ?? accounts.length,
      created: result?.created ?? 0,
      skipped: result?.skipped ?? 0,
      failed: result?.failed ?? 0
    }
    message.success(
      `导入完成：新建 ${importResult.value.created}，跳过 ${importResult.value.skipped}，失败 ${importResult.value.failed}`
    )
    emit('success')
  } catch (error: any) {
    message.error(error?.message || '批量导入失败')
  } finally {
    submitting.value = false
  }
}
</script>
