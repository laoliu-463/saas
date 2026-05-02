<template>
  <div class="talent-page">
    <PageHeader
      title="达人 CRM"
      description="查看达人归属、公海认领、寄样合作和订单产出，支撑渠道长期运营。"
    >
      <template #actions>
        <n-button type="primary" secondary :loading="loading" @click="fetchData">刷新达人</n-button>
        <n-button type="primary" @click="showCreate = true">新增达人</n-button>
      </template>
    </PageHeader>

    <div v-if="pagination.itemCount > 0" class="talent-summary">
      <n-space :size="16" align="center">
        <span class="summary-label">达人概览</span>
        <n-tag type="primary" size="small" round>总达人数: {{ pagination.itemCount }}</n-tag>
        <n-tag type="warning" size="small" round>公海: {{ poolSummary.public }} 人</n-tag>
        <n-tag type="success" size="small" round>私海: {{ poolSummary.private }} 人</n-tag>
      </n-space>
    </div>

    <div class="toolbar">
      <n-space wrap>
        <n-input
          v-model:value="filters.keyword"
          placeholder="搜索达人昵称 / 抖音号 / UID"
          clearable
          style="width: 280px"
          @keyup.enter="handleSearch"
        />
        <n-select
          v-model:value="filters.poolStatus"
          :options="poolOptions"
          placeholder="公海 / 私海"
          clearable
          style="width: 160px"
        />
        <n-button type="primary" @click="handleSearch">查询</n-button>
        <n-button @click="resetFilters">重置</n-button>
      </n-space>
    </div>

    <n-card :bordered="false" class="main-card">
      <n-data-table
        remote
        :columns="columns"
        :data="data"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: TalentListItem) => row.id"
        :scroll-x="1380"
        @update:page="handlePageChange"
      />

      <PageEmpty
        v-if="!loading && data.length === 0"
        title="暂无达人数据"
        description="可先在当前页面新增达人，或前往 /dev/test 初始化测试数据。"
        icon="CRM"
      >
        <template #action>
          <n-space>
            <n-button secondary type="primary" @click="showCreate = true">新增达人</n-button>
            <n-button @click="fetchData">重新加载</n-button>
          </n-space>
        </template>
      </PageEmpty>
    </n-card>

    <TalentCreateModal v-model:show="showCreate" @success="handleCreated" />
    <TalentDetailModal v-model:show="showDetail" :talent-id="activeTalentId" />
  </div>
</template>

<script setup lang="ts">
import { h, computed, onMounted, reactive, ref } from 'vue'
import { NButton, NSpace, NTag, useDialog, useMessage, type DataTableColumns } from 'naive-ui'
import PageEmpty from '../../components/PageEmpty.vue'
import PageHeader from '../../components/PageHeader.vue'
import { useAuthStore } from '../../stores/auth'
import {
  claimTalent,
  getTalentPage,
  releaseTalent,
  type TalentListItem
} from '../../api/talent'
import TalentCreateModal from './components/TalentCreateModal.vue'
import TalentDetailModal from './components/TalentDetailModal.vue'

const message = useMessage()
const dialog = useDialog()
const authStore = useAuthStore()

const loading = ref(false)
const showCreate = ref(false)
const showDetail = ref(false)
const activeTalentId = ref('')
const data = ref<TalentListItem[]>([])

const poolSummary = computed(() => {
  const list = data.value
  return {
    public: list.filter((r) => r.poolStatus !== 'PRIVATE').length,
    private: list.filter((r) => r.poolStatus === 'PRIVATE').length
  }
})

const filters = reactive<{
  keyword: string
  poolStatus: string | null
}>({
  keyword: '',
  poolStatus: null
})

const poolOptions = [
  { label: '公海达人', value: 'PUBLIC' },
  { label: '私海达人', value: 'PRIVATE' }
]

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: false
})

function formatFans(value?: number | null) {
  if (value === null || value === undefined) return '-'
  if (value >= 100000000) return `${(value / 100000000).toFixed(1)}亿`
  if (value >= 10000) return `${(value / 10000).toFixed(1)}万`
  return String(value)
}

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return `¥${(Number(value) / 100).toFixed(2)}`
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function isMine(row: TalentListItem) {
  const currentUserId = String(authStore.userInfo?.id || '')
  return Boolean(currentUserId) && currentUserId === String(row.ownerId || '')
}

