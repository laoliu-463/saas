<template>
  <div class="role-list app-page" data-testid="system-roles-page">
    <PageHeader title="角色管理" description="配置角色名称、数据范围与启用状态。">
      <template #actions>
        <n-button type="primary" @click="openModal('add')">新增角色</n-button>
      </template>
    </PageHeader>

    <div class="app-toolbar">
      <n-space wrap :size="10">
        <n-input
          v-model:value="searchParams.keyword"
          placeholder="角色名称/编码"
          clearable
          data-testid="role-filter-keyword"
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <n-select
          v-model:value="searchParams.status"
          :options="statusOptions"
          placeholder="状态"
          clearable
          data-testid="role-filter-status"
          style="width: 130px"
        />
        <n-button type="primary" size="small" data-testid="role-filter-search" @click="handleSearch">查询</n-button>
        <n-button size="small" data-testid="role-filter-reset" @click="handleReset">重置</n-button>
      </n-space>
    </div>

    <n-card :bordered="false" class="app-panel app-table-shell">
      <n-data-table
        remote
        data-testid="system-roles-table"
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <n-modal v-model:show="showModal" preset="card" :title="modalTitle" :style="{ width: MODAL_WIDTH.md }">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="left" label-width="100">
        <n-form-item label="角色名称" path="roleName">
          <n-input v-model:value="formData.roleName" placeholder="如 管理员" />
        </n-form-item>
        <n-form-item label="数据范围" path="dataScope">
          <n-select v-model:value="formData.dataScope" :options="dataScopeOptions" />
        </n-form-item>
        <n-form-item label="功能权限" path="permissionCodes">
          <n-select
            v-model:value="formData.permissionCodes"
            multiple
            filterable
            clearable
            :options="permissionOptions"
            placeholder="请选择该角色可访问的功能"
          />
        </n-form-item>
        <n-form-item label="状态" path="status">
          <n-switch v-model:value="formData.status" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <n-form-item label="备注" path="remark">
          <n-input v-model:value="formData.remark" type="textarea" placeholder="请输入备注" />
        </n-form-item>
        <div class="app-modal-footer">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" @click="handleSubmit" :loading="submitting">确定</n-button>
        </div>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { ref, reactive, h, onMounted } from 'vue';
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui';
import PageHeader from '../../components/PageHeader.vue';
import { MODAL_WIDTH } from '../../constants/ui';
import {
  getRolePage,
  createRole,
  updateRole,
  deleteRole,
  getPermissionCatalog,
  getRolePermissions,
  assignRolePermissions
} from '../../api/sys';
import { createPaginationState, normalizePageSize } from '../../utils/pagination';
import { sanitizeRoleName } from './user-list-options';
import { ROLE_CODES } from '../../constants/rbac';

const SYSTEM_ROLE_CODES = new Set<string>(Object.values(ROLE_CODES));

const message = useMessage();

const loading = ref(false);
const data = ref([]);
const pagination = reactive(createPaginationState());

// t7-system: 角色三联筛选 - 关键词 + 状态 (后端 SysRoleService.findPage 已支持 keyword/status)
const searchParams = reactive({
  keyword: '',
  status: null as number | null
});
const statusOptions = [
  { label: '正常', value: 1 },
  { label: '停用', value: 0 }
];

const dataScopeOptions = [
  { label: '全部数据', value: 3 },
  { label: '本组数据', value: 2 },
  { label: '仅本人数据', value: 1 }
];

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
    const res = await getRolePage({
      page: pagination.page,
      size: pagination.pageSize,
      keyword: searchParams.keyword || undefined,
      status: searchParams.status ?? undefined
    });
    // 处理多种响应格式
    const responseData = res?.data || res;
    if (responseData?.records && Array.isArray(responseData.records)) {
      data.value = responseData.records;
      pagination.itemCount = responseData.total || 0;
    } else {
      data.value = [];
      pagination.itemCount = 0;
      message.warning('未获取到角色数据');
    }
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '获取角色列表失败' });
    data.value = [];
    pagination.itemCount = 0;
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pagination.page = 1;
  fetchData();
};

const handleReset = () => {
  searchParams.keyword = '';
  searchParams.status = null;
  pagination.page = 1;
  fetchData();
};

const handlePageChange = (page: number) => {
  pagination.page = page;
  fetchData();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = normalizePageSize(pageSize);
  pagination.page = 1;
  fetchData();
};

