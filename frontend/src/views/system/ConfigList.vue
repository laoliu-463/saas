<template>
  <div class="config-list">
    <!-- Toolbar -->
    <div class="config-toolbar">
      <n-space wrap :size="10">
        <n-select
          v-model:value="searchParams.configGroup"
          :options="groupOptions"
          placeholder="全部分组"
          style="width: 140px"
          clearable
        />
        <n-input
          v-model:value="searchParams.keyword"
          placeholder="搜索配置键或名称"
          clearable
          style="width: 200px"
        />
        <n-button type="primary" size="small" @click="fetchData">查询</n-button>
        <n-button type="primary" size="small" @click="openModal('add')">新增配置</n-button>
      </n-space>
    </div>

    <!-- Table -->
    <div class="config-table-card">
      <n-data-table
        remote
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <!-- Modal for Add/Edit -->
    <n-modal v-model:show="showModal" preset="card" :title="modalTitle" style="width: 640px">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="left" label-width="120">
        <n-form-item label="配置键" path="configKey">
          <n-input v-model:value="formData.configKey" placeholder="例：talent.protection_days" :disabled="modalType === 'edit'" />
        </n-form-item>
        <n-form-item label="配置名称" path="configName">
          <n-input v-model:value="formData.configName" placeholder="例：达人保护期天数" />
        </n-form-item>
        <n-form-item label="配置值" path="configValue">
          <n-input
            v-model:value="formData.configValue"
            type="textarea"
            :rows="3"
            placeholder="配置值"
          />
        </n-form-item>
        <n-form-item label="分组" path="configGroup">
          <n-select
            v-model:value="formData.configGroup"
            :options="groupOptions"
            placeholder="请选择分组"
            filterable
            tag
          />
        </n-form-item>
        <n-form-item label="类型" path="configType">
          <n-select
            v-model:value="formData.configType"
            :options="typeOptions"
            placeholder="请选择类型"
          />
        </n-form-item>
        <n-form-item label="排序" path="sortOrder">
          <n-input-number v-model:value="formData.sortOrder" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="状态" path="status">
          <n-switch v-model:value="formData.status" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <n-form-item label="备注" path="remark">
          <n-input v-model:value="formData.remark" placeholder="备注信息（可选）" />
        </n-form-item>
        <div style="display: flex; justify-content: flex-end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" style="margin-left: 12px" @click="handleSubmit" :loading="submitting"
            >确定</n-button
          >
        </div>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue'
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui'
import { getConfigPage, createConfig, updateConfig, deleteConfig } from '../../api/sys'

const message = useMessage()

const loading = ref(false)
const data = ref([])
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
})

const searchParams = reactive({
  configGroup: null as string | null,
  keyword: ''
})

const groupOptions = [
  { label: '达人', value: 'talent' },
  { label: '商家', value: 'merchant' },
  { label: '寄样', value: 'sample' },
  { label: '佣金', value: 'commission' },
  { label: '抖音', value: 'douyin' }
]

const typeOptions = [
  { label: '整数', value: 'int' },
  { label: '数值', value: 'numeric' },
  { label: '布尔', value: 'boolean' },
  { label: 'JSON', value: 'json' },
  { label: '文本', value: 'text' }
]

const groupLabelMap: Record<string, string> = {
  talent: '达人',
  merchant: '商家',
  sample: '寄样',
  commission: '佣金',
  douyin: '抖音'
}

const typeLabelMap: Record<string, string> = {
  int: '整数',
  numeric: '数值',
  boolean: '布尔',
  json: 'JSON',
  text: '文本'
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getConfigPage({
      page: pagination.page,
      size: pagination.pageSize,
      configGroup: searchParams.configGroup || undefined,
      keyword: searchParams.keyword || undefined
    })
    const responseData = res?.data || res
    if (responseData?.records && Array.isArray(responseData.records)) {
      data.value = responseData.records
      pagination.itemCount = responseData.total || 0
    } else {
      data.value = []
      pagination.itemCount = 0
    }
  } catch (error: any) {
    message.error(error?.message || '获取配置列表失败')
    data.value = []
    pagination.itemCount = 0
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  pagination.page = page
  fetchData()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize
  pagination.page = 1
  fetchData()
}

// Modal
const showModal = ref(false)
const modalType = ref<'add' | 'edit'>('add')
const modalTitle = ref('新增配置')
const formRef = ref()
const submitting = ref(false)

const formData = reactive({
  id: undefined as string | undefined,
  configKey: '',
  configName: '',
  configValue: '',
  configGroup: null as string | null,
  configType: 'text',
  sortOrder: 0,
  status: 1,
  remark: ''
})

const configKeyPattern = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$/

