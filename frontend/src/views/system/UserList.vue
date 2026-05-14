<template>
  <div class="user-list">
    <!-- Toolbar -->
    <div class="user-toolbar">
      <n-space wrap :size="10">
        <n-input v-model:value="searchParams.username" placeholder="请输入用户名" clearable style="width: 180px" />
        <n-select v-model:value="searchParams.status" :options="statusOptions" placeholder="全部状态" style="width: 110px;" clearable />
        <n-button type="primary" size="small" @click="fetchData">查询</n-button>
        <n-button type="primary" size="small" @click="openModal('add')">新增用户</n-button>
      </n-space>
    </div>

    <!-- Table -->
    <div class="user-table-card">
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
    <n-modal v-model:show="showModal" preset="card" :title="modalTitle" style="width: 600px;">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="left" label-width="100">
        <n-form-item label="用户名" path="username">
          <n-input v-model:value="formData.username" placeholder="请输入" :disabled="modalType === 'edit'" />
        </n-form-item>
        <n-form-item v-if="modalType === 'add'" label="密码" path="password">
          <n-input v-model:value="formData.password" type="password" placeholder="请输入密码" show-password-on="click" />
        </n-form-item>
        <n-form-item label="真实姓名" path="realName">
          <n-input v-model:value="formData.realName" placeholder="请输入" />
        </n-form-item>
        <n-form-item label="手机" path="phone">
          <n-input v-model:value="formData.phone" placeholder="请输入手机号" />
        </n-form-item>
        <n-form-item label="邮箱" path="email">
          <n-input v-model:value="formData.email" placeholder="请输入邮箱" />
        </n-form-item>
        <n-form-item label="角色分配" path="roleIds">
          <n-select
            v-model:value="selectedRoleId"
            :options="visibleRoleSelectOptions"
            value-field="value"
            label-field="label"
            placeholder="请选择身份"
            filterable
            clearable
          />
          <div class="role-picker__footer">
            <n-tag v-if="selectedRoleLabel" type="success" size="small" round>
              已选身份：{{ selectedRoleLabel }}
            </n-tag>
          </div>
        </n-form-item>
        <n-form-item label="状态" path="status">
          <n-switch v-model:value="formData.status" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <div style="display: flex; justify-content: flex-end;">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" style="margin-left: 12px;" @click="handleSubmit" :loading="submitting">确定</n-button>
        </div>
      </n-form>
    </n-modal>

    <!-- Modal for Reset Password -->
    <n-modal v-model:show="showResetModal" preset="card" title="重置密码" style="width: 400px;">
      <n-form ref="resetFormRef" :model="resetData" :rules="resetRules" label-placement="left" label-width="100">
        <n-form-item label="新密码" path="password">
          <n-input v-model:value="resetData.password" type="password" placeholder="请输入新密码" show-password-on="click" />
        </n-form-item>
        <div style="display: flex; justify-content: flex-end;">
          <n-button @click="showResetModal = false">取消</n-button>
          <n-button type="primary" style="margin-left: 12px;" @click="handleResetSubmit" :loading="submitting">确定</n-button>
        </div>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted, computed, watch } from 'vue';
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui';
import { getUserPage, createUser, updateUser, deleteUser, resetUserPassword, getRoleAll, assignUserRoles } from '../../api/sys';

const message = useMessage();

// Table state
const loading = ref(false);
const data = ref([]);
const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
});

// Search params
const searchParams = reactive({
  username: '',
  status: null
});
const statusOptions = [
  { label: '正常', value: 1 },
  { label: '停用', value: 0 }
];

