<template>
  <div class="talent-page">
    <n-card title="达人库（爬虫真实数据）" :bordered="false">
      <n-space style="margin-bottom: 16px" wrap>
        <n-input v-model:value="query.keyword" placeholder="昵称/达人号" clearable style="width: 220px" />
        <n-input v-model:value="query.region" placeholder="地域" clearable style="width: 140px" />
        <n-input-number v-model:value="query.minFans" :min="0" placeholder="最低粉丝" style="width: 140px" />
        <n-input-number v-model:value="query.maxFans" :min="0" placeholder="最高粉丝" style="width: 140px" />
        <n-input-number v-model:value="query.minScore" :min="0" :max="5" :step="0.1" placeholder="最低评分" style="width: 140px" />
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
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue';
import { NAvatar, NTag, useMessage } from 'naive-ui';
import { getTalentList } from '../../api/talent';

const message = useMessage();
const loading = ref(false);
const rows = ref<any[]>([]);

const query = reactive({
  keyword: '',
  region: '',
  minFans: null as number | null,
  maxFans: null as number | null,
  minScore: null as number | null,
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

const columns = [
  {
    title: '头像',
    key: 'avatarUrl',
    width: 80,
    render: (row: any) => h(NAvatar, { src: row.avatarUrl, round: true, size: 36 })
  },
  {
    title: '达人号',
    key: 'talentId',
    width: 180
  },
  {
    title: '昵称',
    key: 'nickname',
    minWidth: 160
  },
  {
    title: '粉丝数',
    key: 'fansCount',
    width: 120,
    render: (row: any) => formatFans(row.fansCount)
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
    render: (row: any) => row.mainCategory ? h(NTag, { type: 'info', size: 'small' }, { default: () => row.mainCategory }) : '-'
  },
  {
    title: '地域',
    key: 'region',
    width: 120,
    render: (row: any) => row.region || '-'
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
      minScore: query.minScore ?? undefined,
      page: query.page,
      size: query.size
    });

    const pageData = res?.data || {};
    rows.value = pageData.records || [];
    query.total = pageData.total || 0;
    pagination.itemCount = query.total;
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

