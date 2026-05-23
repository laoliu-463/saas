<template>
  <div class="commission-rules-page app-page" data-testid="commission-rules-page">
    <PageHeader title="提成规则" description="V2 差异化提成：按 global / activity / product / user 维度配置招商与渠道比例。" />
    <div class="app-toolbar">
      <n-space wrap :size="10">
        <n-select
          v-model:value="searchParams.dimensionType"
          :options="dimensionOptions"
          placeholder="维度类型"
          style="width: 140px"
          clearable
        />
        <n-select
          v-model:value="searchParams.commissionType"
          :options="commissionTypeOptions"
          placeholder="提成类型"
          style="width: 140px"
          clearable
        />
        <n-button type="primary" size="small" @click="fetchData">查询</n-button>
        <n-button type="primary" size="small" data-testid="commission-rule-create" @click="openModal('add')">
          新增规则
        </n-button>
      </n-space>
    </div>

    <n-card :bordered="false" class="app-panel app-table-shell">
      <n-data-table
        remote
        data-testid="commission-rules-table"
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <n-modal v-model:show="showModal" preset="card" :title="modalTitle" :style="{ width: MODAL_WIDTH.lg }">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="left" label-width="120">
        <n-form-item label="维度类型" path="dimensionType">
          <n-select v-model:value="formData.dimensionType" :options="dimensionOptions" />
        </n-form-item>
        <n-form-item label="维度 ID" path="dimensionId">
          <n-input
            v-model:value="formData.dimensionId"
            :disabled="formData.dimensionType === 'global'"
            placeholder="activity / product / user 时必填"
          />
        </n-form-item>
        <n-form-item label="提成类型" path="commissionType">
          <n-select v-model:value="formData.commissionType" :options="commissionTypeOptions" />
        </n-form-item>
        <n-form-item label="比例" path="ratio">
          <n-input-number v-model:value="formData.ratio" :min="0" :max="1" :step="0.01" style="width: 100%" />
        </n-form-item>
        <n-form-item label="生效开始">
          <n-date-picker v-model:value="formData.effectiveStart" type="datetime" clearable style="width: 100%" />
        </n-form-item>
        <n-form-item label="生效结束">
          <n-date-picker v-model:value="formData.effectiveEnd" type="datetime" clearable style="width: 100%" />
        </n-form-item>
        <n-form-item label="状态">
          <n-switch v-model:value="formData.status" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <div class="app-modal-footer">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="handleSubmit">确定</n-button>
        </div>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import { MODAL_WIDTH } from '../../constants/ui'
import {
  createCommissionRule,
  deleteCommissionRule,
  getCommissionRulePage,
  updateCommissionRule,
  type CommissionRuleItem
} from '../../api/commission'
import { createPaginationState, normalizePageSize } from '../../utils/pagination'

const message = useMessage()
const loading = ref(false)
const data = ref<any[]>([])
const pagination = reactive(createPaginationState())

const searchParams = reactive({
  dimensionType: null as string | null,
  commissionType: null as string | null
})

const dimensionOptions = [
  { label: '全局', value: 'global' },
  { label: '活动', value: 'activity' },
  { label: '商品', value: 'product' },
  { label: '人员', value: 'user' }
]

const commissionTypeOptions = [
  { label: '招商', value: 'recruiter' },
  { label: '渠道', value: 'channel' }
]

const dimensionLabelMap: Record<string, string> = {
  global: '全局',
  activity: '活动',
  product: '商品',
  user: '人员'
}

const commissionTypeLabelMap: Record<string, string> = {
  recruiter: '招商',
  channel: '渠道'
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getCommissionRulePage({
      page: pagination.page,
      size: pagination.pageSize,
      dimensionType: searchParams.dimensionType || undefined,
      commissionType: searchParams.commissionType || undefined
    })
    const responseData = res?.data || res
    data.value = responseData?.records || []
    pagination.itemCount = responseData?.total || 0
  } catch (error: any) {
    message.error(error?.message || '获取提成规则失败')
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
  pagination.pageSize = normalizePageSize(pageSize)
  pagination.page = 1
  fetchData()
}