const roleOptions = ref([]);
const roleNameMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {};
  for (const role of roleOptions.value as any[]) {
    if (role?.id) {
      map[String(role.id)] = role.roleName || role.roleCode || String(role.id);
    }
  }
  return map;
});
const roleDescriptionMap: Record<string, string> = {
  admin: '可访问全部菜单与系统管理能力',
  biz_leader: '落地首页 /dashboard，可看归因与订单工作台',
  colonel_leader: '兼容旧招商组长角色，前台按 biz_leader 口径展示与落地',
  biz_staff: '落地首页 /data，可看数据平台与商品寄样',
  channel_leader: '落地首页 /dashboard，可看归因、达人与寄样',
  channel_staff: '落地首页 /product，可做选品、达人跟进、寄样申请与个人业绩查看',
  ops_staff: '落地首页 /ops/shipping，仅负责寄样发货、物流录入与签收跟进'
};
const roleSelectOptions = computed(() =>
  (roleOptions.value as any[]).map((role) => ({
    value: String(role.id),
    label: String(role.roleName || role.roleCode || ''),
    roleCode: String(role.roleCode || ''),
    description: roleDescriptionMap[String(role.roleCode || '')] || '用于控制菜单与首页落地路径'
  }))
);
const visibleRoleSelectOptions = computed(() =>
  roleSelectOptions.value.filter((role) => role.roleCode !== 'admin' || selectedRoleId.value === role.value)
);
const selectedRoleId = ref<string | null>(null);
const selectedRole = computed(() =>
  roleSelectOptions.value.find((role) => role.value === selectedRoleId.value) || null
);
const selectedRoleLabel = computed(() => selectedRole.value?.label || '');
watch(selectedRoleId, (value) => {
  formData.roleIds = value ? [value] : [];
});

const removeRowLocally = (id: string) => {
  const nextRows = (data.value as any[]).filter((row) => row?.id !== id);
  if (nextRows.length !== (data.value as any[]).length) {
    data.value = nextRows as never[];
    pagination.itemCount = Math.max(0, pagination.itemCount - 1);
    if (!nextRows.length && pagination.page > 1) {
      pagination.page -= 1;
    }
  }
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getUserPage({
      page: pagination.page,
      size: pagination.pageSize,
      keyword: searchParams.username || undefined,
      status: searchParams.status
    });
    // 处理多种响应格式
    const responseData = res?.data || res;
    if (responseData?.records && Array.isArray(responseData.records)) {
      data.value = responseData.records;
      pagination.itemCount = responseData.total || 0;
    } else {
      data.value = [];
      pagination.itemCount = 0;
      message.warning('未获取到用户数据');
    }
  } catch (error: any) {
    message.error(error?.message || '获取用户列表失败');
    data.value = [];
    pagination.itemCount = 0;
  } finally {
    loading.value = false;
  }
};

const handlePageChange = (page: number) => {
  pagination.page = page;
  fetchData();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.page = 1;
  fetchData();
};

// Modal State
const showModal = ref(false);
const modalType = ref<'add'|'edit'>('add');
const modalTitle = ref('新增用户');
const formRef = ref();
const submitting = ref(false);

const formData = reactive({
  id: undefined as string | undefined,
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  roleIds: [] as string[],
  status: 1
});

const isValidationFailure = (error: unknown): boolean => Array.isArray(error);

const normalizeRoleIds = (roleIds: unknown): string[] => {
  if (!Array.isArray(roleIds)) return [];
  return roleIds
    .map((id) => (id == null ? '' : String(id)))
    .filter((id) => !!id);
};
const rules = {
  username: { required: true, message: '请输入用户名', trigger: 'blur' },
  password: { required: true, message: '请输入密码', trigger: 'blur' },
  realName: { required: true, message: '请输入真实姓名', trigger: 'blur' },
  roleIds: { type: 'array', required: true, message: '请选择角色', trigger: 'change' }
};

const openModal = async (type: 'add'|'edit', row?: any) => {
  modalType.value = type;
  modalTitle.value = type === 'add' ? '新增用户' : '编辑用户';
  
  if (type === 'add') {
    Object.assign(formData, { id: undefined, username: '', password: '', realName: '', phone: '', email: '', roleIds: [], status: 1 });
    selectedRoleId.value = null;
  } else if (row) {
    const normalizedRoleIds = normalizeRoleIds(
      Array.isArray(row.roleIds)
        ? row.roleIds
        : (Array.isArray(row.roles) ? row.roles.map((r: any) => r?.id) : [])
    );
    Object.assign(formData, {
      id: row.id,
      username: row.username,
      realName: row.realName,
      phone: row.phone,
      email: row.email,
      roleIds: normalizedRoleIds.slice(0, 1),
      status: row.status
    });
    selectedRoleId.value = normalizedRoleIds[0] || null;
  }
  
  showModal.value = true;
};

