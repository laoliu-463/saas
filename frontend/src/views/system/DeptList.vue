<template>
  <div class="dept-list app-page" data-testid="system-depts-page">
    <PageHeader title="部门管理" description="维护部门组织结构与成员归属。">
      <template #actions>
        <n-button type="primary" data-testid="dept-add-btn" @click="openDeptModal('add')">新增部门</n-button>
      </template>
    </PageHeader>

    <div class="dept-layout">
      <n-card :bordered="false" class="app-panel dept-layout__tree" title="组织树">
        <n-spin :show="loading">
          <n-tree
            v-if="treeData.length"
            data-testid="system-depts-tree"
            block-line
            selectable
            :data="treeData"
            :selected-keys="selectedKeys"
            key-field="id"
            label-field="deptName"
            children-field="children"
            @update:selected-keys="handleTreeSelect"
          />
          <n-empty v-else description="暂无部门数据" />
        </n-spin>
      </n-card>

      <n-card :bordered="false" class="app-panel dept-layout__detail" title="部门详情" data-testid="system-depts-detail">
        <template v-if="selectedNode">
          <n-space vertical :size="16">
            <n-descriptions :column="2" label-placement="left" bordered size="small">
              <n-descriptions-item label="名称">{{ selectedNode.deptName }}</n-descriptions-item>
              <n-descriptions-item label="编码">{{ selectedNode.deptCode }}</n-descriptions-item>
              <n-descriptions-item label="类型">{{ deptTypeLabel(selectedNode.deptType) }}</n-descriptions-item>
              <n-descriptions-item label="负责人">{{ selectedNode.leader || leaderUserLabel || '-' }}</n-descriptions-item>
            </n-descriptions>

            <n-space v-if="isDepartmentSelected" :size="12">
              <n-statistic label="成员数" :value="stats.memberCount ?? 0" />
            </n-space>

            <n-space>
              <n-button size="small" @click="openDeptModal('edit', selectedNode)">编辑</n-button>
              <n-button
                v-if="isDepartmentSelected"
                size="small"
                quaternary
                type="primary"
                @click="goUserManagement"
              >
                用户管理
              </n-button>
              <n-popconfirm @positive-click="handleDelete(selectedNode)">
                <template #trigger>
                  <n-button size="small" type="error" quaternary>删除</n-button>
                </template>
                确认删除该部门？
              </n-popconfirm>
            </n-space>

            <n-data-table
              v-if="isDepartmentSelected"
              remote
              data-testid="dept-members-table"
              size="small"
              title="部门成员"
              :columns="memberColumns"
              :data="memberRows"
              :loading="membersLoading"
              :pagination="memberPagination"
              @update:page="handleMemberPageChange"
              @update:page-size="handleMemberPageSizeChange"
            />
          </n-space>
        </template>
        <n-empty v-else description="请在左侧选择部门" />
      </n-card>
    </div>

    <n-modal
      v-model:show="showDeptModal"
      preset="card"
      :title="deptModalTitle"
      :style="{ width: MODAL_WIDTH.md }"
      data-testid="dept-form-modal"
    >
      <n-form ref="deptFormRef" :model="deptForm" :rules="deptRules" label-placement="left" label-width="100">
        <n-form-item label="部门编码" path="deptCode">
          <n-input v-model:value="deptForm.deptCode" data-testid="dept-code-input" placeholder="如 HQ_BIZ" :disabled="deptModalType === 'edit'" />
        </n-form-item>
        <n-form-item label="部门名称" path="deptName">
          <n-input v-model:value="deptForm.deptName" data-testid="dept-name-input" placeholder="如 招商一部" />
        </n-form-item>
        <n-form-item label="上级部门" path="parentId">
          <n-tree-select v-model:value="deptForm.parentId" :options="departmentTreeOptions" clearable placeholder="无上级则留空" />
        </n-form-item>
        <n-form-item label="负责人" path="leaderUserId">
          <n-select
            v-model:value="deptForm.leaderUserId"
            data-testid="dept-leader-select"
            filterable
            clearable
            placeholder="选择组长用户"
            :options="leaderUserOptions"
            :loading="leaderSearchLoading"
            remote
            @search="searchLeaderUsers"
          />
        </n-form-item>
        <n-form-item label="排序" path="sortOrder">
          <n-input-number v-model:value="deptForm.sortOrder" data-testid="dept-sort-input" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="状态" path="status">
          <n-switch v-model:value="deptForm.status" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <n-form-item label="备注" path="remark">
          <n-input v-model:value="deptForm.remark" type="textarea" placeholder="可选" />
        </n-form-item>
        <div class="app-modal-footer">
          <n-button data-testid="dept-cancel-btn" @click="showDeptModal = false">取消</n-button>
          <n-button type="primary" data-testid="dept-submit-btn" :loading="submitting" @click="submitDeptForm">确定</n-button>
        </div>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NPopconfirm, NTag, useMessage, type FormInst, type FormRules } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import { MODAL_WIDTH } from '../../constants/ui'