function openDetail(row: TalentListItem) {
  activeTalentId.value = row.id
  showDetail.value = true
}

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getTalentPage({
      page: pagination.page,
      size: pagination.pageSize,
      keyword: filters.keyword || undefined,
      poolStatus: filters.poolStatus || undefined
    })
    data.value = Array.isArray(res?.data?.records) ? res.data.records : []
    pagination.itemCount = Number(res?.data?.total || 0)
  } catch (error: any) {
    message.error(error?.msg || error?.message || '加载达人列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchData()
}

function resetFilters() {
  filters.keyword = ''
  filters.poolStatus = null
  pagination.page = 1
  fetchData()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchData()
}

function handleCreated() {
  pagination.page = 1
  fetchData()
}

function handleClaim(row: TalentListItem) {
  dialog.warning({
    title: '确认认领达人',
    content: `认领后，该达人会进入你的私海并开始保护期。是否认领「${row.nickname || '未命名达人'}」？`,
    positiveText: '确认认领',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await claimTalent(row.id)
        message.success('认领成功，该达人已转入你的私海')
        fetchData()
      } catch (error: any) {
        message.error(error?.msg || error?.message || '认领失败')
      }
    }
  })
}

function handleRelease(row: TalentListItem) {
  dialog.warning({
    title: '确认释放达人',
    content: `释放后，该达人会回到公海。是否释放「${row.nickname || '未命名达人'}」？`,
    positiveText: '确认释放',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await releaseTalent(row.id)
        message.success('释放成功，该达人已回到公海')
        fetchData()
      } catch (error: any) {
        message.error(error?.msg || error?.message || '释放失败')
      }
    }
  })
}

const columns: DataTableColumns<TalentListItem> = [
  {
    title: '达人信息',
    key: 'info',
    width: 260,
    render: (row) =>
      h('div', { class: 'talent-main' }, [
        h('div', { class: 'talent-name' }, row.nickname || '未命名达人'),
        h('div', { class: 'talent-sub' }, `抖音号：${row.douyinNo || '-'}`),
        h('div', { class: 'talent-sub' }, `UID：${row.douyinUid || row.uid || '-'}`)
      ])
  },
  {
    title: '达人数据',
    key: 'stats',
    width: 220,
    render: (row) =>
      h('div', { class: 'stats-col' }, [
        h('div', null, `粉丝数：${formatFans(row.fansCount)}`),
        h('div', null, `获赞数：${formatFans(row.likesCount)}`),
        h('div', null, `作品数：${row.worksCount ?? '-'}`)
      ])
  },
  {
    title: '经营表现',
    key: 'business',
    width: 200,
    render: (row) =>
      h('div', { class: 'stats-col' }, [
        h('div', null, `近 30 天销售额：${formatMoney(row.monthlySales)}`),
        h('div', null, `订单数：${row.orderCount ?? 0}`),
        h('div', null, `服务费贡献：${formatMoney(row.serviceFeeContribution)}`)
      ])
  },
  {
    title: '标签',
    key: 'tags',
    width: 180,
    render: (row) =>
      h(NSpace, { size: 8, wrap: true }, {
        default: () => [
          h(NTag, { type: row.poolStatus === 'PRIVATE' ? 'success' : 'warning', size: 'small' }, {
            default: () => (row.poolStatus === 'PRIVATE' ? '私海' : '公海')
          }),
          h(NTag, { type: 'info', size: 'small' }, {
            default: () => `等级 ${row.level || '-'}`
          }),
          h(NTag, { type: 'default', size: 'small' }, {
            default: () => row.ipLocation || '未知地区'
          })
        ]
      })
  },
  {
    title: '归属信息',
    key: 'owner',
    width: 180,
    render: (row) =>
      h('div', { class: 'stats-col' }, [
        h('div', null, `认领人：${row.ownerName || '-'}`),
        h('div', null, `保护期截止：${formatDateTime(row.protectedUntil)}`)
      ])
  },
  {
    title: '合作记录',
    key: 'related',
    width: 140,
    render: (row) =>
      h('div', { class: 'stats-col' }, [
        h('div', null, `寄样：${row.sampleCount ?? 0}`),
        h('div', null, `订单：${row.orderCount ?? 0}`)
      ])
  },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 220,
    render: (row) =>
      h(NSpace, { size: 8 }, {
        default: () => [
          h(
            NButton,
            {
              size: 'small',
              quaternary: true,
              onClick: () => openDetail(row)
            },
            { default: () => '查看详情' }
          ),
          row.poolStatus === 'PUBLIC'
            ? h(
                NButton,
                {
                  size: 'small',
                  quaternary: true,
                  type: 'primary',
                  onClick: () => handleClaim(row)
                },
                { default: () => '认领' }
              )
            : isMine(row)
              ? h(
                  NButton,
                  {
                    size: 'small',
                    quaternary: true,
                    type: 'warning',
                    onClick: () => handleRelease(row)
                  },
                  { default: () => '释放' }
                )
              : null
        ]
      })
  }
]

onMounted(fetchData)
</script>

<style scoped>
.talent-page {
  padding: var(--spacing-xl);
}

.talent-summary {
  margin-bottom: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-md);
}

.summary-label {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
}

.toolbar {
  margin-bottom: var(--spacing-md);
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: var(--bg-card);
}

.main-card {
  border-radius: var(--radius-md);
}

.talent-main {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.talent-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.talent-sub,
.stats-col {
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
}
</style>
