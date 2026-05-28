<template>
  <div class="dept-list app-page" data-testid="system-depts-page">
    <PageHeader title="部门管理" description="维护部门与招商/渠道业务组，管理成员归属与组长。">
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
              <n-statistic label="招商组" :value="stats.recruiterGroupCount ?? 0" />
              <n-statistic label="渠道组" :value="stats.channelGroupCount ?? 0" />
            </n-space>

            <n-space>
              <n-button size="small" @click="openDeptModal('edit', selectedNode)">编辑</n-button>
              <n-button
                v-if="isDepartmentSelected"
                size="small"
                type="primary"
                data-testid="dept-group-add-btn"
                @click="openGroupModal('add')"
              >
                新建业务组
              </n-button>
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
                确认删除该组织节点？
              </n-popconfirm>
            </n-space>

            <n-tabs v-if="isDepartmentSelected" type="line" animated>
              <n-tab-pane name="members" tab="部门成员">
                <n-data-table
                  remote
                  data-testid="dept-members-table"
                  size="small"
                  :columns="memberColumns"
                  :data="memberRows"
                  :loading="membersLoading"
                  :pagination="memberPagination"
                  @update:page="handleMemberPageChange"
                  @update:page-size="handleMemberPageSizeChange"
                />
              </n-tab-pane>
              <n-tab-pane name="groups" tab="业务组">
                <n-data-table
                  size="small"
                  data-testid="dept-groups-table"
                  :columns="groupColumns"
                  :data="groupRows"
                  :loading="groupsLoading"
                />
              </n-tab-pane>
            </n-tabs>

            <div v-else>
              <n-divider>组成员</n-divider>
              <n-space style="margin-bottom: 8px">
                <n-button size="small" type="primary" @click="openAddMembersModal">添加成员</n-button>
              </n-space>
              <n-data-table
                size="small"
                data-testid="group-members-table"
                :columns="groupMemberColumns"
                :data="groupMemberRows"
                :loading="membersLoading"
              />
            </div>
          </n-space>
        </template>
        <n-empty v-else description="请在左侧选择部门或业务组" />
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

    <n-modal v-model:show="showGroupModal" preset="card" :title="groupModalTitle" :style="{ width: MODAL_WIDTH.md }">
      <n-form ref="groupFormRef" :model="groupForm" :rules="groupRules" label-placement="left" label-width="100">
        <n-form-item label="组类型" path="deptType">
          <n-select v-model:value="groupForm.deptType" :options="groupTypeOptions" :disabled="groupModalType === 'edit'" />
        </n-form-item>
        <n-form-item label="组编码" path="deptCode">
          <n-input v-model:value="groupForm.deptCode" placeholder="如 BIZ_A_G1" :disabled="groupModalType === 'edit'" />
        </n-form-item>
        <n-form-item label="组名称" path="deptName">
          <n-input v-model:value="groupForm.deptName" placeholder="如 招商一组" />
        </n-form-item>
        <n-form-item label="组长" path="leaderUserId">
          <n-select
            v-model:value="groupForm.leaderUserId"
            data-testid="group-leader-select"
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
          <n-input-number v-model:value="groupForm.sortOrder" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="状态" path="status">
          <n-switch v-model:value="groupForm.status" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <div class="app-modal-footer">
          <n-button @click="showGroupModal = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="submitGroupForm">确定</n-button>
        </div>
      </n-form>
    </n-modal>

    <n-modal v-model:show="showAddMembersModal" preset="card" title="添加组成员" :style="{ width: MODAL_WIDTH.md }">
      <n-select
        v-model:value="addMemberUserIds"
        multiple
        filterable
        remote
        :options="addMemberOptions"
        :loading="addMemberSearchLoading"
        placeholder="搜索并选择用户"
        @search="searchAddMemberUsers"
      />
      <div class="app-modal-footer">
        <n-button @click="showAddMembersModal = false">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="submitAddMembers">确定</n-button>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { computed, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NPopconfirm,
  NTag,
  useMessage,
  type FormInst,
  type FormRules
} from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import { MODAL_WIDTH } from '../../constants/ui'
import {
  addDeptGroupMembers,
  createDept,
  deleteDept,
  getAssignableUserOptions,
  getDeptGroups,
  getDeptMembers,
  getDeptStats,
  getDeptTree,
  getUserPage,
  removeDeptGroupMembers,
  updateDept
} from '../../api/sys'
import { createPaginationState, MAX_PAGE_SIZE, normalizePageSize } from '../../utils/pagination'

const DEPT_TYPE_DEPARTMENT = 'department'
const GROUP_TYPES = new Set(['recruiter_group', 'channel_group', 'ops_group'])

const message = useMessage()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const treeData = ref<any[]>([])
const flatDepts = ref<any[]>([])
const selectedKeys = ref<string[]>([])
const selectedNode = ref<any | null>(null)