const showModal = ref(false)
const modalType = ref<'add' | 'edit'>('add')
const modalTitle = ref('新增规则')
const formRef = ref()
const submitting = ref(false)

const formData = reactive({
  id: '',
  dimensionType: 'activity',
  dimensionId: '',
  commissionType: 'recruiter',
  ratio: 0.15,
  effectiveStart: null as number | null,
  effectiveEnd: null as number | null,
  status: 1
})

const rules = {
  dimensionType: { required: true, message: '请选择维度类型', trigger: 'change' },
  commissionType: { required: true, message: '请选择提成类型', trigger: 'change' },
  ratio: { required: true, type: 'number', message: '请输入比例', trigger: 'blur' }
}

function formatDateTimeValue(value?: string | null) {
  return value ? new Date(value).getTime() : null
}

function toIsoString(value: number | null) {
  return value ? new Date(value).toISOString().slice(0, 19).replace('T', ' ') : null
}

const openModal = (type: 'add' | 'edit', row?: any) => {
  modalType.value = type
  modalTitle.value = type === 'add' ? '新增规则' : '编辑规则'
  if (type === 'add') {
    Object.assign(formData, {
      id: '',
      dimensionType: 'activity',
      dimensionId: '',
      commissionType: 'recruiter',
      ratio: 0.15,
      effectiveStart: null,
      effectiveEnd: null,
      status: 1
    })
  } else if (row) {
    Object.assign(formData, {
      id: row.id,
      dimensionType: row.dimensionType,
      dimensionId: row.dimensionId || '',
      commissionType: row.commissionType,
      ratio: Number(row.ratio),
      effectiveStart: formatDateTimeValue(row.effectiveStart),
      effectiveEnd: formatDateTimeValue(row.effectiveEnd),
      status: row.status ?? 1
    })
  }
  showModal.value = true
}

const handleSubmit = async () => {
  if (!formRef.value || submitting.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload: CommissionRuleItem = {
      dimensionType: formData.dimensionType,
      dimensionId: formData.dimensionType === 'global' ? null : formData.dimensionId.trim(),
      commissionType: formData.commissionType,
      ratio: formData.ratio,
      effectiveStart: toIsoString(formData.effectiveStart),
      effectiveEnd: toIsoString(formData.effectiveEnd),
      status: formData.status
    }
    if (modalType.value === 'add') {
      await createCommissionRule(payload)
      pagination.page = 1
    } else {
      await updateCommissionRule(formData.id, payload)
    }
    message.success('保存成功')
    showModal.value = false
    await fetchData()
  } catch (error: any) {
    message.error(error?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id: string) => {
  try {
    await deleteCommissionRule(id)
    message.success('删除成功')
    await fetchData()
  } catch (error: any) {
    message.error(error?.message || '删除失败')
  }
}

const columns = [
  {
    title: '维度',
    key: 'dimensionType',
    width: 90,
    render(row: any) {
      return dimensionLabelMap[row.dimensionType] || row.dimensionType
    }
  },
  { title: '维度 ID', key: 'dimensionId', ellipsis: { tooltip: true } },
  {
    title: '提成类型',
    key: 'commissionType',
    width: 90,
    render(row: any) {
      return commissionTypeLabelMap[row.commissionType] || row.commissionType
    }
  },
  {
    title: '比例',
    key: 'ratio',
    width: 80,
    render(row: any) {
      return `${(Number(row.ratio) * 100).toFixed(1)}%`
    }
  },
  { title: '生效开始', key: 'effectiveStart', width: 160 },
  { title: '生效结束', key: 'effectiveEnd', width: 160 },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render(row: any) {
      return h(NTag, { type: row.status ? 'success' : 'default', size: 'small' }, () => (row.status ? '启用' : '停用'))
    }
  },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render(row: any) {
      return h('div', { style: 'display: flex; gap: 8px' }, [
        h(NButton, { size: 'small', onClick: () => openModal('edit', row) }, () => '编辑'),
        h(
          NPopconfirm,
          { onPositiveClick: () => handleDelete(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, () => '删除'),
            default: () => '确认删除该提成规则吗？'
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