const handleSubmit = async () => {
  if (!formRef.value || submitting.value) return;
  try {
    await formRef.value.validate();
  } catch (error) {
    if (!isValidationFailure(error)) {
      message.error((error as any)?.message || '表单校验失败');
    }
    return;
  }

  submitting.value = true;
  try {
    const targetUsername = formData.username;
    const roleLabel = selectedRoleLabel.value;
    if (modalType.value === 'add') {
      await createUser(formData);
      pagination.page = 1;
      searchParams.username = targetUsername;
      searchParams.status = null;
    } else {
      await updateUser(formData.id!, formData);
      await assignUserRoles(formData.id!, { roleIds: formData.roleIds });
      searchParams.username = targetUsername;
    }
    await fetchData();
    showModal.value = false;
    if (modalType.value === 'add') {
      message.success(`用户 ${targetUsername} 已创建${roleLabel ? `，身份：${roleLabel}` : ''}`);
    } else {
      message.success(`用户 ${targetUsername} 已更新${roleLabel ? `，身份：${roleLabel}` : ''}`);
    }
  } catch (error: any) {
    if (!error?.response?.data?.msg && !error?.msg) {
      message.error(error?.message || '保存失败');
    }
  } finally {
    submitting.value = false;
  }
};

const handleDelete = async (id: string) => {
  try {
    await deleteUser(id);
    removeRowLocally(id);
    message.success('删除成功');
    await fetchData();
  } catch (error: any) {
    message.error(error?.message || '删除失败');
  }
};

// Reset Password
const showResetModal = ref(false);
const resetFormRef = ref();
const resetData = reactive({ id: undefined as string | undefined, password: '' });
const resetRules = {
  password: { required: true, message: '请输入新密码', trigger: 'blur' }
};

const openResetModal = (row: any) => {
  resetData.id = row.id;
  resetData.password = '';
  showResetModal.value = true;
};

const handleResetSubmit = async () => {
  if (!resetFormRef.value || !resetData.id || submitting.value) return;
  try {
    await resetFormRef.value.validate();
  } catch (error) {
    if (!isValidationFailure(error)) {
      message.error((error as any)?.message || '表单校验失败');
    }
    return;
  }

  submitting.value = true;
  try {
    await resetUserPassword(resetData.id, { newPassword: resetData.password });
    message.success('密码重置成功');
    showResetModal.value = false;
  } catch (error: any) {
    if (!error?.response?.data?.msg && !error?.msg) {
      message.error(error?.message || '重置失败');
    }
  } finally {
    submitting.value = false;
  }
};

const columns = [
  { title: '状态', key: 'status', render(row: any) { return h(NTag, { type: row.status ? 'success' : 'error' }, { default: () => (row.status ? '正常' : '停用') }); } },
  { title: '用户名', key: 'username' },
  { title: '真实姓名', key: 'realName' },
  {
    title: '角色',
    key: 'roleIds',
    render(row: any) {
      const roleIds = normalizeRoleIds(row.roleIds);
      if (!roleIds.length) {
        return '-';
      }
      return h(
        'div',
        { style: 'display: flex; flex-wrap: wrap; gap: 6px;' },
        roleIds.map((id: string) =>
          h(
            NTag,
            { size: 'small', type: 'info' },
            { default: () => roleNameMap.value[id] || id }
          )
        )
      );
    }
  },
  { title: '手机', key: 'phone' },
  { title: '邮箱', key: 'email' },
  { title: '创建时间', key: 'createTime' },
  {
    title: '操作',
    key: 'actions',
    render(row: any) {
      return h(
        'div',
        { style: 'display: flex; gap: 8px;' },
        [
          h(
            NButton,
            { size: 'small', onClick: () => openModal('edit', row) },
            { default: () => '编辑' }
          ),
          h(
            NButton,
            { size: 'small', onClick: () => openResetModal(row) },
            { default: () => '重置密码' }
          ),
          h(
            NPopconfirm,
            { onPositiveClick: () => handleDelete(row.id) },
            {
              trigger: () =>
                h(
                  NButton,
                  { size: 'small', type: 'error' },
                  { default: () => '删除' }
                ),
              default: () => '确认删除该用户吗？'
            }
          )
        ]
      );
    }
  }
];

onMounted(async () => {
  try {
    const roleRes = await getRoleAll();
    const roleData = roleRes.data || roleRes;
    roleOptions.value = Array.isArray(roleData) ? roleData : (roleData.records || []);
  } catch (error: any) {
    message.error(error?.message || '加载角色列表失败');
  }
  fetchData();
});
</script>

<style scoped>
.user-list {
  max-width: 100%;
}

.user-toolbar {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  margin-bottom: var(--spacing-md);
  box-shadow: var(--shadow-sm);
}

.user-table-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 4px;
  box-shadow: var(--shadow-card);
}

.role-picker {
  width: 100%;
}

.role-picker__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}
</style>