const stats = reactive({
  memberCount: 0,
  recruiterGroupCount: 0,
  channelGroupCount: 0
})

const membersLoading = ref(false)
const groupsLoading = ref(false)
const memberRows = ref<any[]>([])
const groupRows = ref<any[]>([])
const groupMemberRows = ref<any[]>([])
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

const showGroupModal = ref(false)
const groupModalType = ref<'add' | 'edit'>('add')
const editingGroupId = ref<string | null>(null)
const editingGroupParentId = ref<string | null>(null)
const groupFormRef = ref<FormInst | null>(null)
const groupForm = reactive({
  deptType: 'recruiter_group',
  deptCode: '',
  deptName: '',
  leaderUserId: null as string | null,
  sortOrder: 0,
  status: 1
})

const leaderUserOptions = ref<{ label: string; value: string }[]>([])
const leaderSearchLoading = ref(false)
const leaderUserLabel = ref('')

const showAddMembersModal = ref(false)
const addMemberUserIds = ref<string[]>([])
const addMemberOptions = ref<{ label: string; value: string }[]>([])
const addMemberSearchLoading = ref(false)

const deptRules: FormRules = {
  deptCode: { required: true, message: '请输入部门编码', trigger: 'blur' },
  deptName: { required: true, message: '请输入部门名称', trigger: 'blur' }
}
const groupRules: FormRules = {
  deptType: { required: true, message: '请选择组类型', trigger: 'change' },
  deptCode: { required: true, message: '请输入组编码', trigger: 'blur' },
  deptName: { required: true, message: '请输入组名称', trigger: 'blur' }
}

const groupTypeOptions = [
  { label: '招商组', value: 'recruiter_group' },
  { label: '渠道组', value: 'channel_group' },
  { label: '运营组', value: 'ops_group' }
]

const deptModalTitle = computed(() => (deptModalType.value === 'add' ? '新增部门' : '编辑部门'))
const groupModalTitle = computed(() => (groupModalType.value === 'add' ? '新建业务组' : '编辑业务组'))
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
  if (deptType === 'recruiter_group') return '招商组'
  if (deptType === 'channel_group') return '渠道组'
  if (deptType === 'ops_group') return '运营组'
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
  { title: '组别', key: 'groupName', render: (row: any) => row.groupName || '-' },
  { title: '角色', key: 'roleName', render: (row: any) => row.roleName || row.roleCode || '-' },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row: any) =>
      h(NTag, { size: 'small', type: row.status === 1 ? 'success' : 'default' }, () => (row.status === 1 ? '正常' : '停用'))
  }
]

const groupColumns = [
  { title: '组名', key: 'deptName' },
  { title: '编码', key: 'deptCode', width: 120 },
  { title: '类型', key: 'deptType', width: 100, render: (row: any) => deptTypeLabel(row.deptType) },
  { title: '负责人', key: 'leader', width: 120, render: (row: any) => row.leader || '-' },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render: (row: any) =>
      h('div', { class: 'table-actions' }, [
        h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => selectGroup(row) }, () => '查看'),
        h(NButton, { size: 'small', quaternary: true, onClick: () => openGroupModal('edit', row) }, () => '编辑')
      ])
  }
]

const groupMemberColumns = [
  { title: '用户名', key: 'username' },
  { title: '姓名', key: 'realName' },
  { title: '角色', key: 'roleName', render: (row: any) => row.roleName || '-' },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row: any) =>
      h(
        NPopconfirm,
        { onPositiveClick: () => removeGroupMember(row.id) },
        {
          trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '移除'),
          default: () => '确认移出该组？'
        }
      )
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

const selectGroup = async (row: any) => {
  selectedKeys.value = [String(row.id)]
  selectedNode.value = row
  await loadDetail()
}

const loadDetail = async () => {
  if (!selectedNode.value?.id) return
  const id = String(selectedNode.value.id)
  if (isDepartmentSelected.value) {
    await Promise.all([loadStats(id), loadMembers(id), loadGroups(id)])
  } else if (GROUP_TYPES.has(String(selectedNode.value.deptType))) {
    await loadGroupMembers(id)
    leaderUserLabel.value = selectedNode.value.leader || ''
  }
}

