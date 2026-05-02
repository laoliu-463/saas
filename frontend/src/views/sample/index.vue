<template>
  <div class="sample-kanban-page">
    <PageHeader title="寄样台" description="拖拽式管理寄样全流程：审核 → 发货 → 签收 → 交作业。">
      <template #meta>
        <n-tag v-if="totalCount >= 0" size="small" round type="info">共 {{ totalCount }} 单</n-tag>
      </template>
      <template #actions>
        <n-button :loading="loading" type="primary" @click="loadBoard">刷新看板</n-button>
      </template>
    </PageHeader>

    <div class="board-container" v-if="!loading || boardData">
      <KanbanColumn
        v-for="col in columns"
        :key="col.status"
        :status="col.status"
        :title="col.title"
        :dot-color="col.dotColor"
        :cards="boardData?.[col.status] || []"
      >
        <template #default="{ card }">
          <SampleCard
            :card="card"
            @approve="handleAction($event, 'APPROVED')"
            @reject="handleAction($event, 'REJECTED')"
            @ship="openLogistics($event)"
            @sign="handleAction($event, 'SIGNED')"
          />
        </template>
      </KanbanColumn>
    </div>

    <div v-else class="loading-placeholder">
      <n-spin size="large" />
    </div>

    <n-modal v-model:show="showLogistics" preset="dialog" title="填写物流单号">
      <n-form-item label="快递公司" required>
        <n-select
          v-model:value="logisticsForm.company"
          :options="[
            { label: '顺丰速运', value: 'SF' },
            { label: '中通快递', value: 'ZTO' },
            { label: '圆通速递', value: 'YTO' }
          ]"
        />
      </n-form-item>
      <n-form-item label="快递单号" required>
        <n-input v-model:value="logisticsForm.no" placeholder="输入真实单号或测试单号" />
      </n-form-item>
      <template #action>
        <n-button @click="showLogistics = false">取消</n-button>
        <n-button type="primary" :loading="actionLoading" @click="submitLogistics">确认发货</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import KanbanColumn from '../../components/KanbanColumn.vue'
import SampleCard from '../../components/SampleCard.vue'
import type { SampleBoardCard } from '../../components/SampleCard.vue'
import { getSampleBoard } from '../../api/sample'
import { testShipSample } from '../../api/test'
import request from '../../utils/request'

const message = useMessage()
const loading = ref(false)
const actionLoading = ref(false)
const showLogistics = ref(false)
const currentCard = ref<SampleBoardCard | null>(null)
const boardData = ref<Record<string, SampleBoardCard[]> | null>(null)

const logisticsForm = reactive({
  company: 'SF',
  no: ''
})

const columns = [
  { status: 'PENDING_AUDIT', title: '待审核', dotColor: 'var(--color-info)' },
  { status: 'PENDING_SHIP', title: '待发货', dotColor: 'var(--color-primary)' },
  { status: 'SHIPPED', title: '快递中', dotColor: 'var(--color-info)' },
  { status: 'PENDING_TASK', title: '待交作业', dotColor: 'var(--color-warning)' },
  { status: 'FINISHED', title: '已完成', dotColor: 'var(--color-success)' }
]

const totalCount = computed(() => {
  if (!boardData.value) return 0
  return Object.values(boardData.value).reduce((sum, list) => sum + list.length, 0)
})

async function loadBoard() {
  loading.value = true
  try {
    const res: any = await getSampleBoard()
    boardData.value = res.data || {}
  } catch {
    message.error('加载看板数据失败')
  } finally {
    loading.value = false
  }
}

function optimisticRemove(card: SampleBoardCard) {
  if (!boardData.value) return
  const list = boardData.value[card.status]
  if (!list) return
  const idx = list.findIndex(c => c.id === card.id)
  if (idx !== -1) list.splice(idx, 1)
}

function optimisticAdd(card: SampleBoardCard, newStatus: string) {
  if (!boardData.value) return
  if (!boardData.value[newStatus]) boardData.value[newStatus] = []
  boardData.value[newStatus].unshift({ ...card, status: newStatus })
}

async function handleAction(card: SampleBoardCard, action: string) {
  const statusMap: Record<string, string> = {
    APPROVED: 'PENDING_SHIP',
    REJECTED: 'REJECTED',
    SIGNED: 'SHIPPED'
  }
  const newStatus = statusMap[action]
  if (!newStatus) return

  optimisticRemove(card)
  if (action !== 'REJECTED') {
    optimisticAdd(card, newStatus)
  }

  try {
    await request.put(`/samples/${card.id}/status`, {
      action,
      reason: action === 'REJECTED' ? '看板操作拒绝' : undefined
    })
    message.success(action === 'APPROVED' ? '已通过' : action === 'REJECTED' ? '已拒绝' : '已签收')
    await loadBoard()
  } catch {
    message.error('操作失败')
    await loadBoard()
  }
}

function openLogistics(card: SampleBoardCard) {
  currentCard.value = card
  logisticsForm.no = `TEST${Date.now()}`
  showLogistics.value = true
}

async function submitLogistics() {
  if (!currentCard.value) return
  actionLoading.value = true
  const card = currentCard.value

  optimisticRemove(card)
  optimisticAdd(card, 'SHIPPED')

  try {
    await testShipSample(card.id)
    message.success('已模拟发货')
    showLogistics.value = false
    await loadBoard()
  } catch {
    message.error('发货失败')
    await loadBoard()
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadBoard)
</script>

<style scoped>
.sample-kanban-page {
  padding: var(--spacing-lg);
  min-height: calc(100vh - var(--header-height));
}

.board-container {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 16px;
}

.board-container::-webkit-scrollbar {
  height: 6px;
}

.board-container::-webkit-scrollbar-track {
  background: transparent;
}

.board-container::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 3px;
}

.loading-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}
</style>
