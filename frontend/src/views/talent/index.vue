<template>
  <div class="talent-page">
    <n-card title="达人 CRM" :bordered="false">
      <n-space style="margin-bottom: 16px" wrap>
        <n-input v-model:value="query.keyword" placeholder="昵称 / 抖音 UID" clearable style="width: 220px" />
        <n-input v-model:value="query.region" placeholder="地区" clearable style="width: 140px" />
        <n-input-number v-model:value="query.minFans" :min="0" placeholder="最低粉丝" style="width: 140px" />
        <n-input-number v-model:value="query.maxFans" :min="0" placeholder="最高粉丝" style="width: 140px" />
        <n-button type="primary" secondary @click="openCreateModal">新增达人</n-button>
        <n-button type="warning" secondary :loading="batchRefreshing" @click="handleRefreshWeekly">批量刷新</n-button>
        <n-button type="primary" :loading="loading" @click="fetchTalents(1)">查询</n-button>
      </n-space>

      <n-data-table
        remote
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        @update:page="(page:number) => fetchTalents(page)"
      />
    </n-card>

    <n-modal v-model:show="createModalVisible" preset="card" title="新增达人" style="width: 560px;">
      <n-form :model="createForm" label-placement="left" label-width="110">
        <n-form-item label="抖音 UID" path="douyinUid">
          <n-input v-model:value="createForm.douyinUid" placeholder="可选，优先使用" />
        </n-form-item>
        <n-form-item label="抖音号" path="douyinNo">
          <n-input v-model:value="createForm.douyinNo" placeholder="可选" />
        </n-form-item>
        <n-form-item label="主页链接" path="profileUrl">
          <n-input v-model:value="createForm.profileUrl" placeholder="可选，例如 https://www.douyin.com/user/xxx" />
        </n-form-item>
        <n-form-item label="达人昵称" path="nickname">
          <n-input v-model:value="createForm.nickname" placeholder="可选" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="createModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="creating" @click="handleCreate">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="manualModalVisible" preset="card" title="手动补录达人信息" style="width: 640px;">
      <n-form :model="manualForm" label-placement="left" label-width="120">
        <n-form-item label="达人昵称">
          <n-input v-model:value="manualForm.nickname" placeholder="选填" />
        </n-form-item>
        <n-form-item label="头像链接">
          <n-input v-model:value="manualForm.avatarUrl" placeholder="选填" />
        </n-form-item>
        <n-form-item label="粉丝数">
          <n-input-number v-model:value="manualForm.fansCount" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="获赞数">
          <n-input-number v-model:value="manualForm.likesCount" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="关注数">
          <n-input-number v-model:value="manualForm.followingCount" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="作品数">
          <n-input-number v-model:value="manualForm.worksCount" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="IP 属地">
          <n-input v-model:value="manualForm.ipLocation" placeholder="选填" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="manualModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="manualSubmitting" @click="submitManualFill">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue';
import { NAvatar, NButton, NTag, useMessage } from 'naive-ui';
import {
  claimTalent,
  createTalent,
  getLatestEnrichTask,
  getTalentList,
  manualFillTalent,
  refreshTalent,
  refreshWeeklyTalents
} from '../../api/talent';

const message = useMessage();
const loading = ref(false);
const refreshingId = ref<string | null>(null);
const batchRefreshing = ref(false);
const rows = ref<any[]>([]);
const creating = ref(false);
const createModalVisible = ref(false);
const manualModalVisible = ref(false);
const manualSubmitting = ref(false);
const currentManualTalentId = ref<string | null>(null);

const createForm = reactive({
  douyinUid: '',
  douyinNo: '',
  profileUrl: '',
  nickname: ''
});

const manualForm = reactive({
  nickname: '',
  avatarUrl: '',
  fansCount: null as number | null,
  likesCount: null as number | null,
  followingCount: null as number | null,
  worksCount: null as number | null,
  ipLocation: ''
});

const query = reactive({
  keyword: '',
  region: '',
  minFans: null as number | null,
  maxFans: null as number | null,
  page: 1,
  size: 10,
  total: 0
});

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: false
});

const formatFans = (fans?: number) => {
  if (fans === null || fans === undefined) return '-';
  if (fans >= 100000000) return `${(fans / 100000000).toFixed(1)}亿`;
  if (fans >= 10000) return `${(fans / 10000).toFixed(1)}万`;
  return `${fans}`;
};