const loadStats = async (deptId: string) => {
  try {
    const res: any = await getDeptStats(deptId)
    const data = res?.data || {}
    stats.memberCount = Number(data.memberCount ?? 0)
    stats.recruiterGroupCount = Number(data.recruiterGroupCount ?? 0)
    stats.channelGroupCount = Number(data.channelGroupCount ?? 0)
  } catch {
    stats.memberCount = 0
    stats.recruiterGroupCount = 0
    stats.channelGroupCount = 0
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

const loadGroups = async (deptId: string) => {
  groupsLoading.value = true
  try {
    const res: any = await getDeptGroups(deptId)
    groupRows.value = Array.isArray(res?.data) ? res.data : []
  } catch {
    groupRows.value = []
  } finally {
    groupsLoading.value = false
  }
}

const loadGroupMembers = async (groupId: string) => {
  membersLoading.value = true
  try {
    const res: any = await getUserPage({ page: 1, size: MAX_PAGE_SIZE, groupId })
    const data = res?.data || res
    groupMemberRows.value = Array.isArray(data?.records) ? data.records : []
  } catch {
    groupMemberRows.value = []
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

const resetGroupForm = () => {
  groupForm.deptType = 'recruiter_group'
  groupForm.deptCode = ''
  groupForm.deptName = ''
  groupForm.leaderUserId = null
  groupForm.sortOrder = 0
  groupForm.status = 1
  editingGroupParentId.value = null
  leaderUserOptions.value = []
}

const openGroupModal = (type: 'add' | 'edit', row?: any) => {
  groupModalType.value = type
  editingGroupId.value = type === 'edit' ? String(row?.id || '') : null
  resetGroupForm()
  if (type === 'edit' && row) {
    editingGroupParentId.value = row.parentId ? String(row.parentId) : null
    groupForm.deptType = row.deptType || 'recruiter_group'
    groupForm.deptCode = row.deptCode || ''
    groupForm.deptName = row.deptName || ''
    groupForm.leaderUserId = row.leaderUserId ? String(row.leaderUserId) : null
    groupForm.sortOrder = Number(row.sortOrder ?? 0)
    groupForm.status = Number(row.status ?? 1)
    if (groupForm.leaderUserId) {
      leaderUserOptions.value = [{ label: row.leader || groupForm.leaderUserId, value: groupForm.leaderUserId }]
    }
  }
  showGroupModal.value = true
  searchLeaderUsers('')
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

const submitGroupForm = async () => {
  await groupFormRef.value?.validate()
  if (!selectedNode.value?.id && groupModalType.value === 'add') {
    message.warning('请先选择所属部门')
    return
  }
  submitting.value = true
  const parentId =
    groupModalType.value === 'add'
      ? String(selectedNode.value.id)
      : editingGroupParentId.value || (selectedNode.value?.parentId ? String(selectedNode.value.parentId) : null)
  const payload = {
    parentId,
    deptCode: groupForm.deptCode.trim(),
    deptName: groupForm.deptName.trim(),
    deptType: groupForm.deptType,
    leaderUserId: groupForm.leaderUserId || null,
    leader: resolveSelectedLeaderName(groupForm.leaderUserId),
    sortOrder: groupForm.sortOrder,
    status: groupForm.status
  }
  try {
    if (groupModalType.value === 'add') {
      await createDept(payload)
      message.success('业务组已创建')
    } else if (editingGroupId.value) {
      await updateDept(editingGroupId.value, { ...payload, parentId: selectedNode.value.parentId || parentId })
      message.success('业务组已更新')
    }
    showGroupModal.value = false
    await fetchTree()
    if (isDepartmentSelected.value && selectedNode.value?.id) {
      await loadGroups(String(selectedNode.value.id))
    }
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '保存失败' })
  } finally {
    submitting.value = false
  }
}

const openAddMembersModal = () => {
  addMemberUserIds.value = []
  addMemberOptions.value = []
  showAddMembersModal.value = true
  void searchAddMemberUsers('')
}

const searchAddMemberUsers = async (keyword: string) => {
  addMemberSearchLoading.value = true
  try {
    const res: any = await getAssignableUserOptions({ keyword: keyword || undefined })
    const list = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []
    addMemberOptions.value = list.map((user: any) => ({
      label: `${user.realName || user.username} (${user.username})`,
      value: String(user.id)
    }))
  } finally {
    addMemberSearchLoading.value = false
  }
}

const submitAddMembers = async () => {
  if (!selectedNode.value?.id || !addMemberUserIds.value.length) {
    message.warning('请选择要添加的用户')
    return
  }
  submitting.value = true
  try {
    await addDeptGroupMembers(String(selectedNode.value.id), { userIds: addMemberUserIds.value })
    message.success('成员已添加')
    showAddMembersModal.value = false
    await loadGroupMembers(String(selectedNode.value.id))
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '添加失败' })
  } finally {
    submitting.value = false
  }
}

const removeGroupMember = async (userId: string) => {
  if (!selectedNode.value?.id) return
  try {
    await removeDeptGroupMembers(String(selectedNode.value.id), { userIds: [userId] })
    message.success('已移出该组')
    await loadGroupMembers(String(selectedNode.value.id))
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '移除失败' })
  }
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

.table-actions {
  display: flex;
  gap: 4px;
}

@media (max-width: 960px) {
  .dept-layout {
    grid-template-columns: 1fr;
  }
}
</style>
