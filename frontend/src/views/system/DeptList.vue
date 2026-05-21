<template>
  <div class="dept-list app-page" data-testid="system-depts-page">
    <PageHeader title="部门管理" description="维护招商组、渠道组、运营组等业务组织，供用户归属与数据权限使用。">
      <template #actions>
        <n-button type="primary" data-testid="dept-add-btn" @click="openModal('add')">新增部门</n-button>
      </template>
    </PageHeader>

    <n-card :bordered="false" class="app-panel app-table-shell">
      <n-data-table
        :columns="columns"
        :data="data"
        :loading="loading"
        :row-key="(row: any) => row.id"
        default-expand-all
      />
    </n-card>

    <n-modal v-model:show="showModal" preset="card" :title="modalTitle" :style="{ width: MODAL_WIDTH.md }">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="left" label-width="100">
        <n-form-item label="部门编码" path="deptCode">
          <n-input v-model:value="formData.deptCode" placeholder="如 BIZ_A" :disabled="modalType === 'edit'" />
        </n-form-item>
        <n-form-item label="部门名称" path="deptName">
          <n-input v-model:value="formData.deptName" placeholder="如 招商一组" />
        </n-form-item>
        <n-form-item label="上级部门" path="parentId">
          <n-select
            v-model:value="formData.parentId"
            :options="parentOptions"
            clearable
            placeholder="无上级则留空"
          />
        </n-form-item>
        <n-form-item label="负责人" path="leader">
          <n-input v-model:value="formData.leader" placeholder="负责人姓名" />
        </n-form-item>
        <n-form-item label="排序" path="sortOrder">
          <n-input-number v-model:value="formData.sortOrder" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="状态" path="status">
          <n-switch v-model:value="formData.status" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <n-form-item label="备注" path="remark">
          <n-input v-model:value="formData.remark" type="textarea" placeholder="可选" />
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
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, NTag, useMessage, type FormInst, type FormRules } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import { MODAL_WIDTH } from '../../constants/ui'
import { createDept, deleteDept, getDeptTree, updateDept } from '../../api/sys'

const message = useMessage()
const loading = ref(false)
const submitting = ref(false)
const data = ref<any[]>([])
const flatDepts = ref<any[]>([])
const showModal = ref(false)
const modalType = ref<'add' | 'edit'>('add')
const editingId = ref<string | null>(null)
const formRef = ref<FormInst | null>(null)

const formData = reactive({
  deptCode: '',
  deptName: '',
  parentId: null as string | null,
  leader: '',
  sortOrder: 0,
  status: 1,
  remark: ''
})

const rules: FormRules = {
  deptCode: { required: true, message: '请输入部门编码', trigger: 'blur' },
  deptName: { required: true, message: '请输入部门名称', trigger: 'blur' }
}

const modalTitle = computed(() => (modalType.value === 'add' ? '新增部门' : '编辑部门'))

const parentOptions = computed(() =>
  flatDepts.value
    .filter((item) => item.id !== editingId.value)
    .map((item) => ({
      label: item.deptName,
      value: item.id
    }))
)

const columns = [
  { title: '部门名称', key: 'deptName', minWidth: 180 },
  { title: '部门编码', key: 'deptCode', width: 120 },
  { title: '负责人', key: 'leader', width: 120, render: (row: any) => row.leader || '-' },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row: any) =>
      h(NTag, { type: row.status === 1 ? 'success' : 'default', size: 'small', round: true }, () =>
        row.status === 1 ? '启用' : '停用'
      )
  },
  { title: '排序', key: 'sortOrder', width: 80 },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row: any) =>
      h('div', { class: 'table-actions' }, [
        h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openModal('edit', row) }, () => '编辑'),
        h(
          NPopconfirm,
          { onPositiveClick: () => handleDelete(row) },
          {
            trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, () => '删除'),
            default: () => '确认删除该部门？'
          }
        )
      ])
  }
]

function flattenTree(nodes: any[], bucket: any[] = []) {
  for (const node of nodes) {
    bucket.push(node)
    if (Array.isArray(node.children) && node.children.length) {
      flattenTree(node.children, bucket)
    }
  }
  return bucket
}

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await getDeptTree()
    const tree = Array.isArray(res?.data) ? res.data : []
    data.value = tree
    flatDepts.value = flattenTree(tree)
  } catch (error: any) {
    message.error(error?.response?.data?.msg || '加载部门列表失败')
    data.value = []
    flatDepts.value = []
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  formData.deptCode = ''
  formData.deptName = ''
  formData.parentId = null
  formData.leader = ''
  formData.sortOrder = 0
  formData.status = 1
  formData.remark = ''
}

const openModal = (type: 'add' | 'edit', row?: any) => {
  modalType.value = type
  editingId.value = type === 'edit' ? String(row?.id || '') : null
  resetForm()
  if (type === 'edit' && row) {
    formData.deptCode = row.deptCode || ''
    formData.deptName = row.deptName || ''
    formData.parentId = row.parentId || null
    formData.leader = row.leader || ''
    formData.sortOrder = Number(row.sortOrder ?? 0)
    formData.status = Number(row.status ?? 1)
    formData.remark = row.remark || ''
  }
  showModal.value = true
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  const payload = {
    deptCode: formData.deptCode.trim(),
    deptName: formData.deptName.trim(),
    parentId: formData.parentId || null,
    leader: formData.leader || null,
    sortOrder: formData.sortOrder,
    status: formData.status,
    remark: formData.remark || null
  }
  try {
    if (modalType.value === 'add') {
      await createDept(payload)
      message.success('部门已创建')
    } else if (editingId.value) {
      await updateDept(editingId.value, payload)
      message.success('部门已更新')
    }
    showModal.value = false
    await fetchData()
  } catch (error: any) {
    message.error(error?.response?.data?.msg || '保存失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row: any) => {
  try {
    await deleteDept(String(row.id))
    message.success('部门已删除')
    await fetchData()
  } catch (error: any) {
    message.error(error?.response?.data?.msg || '删除失败')
  }
}

onMounted(() => {
  void fetchData()
})
</script>

<style scoped>
.table-actions {
  display: flex;
  gap: 4px;
}
</style>