const enrichStatusLabel = (status?: string) => {
  if (!status) return '未创建';
  const normalized = String(status).toUpperCase();
  if (normalized === 'PENDING') return '待处理';
  if (normalized === 'RUNNING') return '进行中';
  if (normalized === 'SUCCESS') return '成功';
  if (normalized === 'WAIT_MANUAL') return '待手动补录';
  if (normalized === 'FAILED') return '失败';
  return status;
};

const enrichStatusTagType = (status?: string): 'default' | 'info' | 'success' | 'warning' | 'error' => {
  if (!status) return 'default';
  const normalized = String(status).toUpperCase();
  if (normalized === 'PENDING') return 'default';
  if (normalized === 'RUNNING') return 'info';
  if (normalized === 'SUCCESS') return 'success';
  if (normalized === 'WAIT_MANUAL') return 'warning';
  if (normalized === 'FAILED') return 'error';
  return 'default';
};

const loadLatestEnrichTasks = async (items: any[]) => {
  if (!items?.length) return;
  const settled = await Promise.allSettled(
    items.map(async (item) => {
      if (!item?.id) return null;
      const res = await getLatestEnrichTask(item.id);
      return { id: item.id, task: res?.data || null };
    })
  );

  const taskMap = new Map<string, any>();
  settled.forEach((entry) => {
    if (entry.status === 'fulfilled' && entry.value?.id) {
      taskMap.set(entry.value.id, entry.value.task);
    }
  });

  rows.value = rows.value.map((row) => {
    const task = taskMap.get(row.id) || null;
    return {
      ...row,
      enrichTaskStatus: task?.taskStatus || null,
      enrichTaskError: task?.errorMsg || null
    };
  });
};

const handleRefresh = async (row: any) => {
  if (!row?.id) {
    message.warning('当前行缺少达人 ID');
    return;
  }
  refreshingId.value = row.id;
  try {
    await refreshTalent(row.id);
    message.success('达人信息刷新成功');
    await fetchTalents(pagination.page || 1);
  } catch (error: any) {
    message.error(error?.message || '刷新失败');
  } finally {
    refreshingId.value = null;
  }
};

const handleRefreshWeekly = async () => {
  batchRefreshing.value = true;
  try {
    await refreshWeeklyTalents();
    message.success('已触发批量刷新任务');
    await fetchTalents(pagination.page || 1);
  } catch (error: any) {
    message.error(error?.message || '批量刷新触发失败');
  } finally {
    batchRefreshing.value = false;
  }
};

const openCreateModal = () => {
  createForm.douyinUid = '';
  createForm.douyinNo = '';
  createForm.profileUrl = '';
  createForm.nickname = '';
  createModalVisible.value = true;
};

const handleCreate = async () => {
  const uid = resolveCreateUid();
  if (!uid) {
    message.warning('请至少填写：抖音 UID / 抖音号 / 主页链接 其中一项');
    return;
  }
  creating.value = true;
  try {
    const created: any = await createTalent({
      douyinUid: uid,
      douyinNo: createForm.douyinNo?.trim() || undefined,
      profileUrl: createForm.profileUrl?.trim() || undefined,
      nickname: createForm.nickname?.trim() || undefined
    });

    const createdId = created?.data?.id || created?.id;
    if (createdId) {
      try {
        await claimTalent(createdId);
        message.success('新增并认领成功');
      } catch (error: any) {
        message.warning(error?.message || '新增成功，但自动认领失败');
      }
    } else {
      message.success('新增达人成功');
    }

    createModalVisible.value = false;
    await fetchTalents(1);
  } catch (error: any) {
    message.error(error?.message || '新增达人失败');
  } finally {
    creating.value = false;
  }
};

const resolveCreateUid = () => {
  const douyinUid = createForm.douyinUid?.trim();
  if (douyinUid) return douyinUid;

  const douyinNo = createForm.douyinNo?.trim();
  if (douyinNo) return douyinNo;

  const profileUrl = createForm.profileUrl?.trim();
  if (!profileUrl) return '';

  const userMatch = profileUrl.match(/\/user\/([^/?]+)/i);
  if (userMatch?.[1]) return userMatch[1];

  const secUidMatch = profileUrl.match(/sec_uid=([^&]+)/i);
  if (secUidMatch?.[1]) return secUidMatch[1];

  return profileUrl;
};