const showModal = ref(false);
const modalType = ref<'add'|'edit'>('add');
const modalTitle = ref('新增角色');
const formRef = ref();
const submitting = ref(false);
const permissionOptions = ref<Array<{ label: string; value: string }>>([]);

const formData = reactive({
  id: undefined as string | undefined,
  roleCode: '',
  roleName: '',
  dataScope: 1,
  status: 1,
  remark: '',
  permissionCodes: [] as string[]
});

const isValidationFailure = (error: unknown): boolean => Array.isArray(error);

const rules = {
  roleName: { required: true, message: '请输入角色名称', trigger: 'blur' },
  dataScope: { type: 'number', required: true, message: '请选择数据范围', trigger: 'change' }
};

const loadPermissionCatalog = async () => {
  if (permissionOptions.value.length) return;
  const response = await getPermissionCatalog();
  const items = response?.data || response || [];
  permissionOptions.value = (Array.isArray(items) ? items : []).map((item: any) => ({
    label: `${item.permissionCode}（${item.resourceCode}/${item.actionCode}）`,
    value: item.permissionCode
  }));
};

const openModal = async (type: 'add'|'edit', row?: any) => {
  modalType.value = type;
  modalTitle.value = type === 'add' ? '新增角色' : '编辑角色';

  try {
    await loadPermissionCatalog();
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '获取权限目录失败' });
    return;
  }

  if (type === 'add') {
    Object.assign(formData, {
      id: undefined,
      roleCode: '',
      roleName: '',
      dataScope: 1,
      status: 1,
      remark: '',
      permissionCodes: []
    }); // roleCode 由后端自动生成
  } else if (row) {
    const permissionResponse = await getRolePermissions(row.id);
    const permissionCodes = permissionResponse?.data || permissionResponse || [];
    Object.assign(formData, {
      id: row.id,
      roleCode: row.roleCode,
      roleName: sanitizeRoleName(row),
      dataScope: row.dataScope,
      status: row.status,
      remark: row.remark,
      permissionCodes: Array.isArray(permissionCodes) ? permissionCodes : []
    });
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
    if (modalType.value === 'add') {
      const { roleCode: _ignored, permissionCodes, ...payload } = formData;
      const created = await createRole(payload);
      const createdRole = created?.data || created;
      if (!createdRole?.id) throw new Error('创建角色后未返回角色 ID');
      await assignRolePermissions(createdRole.id, permissionCodes);
      message.success('新增成功');
    } else {
      const { permissionCodes, ...payload } = formData;
      await updateRole(formData.id!, payload);
      await assignRolePermissions(formData.id!, permissionCodes);
      message.success('编辑成功');
    }
    showModal.value = false;
    await fetchData();
  } catch (error: any) {
    if (!error?.response?.data?.msg && !error?.msg) {
      notifyApiFailure(error, message, { fallbackMessage: '保存失败' });
    }
  } finally {
    submitting.value = false;
  }
};

const handleDelete = async (id: string) => {
  try {
    await deleteRole(id);
    removeRowLocally(id);
    message.success('删除成功');
    await fetchData();
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '删除失败' });
  }
};

const scopeMap: Record<number, string> = { 1: '仅本人', 2: '本组数据', 3: '全部数据' };

const columns = [
  {
    title: '角色名称',
    key: 'roleName',
    render(row: any) {
      const code = String(row.roleCode || '');
      return h(
        'div',
        { style: 'display: flex; align-items: center; gap: 8px;' },
        [
          h('span', sanitizeRoleName(row)),
          SYSTEM_ROLE_CODES.has(code)
            ? h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => '系统内置' })
            : null
        ]
      );
    }
  },
  { title: '数据范围', key: 'dataScope', render(row: any) { return scopeMap[row.dataScope] || '-'; } },
  { title: '状态', key: 'status', render(row: any) { return h(NTag, { type: row.status ? 'success' : 'error' }, { default: () => (row.status ? '正常' : '停用') }); } },
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
            NPopconfirm,
            { onPositiveClick: () => handleDelete(row.id) },
            {
              trigger: () =>
                h(
                  NButton,
                  { size: 'small', type: 'error' },
                  { default: () => '删除' }
                ),
              default: () => '确认删除该角色吗？'
            }
          )
        ]
      );
    }
  }
];

onMounted(() => {
  fetchData();
  loadPermissionCatalog().catch(() => undefined);
});
</script>
