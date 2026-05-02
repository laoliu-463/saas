<template>
  <div class="role-list">
    <n-card title="角色管理" :bordered="false">
      <n-space style="margin-bottom: 16px;">
        <n-button type="primary" @click="openModal('add')">新增角色</n-button>
      </n-space>

      <n-data-table
        remote
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </n-card>

    <n-modal v-model:show="showModal" preset="card" :title="modalTitle" style="width: 500px;">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="left" label-width="100">
        <n-form-item label="角色编码" path="roleCode">
          <n-input v-model:value="formData.roleCode" placeholder="如 admin" :disabled="modalType === 'edit'" />
        </n-form-item>
        <n-form-item label="角色名称" path="roleName">
          <n-input v-model:value="formData.roleName" placeholder="如 管理员" />
        </n-form-item>
        <n-form-item label="数据范围" path="dataScope">
          <n-select v-model:value="formData.dataScope" :options="dataScopeOptions" />
        </n-form-item>
        <n-form-item label="状态" path="status">
          <n-switch v-model:value="formData.status" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <n-form-item label="备注" path="remark">
          <n-input v-model:value="formData.remark" type="textarea" placeholder="请输入备注" />
        </n-form-item>
        <div style="display: flex; justify-content: flex-end;">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" style="margin-left: 12px;" @click="handleSubmit" :loading="submitting">确定</n-button>
        </div>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue';
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui';
import { getRolePage, createRole, updateRole, deleteRole } from '../../api/sys';

const message = useMessage();

const loading = ref(false);
const data = ref([]);
const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50]
});

const dataScopeOptions = [
  { label: '全部数据', value: 3 },
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
      size: pagination.pageSize
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
    message.error(error?.message || '获取角色列表失败');
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

const showModal = ref(false);
const modalType = ref<'add'|'edit'>('add');
const modalTitle = ref('新增角色');
const formRef = ref();
const submitting = ref(false);

const formData = reactive({
  id: undefined as string | undefined,
  roleCode: '',
  roleName: '',
  dataScope: 1,
  status: 1,
  remark: ''
});

const isValidationFailure = (error: unknown): boolean => Array.isArray(error);

const rules = {
  roleCode: { required: true, message: '请输入角色编码', trigger: 'blur' },
  roleName: { required: true, message: '请输入角色名称', trigger: 'blur' },
  dataScope: { type: 'number', required: true, message: '请选择数据范围', trigger: 'change' }
};

const openModal = (type: 'add'|'edit', row?: any) => {
  modalType.value = type;
  modalTitle.value = type === 'add' ? '新增角色' : '编辑角色';
  
  if (type === 'add') {
    Object.assign(formData, { id: undefined, roleCode: '', roleName: '', dataScope: 1, status: 1, remark: '' });
  } else if (row) {
    Object.assign(formData, {
      id: row.id,
      roleCode: row.roleCode,
      roleName: row.roleName,
      dataScope: row.dataScope,
      status: row.status,
      remark: row.remark
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
      await createRole(formData);
      message.success('新增成功');
    } else {
      await updateRole(formData.id!, formData);
      message.success('编辑成功');
    }
    showModal.value = false;
    await fetchData();
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
    await deleteRole(id);
    removeRowLocally(id);
    message.success('删除成功');
    await fetchData();
  } catch (error: any) {
    message.error(error?.message || '删除失败');
  }
};

const scopeMap: Record<number, string> = { 1: '仅本人', 3: '全部数据' };

const columns = [
  { title: '角色编码', key: 'roleCode' },
  { title: '角色名称', key: 'roleName' },
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
});
</script>