const validateConfigValue = (_rule: any, value: string) => {
  if (!value) return new Error('请输入配置值')
  const type = formData.configType
  if (type === 'int' && !/^-?\d+$/.test(value.trim())) {
    return new Error('配置值必须为整数')
  }
  if (type === 'numeric' && isNaN(Number(value.trim()))) {
    return new Error('配置值必须为数值')
  }
  if (type === 'boolean' && !['true', 'false', '0', '1'].includes(value.trim().toLowerCase())) {
    return new Error('配置值必须为布尔值（true/false/0/1）')
  }
  if (type === 'json') {
    try { JSON.parse(value.trim()) } catch { return new Error('配置值不是合法的 JSON') }
  }
  return true
}

const rules = {
  configKey: [
    { required: true, message: '请输入配置键', trigger: 'blur' },
    { pattern: configKeyPattern, message: '配置键格式：小写字母开头，允许小写字母/数字/下划线，用点分隔（如 talent.protection_days）', trigger: 'blur' }
  ],
  configName: { required: true, message: '请输入配置名称', trigger: 'blur' },
  configValue: { required: true, validator: validateConfigValue, trigger: 'blur' },
  configGroup: { required: true, message: '请选择分组', trigger: 'change' },
  configType: { required: true, message: '请选择类型', trigger: 'change' }
}

const openModal = (type: 'add' | 'edit', row?: any) => {
  modalType.value = type
  modalTitle.value = type === 'add' ? '新增配置' : '编辑配置'
  if (type === 'add') {
    Object.assign(formData, {
      id: undefined,
      configKey: '',
      configName: '',
      configValue: '',
      configGroup: null,
      configType: 'text',
      sortOrder: 0,
      status: 1,
      remark: ''
    })
  } else if (row) {
    Object.assign(formData, {
      id: row.id,
      configKey: row.configKey,
      configName: row.configName,
      configValue: row.configValue,
      configGroup: row.configGroup,
      configType: row.configType || 'text',
      sortOrder: row.sortOrder ?? 0,
      status: row.status ?? 1,
      remark: row.remark || ''
    })
  }
  showModal.value = true
}

const handleSubmit = async () => {
  if (!formRef.value || submitting.value) return
  try {
    await formRef.value.validate()
  } catch (error) {
    if (Array.isArray(error)) return
    message.error((error as any)?.message || '表单校验失败')
    return
  }

  submitting.value = true
  try {
    const payload = {
      ...formData,
      configKey: formData.configKey.trim(),
      configName: formData.configName.trim(),
      configValue: formData.configValue.trim(),
      remark: formData.remark?.trim() || ''
    }
    if (modalType.value === 'add') {
      await createConfig(payload)
      pagination.page = 1
    } else {
      await updateConfig(formData.id!, payload)
    }
    await fetchData()
    showModal.value = false
    message.success(modalType.value === 'add' ? '配置已创建' : '配置已更新')
  } catch (error: any) {
    if (!error?.response?.data?.msg && !error?.msg) {
      message.error(error?.message || '保存失败')
    }
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id: string) => {
  try {
    await deleteConfig(id)
    message.success('删除成功')
    await fetchData()
  } catch (error: any) {
    message.error(error?.message || '删除失败')
  }
}

const columns = [
  {
    title: '状态',
    key: 'status',
    width: 80,
    render(row: any) {
      return h(NTag, { type: row.status ? 'success' : 'error' }, { default: () => (row.status ? '启用' : '停用') })
    }
  },
  { title: '配置键', key: 'configKey', ellipsis: { tooltip: true } },
  { title: '配置名称', key: 'configName', ellipsis: { tooltip: true } },
  {
    title: '配置值',
    key: 'configValue',
    ellipsis: { tooltip: true },
    width: 200
  },
  {
    title: '分组',
    key: 'configGroup',
    width: 80,
    render(row: any) {
      return h(
        NTag,
        { size: 'small', type: 'info' },
        { default: () => groupLabelMap[row.configGroup] || row.configGroup || '-' }
      )
    }
  },
  {
    title: '类型',
    key: 'configType',
    width: 60,
    render(row: any) {
      return typeLabelMap[row.configType] || row.configType || '-'
    }
  },
  { title: '排序', key: 'sortOrder', width: 60 },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render(row: any) {
      return h('div', { style: 'display: flex; gap: 8px' }, [
        h(NButton, { size: 'small', onClick: () => openModal('edit', row) }, { default: () => '编辑' }),
        h(
          NPopconfirm,
          { onPositiveClick: () => handleDelete(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
            default: () => '确认删除该配置项吗？'
          }
        )
      ])
    }
  }
]

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.config-list {
  max-width: 100%;
}

.config-toolbar {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  margin-bottom: var(--spacing-md);
  box-shadow: var(--shadow-sm);
}

.config-table-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 4px;
  box-shadow: var(--shadow-card);
}
</style>