const openManualFillModal = (row: any) => {
  if (!row?.id) {
    message.warning('当前行缺少达人 ID');
    return;
  }
  currentManualTalentId.value = row.id;
  manualForm.nickname = row.nickname || '';
  manualForm.avatarUrl = row.avatarUrl || '';
  manualForm.fansCount = row.fansCount ?? row.fans ?? null;
  manualForm.likesCount = row.likesCount ?? null;
  manualForm.followingCount = row.followingCount ?? null;
  manualForm.worksCount = row.worksCount ?? null;
  manualForm.ipLocation = row.ipLocation || '';
  manualModalVisible.value = true;
};

const submitManualFill = async () => {
  if (!currentManualTalentId.value) {
    message.warning('未选择达人');
    return;
  }
  manualSubmitting.value = true;
  try {
    await manualFillTalent(currentManualTalentId.value, {
      nickname: manualForm.nickname?.trim() || undefined,
      avatarUrl: manualForm.avatarUrl?.trim() || undefined,
      fansCount: manualForm.fansCount ?? undefined,
      likesCount: manualForm.likesCount ?? undefined,
      followingCount: manualForm.followingCount ?? undefined,
      worksCount: manualForm.worksCount ?? undefined,
      ipLocation: manualForm.ipLocation?.trim() || undefined
    });
    message.success('手动补录成功');
    manualModalVisible.value = false;
    await fetchTalents(pagination.page || 1);
  } catch (error: any) {
    message.error(error?.message || '手动补录失败');
  } finally {
    manualSubmitting.value = false;
  }
};

const columns = [
  {
    title: '头像',
    key: 'avatarUrl',
    width: 80,
    render: (row: any) => h(NAvatar, { src: row.avatarUrl, round: true, size: 36 })
  },
  {
    title: '抖音 UID',
    key: 'douyinUid',
    width: 180,
    render: (row: any) => row.douyinUid || row.talentId || '-'
  },
  {
    title: '昵称',
    key: 'nickname',
    minWidth: 160,
    render: (row: any) => row.nickname || '-'
  },
  {
    title: '粉丝数',
    key: 'fansCount',
    width: 120,
    render: (row: any) => formatFans(row.fansCount ?? row.fans)
  },
  {
    title: '评分',
    key: 'creditScore',
    width: 100,
    render: (row: any) => row.creditScore ?? '-'
  },
  {
    title: '主营类目',
    key: 'mainCategory',
    minWidth: 140,
    render: (row: any) => row.mainCategory
      ? h(NTag, { type: 'info', size: 'small' }, { default: () => row.mainCategory })
      : '-'
  },
  {
    title: '地区',
    key: 'region',
    width: 120,
    render: (row: any) => row.ipLocation || row.region || '-'
  },
  {
    title: '补全状态',
    key: 'enrichTaskStatus',
    width: 110,
    render: (row: any) => h(
      NTag,
      { size: 'small', type: enrichStatusTagType(row.enrichTaskStatus) },
      { default: () => enrichStatusLabel(row.enrichTaskStatus) }
    )
  },
  {
    title: '失败原因',
    key: 'enrichTaskError',
    minWidth: 200,
    render: (row: any) => row.enrichTaskError || '-'
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render: (row: any) => h(
      'div',
      { style: 'display:flex; gap:8px;' },
      [
        h(
          NButton,
          {
            size: 'small',
            type: 'primary',
            tertiary: true,
            loading: refreshingId.value === row.id,
            onClick: () => handleRefresh(row)
          },
          { default: () => '刷新' }
        ),
        h(
          NButton,
          {
            size: 'small',
            type: 'warning',
            tertiary: true,
            onClick: () => openManualFillModal(row)
          },
          { default: () => '手动补录' }
        )
      ]
    )
  }
];

const fetchTalents = async (page = 1) => {
  loading.value = true;
  try {
    query.page = page;
    pagination.page = page;
    const res = await getTalentList({
      keyword: query.keyword || undefined,
      region: query.region || undefined,
      minFans: query.minFans ?? undefined,
      maxFans: query.maxFans ?? undefined,
      page: query.page,
      size: query.size
    });

    const pageData = res?.data || {};
    rows.value = pageData.records || [];
    query.total = pageData.total || 0;
    pagination.itemCount = query.total;
    await loadLatestEnrichTasks(rows.value);
  } catch (error: any) {
    rows.value = [];
    pagination.itemCount = 0;
    message.error(error?.message || '获取达人列表失败');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchTalents(1);
});
</script>

<style scoped>
.talent-page {
  min-height: 100%;
}
</style>
