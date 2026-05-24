<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="批量导入物流单号"
    style="width: 720px"
    data-testid="sample-logistics-import-modal"
    @after-leave="resetState"
  >
    <n-alert type="info" :bordered="false">
      支持 .xlsx / .xls（≤10MB，最多 1000 行）。表头需包含：申请编号、物流公司、物流单号。
    </n-alert>
    <n-space style="margin: 12px 0">
      <n-button data-testid="logistics-import-template" @click="downloadTemplate">下载模板</n-button>
      <n-upload
        :show-file-list="false"
        accept=".xlsx,.xls"
        :custom-request="handleUpload"
      >
        <n-button type="primary" :loading="uploading" data-testid="logistics-import-upload">上传 Excel</n-button>
      </n-upload>
    </n-space>

    <div v-if="result" data-testid="logistics-import-result">
      <n-alert type="success" :bordered="false" style="margin-bottom: 12px">
        导入完成：共 {{ result.total }} 行，成功 {{ result.successCount }}，失败 {{ result.failedCount }}
      </n-alert>
      <n-data-table
        v-if="failedItems.length"
        size="small"
        :columns="columns"
        :data="failedItems"
        :max-height="240"
        data-testid="logistics-import-failures"
      />
    </div>
  </n-modal>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { computed, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { downloadLogisticsImportTemplate, importSampleLogistics } from '../../api/sample'

const visible = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ success: [] }>()
const message = useMessage()

const uploading = ref(false)
const result = ref<any>(null)

const failedItems = computed(() =>
  Array.isArray(result.value?.items)
    ? result.value.items.filter((item: any) => !item.success)
    : []
)

const columns = [
  { title: '行号', key: 'rowNo', width: 70 },
  { title: '申请编号', key: 'sampleNo' },
  { title: '失败原因', key: 'message' }
]

const resetState = () => {
  result.value = null
  uploading.value = false
}

const downloadTemplate = async () => {
  try {
    const blob: any = await downloadLogisticsImportTemplate()
    const url = URL.createObjectURL(blob instanceof Blob ? blob : blob?.data)
    const a = document.createElement('a')
    a.href = url
    a.download = 'sample-logistics-import-template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '模板下载失败' })
  }
}

const handleUpload = async ({ file, onFinish, onError }: any) => {
  const raw = file?.file as File
  if (!raw) {
    onError?.()
    return
  }
  const name = raw.name.toLowerCase()
  if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
    message.error('仅支持 Excel 文件（.xlsx / .xls）')
    onError?.()
    return
  }
  if (raw.size > 10 * 1024 * 1024) {
    message.error('文件不能超过 10MB')
    onError?.()
    return
  }
  uploading.value = true
  try {
    const res: any = await importSampleLogistics(raw)
    result.value = res?.data || res
    message.success(`导入完成：成功 ${result.value?.successCount ?? 0}，失败 ${result.value?.failedCount ?? 0}`)
    emit('success')
    onFinish?.()
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '导入失败' })
    onError?.()
  } finally {
    uploading.value = false
  }
}
</script>