import {
  createDept,
  deleteDept,
  getAssignableUserOptions,
  getDeptMembers,
  getDeptStats,
  getDeptTree,
  updateDept
} from '../../api/sys'
import { createPaginationState, normalizePageSize } from '../../utils/pagination'
import { ROLE_NAME_MAP } from '../../constants/rbac'

const DEPT_TYPE_DEPARTMENT = 'department'

const message = useMessage()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const treeData = ref<any[]>([])
const flatDepts = ref<any[]>([])
const selectedKeys = ref<string[]>([])
const selectedNode = ref<any | null>(null)

const stats = reactive({
  memberCount: 0
})

const membersLoading = ref(false)
const memberRows = ref<any[]>([])
const memberPagination = reactive(createPaginationState())

const showDeptModal = ref(false)
const deptModalType = ref<'add' | 'edit'>('add')
const editingDeptId = ref<string | null>(null)
const deptFormRef = ref<FormInst | null>(null)
const deptForm = reactive({
  deptCode: '',
  deptName: '',
  parentId: null as string | null,
  leaderUserId: null as string | null,
  sortOrder: 0,
  status: 1,
  remark: ''
})

const leaderUserOptions = ref<{ label: string; value: string }[]>([])
const leaderSearchLoading = ref(false)
const leaderUserLabel = ref('')

const deptRules: FormRules = {
  deptCode: { required: true, message: '请输入部门编码', trigger: 'blur' },
  deptName: { required: true, message: '请输入部门名称', trigger: 'blur' }
}

const deptModalTitle = computed(() => (deptModalType.value === 'add' ? '新增部门' : '编辑部门'))
const isDepartmentSelected = computed(() => {
  const type = String(selectedNode.value?.deptType || DEPT_TYPE_DEPARTMENT)
  return type === DEPT_TYPE_DEPARTMENT || type === 'BUSINESS'
})

const departmentTreeOptions = computed(() =>
  toTreeSelectOptions(treeData.value, (node) => {
    const type = String(node.deptType || DEPT_TYPE_DEPARTMENT)
    return type === DEPT_TYPE_DEPARTMENT || type === 'BUSINESS'
  })
)

function deptTypeLabel(deptType?: string) {
  if (!deptType || deptType === DEPT_TYPE_DEPARTMENT || deptType === 'BUSINESS') return '部门'
  return deptType
}

function flattenTree(nodes: any[], bucket: any[] = []) {
  for (const node of nodes || []) {
    bucket.push(node)
    if (node.children?.length) flattenTree(node.children, bucket)
  }
  return bucket
}

function toTreeSelectOptions(nodes: any[], predicate: (node: any) => boolean): any[] {
  return (nodes || [])
    .map((node) => {
      const children = toTreeSelectOptions(node.children || [], predicate)
      const allowed = predicate(node)
      if (!allowed && !children.length) return null
      return {
        key: String(node.id),
        label: node.deptName,
        value: String(node.id),
        disabled: !allowed,
        children: children.length ? children : undefined
      }
    })
    .filter(Boolean)
}

const memberColumns = [
  { title: '用户名', key: 'username' },
  { title: '姓名', key: 'realName' },
  { title: '角色', key: 'roleName', render: (row: any) => row.roleName || ROLE_NAME_MAP[row.roleCode] || '自定义角色' },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row: any) =>
      h(NTag, { size: 'small', type: row.status === 1 ? 'success' : 'default' }, () => (row.status === 1 ? '正常' : '停用'))
  }
]

const fetchTree = async () => {
  loading.value = true
  try {
    const res: any = await getDeptTree()
    const tree = Array.isArray(res?.data) ? res.data : []
    treeData.value = tree
    flatDepts.value = flattenTree(tree)
    if (!selectedKeys.value.length && flatDepts.value.length) {
      const firstDept = flatDepts.value.find((item) => {
        const type = String(item.deptType || DEPT_TYPE_DEPARTMENT)
        return type === DEPT_TYPE_DEPARTMENT || type === 'BUSINESS'
      })
      if (firstDept) {
        selectedKeys.value = [String(firstDept.id)]
        selectedNode.value = firstDept
        await loadDetail()
      }
    }
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载组织树失败' })
    treeData.value = []
    flatDepts.value = []
  } finally {
    loading.value = false
  }
}

const handleTreeSelect = async (keys: string[]) => {
  if (!keys.length) return
  selectedKeys.value = keys
  selectedNode.value = flatDepts.value.find((item) => String(item.id) === keys[0]) || null
  memberPagination.page = 1
  await loadDetail()
}

const loadDetail = async () => {
  if (!selectedNode.value?.id || !isDepartmentSelected.value) return
  const id = String(selectedNode.value.id)
  await Promise.all([loadStats(id), loadMembers(id)])
}

const loadStats = async (deptId: string) => {
  try {
    const res: any = await getDeptStats(deptId)
    const data = res?.data || {}
    stats.memberCount = Number(data.memberCount ?? 0)
  } catch {
    stats.memberCount = 0
  }
}

const loadMembers = async (deptId: string) => {
  membersLoading.value = true
  try {
    const res: any = await getDeptMembers(deptId, {
      page: memberPagination.page,
      size: normalizePageSize(memberPagination.pageSize)
    })
    const data = res?.data || res
    memberRows.value = Array.isArray(data?.records) ? data.records : []
    memberPagination.itemCount = Number(data?.total ?? memberRows.value.length)
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载成员失败' })
    memberRows.value = []
    memberPagination.itemCount = 0
  } finally {
    membersLoading.value = false
  }
}

const handleMemberPageChange = async (page: number) => {
  memberPagination.page = page
  if (selectedNode.value?.id && isDepartmentSelected.value) {
    await loadMembers(String(selectedNode.value.id))
  }
}

const handleMemberPageSizeChange = async (pageSize: number) => {
  memberPagination.pageSize = normalizePageSize(pageSize)
  memberPagination.page = 1
  if (selectedNode.value?.id && isDepartmentSelected.value) {
    await loadMembers(String(selectedNode.value.id))
  }
}

const resetDeptForm = () => {
  deptForm.deptCode = ''
  deptForm.deptName = ''
  deptForm.parentId = null
  deptForm.leaderUserId = null
  deptForm.sortOrder = 0
  deptForm.status = 1
  deptForm.remark = ''
  leaderUserOptions.value = []
}

const openDeptModal = (type: 'add' | 'edit', row?: any) => {
  deptModalType.value = type
  editingDeptId.value = type === 'edit' ? String(row?.id || '') : null
  resetDeptForm()
  if (type === 'edit' && row) {
    deptForm.deptCode = row.deptCode || ''
    deptForm.deptName = row.deptName || ''
    deptForm.parentId = row.parentId ? String(row.parentId) : null
    deptForm.leaderUserId = row.leaderUserId ? String(row.leaderUserId) : null
    deptForm.sortOrder = Number(row.sortOrder ?? 0)
    deptForm.status = Number(row.status ?? 1)
    deptForm.remark = row.remark || ''
    if (deptForm.leaderUserId) {
      leaderUserOptions.value = [{ label: row.leader || deptForm.leaderUserId, value: deptForm.leaderUserId }]
    }
  } else if (isDepartmentSelected.value && selectedNode.value) {
    deptForm.parentId = String(selectedNode.value.id)
  }
  showDeptModal.value = true
  searchLeaderUsers('')
}

const submitDeptForm = async () => {
  await deptFormRef.value?.validate()
  submitting.value = true
  const payload = {
    deptCode: deptForm.deptCode.trim(),
    deptName: deptForm.deptName.trim(),
    parentId: deptForm.parentId || null,
    deptType: DEPT_TYPE_DEPARTMENT,
    leaderUserId: deptForm.leaderUserId || null,
    leader: resolveSelectedLeaderName(deptForm.leaderUserId),
    sortOrder: deptForm.sortOrder,
    status: deptForm.status,
    remark: deptForm.remark || null
  }
  try {
    if (deptModalType.value === 'add') {
      await createDept(payload)
      message.success('部门已创建')
    } else if (editingDeptId.value) {
      await updateDept(editingDeptId.value, payload)
      message.success('部门已更新')
    }
    showDeptModal.value = false
    await fetchTree()
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '保存失败' })
  } finally {
    submitting.value = false
  }
}

const searchLeaderUsers = async (keyword: string) => {
  leaderSearchLoading.value = true
  try {
    const res: any = await getAssignableUserOptions({ keyword: keyword || undefined })
    const list = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []
    leaderUserOptions.value = list.map((user: any) => ({
      label: `${user.realName || user.username} (${user.username})`,
      value: String(user.id)
    }))
  } finally {
    leaderSearchLoading.value = false
  }
}

const resolveSelectedLeaderName = (userId: string | null) => {
  if (!userId) return null
  const option = leaderUserOptions.value.find((item) => item.value === userId)
  return option?.label.replace(/\s*\([^)]*\)\s*$/, '') || null
}

const handleDelete = async (row: any) => {
  try {
    await deleteDept(String(row.id))
    message.success('已删除')
    selectedKeys.value = []
    selectedNode.value = null
    await fetchTree()
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '删除失败' })
  }
}

const goUserManagement = () => {
  if (!selectedNode.value?.id) return
  router.push({ path: '/system/users', query: { deptId: String(selectedNode.value.id) } })
}

onMounted(() => {
  void fetchTree()
})
</script>

<style scoped>
.dept-layout {
  display: grid;
  grid-template-columns: minmax(240px, 320px) 1fr;
  gap: 12px;
  align-items: start;
}

.dept-layout__tree,
.dept-layout__detail {
  min-height: 520px;
}

@media (max-width: 960px) {
  .dept-layout {
    grid-template-columns: 1fr;
  }
}
</style>
